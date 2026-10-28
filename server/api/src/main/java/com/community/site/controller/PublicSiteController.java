package com.community.site.controller;

import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.service.PublicSiteService;
import com.community.zconfiguration.response.ApiResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 사이트가 읽는 사이트 설정 — 로그인 없이 부른다. 사이트 이름·소개·로고·아이콘·링크 미리보기 이미지·표시 기본값·테마만 내보낸다.
 * {@code ETag} 가 사본의 지문이라 같은 값이면 {@code 304} 로 답한다(스프링이 {@code If-None-Match} 를 비교한다). 브라우저는 60초 동안 다시 묻지 않는다.
 */
@RestController
@RequestMapping("/api/site")
public class PublicSiteController {

    private final PublicSiteService siteService;

    public PublicSiteController(PublicSiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<SiteInfoResponse>> info() {
        PublicSiteSnapshot snapshot = siteService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.info()));
    }

    /** 테마 + 그 테마의 메뉴 설정 — {@code /info} 와 같은 사본·같은 캐시 규칙({@code theme} 은 {@code info.theme} 과 같은 값) */
    @GetMapping("/theme")
    public ResponseEntity<ApiResponse<SiteThemeResponse>> theme() {
        PublicSiteSnapshot snapshot = siteService.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.theme()));
    }
}
