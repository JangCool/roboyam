package com.community.site.controller;

import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.response.WebManifestResponse;
import com.community.site.service.PublicSiteService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 앱 매니페스트 — 사이트 이름·아이콘을 설정에서 만들어 내린다(정적 파일이 아니다). 표준 형식이라 {@code ApiResponse} 로 감싸지 않고,
 * 형식({@code application/manifest+json})을 올바로 알린다. {@code SecurityConfig} 의 {@code anyRequest().permitAll()} 로 열려 있다.
 */
@RestController
public class WebManifestController {

    private static final MediaType MANIFEST = MediaType.parseMediaType("application/manifest+json");

    private final PublicSiteService siteService;

    public WebManifestController(PublicSiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/manifest.webmanifest")
    public ResponseEntity<WebManifestResponse> manifest() {
        PublicSiteSnapshot snapshot = siteService.snapshot();
        return ResponseEntity.ok()
                .contentType(MANIFEST)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .eTag(snapshot.etag())
                .body(snapshot.manifest());
    }
}
