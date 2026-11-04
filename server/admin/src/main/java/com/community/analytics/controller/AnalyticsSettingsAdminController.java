package com.community.analytics.controller;

import com.community.analytics.dto.AnalyticsSettingsAdmin;
import com.community.analytics.service.AnalyticsSettingsAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「설정 › 통계 수집」(admin-web {@code useAnalyticsSettingsApi}, docs/features/site-analytics-contract.md 「API」) — 조회·이력 {@code site.read} · 저장 {@code site.manage}(CSRF).
 * 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/analytics/settings")
public class AnalyticsSettingsAdminController {

    private final AnalyticsSettingsAdminService service;

    public AnalyticsSettingsAdminController(AnalyticsSettingsAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AnalyticsSettingsAdmin.View>> get() {
        return ok(service.get());
    }

    /** 저장 — 1단계 여덟 칸 모두(2단계 세 칸 environment·searchTerms·bots 는 비면 그대로), 틀리면 400 {@code fields.<칸>}(보관 일수 {@code fields.rawRetentionDays}), 감사 {@code STATS_COLLECTION_UPDATE}. 같은 값이면 이력·감사 없음 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ResponseEntity<ApiResponse<AnalyticsSettingsAdmin.View>> save(@RequestBody(required = false) AnalyticsSettingsAdmin.Save body, AdminActor actor) {
        return ok(service.save(actor, body));
    }

    /** 변경 이력 — 최신 먼저(기본 20 · 최대 100, 틀리면 400 {@code fields.page}·{@code fields.size}) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<AnalyticsSettingsAdmin.HistoryPage>> history(@RequestParam(required = false) String page,
                                                                                 @RequestParam(required = false) String size) {
        return ok(service.history(page, size));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
