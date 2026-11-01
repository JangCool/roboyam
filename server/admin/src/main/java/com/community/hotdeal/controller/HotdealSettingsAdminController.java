package com.community.hotdeal.controller;

import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealRuleView;
import com.community.hotdeal.dto.response.HotdealSettingsView;
import com.community.hotdeal.service.HotdealSettingsAdminService;
import com.community.hotdeal.service.HotdealSiteAffiliateAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 핫딜 설정(admin-web {@code useHotdealSettingsApi} — 조회 {@code hotdeal.read}, 저장 {@code hotdeal.manage})과 사이트 제휴 몰(admin-web {@code useHotdealSiteAffiliateApi} —
 * 조회·편집 모두 {@code hotdeal.manage}, 계약 D5 · 설정 저장과 따로 바로 저장).
 */
@RestController
public class HotdealSettingsAdminController {

    private final HotdealSettingsAdminService settings;
    private final HotdealSiteAffiliateAdminService siteStores;

    public HotdealSettingsAdminController(HotdealSettingsAdminService settings, HotdealSiteAffiliateAdminService siteStores) {
        this.settings = settings;
        this.siteStores = siteStores;
    }

    // ── 핫딜 설정 ─────────────────────────────────────────────────────────

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/api/admin/hotdeal-settings")
    public ResponseEntity<ApiResponse<HotdealSettingsView>> get() {
        return HotdealAdminController.ok(settings.get());
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @PutMapping("/api/admin/hotdeal-settings")
    public ResponseEntity<ApiResponse<HotdealSettingsView>> save(@RequestBody(required = false) HotdealAdminRequest.Settings body, AdminActor actor) {
        return HotdealAdminController.ok(settings.save(actor, body));
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/api/admin/hotdeal-settings/affiliate-impact")
    public ResponseEntity<ApiResponse<HotdealSettingsView.AffiliateImpact>> affiliateImpact(@RequestParam(required = false) String mode) {
        return HotdealAdminController.ok(settings.affiliateImpact(mode));
    }

    // ── 사이트 제휴 몰 ────────────────────────────────────────────────────

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @GetMapping("/api/admin/hotdeal-site-affiliates")
    public ResponseEntity<ApiResponse<HotdealRuleView.SiteStoreList>> siteStores() {
        return HotdealAdminController.ok(siteStores.list());
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @PostMapping("/api/admin/hotdeal-site-affiliates")
    public ResponseEntity<ApiResponse<HotdealRuleView.SiteStore>> createSiteStore(@RequestBody(required = false) HotdealAdminRequest.SiteStore body, AdminActor actor) {
        return HotdealAdminController.ok(siteStores.create(actor, body));
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @PutMapping("/api/admin/hotdeal-site-affiliates/{id}")
    public ResponseEntity<ApiResponse<HotdealRuleView.SiteStore>> updateSiteStore(@PathVariable String id,
                                                                                  @RequestBody(required = false) HotdealAdminRequest.SiteStore body, AdminActor actor) {
        return HotdealAdminController.ok(siteStores.update(actor, id, body));
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @DeleteMapping("/api/admin/hotdeal-site-affiliates/{id}")
    public ResponseEntity<Void> deleteSiteStore(@PathVariable String id, AdminActor actor) {
        siteStores.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
