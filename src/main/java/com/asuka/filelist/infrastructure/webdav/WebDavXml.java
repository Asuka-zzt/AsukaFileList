package com.asuka.filelist.infrastructure.webdav;

import com.asuka.filelist.application.webdav.DavResource;
import com.asuka.filelist.common.util.FileTypeUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * WebDAV XML 构建：PROPFIND 207 Multi-Status 与 href 编码、日期格式化。
 */
final class WebDavXml {

    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC);

    private WebDavXml() {
    }

    /**
     * 构建 LOCK 响应体（lockdiscovery）。
     */
    static String lockDiscovery(String token, long timeoutSeconds) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<D:prop xmlns:D=\"DAV:\"><D:lockdiscovery><D:activelock>"
                + "<D:locktype><D:write/></D:locktype>"
                + "<D:lockscope><D:exclusive/></D:lockscope>"
                + "<D:depth>infinity</D:depth>"
                + "<D:timeout>Second-" + timeoutSeconds + "</D:timeout>"
                + "<D:locktoken><D:href>" + escape(token) + "</D:href></D:locktoken>"
                + "</D:activelock></D:lockdiscovery></D:prop>\n";
    }

    /**
     * 构建 multistatus；davPrefix 形如 /dav，resources 第一个为目标自身，其余为子项。
     */
    static String multiStatus(String davPrefix, List<DavResource> resources) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<D:multistatus xmlns:D=\"DAV:\">\n");
        for (DavResource r : resources) {
            appendResponse(sb, davPrefix, r);
        }
        sb.append("</D:multistatus>\n");
        return sb.toString();
    }

    private static void appendResponse(StringBuilder sb, String davPrefix, DavResource r) {
        sb.append("<D:response><D:href>").append(href(davPrefix, r)).append("</D:href>");
        sb.append("<D:propstat><D:prop>");
        sb.append("<D:displayname>").append(escape(r.name())).append("</D:displayname>");
        if (r.collection()) {
            sb.append("<D:resourcetype><D:collection/></D:resourcetype>");
        } else {
            sb.append("<D:resourcetype/>");
            sb.append("<D:getcontentlength>").append(r.size()).append("</D:getcontentlength>");
            sb.append("<D:getcontenttype>").append(escape(FileTypeUtils.guessContentType(r.name())))
                    .append("</D:getcontenttype>");
        }
        sb.append("<D:getlastmodified>").append(RFC1123.format(orEpoch(r.modified()))).append("</D:getlastmodified>");
        sb.append("<D:creationdate>").append(ISO.format(orEpoch(r.created()))).append("</D:creationdate>");
        sb.append("<D:supportedlock>")
                .append("<D:lockentry><D:lockscope><D:exclusive/></D:lockscope>")
                .append("<D:locktype><D:write/></D:locktype></D:lockentry>")
                .append("</D:supportedlock>");
        sb.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>\n");
    }

    /**
     * href = davPrefix + 逐段编码的路径（集合补尾斜杠）。
     */
    static String href(String davPrefix, DavResource r) {
        StringBuilder href = new StringBuilder(davPrefix);
        String path = r.path();
        if (!"/".equals(path)) {
            for (String seg : path.split("/")) {
                if (!seg.isEmpty()) {
                    href.append('/').append(encodeSegment(seg));
                }
            }
        }
        if (r.collection() && (href.length() == 0 || href.charAt(href.length() - 1) != '/')) {
            href.append('/');
        }
        return escape(href.toString());
    }

    /**
     * 路径段编码：保留 RFC3986 unreserved，其余百分号编码。
     */
    private static String encodeSegment(String segment) {
        StringBuilder out = new StringBuilder(segment.length());
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(Character.forDigit((c >> 4) & 0xF, 16))
                        .append(Character.forDigit(c & 0xF, 16));
            }
        }
        return out.toString();
    }

    private static Instant orEpoch(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    /**
     * XML 文本转义。
     */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
