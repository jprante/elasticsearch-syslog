package org.elasticsearch.common.syslog;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.cache.CacheLoader;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.joda.time.format.DateTimeFormat;
import org.elasticsearch.common.joda.time.format.DateTimeFormatter;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a syslog message with RFC 3164 or RFC 5424 date format
 */
public class MessageParser {

    private final static Pattern TWO_SPACES = Pattern.compile("  ");

    private final static DateTimeFormatter formatter =
            Joda.forPattern("dateOptionalTime", Locale.ROOT).printer();

    private final static DateTimeFormatter rfc3164Format =
            DateTimeFormat.forPattern("MMM d HH:mm:ss").withZoneUTC();

    private final static int RFC3164_LEN = 15;

    private final static int RFC5424_PREFIX_LEN = 19;

    private final DateTimeFormatter timeParser;

    private Cache<String, Long> timestampCache;

    private Map<String,String> fieldNames = new HashMap<String,String>() {{
        put("host", "host");
        put("facility", "facility");
        put("severity", "severity");
        put("timestamp", "timestamp");
        put("message", "message");
    }};

    private Map<String,Pattern> patterns;

    public MessageParser() {
        timeParser = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").withZoneUTC();
        timestampCache = CacheBuilder.newBuilder().maximumSize(1000).build(
                new CacheLoader<String, Long>() {

                    @Override
                    public Long load(String key) throws Exception {
                        return timeParser.parseMillis(key);
                    }
                });
    }

    public MessageParser setPatterns(Map<String,Pattern> patterns) {
        this.patterns = patterns;
        return this;
    }

    public MessageParser setFieldName(String name, String newName) {
        fieldNames.put(name, newName);
        return this;
    }

    public void parseMessage(String msg, XContentBuilder builder) throws IOException {
        int msgLen = msg.length();
        int pos = 0;
        if (msg.charAt(pos) != '<') {
            throw new ElasticsearchIllegalArgumentException("bad format: invalid priority: cannot find open bracket '<' " + msg);
        }
        int end = msg.indexOf('>');
        if (end < 0 || end > 6) {
            throw new ElasticsearchIllegalArgumentException("bad format: invalid priority: cannot find end bracket '>' " + msg);
        }
        int pri = Integer.parseInt(msg.substring(1, end));
        Facility facility = Facility.fromNumericalCode(pri / 8);
        Severity severity = Severity.fromNumericalCode(pri % 8);
        builder.field(fieldNames.get("facility"), facility.label())
                .field(fieldNames.get("severity"), severity.label());
        if (msgLen <= end + 1) {
            throw new ElasticsearchIllegalArgumentException("bad format: no data except priority " + msg);
        }
        pos = end + 1;
        if (msgLen > pos + 2 && "1 ".equals(msg.substring(pos, pos + 2))) {
            pos += 2;
        }
        long timestamp;
        char ch = msg.charAt(pos);
        if (ch == '-') {
            timestamp = System.currentTimeMillis();
            if (msgLen <= pos + 2) {
                throw new ElasticsearchIllegalArgumentException("bad syslog format (missing hostname)");
            }
            pos += 2;
        } else if (ch >= 'A' && ch <= 'Z') {
            if (msgLen <= pos + RFC3164_LEN) {
                throw new ElasticsearchIllegalArgumentException("bad timestamp format");
            }
            timestamp = parseRFC3164Time(msg.substring(pos, pos + RFC3164_LEN));
            pos += RFC3164_LEN + 1;
        } else {
            int sp = msg.indexOf(' ', pos);
            if (sp == -1) {
                throw new ElasticsearchIllegalArgumentException("bad timestamp format");
            }
            timestamp = parseRFC5424Date(msg.substring(pos, sp));
            pos = sp + 1;
        }
        builder.field(fieldNames.get("timestamp"), formatter.print(timestamp));
        int ns = msg.indexOf(' ', pos);
        if (ns == -1) {
            throw new ElasticsearchIllegalArgumentException("bad syslog format (missing hostname)");
        }
        String hostname = msg.substring(pos, ns);
        builder.field(fieldNames.get("host"), hostname);
        String data;
        if (msgLen > ns + 1) {
            pos = ns + 1;
            data = msg.substring(pos);
        } else {
            data = msg;
        }
        builder.field(fieldNames.get("message"), data);
        if (patterns != null) {
            for (Map.Entry<String,Pattern> entry : patterns.entrySet()) {
                Matcher m = entry.getValue().matcher(data);
                if (m.find()) {
                    builder.field(entry.getKey(), m.group(1));
                }
            }
        }
    }

