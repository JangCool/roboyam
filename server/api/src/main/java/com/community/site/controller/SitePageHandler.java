package com.community.site.controller;

import com.community.ad.service.AdSenseSiteService;
import com.community.analytics.collect.VisitCollector;
import com.community.installation.service.InstallationState;
import com.community.site.boot.BootPreloadRenderer;
import com.community.site.config.AdminPreviewFrame;
import com.community.site.dto.RenderedIndex;
import com.community.site.service.IndexHtmlRenderer;
import com.community.site.service.PublicSiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 화면(SPA) 주소 요청에 <b>머리를 사이트 설정으로 채운 {@code index.html}</b> 을 내리는 처리기 — 길은 {@code SitePageRoutes}(GET·HEAD 이고 {@link #isPage} 인 주소).
 * 탭 아이콘·매니페스트 링크·링크 미리보기 메타를 크롤러가 읽게 하려는 것이다({@link IndexHtmlRenderer}). SPA 의 모든 주소({@code /}·{@code /login}·{@code /posts/1} …)는 같은 {@code index.html} 이다.
 *
 * <ul>
 *   <li>내려 보내는 {@code index.html} 은 {@code Cache-Control: no-cache} + {@code ETag} 라 브라우저가 매번 물어보되 같으면 {@code 304} 다(설정을 바꾸면 곧 따라온다).</li>
 *   <li>관리자 미리보기 요청({@link AdminPreviewFrame#isPreview} — {@code ?adminPreview=1})이면 머리에 관리자 origin 메타를 더한 사본을 내린다(프레임 헤더는 보안 설정이 붙인다).</li>
 *   <li>그 밖의 화면 요청은 구글 애드센스가 VERIFY·SHOW 이고 게시자 ID 가 있으면 머리 끝에 사이트 확인 메타·{@code adsbygoogle.js} 를 더한다({@link IndexHtmlRenderer#withAdSense} —
 *       설정은 요청마다 읽어 저장 뒤 다음 요청부터, 미리보기에는 넣지 않는다 — docs/features/adsense-contract.md).</li>
 *   <li>설치를 마친 사이트의 GET 화면 요청이면 첫 화면 데이터({@link BootPreloadRenderer} — 화면이 처음 부르는 API 응답)를 함께 싣고 {@code Cache-Control: private, no-cache} 로 내린다
 *       (로그인한 사람의 정보가 들 수 있다 — 공유 캐시가 담지 않게). 규칙 {@code .claude/guides/frontend/first-load.md}.</li>
 *   <li>화면 파일이 없으면(빌드 전) 404, 머리를 만들다 실패하면 빌드가 만든 {@code index.html} 을 그대로 내린다(화면이 막히지 않는다).</li>
 *   <li>봇·크롤러 통계(site-analytics-contract 「2단계 — 봇」): 크롤러가 받는 첫 응답이 이 {@code index.html} 이다 — 요청마다 원래 User-Agent 로
 *       {@link VisitCollector#countBotPage} 가 봇만 종류별로 메모리에 센다(화면 요청 하나 = 1, 그 뒤 화면이 부르는 {@code /api/**} 는 세지 않는다).</li>
 * </ul>
 * 예전에는 서블릿 필터({@code SiteHeadFilter})였다 — 다른 API 처럼 MVC 안에서 처리하려고 옮겼다(사용자 결정 2026-11-14, 동작은 같다).
 */
@Component
public class SitePageHandler {

    private static final Logger log = LoggerFactory.getLogger(SitePageHandler.class);

    private static final String[] SKIP_PREFIXES = {"/api/", "/media/", "/actuator/", "/error/", "/_nuxt/", "/.well-known/"};
    private static final String[] SKIP_EXACT = {"/api", "/media", "/actuator", "/error"};
    private static final String INDEX_FILE = "index.html";
    private static final String STATIC_INDEX = "static/index.html";
    private static final MediaType HTML = MediaType.parseMediaType("text/html;charset=UTF-8");

    private final PublicSiteService siteService;
    private final IndexHtmlRenderer renderer;
    private final AdminPreviewFrame preview;
    private final VisitCollector visits;
    private final AdSenseSiteService adsense;
    private final BootPreloadRenderer boot;
    private final InstallationState installation;

    public SitePageHandler(PublicSiteService siteService, IndexHtmlRenderer renderer, AdminPreviewFrame preview, VisitCollector visits,
                           AdSenseSiteService adsense, BootPreloadRenderer boot, InstallationState installation) {
        this.siteService = siteService;
        this.renderer = renderer;
        this.preview = preview;
        this.visits = visits;
        this.adsense = adsense;
        this.boot = boot;
        this.installation = installation;
    }

    /** 화면 주소(SPA 라우트)인가 — 확장자가 없는 주소와 {@code /index.html}. {@code /api}·{@code /media}·{@code /_nuxt} 등은 아니다 */
    public static boolean isPage(String path) {
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

    /** 화면 주소 요청 → 머리를 채운 {@code index.html} */
    public ServerResponse page(ServerRequest request) {
        HttpServletRequest servlet = request.servletRequest();
        visits.countBotPage(servlet.getHeader(HttpHeaders.USER_AGENT)); // 봇 통계 — 메모리에 센다(꺼져 있으면 아무것도 하지 않는다)
        Optional<RenderedIndex> rendered;
        try {
            rendered = renderer.render(siteService.snapshot());
        } catch (RuntimeException e) {
            log.warn("index.html 머리를 채우지 못해 틀 그대로 내린다: {}", e.toString());
            return staticIndex();
        }
        if (rendered.isEmpty()) {
            return ServerResponse.notFound().build(); // 화면을 아직 빌드하지 않았다
        }
        RenderedIndex index = rendered.get();
        boolean withBoot = false;
        if (preview.isPreview(servlet)) {
            index = renderer.withAdminOrigin(index, preview.adminOrigin().orElseThrow()); // 미리보기에는 애드센스·첫 화면 데이터를 넣지 않는다
        } else {
            String client = adsenseClient();
            if (client != null) {
                index = renderer.withAdSense(index, client);
            }
            String json = bootJson(request);
            if (json != null) {
                index = renderer.withBoot(index, json);
                withBoot = true;
            }
        }
        CacheControl cache = withBoot ? CacheControl.noCache().cachePrivate() : CacheControl.noCache();
        String etag = "\"" + index.etag() + "\"";
        if (request.checkNotModified(etag).isPresent()) {
            return ServerResponse.status(HttpStatus.NOT_MODIFIED).cacheControl(cache).eTag(etag).build();
        }
        if (HttpMethod.HEAD.equals(request.method())) {
            return ServerResponse.ok().contentType(HTML).cacheControl(cache).eTag(etag).contentLength(index.body().length).build(); // 머리글만
        }
        return ServerResponse.ok().contentType(HTML).cacheControl(cache).eTag(etag).body(index.body());
    }

    /**
     * 첫 화면 데이터 — 설치를 마친 사이트의 {@code GET} 화면 요청에만(HEAD·설치 전·미리보기는 빼고). 만들다 실패하면 null(화면이 평소처럼 API 를 부른다).
     * 로그인 상태 응답이 세션 쿠키 기한을 늘리므로 원래 응답 객체가 필요하다 — MVC 가 요청마다 붙여 두는 {@link ServletRequestAttributes} 에서 꺼낸다.
     */
    private String bootJson(ServerRequest request) {
        if (!HttpMethod.GET.equals(request.method()) || !installation.isInstalled()) {
            return null;
        }
        HttpServletResponse response = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes ? attributes.getResponse() : null;
        if (response == null) {
            return null;
        }
        try {
            return boot.render(request.servletRequest(), response, request.path());
        } catch (RuntimeException e) {
            log.warn("첫 화면 데이터를 싣지 못해 빼고 내린다: {}", e.toString());
            return null;
        }
    }

    /** 머리에 넣을 애드센스 게시자 ID — 끔·없음이면 null. 읽다 실패하면 애드센스 없이 내린다(화면이 막히지 않게) */
    private String adsenseClient() {
        try {
            return adsense.activeClient().orElse(null);
        } catch (RuntimeException e) {
            log.warn("애드센스 설정을 읽지 못해 머리에 넣지 않는다: {}", e.toString());
            return null;
        }
    }

    /** 빌드가 만든 {@code index.html} 그대로(머리를 채우지 못했을 때) — 없으면 404 */
    private static ServerResponse staticIndex() {
        ClassPathResource file = new ClassPathResource(STATIC_INDEX);
        if (!file.exists()) {
            return ServerResponse.notFound().build();
        }
        try (InputStream in = file.getInputStream()) {
            return ServerResponse.ok().contentType(HTML).cacheControl(CacheControl.noCache()).body(in.readAllBytes());
        } catch (IOException e) {
            return ServerResponse.notFound().build();
        }
    }
}
