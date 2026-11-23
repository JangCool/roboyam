package com.community.sitemap.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.installation.service.InstallationState;
import com.community.post.service.PostOpenRules;
import com.community.site.service.PublicSiteService;
import com.community.sitemap.dto.SitemapPostData;
import com.community.sitemap.repository.master.SitemapPostRepositoryQDSL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/**
 * 검색엔진용 사이트맵·{@code robots.txt} 본문(docs/features/sitemap-contract.md) — 구글 같은 검색엔진이 비회원이 읽을 수 있는 글을 빠짐없이 찾게 한다.
 *
 * <ul>
 *   <li>주소 앞부분은 사이트 설정의 공개 주소({@code PublicSiteSnapshot#publicUrl})다. 설치 전이거나 공개 주소가 없으면 사이트맵은 비어 있고(404),
 *       {@code robots.txt} 는 {@code Sitemap} 줄 없이 낸다.</li>
 *   <li>게시판은 켜진 공개 게시판(소모임 게시판 아님) 중 성인 전용이 아니고 유효 정책 「검색엔진 색인」이 켜진 것. 글은 그런 게시판의 글과 열린 소모임의 공개 글 중
 *       비회원이 열 수 있는 것({@link SitemapPostRepositoryQDSL}).</li>
 *   <li>캐시 틀에 넣지 않는다 — 요청마다 읽는다(되묻기는 응답의 {@code max-age} 가 줄인다).</li>
 * </ul>
 */
@Service
public class SitemapService {

    private static final Logger log = LoggerFactory.getLogger(SitemapService.class);

    /** 글 사이트맵 한 파일의 글 수(sitemaps.org 상한 50,000 안) */
    static final int PAGE_SIZE = 10_000;
    /** 글 파일 수 상한 — 색인 하나가 가리킬 수 있는 파일 수(sitemaps.org 상한 50,000)에서 고정 화면 파일을 뺀 것 */
    static final int MAX_POST_FILES = 49_999;
    /** 파일 번호 — 앞에 0 을 붙인 번호는 받지 않는다(주소는 하나) */
    private static final Pattern FILE_NUMBER = Pattern.compile("[1-9][0-9]{0,4}");
    /** 로그인 없이 보고 noindex 가 아닌 고정 화면 */
    private static final List<String> FIXED_PAGES = List.of("/", "/about", "/terms", "/privacy", "/policy");

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final String ROBOTS = "User-agent: *\nAllow: /\n";

    private final PublicSiteService site;
    private final InstallationState installation;
    private final BoardCatalog catalog;
    private final SitemapPostRepositoryQDSL posts;

    public SitemapService(PublicSiteService site, InstallationState installation, BoardCatalog catalog, SitemapPostRepositoryQDSL posts) {
        this.site = site;
        this.installation = installation;
        this.catalog = catalog;
        this.posts = posts;
    }

    /** {@code /sitemap.xml} — 고정 화면 파일과 글 파일들의 색인. 설치 전·공개 주소 없음이면 비어 있다 */
    public Optional<String> index() {
        return baseUrl().map(base -> {
            Boards boards = boards();
            long count = posts.count(boards.general(), boards.group(), Instant.now());
            long files = Math.min(MAX_POST_FILES, (count + PAGE_SIZE - 1) / PAGE_SIZE);
            StringBuilder xml = new StringBuilder(XML).append("<sitemapindex xmlns=\"").append(NAMESPACE).append("\">\n");
            sitemap(xml, base + "/sitemap-pages.xml");
            for (long n = 1; n <= files; n++) {
                sitemap(xml, base + "/sitemap-posts-" + n + ".xml");
            }
            return xml.append("</sitemapindex>\n").toString();
        });
    }

    /** {@code /sitemap-pages.xml} — 고정 화면과 공개 게시판 목록 */
    public Optional<String> pages() {
        return baseUrl().map(base -> {
            StringBuilder xml = urlset();
            FIXED_PAGES.forEach(path -> url(xml, base + path, null));
            catalog.snapshot().boards().stream().filter(SitemapService::listedBoard)
                    .forEach(b -> url(xml, base + "/boards/" + UriUtils.encodePathSegment(b.slug(), StandardCharsets.UTF_8), null));
            return xml.append("</urlset>\n").toString();
        });
    }

