package com.community.menu.controller;

import com.community.auth.dto.AuthUser;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.service.MenuLimits;
import com.community.menu.service.PublicMenuService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 사이트 메뉴 — 로그인 없이 부른다(로그인했으면 그 사람 기준으로 거른다). user-web {@code useMenuApi.types.ts} 의 {@code MenuResponse} 모양 그대로.
 * 보는 사람마다 달라서 브라우저·중간 캐시에 남기지 않는다({@code no-store}).
 */
@RestController
@RequestMapping("/api/menu")
public class PublicMenuController {

    private final PublicMenuService service;

    public PublicMenuController(PublicMenuService service) {
        this.service = service;
    }

    /** {@code set} 기본값 {@code main}. 없는 그룹 404 {@code MENU_SET_NOT_FOUND}, 꺼진 그룹은 빈 {@code nodes} */
    @GetMapping
    public ResponseEntity<ApiResponse<MenuResponse>> menu(@RequestParam(name = "set", defaultValue = MenuLimits.MAIN_SET) String set, Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.menu(set, userId)));
    }
}
