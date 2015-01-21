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
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    private Map<String, String> fieldNames = new HashMap<String, String>() {{
        put("host", "host");
        put("facility", "facility");
        put("severity", "severity");
        put("timestamp", "timestamp");
        put("message", "message");
    }};

    private Map<String, Pattern> patterns;

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

    public MessageParser setPatterns(Map<String, Pattern> patterns) {
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
        try {
            if (data.startsWith("@cee:")) {
                data = data.substring(5);
            }
            JsonParser parser = new JsonParser(new StringReader(data));
            Map<String,Object> map = (Map<String,Object>)parser.parse();
            builder.map(map);
        } catch (Throwable t) {
            // ignore
        }
        String message = fieldNames.get("message");
        builder.field(message, data);
        if (patterns != null) {
            for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
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
        DateTime fixed = date.withYear(year);
        if (fixed.isAfter(now) && fixed.minusMonths(1).isAfter(now)) {
            fixed = date.withYear(year - 1);
        } else if (fixed.isBefore(now) && fixed.plusMonths(1).isBefore(now)) {
            fixed = date.withYear(year + 1);
        }
        return fixed.getMillis();
    }

    class JsonParser {

        private static final int DEFAULT_BUFFER_SIZE = 1024;

        private final Reader reader;

        private final char[] buf;

        private int index;

        private int fill;

        private int ch;

        private StringBuilder sb;

        private int start;

        public JsonParser(Reader reader) {
            this(reader, DEFAULT_BUFFER_SIZE);
        }

        public JsonParser(Reader reader, int buffersize) {
            this.reader = reader;
            buf = new char[buffersize];
            start = -1;
        }

        public Object parse() throws IOException {
            read();
            skipBlank();
            Object result = parseValue();
            skipBlank();
            if (ch != -1) {
                throw new IOException("unexpected character: " + ch);
            }
            return result;
        }

        private Object parseValue() throws IOException {
            switch (ch) {
                case 'n':
                    return parseNull();
                case 't':
                    return parseTrue();
                case 'f':
                    return parseFalse();
                case '"':
                    return parseString();
                case '[':
                    return parseList();
                case '{':
                    return parseMap();
                case '-':
                case '+':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    return parseNumber();
            }
            throw new IOException("value");
        }

        private List<Object> parseList() throws IOException {
            read();
            List<Object> list = new ArrayList<Object>();
            skipBlank();
            if (parseChar(']')) {
                return list;
            }
            do {
                skipBlank();
                list.add(parseValue());
                skipBlank();
            } while (parseChar(','));
            if (!parseChar(']')) {
                expected("',' or ']'");
            }
            return list;
        }

        private Map<String,Object> parseMap() throws IOException {
            read();
            Map<String,Object> object = new LinkedHashMap<String,Object>();
            skipBlank();
            if (parseChar('}')) {
                return object;
            }
            do {
                skipBlank();
                if (ch != '"') {
                    expected("name");
                }
                String name = parseString();
                skipBlank();
                if (!parseChar(':')) {
                    expected("':'");
                }
                skipBlank();
                object.put(name, parseValue());
                skipBlank();
            } while (parseChar(','));
            if (!parseChar('}')) {
                expected("',' or '}'");
            }
            return object;
        }

        private Object parseNull() throws IOException {
            read();
            checkForChar('u');
            checkForChar('l');
            checkForChar('l');
            return null;
        }

        private Object parseTrue() throws IOException {
            read();
            checkForChar('r');
            checkForChar('u');
            checkForChar('e');
            return Boolean.TRUE;
        }

        private Object parseFalse() throws IOException {
            read();
            checkForChar('a');
            checkForChar('l');
            checkForChar('s');
            checkForChar('e');
            return Boolean.FALSE;
        }

        private void checkForChar(char ch) throws IOException {
            if (!parseChar(ch)) {
                expected("'" + ch + "'");
            }
        }

        private String parseString() throws IOException {
            read();
            startCapture();
            while (ch != '"') {
                if (ch == '\\') {
                    pauseCapture();
                    parseEscaped();
                    startCapture();
                } else if (ch < 0x20) {
                    expected("valid string character");
                } else {
                    read();
                }
            }
            String s = endCapture();
            read();
            return s;
        }

        private void parseEscaped() throws IOException {
            read();
            switch (ch) {
                case '"':
                case '/':
                case '\\':
                    sb.append((char) ch);
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'u':
                    char[] hex = new char[4];
                    for (int i = 0; i < 4; i++) {
                        read();
                        if (!isHexDigit()) {
                            expected("hexadecimal digit");
                        }
                        hex[i] = (char) ch;
                    }
                    sb.append((char) Integer.parseInt(String.valueOf(hex), 16));
                    break;
                default:
                    expected("valid escape sequence");
            }
            read();
        }

        private Object parseNumber() throws IOException {
            startCapture();
            parseChar('-');
            int firstDigit = ch;
            if (!parseDigit()) {
                expected("digit");
            }
            if (firstDigit != '0') {
                while (parseDigit()) {
                }
            }
            parseFraction();
            parseExponent();
            return endCapture();
        }

        private boolean parseFraction() throws IOException {
            if (!parseChar('.')) {
                return false;
            }
            if (!parseDigit()) {
                expected("digit");
            }
            while (parseDigit()) {
            }
            return true;
        }

        private boolean parseExponent() throws IOException {
            if (!parseChar('e') && !parseChar('E')) {
                return false;
            }
            if (!parseChar('+')) {
                parseChar('-');
            }
            if (!parseDigit()) {
                expected("digit");
            }
            while (parseDigit()) {
            }
            return true;
        }

        private boolean parseChar(char ch) throws IOException {
            if (this.ch != ch) {
                return false;
            }
            read();
            return true;
        }

        private boolean parseDigit() throws IOException {
            if (!isDigit()) {
                return false;
            }
            read();
            return true;
        }

        private void skipBlank() throws IOException {
            while (isWhiteSpace()) {
                read();
            }
        }

        private void read() throws IOException {
            if (ch == -1) {
                throw new IOException("unexpected end of input");
            }
            if (index == fill) {
                if (start != -1) {
                    sb.append(buf, start, fill - start);
                    start = 0;
                }
                fill = reader.read(buf, 0, buf.length);
                index = 0;
                if (fill == -1) {
                    ch = -1;
                    return;
                }
            }
            ch = buf[index++];
        }

        private void startCapture() {
            if (sb == null) {
                sb = new StringBuilder();
            }
            start = index - 1;
        }

        private void pauseCapture() {
            int end = ch == -1 ? index : index - 1;
            sb.append(buf, start, end - start);
            start = -1;
        }

        private String endCapture() {
            int end = ch == -1 ? index : index - 1;
            String captured;
            if (sb.length() > 0) {
                sb.append(buf, start, end - start);
                captured = sb.toString();
                sb.setLength(0);
            } else {
                captured = new String(buf, start, end - start);
            }
            start = -1;
            return captured;
        }

        private boolean isWhiteSpace() {
            return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
        }

        private boolean isDigit() {
            return ch >= '0' && ch <= '9';
        }

        private boolean isHexDigit() {
            return ch >= '0' && ch <= '9'
                    || ch >= 'a' && ch <= 'f'
                    || ch >= 'A' && ch <= 'F';
        }

        private void expected(String expected) throws IOException {
            if (ch == -1) {
                throw new IOException("unexpected end of input");
            }
            throw new IOException("expected " + expected);
        }
    }

}