    /**
     * {@code /sitemap-posts-{n}.xml} — 공개 글 {@value #PAGE_SIZE}개씩(글 ID 오름차순). 번호가 아니거나 0 이하·범위 밖이면 비어 있다
     *
     * @param number 주소의 번호 글자
     */
    public Optional<String> posts(String number) {
        if (!FILE_NUMBER.matcher(number).matches()) {
            return Optional.empty();
        }
        int n = Integer.parseInt(number);
        if (n > MAX_POST_FILES) {
            return Optional.empty();
        }
        return baseUrl().flatMap(base -> {
            Boards boards = boards();
            List<SitemapPostData> rows = posts.page(boards.general(), boards.group(), (n - 1L) * PAGE_SIZE, PAGE_SIZE, Instant.now());
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            StringBuilder xml = urlset();
            rows.forEach(row -> url(xml, base + "/posts/" + row.id(), row.lastModified()));
            return Optional.of(xml.append("</urlset>\n").toString());
        });
    }

    /**
     * {@code /robots.txt} — 모두 허용 + 사이트맵 주소. {@code /api/} 를 막지 않는다(구글이 화면을 그릴 때 부른다).
     * 설정을 읽다 실패해도 {@code Sitemap} 줄만 빼고 낸다 — robots.txt 가 5xx 면 구글은 사이트 전체를 막은 것으로 본다.
     */
    public String robots() {
        try {
            return baseUrl().map(base -> ROBOTS + "\nSitemap: " + base + "/sitemap.xml\n").orElse(ROBOTS);
        } catch (RuntimeException e) {
            log.warn("robots.txt 에 사이트맵 주소를 넣지 못했다: {}", e.toString());
            return ROBOTS;
        }
    }

    /** 사이트 공개 주소(끝의 {@code /} 없이) — 설치 전이거나 없으면 비어 있다 */
    private Optional<String> baseUrl() {
        if (!installation.isInstalled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(site.snapshot().publicUrl()).map(url -> url.strip().replaceAll("/+$", "")).filter(Predicate.not(String::isEmpty));
    }

    /** 글을 넣어도 되는 게시판 ID — 소모임 게시판은 꺼져 있어도 소모임 글이 열리므로({@link PostOpenRules}) 켜짐을 보지 않는다 */
    private Boards boards() {
        List<EffectiveBoard> all = catalog.snapshot().boards();
        return new Boards(all.stream().filter(SitemapService::listedBoard).map(EffectiveBoard::id).toList(),
                all.stream().filter(b -> !PostOpenRules.isPublicBoard(b) && b.indexable()).map(EffectiveBoard::id).toList());
    }

    /** 사이트맵에 넣는 게시판 — 켜진 공개 게시판이고 성인 전용이 아니며 「검색엔진 색인」이 켜졌다 */
    private static boolean listedBoard(EffectiveBoard b) {
        return b.active() && PostOpenRules.isPublicBoard(b) && !b.adultOnly() && b.indexable();
    }

    private static StringBuilder urlset() {
        return new StringBuilder(XML).append("<urlset xmlns=\"").append(NAMESPACE).append("\">\n");
    }

    private static void sitemap(StringBuilder xml, String loc) {
        xml.append("<sitemap><loc>").append(esc(loc)).append("</loc></sitemap>\n");
    }

    private static void url(StringBuilder xml, String loc, Instant lastModified) {
        xml.append("<url><loc>").append(esc(loc)).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(lastModified.truncatedTo(ChronoUnit.SECONDS)).append("</lastmod>");
        }
        xml.append("</url>\n");
    }

    /** {@code & < > " '} 를 이스케이프한다 — 내는 꼴({@code &amp; &lt; &gt; &quot; &#39;})은 XML 에서도 유효하다 */
    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    /**
     * @param general 일반 글을 넣을 공개 게시판
     * @param group   소모임 글을 넣을 소모임 게시판
     */
    private record Boards(List<Long> general, List<Long> group) {
    }
}
