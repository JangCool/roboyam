package com.community.aimember.service;

import com.community.aimember.entity.AiNewsSourceKind;
import com.community.zconfiguration.time.ServiceTime;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 뉴스 피드 주소 만들기 · RSS 2.0 / RSS 1.0(RDF) / Atom XML 읽기(순수 함수 — ai-news-reporter-contract 「소재 수집」). JDK XML 파서만 쓴다(외부 라이브러리 없음, DTD·외부 엔티티 끔).
 * 항목마다 제목 · 원문 링크 · 짧은 요약(HTML 을 걷은 설명) · 출처 이름 · 발행 시각만 꺼낸다 — 본문({@code content:encoded}·Atom {@code content})·사진은 읽지 않는다.
 *
 * <ul>
 *   <li>{@code RSS} — 관리자가 넣은 피드 주소 그대로(http·https 만). 출처 이름 = 관리자가 정한 이름.</li>
 *   <li>{@code YOUTUBE} — {@value #YOUTUBE_FEED}{@code <채널 ID>}(Atom). 요약 = {@code media:description} 앞부분. 출처 이름 = 관리자가 정한 이름.</li>
 *   <li>{@code KEYWORD} — 구글 뉴스 RSS 검색. 출처 이름 = 항목의 {@code <source>}(원 언론사), 없으면 제목 끝 「 - 언론사」. 제목 끝의 「 - 언론사」는 뗀다.</li>
 * </ul>
 */
public final class AiNewsFeeds {

    static final String YOUTUBE_FEED = "https://www.youtube.com/feeds/videos.xml?channel_id=";
    static final String GOOGLE_NEWS_SEARCH = "https://news.google.com/rss/search?q=";
    /** 유튜브 채널 ID 모양(관리자 저장 검사와 같이 본다) */
    public static final Pattern CHANNEL_ID = Pattern.compile("[A-Za-z0-9_-]{10,64}");
    /** 출처 하나에서 한 번에 받는 최대 항목 수(피드 앞쪽 = 최신) */
    static final int ITEMS_PER_SOURCE = 30;

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final DateTimeFormatter PLAIN_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]", Locale.ROOT);

    private AiNewsFeeds() {
    }

    /** 피드에서 꺼낸 항목 하나 — 링크는 http(s) 절대 주소 */
    public record FeedItem(String title, String link, String summary, String sourceName, Instant publishedAt) {
    }

    /** 피드가 아니거나 읽을 수 없다 — {@code code} 는 출처 줄 오류 칸에 남기는 짧은 사유 */
    public static final class FeedException extends Exception {

        private final String code;

        public FeedException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** 출처 → 받을 주소. 주소가 틀리면 null(관리자 저장 검사가 먼저 막는다) */
    public static String feedUrl(AiNewsSourceKind kind, String address) {
        String a = address == null ? "" : address.strip();
        if (a.isEmpty()) {
            return null;
        }
        return switch (kind) {
            case RSS -> httpUrl(a) ? a : null;
            case YOUTUBE -> CHANNEL_ID.matcher(a).matches() ? YOUTUBE_FEED + a : null;
            case KEYWORD -> GOOGLE_NEWS_SEARCH + URLEncoder.encode(a, StandardCharsets.UTF_8) + "&hl=ko&gl=KR&ceid=KR:ko";
        };
    }

    /** http·https 절대 주소인가 */
    public static boolean httpUrl(String value) {
        try {
            URI u = URI.create(value.strip());
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && u.getHost() != null && !u.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 피드 읽기 — 앞에서부터 최대 {@value #ITEMS_PER_SOURCE}개. 제목·링크가 없는 항목은 버린다.
     *
     * @param defaultSourceName 출처 이름(KEYWORD 는 항목의 원 언론사가 없을 때만)
     * @throws FeedException XML 이 아니거나(PARSE_ERROR) RSS·Atom 이 아니다(NOT_FEED)
     */
    public static List<FeedItem> parse(byte[] xml, AiNewsSourceKind kind, String defaultSourceName) throws FeedException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            doc = b.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new FeedException("PARSE_ERROR");
        }
        Element root = doc.getDocumentElement();
        String rootName = local(root);
        List<Element> entries;
        boolean atom;
        if ("rss".equals(rootName) || "RDF".equals(rootName)) {
            entries = descendants(root, "item");
            atom = false;
        } else if ("feed".equals(rootName)) {
            entries = children(root, "entry");
            atom = true;
        } else {
            throw new FeedException("NOT_FEED");
        }
        List<FeedItem> out = new ArrayList<>();
        for (Element e : entries) {
            if (out.size() >= ITEMS_PER_SOURCE) {
                break;
            }
            FeedItem item = atom ? atomItem(e, kind, defaultSourceName) : rssItem(e, kind, defaultSourceName);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private static FeedItem rssItem(Element e, AiNewsSourceKind kind, String defaultSourceName) {
        String title = plain(text(e, "title"));
        String link = text(e, "link").strip();
        if (link.isEmpty()) {
            String guid = text(e, "guid").strip();
            link = httpUrl(guid) ? guid : "";
        }
        String summary = plain(text(e, "description"));
        String dateText = text(e, "pubDate");
        if (dateText.isBlank()) {
            dateText = text(e, "date"); // dc:date(RSS 1.0)
        }
        String sourceName = defaultSourceName;
        if (kind == AiNewsSourceKind.KEYWORD) {
            String src = plain(text(e, "source"));
            String[] split = splitPublisher(title, src);
            title = split[0];
            sourceName = split[1].isEmpty() ? defaultSourceName : split[1];
            summary = ""; // 구글 뉴스 설명은 관련 기사 링크 목록이다 — 요약으로 쓰지 않는다
        }
        return item(title, link, summary, sourceName, parseDate(dateText));
    }

    private static FeedItem atomItem(Element e, AiNewsSourceKind kind, String defaultSourceName) {
        String title = plain(text(e, "title"));
        String link = "";
        for (Element l : children(e, "link")) {
            String rel = l.getAttribute("rel");
            if (rel.isEmpty() || "alternate".equals(rel)) {
                link = l.getAttribute("href").strip();
                break;
            }
        }
        String summary = plain(text(e, "summary"));
        if (summary.isEmpty()) {
            List<Element> desc = descendants(e, "description"); // 유튜브 media:group/media:description
            summary = desc.isEmpty() ? "" : plain(desc.get(0).getTextContent());
        }
        String dateText = text(e, "published");
        if (dateText.isBlank()) {
            dateText = text(e, "updated");
        }
        return item(title, link, summary, defaultSourceName, parseDate(dateText));
    }

    private static FeedItem item(String title, String link, String summary, String sourceName, Instant publishedAt) {
        if (title.isEmpty() || !httpUrl(link) || link.length() > 1000) {
            return null;
        }
        return new FeedItem(title, link.strip(), summary, sourceName == null ? "" : sourceName.strip(), publishedAt);
    }

    /** 구글 뉴스 제목 「기사 제목 - 언론사」 → [제목, 언론사]. {@code source} 가 있으면 그 이름을 쓰고 제목 끝의 같은 이름을 뗀다 */
    static String[] splitPublisher(String title, String source) {
        String t = title == null ? "" : title.strip();
        String s = source == null ? "" : source.strip();
        int dash = t.lastIndexOf(" - ");
        if (dash > 0) {
            String tail = t.substring(dash + 3).strip();
            if (s.isEmpty() || tail.equalsIgnoreCase(s)) {
                return new String[]{t.substring(0, dash).strip(), s.isEmpty() ? tail : s};
            }
        }
        return new String[]{t, s};
    }

    /** HTML 태그를 걷고 엔티티를 풀고 공백을 하나로 */
    static String plain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String t = TAGS.matcher(html).replaceAll(" ");
        t = HtmlUtils.htmlUnescape(t).replace(' ', ' ');
        t = TAGS.matcher(t).replaceAll(" "); // 두 번 이스케이프된 설명(&lt;p&gt;)
        return SPACES.matcher(t).replaceAll(" ").strip();
    }

    /** RFC 1123(RSS) · ISO 8601(Atom) · 「yyyy-MM-dd HH:mm:ss」(한국 시각으로 보는 일부 국내 피드). 못 읽으면 null */
    static Instant parseDate(String raw) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // 다음 모양
        }
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
            // 다음 모양
        }
        try {
            return LocalDateTime.parse(v, PLAIN_DATE_TIME).atZone(ServiceTime.ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ── DOM 도우미 ───────────────────────────────────────────────────────

    private static String local(Node n) {
        String name = n.getLocalName();
        return name != null ? name : n.getNodeName();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n instanceof Element el && localName.equals(local(el))) {
                out.add(el);
            }
        }
        return out;
    }

    private static List<Element> descendants(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList list = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < list.getLength(); i++) {
            out.add((Element) list.item(i));
        }
        return out;
    }

    /** 바로 아래 자식 중 그 이름의 첫 글자 */
    private static String text(Element parent, String localName) {
        List<Element> found = children(parent, localName);
        return found.isEmpty() ? "" : found.get(0).getTextContent();
    }
}
