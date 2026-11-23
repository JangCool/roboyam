package com.community.sitemap.controller;

import com.community.sitemap.service.SitemapService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색엔진용 {@code /sitemap.xml}·{@code /sitemap-pages.xml}·{@code /sitemap-posts-{n}.xml}·{@code /robots.txt}(docs/features/sitemap-contract.md).
 *
 * <p>표준 형식이라 {@code ApiResponse} 로 감싸지 않는다({@code AdsTxtController} 와 같은 모양) — {@code SecurityConfig} 의 {@code anyRequest().permitAll()} 로 열려 있고,
 * 화면 파일 처리기({@code SpaResourceConfig} 의 {@code /**})보다 먼저 받으므로 404 가 화면 {@code index.html} 로 바뀌지 않는다.
 * CSRF 쿠키는 붙이지 않는다({@code CsrfCookieFilter}) — 공개 캐시에 담기는 응답이다.
 */
@RestController
public class SitemapController {

    private static final MediaType XML_UTF8 = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);
    private static final MediaType TEXT_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
    private static final CacheControl SITEMAP_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final SitemapService sitemaps;

    public SitemapController(SitemapService sitemaps) {
        this.sitemaps = sitemaps;
    }

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> index() {
        return xml(sitemaps.index());
    }

    @GetMapping("/sitemap-pages.xml")
    public ResponseEntity<String> pages() {
        return xml(sitemaps.pages());
    }

    /** 번호는 글자로 받는다 — 숫자가 아니거나 범위를 넘는 번호도 400 이 아니라 404 다 */
    @GetMapping("/sitemap-posts-{n}.xml")
    public ResponseEntity<String> posts(@PathVariable("n") String number) {
        return xml(sitemaps.posts(number));
    }

    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok().contentType(TEXT_UTF8).cacheControl(CacheControl.noCache()).body(sitemaps.robots());
    }

    /** 사이트맵 응답 — 없으면 본문 없는 404(곧 생길 수 있으니 저장하지 않게) */
    private static ResponseEntity<String> xml(Optional<String> body) {
        return body.map(b -> ResponseEntity.ok().contentType(XML_UTF8).cacheControl(SITEMAP_CACHE).body(b))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noCache()).build());
    }
}
