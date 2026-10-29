package com.community.site.config;

import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.RenderedIndex;
import com.community.site.service.IndexHtmlRenderer;
import com.community.site.service.PublicSiteService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 화면(SPA) 주소 요청에는 <b>머리를 사이트 설정으로 채운 {@code index.html}</b> 을 내린다({@link IndexHtmlRenderer}) — 탭 아이콘·매니페스트 링크·링크 미리보기 메타를
 * 크롤러가 읽게 하려는 것이다. SPA 의 모든 주소({@code /}·{@code /login}·{@code /posts/1} …)는 같은 {@code index.html} 이므로 확장자가 없는 주소를 모두 이 필터가 받는다.
 *
 * <p>적용하지 않는 요청: {@code GET}·{@code HEAD} 가 아닌 것, {@code /api}·{@code /media}·{@code /actuator}·{@code /error}·{@code /_nuxt}·{@code /.well-known},
 * 파일 확장자가 있는 주소(정적 자산 — {@code /index.html} 만 예외). 화면 파일이 없거나(빌드 전) 머리를 만들다 실패하면 그대로 지나가 평소처럼 처리된다(서버가 막히지 않는다).
 * 내려 보내는 {@code index.html} 은 {@code Cache-Control: no-cache} + {@code ETag} 라 브라우저가 매번 물어보되 같으면 {@code 304} 다(설정을 바꾸면 곧 따라온다).
 * 관리자 미리보기 요청({@link AdminPreviewFrame#isPreview} — {@code ?adminPreview=1})이면 머리에 관리자 origin 메타를 더한 사본을 내린다(프레임 헤더는 보안 설정이 붙인다).
 *
 * <p>보안 필터 체인 뒤에서 돈다 — 요청 정리(HttpFirewall)를 거친 경로만 온다.
 */
@Component
public class SiteHeadFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SiteHeadFilter.class);

    private static final String[] SKIP_PREFIXES = {"/api/", "/media/", "/actuator/", "/error/", "/_nuxt/", "/.well-known/"};
    private static final String[] SKIP_EXACT = {"/api", "/media", "/actuator", "/error"};
    private static final String INDEX_FILE = "index.html";

    private final PublicSiteService siteService;
    private final IndexHtmlRenderer renderer;
    private final AdminPreviewFrame preview;

    public SiteHeadFilter(PublicSiteService siteService, IndexHtmlRenderer renderer, AdminPreviewFrame preview) {
        this.siteService = siteService;
        this.renderer = renderer;
        this.preview = preview;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            return true;
        }
        return !isPage(request.getServletPath());
    }

    /** 화면 주소(SPA 라우트)인가 — 확장자가 없는 주소와 {@code /index.html} */
    static boolean isPage(String path) {
        for (String exact : SKIP_EXACT) {
            if (path.equals(exact)) {
                return false;
            }
        }
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".") || last.equals(INDEX_FILE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Optional<RenderedIndex> rendered;
        try {
            PublicSiteSnapshot snapshot = siteService.snapshot();
            rendered = renderer.render(snapshot);
        } catch (RuntimeException e) {
            log.warn("index.html 머리를 채우지 못해 틀 그대로 내린다: {}", e.toString());
            chain.doFilter(request, response);
            return;
        }
        if (rendered.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        RenderedIndex index = rendered.get();
        if (preview.isPreview(request)) {
            index = renderer.withAdminOrigin(index, preview.adminOrigin().orElseThrow());
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if (new ServletWebRequest(request, response).checkNotModified("\"" + index.etag() + "\"")) {
            return; // 304
        }
        response.setContentType("text/html;charset=UTF-8");
        response.setContentLength(index.body().length);
        if (!HttpMethod.HEAD.matches(request.getMethod())) {
            response.getOutputStream().write(index.body());
        }
    }
}
