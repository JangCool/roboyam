package com.community.terms.controller;

import com.community.terms.dto.response.PublicTermsPageResponse;
import com.community.terms.dto.response.PublicTermsVersionResponse;
import com.community.terms.dto.response.TermsSummaryResponse;
import com.community.terms.service.PublicTermsService;
import com.community.zconfiguration.response.ApiResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 약관 API(docs/features/terms-contract.md) — 로그인 없이 부른다. 경로의 {@code {kind}} 는 {@code terms}·{@code privacy}(모르면 404).
 * 브라우저는 60초 동안 다시 묻지 않는다({@code Cache-Control: public, max-age=60} — {@code /api/site/info} 와 같다).
 */
@RestController
@RequestMapping("/api/terms")
public class PublicTermsController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final PublicTermsService service;

    public PublicTermsController(PublicTermsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<TermsSummaryResponse>> summary() {
        return ResponseEntity.ok().cacheControl(CACHE).body(ApiResponse.success(service.summary()));
    }

    @GetMapping("/{kind}")
    public ResponseEntity<ApiResponse<PublicTermsPageResponse>> page(@PathVariable String kind) {
        return ResponseEntity.ok().cacheControl(CACHE).body(ApiResponse.success(service.page(kind)));
    }

    @GetMapping("/{kind}/versions/{version}")
    public ResponseEntity<ApiResponse<PublicTermsVersionResponse>> version(@PathVariable String kind, @PathVariable int version) {
        return ResponseEntity.ok().cacheControl(CACHE).body(ApiResponse.success(service.version(kind, version)));
    }
}
