package com.community.site.service;

import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.RenderedIndex;
import com.community.site.dto.response.SiteIconResponse;
import com.community.site.dto.response.SiteInfoResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * 화면(SPA)의 {@code index.html} <b>머리(head)를 사이트 설정으로 채운다</b>. 탭 아이콘·매니페스트 링크·링크 미리보기 메타(og·twitter)는 화면이 뜬 뒤 자바스크립트로 넣으면
 * 크롤러(카카오톡·페이스북·검색엔진)가 읽지 못하므로 서버가 처음부터 넣어 내린다.
 *
 * <ul>
 *   <li>빌드가 만든 {@code classpath:/static/index.html} 을 틀로 쓴다. 없으면(화면을 아직 빌드하지 않은 환경) {@link Optional#empty()} — 부르는 쪽이 그대로 지나가게 한다.</li>
 *   <li>틀 머리에 이미 있는 {@code <title>}·{@code description}·{@code og:*}·{@code twitter:*}·매니페스트 링크는 지우고 우리 것을 머리 앞({@code <meta charset>} 바로 뒤)에 넣는다.
 *       {@code <script>}·{@code <style>} 안은 건드리지 않는다. 아이콘 링크는 운영자가 아이콘을 올렸을 때만 틀의 것을 지우고 바꿔 넣는다(없으면 틀의 링크를 두고, 틀에도 없으면 기본 SVG 아이콘).</li>
 *   <li>이름·소개·주소는 모두 HTML 이스케이프한다(속성값 포함). 설정 행이 없으면(설치 전) 틀을 그대로 내린다.</li>
 *   <li>결과는 설정 사본({@link PublicSiteSnapshot})이 바뀔 때까지 캐시한다.</li>
 * </ul>
 */
@Service
public class IndexHtmlRenderer {

    private static final Logger log = LoggerFactory.getLogger(IndexHtmlRenderer.class);

    private static final String INDEX = "static/index.html";
    private static final String MANIFEST_URL = "/manifest.webmanifest";
    private static final String TITLE_SEPARATOR = " — ";
    /** 관리자 미리보기 응답에만 넣는 관리자 origin 메타 이름(user-web {@code plugins/03.admin-preview.client.ts} 가 읽는다) */
    static final String ADMIN_ORIGIN_META = "roboyam-admin-origin";

    private static final Pattern HEAD_OPEN = Pattern.compile("<head(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAD_CLOSE = Pattern.compile("</head\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("<(script|style)\\b[^>]*>.*?</\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_META = Pattern.compile("<meta\\s+charset=[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>.*?</title\\s*>\\s*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SEO_META = Pattern.compile(
            "<meta\\b[^>]*?\\b(?:name|property)=[\"']?(?:description|og:[^\"'\\s>]*|twitter:[^\"'\\s>]*)[\"']?(?=[\\s>/])[^>]*>\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MANIFEST_LINK = Pattern.compile("<link\\b[^>]*?\\brel=[\"']?manifest[\"']?(?=[\\s>/])[^>]*>\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ICON_LINK = Pattern.compile(
            "<link\\b[^>]*?\\brel=[\"']?(?:apple-touch-icon(?:-precomposed)?|shortcut icon|icon)[\"']?(?=[\\s>/])[^>]*>\\s*", Pattern.CASE_INSENSITIVE);

    private volatile String template;
    private volatile Cached cached;

    /** 설정 사본에 맞게 머리를 채운 index.html. 화면 파일이 없으면 비어 있다 */
    public Optional<RenderedIndex> render(PublicSiteSnapshot snapshot) {
        String html = template();
        if (html == null) {
            return Optional.empty();
        }
        Cached current = cached;
        if (current != null && current.snapshotEtag.equals(snapshot.etag())) {
            return Optional.of(current.index);
        }
        byte[] body = fill(html, snapshot).getBytes(StandardCharsets.UTF_8);
        RenderedIndex index = new RenderedIndex(body, hash(body));
        cached = new Cached(snapshot.etag(), index);
        return Optional.of(index);
    }

    /**
     * 관리자 미리보기 응답 — 머리 맨 앞에 {@code <meta name="roboyam-admin-origin" content="관리자 origin">} 을 더한 사본(지문도 새로). 공개 화면이 이 주소의 메시지만 받는다
     * (user-web {@code plugins/03.admin-preview.client.ts}). 머리가 없는 틀이면 그대로다.
     */
    public RenderedIndex withAdminOrigin(RenderedIndex index, String adminOrigin) {
        String html = new String(index.body(), StandardCharsets.UTF_8);
        Matcher open = HEAD_OPEN.matcher(html);
        if (!open.find()) {
            return index;
        }
        String meta = "\n<meta name=\"" + ADMIN_ORIGIN_META + "\" content=\"" + HtmlUtils.htmlEscape(adminOrigin) + "\">";
        byte[] body = (html.substring(0, open.end()) + meta + html.substring(open.end())).getBytes(StandardCharsets.UTF_8);
        return new RenderedIndex(body, hash(body));
    }

    /** 틀은 jar 안 파일이라 한 번 읽으면 된다. 없으면 기억하지 않고 다음 요청에 다시 본다(빌드 직후의 개발 환경) */
    private String template() {
        String loaded = template;
        if (loaded != null) {
            return loaded;
        }
        ClassPathResource resource = new ClassPathResource(INDEX);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            loaded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("화면 index.html 을 읽지 못했다: {}", e.toString());
            return null;
        }
        template = loaded;
        return loaded;
    }

    private static String fill(String html, PublicSiteSnapshot snapshot) {
        SiteInfoResponse info = snapshot.info();
        Matcher open = HEAD_OPEN.matcher(html);
        Matcher close = HEAD_CLOSE.matcher(html);
        if (info.siteName() == null || !open.find() || !close.find(open.end())) {
            return html; // 설정 행이 없거나(설치 전) 머리가 없는 틀 — 채울 것도 자리도 없다
        }
        String head = html.substring(open.end(), close.start());
        boolean customIcon = info.icon() != null;
        boolean templateHasIcon = ICON_LINK.matcher(head).find();

        String cleaned = removeOutsideScripts(head, customIcon);
        String block = block(snapshot, customIcon || templateHasIcon);
        int at = 0;
        Matcher charset = CHARSET_META.matcher(cleaned);
        if (charset.find()) {
            at = charset.end();
        }
        return html.substring(0, open.end()) + cleaned.substring(0, at) + "\n" + block + cleaned.substring(at) + html.substring(close.start());
    }

    /** {@code <script>}·{@code <style>} 는 그대로 두고 그 밖의 곳에서 우리가 다시 넣을 태그를 지운다 */
    private static String removeOutsideScripts(String head, boolean removeIcons) {
        StringBuilder out = new StringBuilder(head.length());
        Matcher blocks = SCRIPT_OR_STYLE.matcher(head);
        int last = 0;
        while (blocks.find()) {
            out.append(removeTags(head.substring(last, blocks.start()), removeIcons)).append(blocks.group());
            last = blocks.end();
        }
        out.append(removeTags(head.substring(last), removeIcons));
        return out.toString();
    }

    private static String removeTags(String part, boolean removeIcons) {
        String result = MANIFEST_LINK.matcher(SEO_META.matcher(TITLE.matcher(part).replaceAll("")).replaceAll("")).replaceAll("");
        return removeIcons ? ICON_LINK.matcher(result).replaceAll("") : result;
    }

    private static String block(PublicSiteSnapshot snapshot, boolean keepsIcon) {
        SiteInfoResponse info = snapshot.info();
        String name = info.siteName();
        String tagline = info.tagline();
        String title = tagline == null ? name : name + TITLE_SEPARATOR + tagline;
        String description = tagline == null ? name : tagline;

        StringBuilder b = new StringBuilder();
        SiteIconResponse icon = info.icon();
        if (icon != null) {
            b.append(link("icon", "image/png", "32x32", icon.favicon32()));
            b.append(link("icon", "image/png", "48x48", icon.favicon48()));
            b.append(link("apple-touch-icon", null, "180x180", icon.appleTouch()));
        } else if (!keepsIcon) {
            b.append(link("icon", "image/svg+xml", "any", SiteMediaKeys.DEFAULT_ICON_URL));
        }
        b.append(link("manifest", null, null, MANIFEST_URL));
        b.append("<title>").append(esc(title)).append("</title>\n");
        b.append(meta("name", "description", description));
        b.append(meta("property", "og:type", "website"));
        b.append(meta("property", "og:site_name", name));
        b.append(meta("property", "og:title", title));
        b.append(meta("property", "og:description", description));
        b.append(meta("property", "og:image", info.ogImageUrl()));
        if (snapshot.ogCustom()) {
            b.append(meta("property", "og:image:width", String.valueOf(SiteMediaKeys.OG_WIDTH)));
            b.append(meta("property", "og:image:height", String.valueOf(SiteMediaKeys.OG_HEIGHT)));
        }
        b.append(meta("name", "twitter:card", "summary_large_image"));
        b.append(meta("name", "twitter:title", title));
        b.append(meta("name", "twitter:description", description));
        b.append(meta("name", "twitter:image", info.ogImageUrl()));
        return b.toString();
    }

    private static String link(String rel, String type, String sizes, String href) {
        return "<link rel=\"" + rel + "\"" + (type == null ? "" : " type=\"" + type + "\"") + (sizes == null ? "" : " sizes=\"" + sizes + "\"")
                + " href=\"" + esc(href) + "\">\n";
    }

    private static String meta(String attribute, String key, String content) {
        return "<meta " + attribute + "=\"" + key + "\" content=\"" + esc(content) + "\">\n";
    }

    /** {@code & < > " '} 를 이스케이프한다(한글 같은 글자는 그대로) — 속성값에도 안전하다 */
    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }

    private static String hash(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body), 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Cached(String snapshotEtag, RenderedIndex index) {
    }
}
