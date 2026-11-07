package com.community.popup.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.popup.dto.request.SitePopupOrderRequest;
import com.community.popup.dto.request.SitePopupSaveRequest;
import com.community.popup.dto.request.SitePopupVisibleRequest;
import com.community.popup.dto.response.SitePopupItemView;
import com.community.popup.dto.response.SitePopupListView;
import com.community.popup.service.SitePopupAdminService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팝업 관리 API(관리자 앱, docs/features/site-popup-contract.md) — 목록은 {@code site.read}, 나머지(만들기·고치기·차례·노출·지우기)는 {@code site.manage}(CSRF).
 * 규칙은 {@link SitePopupAdminService}. 그림은 {@code POST /api/admin/files}(purpose SITE_POPUP_IMAGE)로 먼저 올린다. 모든 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/site-popups")
public class SitePopupController {

    private final SitePopupAdminService service;

    public SitePopupController(SitePopupAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<SitePopupListView>> list() {
        return noStore(service.list());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<SitePopupItemView>> create(@RequestBody(required = false) SitePopupSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 차례 바꾸기 — 새 목록. 경로가 {@code /{id}} 보다 먼저 맞는다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/order")
    public ResponseEntity<ApiResponse<SitePopupListView>> order(@RequestBody(required = false) SitePopupOrderRequest body, AdminActor actor) {
        return noStore(service.order(actor, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SitePopupItemView>> update(@PathVariable String id, @RequestBody(required = false) SitePopupSaveRequest body,
                                                                 AdminActor actor) {
        return noStore(service.update(actor, id, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{id}/visible")
    public ResponseEntity<ApiResponse<SitePopupItemView>> visible(@PathVariable String id, @RequestBody(required = false) SitePopupVisibleRequest body,
                                                                  AdminActor actor) {
        return noStore(service.visible(actor, id, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
