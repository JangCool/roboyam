package com.community.analytics.controller;

import com.community.analytics.dto.VisitForget;
import com.community.analytics.forget.VisitForgetRateLimiter;
import com.community.analytics.forget.VisitForgetService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.security.PublicVisitPaths;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방문 기록 삭제 요청(공개 {@code POST /api/visits/forget} — 로그인·CSRF 없음, {@link PublicVisitPaths#FORGET}). site-analytics-contract 「2단계 — 정보주체 요청」.
 * 202 {@code { requestId, receivedAt }} · 400 {@code VALIDATION}({@code fields.visitorId}) · 429 {@code VISIT_FORGET_RATE_LIMITED}(IP 당 1시간 5번, 메모리 — IP 는 저장하지 않는다) ·
 * 503 {@code INDEX_KEY_MISSING}. 횟수는 형식 검사 앞에서 센다(틀린 ID 로 두드려도 같은 한도).
 */
@RestController
public class VisitForgetController {

    private final VisitForgetService service;
    private final VisitForgetRateLimiter limiter;
    private final ClientIpResolver ipResolver;

    public VisitForgetController(VisitForgetService service, VisitForgetRateLimiter limiter, ClientIpResolver ipResolver) {
        this.service = service;
        this.limiter = limiter;
        this.ipResolver = ipResolver;
    }

    @PostMapping(PublicVisitPaths.FORGET)
    public ResponseEntity<ApiResponse<VisitForget.Response>> forget(@RequestBody(required = false) VisitForget.Request body, HttpServletRequest request) {
        limiter.acquire(ipResolver.resolve(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.submit(body)));
    }
}
