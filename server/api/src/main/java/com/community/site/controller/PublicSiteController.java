package com.community.site.controller;

import com.community.popup.dto.response.SitePopupsResponse;
import com.community.popup.service.PublicSitePopupService;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.response.SiteAboutPublicResponse;
import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.dto.response.SiteNoticeBarResponse;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.service.PublicSiteNoticeService;
import com.community.site.service.PublicSiteService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 사이트가 읽는 사이트 설정 — 로그인 없이 부른다. 사이트 이름·소개·로고·아이콘·링크 미리보기 이미지·표시 기본값·테마만 내보낸다.
 * {@code ETag} 가 사본의 지문이라 같은 값이면 {@code 304} 로 답한다(스프링이 {@code If-None-Match} 를 비교한다).
 * 브라우저는 저장해 두되 쓸 때마다 묻는다({@code Cache-Control: no-cache}) — 운영자가 바꾼 값이 서버 사본(20초) 말고는 늦지 않게, 바뀌지 않았으면 본문 없이 {@code 304}.
 */
@RestController
@RequestMapping("/api/site")
public class PublicSiteController {

    private final PublicSiteService siteService;
    private final PublicSiteNoticeService noticeService;
    private final PublicSitePopupService popupService;

    public PublicSiteController(PublicSiteService siteService, PublicSiteNoticeService noticeService, PublicSitePopupService popupService) {
        this.siteService = siteService;
        this.noticeService = noticeService;
        this.popupService = popupService;
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<SiteInfoResponse>> info() {
        PublicSiteSnapshot snapshot = siteService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.info()));
    }

    /**
     * 공지 띠 — 꺼져 있으면 {@code { version: "off", items: [] }}. 자기 사본({@link PublicSiteNoticeService}, 20초)을 쓰고 캐시 규칙은 {@code /info} 와 같다
     * ({@code ETag} 는 공지 띠 사본의 지문).
     */
    @GetMapping("/notice-bar")
    public ResponseEntity<ApiResponse<SiteNoticeBarResponse>> noticeBar() {
        PublicSiteNoticeService.Snapshot snapshot = noticeService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.response()));
    }

    /**
     * 팝업(docs/features/site-popup-contract.md) — 노출을 켰고 기간 안인 것만 목록 차례대로 {@code { items }}. 자기 사본({@link PublicSitePopupService}, 20초 — 기간은 요청마다
     * 지금 시각으로 다시 거른다)을 쓰고 캐시 규칙은 {@code /info} 와 같다({@code ETag} 는 걸러 낸 응답의 지문). 보일 곳·기기·「보지 않기」 기록은 화면이 가린다.
     */
    @GetMapping("/popups")
    public ResponseEntity<ApiResponse<SitePopupsResponse>> popups() {
        PublicSitePopupService.Snapshot snapshot = popupService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.response()));
    }

    /**
     * SVG 로고 한 칸(V237, 2026-11-16) — 사이트 정보의 {@code logos}·{@code genderLogos} 가 SVG 칸에 주는 주소. {@code b} 는 로고 묶음 번호(V249 — 없으면 오늘의 성별 무관 묶음). 본문은 저장할 때 위험 요소를 거절한 SVG 지만,
     * 주소를 직접 열어도 스크립트·외부 자원이 돌지 않게 CSP·nosniff 를 붙인다. 지문({@code v})이 지금 본문과 같으면 1년 캐시, 아니면 매번 확인.
     */
    @GetMapping("/logos/{variant}.svg")
    public ResponseEntity<String> logoSvg(@org.springframework.web.bind.annotation.PathVariable String variant,
                                          @org.springframework.web.bind.annotation.RequestParam(name = "b", required = false) String bundle,
                                          @org.springframework.web.bind.annotation.RequestParam(name = "v", required = false) String version) {
        Long bundleId = null;
        if (bundle != null) {
            try {
                bundleId = Long.parseLong(bundle);
            } catch (NumberFormatException e) {
                return ResponseEntity.notFound().build();
            }
        }
        return siteService.logoSvg(variant, bundleId).map(svg -> {
            String fingerprint = com.community.site.service.SiteAssets.svgFingerprint(svg);
            CacheControl cache = fingerprint.equals(version)
                    ? CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable()
                    : CacheControl.noCache();
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.valueOf("image/svg+xml;charset=UTF-8"))
                    .cacheControl(cache)
                    .eTag(fingerprint)
                    .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(svg);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 사이트 소개(V203 — 공개 푸터 「사이트 소개」 페이지) — {@code /info} 와 같은 사본·같은 캐시 규칙. 없으면 {@code html: null}({@code info.about} 이 false) */
    @GetMapping("/about")
    public ResponseEntity<ApiResponse<SiteAboutPublicResponse>> about() {
        PublicSiteSnapshot snapshot = siteService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(new SiteAboutPublicResponse(snapshot.aboutHtml())));
    }

    /** 테마 + 그 테마의 메뉴 설정 — {@code /info} 와 같은 사본·같은 캐시 규칙({@code theme} 은 {@code info.theme} 과 같은 값) */
    @GetMapping("/theme")
    public ResponseEntity<ApiResponse<SiteThemeResponse>> theme() {
        PublicSiteSnapshot snapshot = siteService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.theme()));
    }
}
