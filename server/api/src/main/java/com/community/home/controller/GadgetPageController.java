package com.community.home.controller;

import com.community.auth.dto.AuthUser;
import com.community.home.dto.response.GadgetPageResponse;
import com.community.home.service.HomeGadgetService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 가젯 페이지 {@code /p/{key}} 의 데이터 — 모양은 user-web {@code useGadgetPageApi.types.ts}(gadget-page-contract 「API」). 로그인 없이 읽는다({@code SecurityConfig}).
 * 보는 사람 몫(차단·메모)이 섞여 캐시하지 않는다. 없는 페이지는 404 {@code GADGET_PAGE_NOT_FOUND}.
 */
@RestController
@RequestMapping("/api/pages")
public class GadgetPageController {

    private final HomeGadgetService gadgets;

    public GadgetPageController(HomeGadgetService gadgets) {
        this.gadgets = gadgets;
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<GadgetPageResponse>> page(@PathVariable String key, Authentication authentication) {
        Long viewerId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(gadgets.page(key, viewerId)));
    }
}
