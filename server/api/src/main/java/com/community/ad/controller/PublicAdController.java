package com.community.ad.controller;

import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.PublicAdView;
import com.community.ad.service.PublicAdService;
import com.community.auth.dto.AuthUser;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 광고(로그인 없이 — {@code SecurityConfig}) — 나가는 광고 목록 · 노출 자리 설정 · 노출·클릭 집계(user-web {@code usePublicAdApi}, docs/features/ad-contract.md
 * 「공개 노출 목업」). 집계 두 주소는 CSRF 검사에서 뺐고({@link com.community.zconfiguration.security.PublicAdPaths}) 셌든 버렸든 같은 200(본문 data 없음)이다.
 * 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/ads")
public class PublicAdController {

    private final PublicAdService service;
    private final ClientIpResolver clientIps;

    public PublicAdController(PublicAdService service, ClientIpResolver clientIps) {
        this.service = service;
        this.clientIps = clientIps;
    }

    /** {@code types} 쉼표 구분 1~8개 · {@code board} 게시판 맥락이면 그 slug · {@code prefix} 말머리(지금 쓰지 않는다) */
    @GetMapping
    public ResponseEntity<ApiResponse<PublicAdView.Page>> list(@RequestParam(required = false) String types, @RequestParam(required = false) String board,
                                                               @RequestParam(required = false) String prefix) {
        return ok(service.list(types, board));
    }

    @GetMapping("/placements")
    public ResponseEntity<ApiResponse<AdPlacements>> placements() {
        return ok(service.placements());
    }

    @PostMapping("/impressions")
    public ResponseEntity<ApiResponse<Void>> impressions(@RequestBody(required = false) PublicAdView.Impressions body, HttpServletRequest request,
                                                         Authentication authentication) {
        service.impressions(visitor(request, authentication), body);
        return ok(null);
    }

    @PostMapping("/{id}/clicks")
    public ResponseEntity<ApiResponse<Void>> click(@PathVariable String id, @RequestBody(required = false) PublicAdView.Click body, HttpServletRequest request,
                                                   Authentication authentication) {
        service.click(visitor(request, authentication), id, body);
        return ok(null);
    }

    private PublicAdService.Visitor visitor(HttpServletRequest request, Authentication authentication) {
        Long member = authentication != null && authentication.getPrincipal() instanceof AuthUser user ? user.id() : null;
        return new PublicAdService.Visitor(member, clientIps.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
