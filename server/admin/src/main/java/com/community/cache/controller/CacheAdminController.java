package com.community.cache.controller;

import com.community.cache.dto.request.CacheEnabledRequest;
import com.community.cache.dto.request.CacheTtlRequest;
import com.community.cache.dto.response.CacheDetailView;
import com.community.cache.dto.response.CacheOverviewView;
import com.community.cache.dto.response.CacheRefreshResultView;
import com.community.cache.dto.response.CacheView;
import com.community.cache.service.CacheAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐시 관리 API(docs/features/cache-admin-contract.md) — 모양은 admin-web {@code useCacheAdminApi.types.ts}. 보기 {@code cache.read}, 바꾸기 {@code cache.manage}.
 * 응답은 {@code Cache-Control: no-store}. 쓰기는 CSRF(전역 설정). 없는 캐시 404 {@code CACHE_NOT_FOUND}, 꺼진 캐시 갱신 409 {@code CACHE_DISABLED},
 * 전체가 꺼져 있을 때 캐시별 켜기/끄기 409 {@code CACHE_GLOBAL_DISABLED}, TTL 범위 밖 400 {@code fields.ttlSeconds}.
 * {@code /settings}·{@code /refresh} 는 캐시 키({@code home.layout} 처럼 점이 있다)와 겹치지 않는다 — 카탈로그에 그런 키가 없다.
 */
@RestController
@RequestMapping("/api/admin/caches")
public class CacheAdminController {

    private final CacheAdminService service;

    public CacheAdminController(CacheAdminService service) {
        this.service = service;
    }

    /** 서버 목록 · 확인 주기 · 캐시 목록 · 캐시하지 않는 가젯 · 미리보기 쪽 후보 · 공개 주소 */
    @RequirePermission(Permission.CACHE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<CacheOverviewView>> overview() {
        return ok(service.overview());
    }

    /** 상세 — 서버별 상태 · 갱신 기록 · 설정 변경 이력 · 가젯 놓은 곳 */
    @RequirePermission(Permission.CACHE_READ)
    @GetMapping("/{cacheKey:.+}")
    public ResponseEntity<ApiResponse<CacheDetailView>> detail(@PathVariable String cacheKey) {
        return ok(service.detail(cacheKey));
    }

    /** 전체 갱신 — 202, 꺼진 캐시는 건너뛴다 */
    @RequirePermission(Permission.CACHE_MANAGE)
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CacheRefreshResultView>> refreshAll(AdminActor actor) {
        return accepted(service.refreshAll(actor));
    }

    /** 지금 갱신(한 캐시) — 202 */
    @RequirePermission(Permission.CACHE_MANAGE)
    @PostMapping("/{cacheKey:.+}/refresh")
    public ResponseEntity<ApiResponse<CacheRefreshResultView>> refresh(@PathVariable String cacheKey, AdminActor actor) {
        return accepted(service.refresh(actor, cacheKey));
    }

    /** 전체 캐시 켜기/끄기 — 바뀐 개요 */
    @RequirePermission(Permission.CACHE_MANAGE)
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<CacheOverviewView>> settings(@Valid @RequestBody CacheEnabledRequest body, AdminActor actor) {
        return ok(service.setGlobalEnabled(actor, body.enabled()));
    }

    /** TTL 변경 — 바뀐 목록 줄 */
    @RequirePermission(Permission.CACHE_MANAGE)
    @PutMapping("/{cacheKey:.+}/ttl")
    public ResponseEntity<ApiResponse<CacheView>> ttl(@PathVariable String cacheKey, @Valid @RequestBody CacheTtlRequest body, AdminActor actor) {
        return ok(service.updateTtl(actor, cacheKey, body.ttlSeconds()));
    }

    /** 이 캐시 켜기/끄기 — 바뀐 목록 줄 */
    @RequirePermission(Permission.CACHE_MANAGE)
    @PutMapping("/{cacheKey:.+}/enabled")
    public ResponseEntity<ApiResponse<CacheView>> enabled(@PathVariable String cacheKey, @Valid @RequestBody CacheEnabledRequest body, AdminActor actor) {
        return ok(service.setEnabled(actor, cacheKey, body.enabled()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> accepted(T body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
