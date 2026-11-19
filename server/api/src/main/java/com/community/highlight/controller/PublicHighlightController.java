package com.community.highlight.controller;

import com.community.auth.dto.AuthUser;
import com.community.highlight.dto.response.HighlightPeriodPageResponse;
import com.community.highlight.service.HighlightPeriodService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 「아추!」(아이로빗 추천) 페이지 — 모양은 user-web {@code useHighlightApi.types.ts}. 로그인 없이 읽는다({@code SecurityConfig}).
 * 쿼리 값은 글자로 받아 서비스가 칸별로 모아 검사한다(숫자가 아닌 값도 {@code 400 VALIDATION fields.*}).
 */
@RestController
@RequestMapping("/api/highlights")
public class PublicHighlightController {

    private final HighlightPeriodService highlights;

    public PublicHighlightController(HighlightPeriodService highlights) {
        this.highlights = highlights;
    }

    /** 기간 안에 고른 홈 하이라이트 — {@code period}(DAY 기본 · WEEK · MONTH, 대소문자 무시) · {@code page}(1~100) · {@code size}(1~50, 기본 20) */
    @GetMapping
    public ResponseEntity<ApiResponse<HighlightPeriodPageResponse>> list(@RequestParam(required = false) String period, @RequestParam(required = false) String page,
                                                                        @RequestParam(required = false) String size, Authentication authentication) {
        Long viewerId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(highlights.list(period, page, size, viewerId)));
    }
}