    private Long parseRFC5424Date(String msg) {
        int len = msg.length();
        if (len <= RFC5424_PREFIX_LEN) {
            throw new ElasticsearchIllegalArgumentException("bad format: not a valid RFC5424 timestamp: " + msg);
        }
        String timestampPrefix = msg.substring(0, RFC5424_PREFIX_LEN);
        Long timestamp = timestampCache.getIfPresent(timestampPrefix);
        int pos = RFC5424_PREFIX_LEN;
        if (timestamp == null) {
            throw new ElasticsearchIllegalArgumentException("parse error: timestamp is null");
        }
        if (msg.charAt(pos) == '.') {
            boolean found = false;
            int end = pos + 1;
            if (len <= end) {
                throw new ElasticsearchIllegalArgumentException("bad timestamp format (no TZ)");
            }
            while (!found) {
                char ch = msg.charAt(end);
                if (ch >= '0' && ch <= '9') {
                    end++;
                } else {
                    found = true;
                }
            }
            if (end - (pos + 1) > 0) {
                long milliseconds = (long) (Double.parseDouble(msg.substring(pos, end)) * 1000.0);
                timestamp += milliseconds;
            } else {
                throw new ElasticsearchIllegalArgumentException("bad format: invalid timestamp (fractional portion): " + msg);
            }
            pos = end;
        }
        char ch = msg.charAt(pos);
        if (ch != 'Z') {
            if (ch == '+' || ch == '-') {
                if (len <= pos + 5) {
                    throw new ElasticsearchIllegalArgumentException("bad format: invalid timezone: " + msg);
                }
                int sign = ch == '+' ? +1 : -1;
                char[] hourzone = new char[5];
                for (int i = 0; i < 5; i++) {
                    hourzone[i] = msg.charAt(pos + 1 + i);
                }
                if (hourzone[0] >= '0' && hourzone[0] <= '9'
                        && hourzone[1] >= '0' && hourzone[1] <= '9'
                        && hourzone[2] == ':'
                        && hourzone[3] >= '0' && hourzone[3] <= '9'
                        && hourzone[4] >= '0' && hourzone[4] <= '9') {
                    int hourOffset = Integer.parseInt(msg.substring(pos + 1, pos + 3));
                    int minOffset = Integer.parseInt(msg.substring(pos + 4, pos + 6));
                    timestamp -= sign * ((hourOffset * 60) + minOffset) * 60000;
                } else {
                    throw new ElasticsearchIllegalArgumentException("bad format: invalid timezone: " + msg);
                }
            }
        }
        return timestamp;
    }

    private Long parseRFC3164Time(String timestamp) {
        DateTime now = DateTime.now();
        int year = now.getYear();
        timestamp = TWO_SPACES.matcher(timestamp).replaceFirst(" ");
        DateTime date;
        try {
            date = rfc3164Format.parseDateTime(timestamp);
        } catch (Exception e) {
            return 0L;
        }
        if (date != null) {
            DateTime fixed = date.withYear(year);
            if (fixed.isAfter(now) && fixed.minusMonths(1).isAfter(now)) {
                fixed = date.withYear(year - 1);
            } else if (fixed.isBefore(now) && fixed.plusMonths(1).isBefore(now)) {
                fixed = date.withYear(year + 1);
            }
            date = fixed;
        }
        return date == null ? 0L : date.getMillis();
    }

}
