package org.elasticsearch.syslog;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.ChannelBufferBytesReference;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.joda.time.format.DateTimeFormat;
import org.elasticsearch.common.joda.time.format.DateTimeFormatter;
import org.elasticsearch.common.netty.bootstrap.ConnectionlessBootstrap;
import org.elasticsearch.common.netty.bootstrap.ServerBootstrap;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.channel.Channel;
import org.elasticsearch.common.netty.channel.ChannelHandlerContext;
import org.elasticsearch.common.netty.channel.ChannelPipeline;
import org.elasticsearch.common.netty.channel.ChannelPipelineFactory;
import org.elasticsearch.common.netty.channel.Channels;
import org.elasticsearch.common.netty.channel.ExceptionEvent;
import org.elasticsearch.common.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.elasticsearch.common.netty.channel.MessageEvent;
import org.elasticsearch.common.netty.channel.ReceiveBufferSizePredictorFactory;
import org.elasticsearch.common.netty.channel.SimpleChannelUpstreamHandler;
import org.elasticsearch.common.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.elasticsearch.common.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.syslog.MessageParser;
import org.elasticsearch.common.transport.PortsRange;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.util.concurrent.EsExecutors.daemonThreadFactory;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class SyslogService extends AbstractLifecycleComponent<SyslogService> {

    private final Client client;

    private final NetworkService networkService;

    private final String host;

    private final String port;

    private final ByteSizeValue receiveBufferSize;

    private final ReceiveBufferSizePredictorFactory receiveBufferSizePredictorFactory;

    private final int bulkActions;

    private final ByteSizeValue bulkSize;

    private final TimeValue flushInterval;

    private final int concurrentRequests;

    private final MessageParser messageParser;

    private final String index;

    private final String type;

    private final boolean isTimeWindow;

    private DateTimeFormatter formatter;

    private BulkProcessor bulkProcessor;

    private ConnectionlessBootstrap udpBootstrap;

    private ServerBootstrap tcpBootstrap;

    private Channel udpChannel;

    private Channel tcpChannel;

    @Inject
    public SyslogService(Settings settings, Client client, NetworkService networkService) {
        super(settings);
        this.client = client;
        this.networkService = networkService;
        this.host = componentSettings.get("host");
        this.port = componentSettings.get("port", "9500-9600");
        this.bulkActions = componentSettings.getAsInt("bulk_actions", 1000);
        this.bulkSize = componentSettings.getAsBytesSize("bulk_size", new ByteSizeValue(5, ByteSizeUnit.MB));
        this.flushInterval = componentSettings.getAsTime("flush_interval", TimeValue.timeValueSeconds(5));
        this.concurrentRequests = componentSettings.getAsInt("concurrent_requests", 4);
        this.receiveBufferSize = componentSettings.getAsBytesSize("receive_buffer_size", new ByteSizeValue(10, ByteSizeUnit.MB));
        this.receiveBufferSizePredictorFactory =
                new FixedReceiveBufferSizePredictorFactory(componentSettings.getAsBytesSize("receive_predictor_size", receiveBufferSize).bytesAsInt());
        this.index = componentSettings.get("index", "'syslog-'YYYY.MM.dd");
        this.isTimeWindow = componentSettings.getAsBoolean("index_is_timewindow", true);
        if (isTimeWindow) {
            formatter = DateTimeFormat.forPattern(index);
        }
        this.type = componentSettings.get("type", "syslog");
        Map<String,Object> map = (Map<String,Object>)componentSettings.getAsStructuredMap().get("patterns");
        Map<String, Pattern> patterns = newHashMap();
        if (map != null) {
            for (String key : map.keySet()) {
                patterns.put(key, Pattern.compile((String)map.get(key)));
            }
        }
        this.messageParser = new MessageParser().setPatterns(patterns);
        map = (Map<String,Object>)componentSettings.getAsStructuredMap().get("field_names");
        if (map != null) {
            for (String key : map.keySet()) {
                messageParser.setFieldName(key, (String)map.get(key));
            }
        }
        logger.info("syslog server at host [{}], port [{}], bulk_actions [{}], bulk_size [{}], flush_interval [{}], concurrent_requests [{}], index [{}], type [{}], patterns [{}]",
                host, port, bulkActions, bulkSize, flushInterval, concurrentRequests, index, type, patterns);
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        bulkProcessor = BulkProcessor.builder(client, new BulkListener())
                .setBulkActions(bulkActions)
                .setBulkSize(bulkSize)
                .setFlushInterval(flushInterval)
                .setConcurrentRequests(concurrentRequests)
                .build();
        initializeUDP();
        initializeTCP();
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        if (udpChannel != null) {
            udpChannel.close().awaitUninterruptibly();
        }
        if (udpBootstrap != null) {
            udpBootstrap.releaseExternalResources();
        }
        if (tcpChannel != null) {
            tcpChannel.close().awaitUninterruptibly();
        }
        if (tcpBootstrap != null) {
            tcpBootstrap.releaseExternalResources();
        }
        bulkProcessor.close();
    }

    @Override
    protected void doClose() throws ElasticsearchException {
    }

    private void initializeUDP() {
        udpBootstrap = new ConnectionlessBootstrap(new NioDatagramChannelFactory(Executors.newCachedThreadPool(daemonThreadFactory(settings, "syslog_udp_worker"))));
        udpBootstrap.setOption("receiveBufferSize", receiveBufferSize.bytesAsInt());
        udpBootstrap.setOption("receiveBufferSizePredictorFactory", receiveBufferSizePredictorFactory);
        udpBootstrap.setOption("broadcast", "false");
        udpBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new Handler("udp"));
            }
        });

        InetAddress address;
        try {
            address = networkService.resolveBindHostAddress(host);
        } catch (IOException e) {
            logger.warn("failed to resolve host {}", e, host);
            return;
        }
        final InetAddress hostAddress = address;
        PortsRange portsRange = new PortsRange(port);
        final AtomicReference<Exception> lastException = new AtomicReference<>();
        boolean success = portsRange.iterate(new PortsRange.PortCallback() {
            @Override
            public boolean onPortNumber(int portNumber) {
                try {
                    udpChannel = udpBootstrap.bind(new InetSocketAddress(hostAddress, portNumber));
                } catch (Exception e) {
                    lastException.set(e);
                    return false;
                }
                return true;
            }
        });
        if (!success) {
            logger.warn("failed to bind to {}/{}", lastException.get(), hostAddress, port);
            return;
        }
        logger.info("UDP listener running, address {}", udpChannel.getLocalAddress());
    }

    private void initializeTCP() {
        tcpBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(daemonThreadFactory(settings, "syslog_tcp_boss")),
                Executors.newCachedThreadPool(daemonThreadFactory(settings, "syslog_tcp_worker")),
                componentSettings.getAsInt("tcp.worker", 4)));
        tcpBootstrap.setOption("receiveBufferSize", receiveBufferSize.bytesAsInt());
        tcpBootstrap.setOption("receiveBufferSizePredictorFactory", receiveBufferSizePredictorFactory);
        tcpBootstrap.setOption("reuseAddress", componentSettings.getAsBoolean("tcp.reuse_address", true));
        tcpBootstrap.setOption("tcpNoDelay", componentSettings.getAsBoolean("tcp.no_delay", true));
        tcpBootstrap.setOption("keepAlive", componentSettings.getAsBoolean("tcp.keep_alive", true));

        tcpBootstrap.setOption("child.receiveBufferSize", receiveBufferSize.bytesAsInt());
        tcpBootstrap.setOption("child.receiveBufferSizePredictorFactory", receiveBufferSizePredictorFactory);
        tcpBootstrap.setOption("child.reuseAddress", componentSettings.getAsBoolean("tcp.reuse_address", true));
        tcpBootstrap.setOption("child.tcpNoDelay", componentSettings.getAsBoolean("tcp.no_delay", true));
        tcpBootstrap.setOption("child.keepAlive", componentSettings.getAsBoolean("tcp.keep_alive", true));

        tcpBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new Handler("tcp"));
            }
        });

        InetAddress address;
        try {
            address = networkService.resolveBindHostAddress(host);
        } catch (IOException e) {
            logger.warn("failed to resolve host {}", e, host);
            return;
        }
        final InetAddress hostAddress = address;
        PortsRange portsRange = new PortsRange(port);
        final AtomicReference<Exception> lastException = new AtomicReference<>();
        boolean success = portsRange.iterate(new PortsRange.PortCallback() {
            @Override
            public boolean onPortNumber(int portNumber) {
                try {
                    tcpChannel = tcpBootstrap.bind(new InetSocketAddress(hostAddress, portNumber));
                } catch (Exception e) {
                    lastException.set(e);
                    return false;
                }
                return true;
            }
        });
        if (!success) {
            logger.warn("failed to bind to {}/{}", lastException.get(), hostAddress, port);
            return;
        }
        logger.info("TCP listener running, address {}", tcpChannel.getLocalAddress());
    }

    class Handler extends SimpleChannelUpstreamHandler {

        private final String protocol;

        Handler(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
            XContentBuilder builder = jsonBuilder();
            parse(ctx, buffer, builder);
            IndexRequest indexRequest = new IndexRequest(isTimeWindow ? formatter.print(new DateTime()) : index)
                    .type(type)
                    .opType(IndexRequest.OpType.INDEX)
                    .source(builder);
            try {
                bulkProcessor.add(indexRequest);
            } catch (Exception e1) {
                logger.warn("failed to execute bulk request", e1);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            if (e.getCause() instanceof BindException) {
                // ignore, this happens when we retry binding to several ports, its fine if we fail...
                return;
            }
            logger.warn("failure caught", e.getCause());
        }

        private void parse(ChannelHandlerContext ctx, ChannelBuffer buffer, XContentBuilder builder) throws IOException {
            SocketAddress localAddress = ctx.getChannel().getLocalAddress();
            SocketAddress remoteAddress = ctx.getChannel().getRemoteAddress();
            ChannelBufferBytesReference ref = new ChannelBufferBytesReference(buffer);
            try {
                builder.startObject();
                builder.field("protocol", protocol);
                if (localAddress != null) {
                    builder.field("local", localAddress.toString());
                }
                if (remoteAddress != null) {
                    builder.field("remote", remoteAddress.toString());
                }
                messageParser.parseMessage(ref.toUtf8(), builder);
                builder.endObject();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    class BulkListener implements BulkProcessor.Listener {

        @Override
        public void beforeBulk(long executionId, BulkRequest request) {
            if (logger.isTraceEnabled()) {
                logger.trace("[{}] executing [{}]/[{}]", executionId, request.numberOfActions(), new ByteSizeValue(request.estimatedSizeInBytes()));
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            if (logger.isTraceEnabled()) {
                logger.trace("[{}] executed  [{}]/[{}], took [{}]", executionId, request.numberOfActions(), new ByteSizeValue(request.estimatedSizeInBytes()), response.getTook());
            }
            if (response.hasFailures()) {
                logger.warn("[{}] failed to execute bulk request: {}", executionId, response.buildFailureMessage());
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, Throwable e) {
            logger.warn("[{}] failed to execute bulk request", e, executionId);
        }
    }
}
