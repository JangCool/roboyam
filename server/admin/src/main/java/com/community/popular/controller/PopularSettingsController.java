package com.community.popular.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.popular.dto.request.PopularSettingsRequest;
import com.community.popular.dto.response.PopularSettingsView;
import com.community.popular.service.PopularSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기글 설정 API(관리자 앱) — 점수식·조건·주 시작 요일. 조회 {@code popular.read} 또는 {@code popular.settings}, 저장 {@code popular.settings}(CSRF).
 * 규칙은 {@link PopularSettingsService}. docs/features/popular-highlight-contract.md 「서버 구현 결과」.
 */
@RestController
@RequestMapping("/api/admin/popular-settings")
public class PopularSettingsController {

    private final PopularSettingsService service;

    public PopularSettingsController(PopularSettingsService service) {
        this.service = service;
    }

    @RequirePermission(anyOf = {Permission.POPULAR_READ, Permission.POPULAR_SETTINGS})
    @GetMapping
    public ResponseEntity<ApiResponse<PopularSettingsView>> load() {
        return noStore(service.load());
    }

    /** 11칸을 통째로 저장 — 바뀌면 순위 다시 계산을 요청한다. 저장된 뒤의 같은 모양을 돌려준다 */
    @RequirePermission(Permission.POPULAR_SETTINGS)
    @PutMapping
    public ResponseEntity<ApiResponse<PopularSettingsView>> save(@RequestBody(required = false) PopularSettingsRequest body, AdminActor actor) {
        return noStore(service.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
