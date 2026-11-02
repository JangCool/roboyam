package com.community.ad.controller;

import com.community.ad.dto.AdAdvertiserAdminRequest;
import com.community.ad.dto.AdAdvertiserAdminView;
import com.community.ad.service.AdAdvertiserAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 광고주 관리(admin-web {@code useAdAdvertiserApi} — {@code /ad-advertisers}, docs/features/ad-advertiser-contract.md). 조회 {@code ad.read} · 만들기·고치기·보관·삭제
 * {@code ad.manage}. 연락처 평문은 {@code POST /api/admin/ads/advertisers/{id}/reveal}({@code ad.contact.reveal}), 성과는 {@code GET /api/admin/ads/stats}. 응답은
 * {@code Cache-Control: no-store}. 없는 광고주 404 {@code AD_ADVERTISER_NOT_FOUND}, 지울 수 없음 409 {@code AD_ADVERTISER_HAS_BOOKINGS}, 보관할 수 없음 409
 * {@code AD_ADVERTISER_HAS_OPEN_BOOKINGS}, 사업자 광고주 보관·삭제 409 {@code AD_ADVERTISER_BUSINESS}.
 */
@RestController
@RequestMapping("/api/admin/ad-advertisers")
public class AdAdvertiserAdminController {

    private final AdAdvertiserAdminService service;

    public AdAdvertiserAdminController(AdAdvertiserAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.AD_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AdAdvertiserAdminView.Page>> list(@RequestParam(required = false) String kind, @RequestParam(required = false) String state,
                                                                       @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
                                                                       @RequestParam(required = false) String dir, @RequestParam(required = false) String page,
                                                                       @RequestParam(required = false) String size) {
        return ok(service.list(kind, state, q, sort, dir, page, size));
    }

    @RequirePermission(Permission.AD_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdAdvertiserAdminView.Detail>> detail(@PathVariable String id) {
        return ok(service.detail(id));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<AdAdvertiserAdminView.Detail>> create(@RequestBody(required = false) AdAdvertiserAdminRequest.Create body, AdminActor actor) {
        return ok(service.create(actor, body));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdAdvertiserAdminView.Detail>> update(@PathVariable String id, @RequestBody(required = false) AdAdvertiserAdminRequest.Update body,
                                                                           AdminActor actor) {
        return ok(service.update(actor, id, body));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<AdAdvertiserAdminView.Detail>> archive(@PathVariable String id, AdminActor actor) {
        return ok(service.setArchived(actor, id, true));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<ApiResponse<AdAdvertiserAdminView.Detail>> unarchive(@PathVariable String id, AdminActor actor) {
        return ok(service.setArchived(actor, id, false));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
