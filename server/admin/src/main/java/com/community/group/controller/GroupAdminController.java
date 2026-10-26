package com.community.group.controller;

import com.community.group.dto.request.GroupAdminMemoRequest;
import com.community.group.dto.request.GroupAdminReasonRequest;
import com.community.group.dto.response.GroupAdminDetailView;
import com.community.group.dto.response.GroupAdminListView;
import com.community.group.dto.response.GroupOpenRequestListView;
import com.community.group.service.GroupAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소모임 개설 승인·소모임 관리 API — admin-web {@code useGroupAdminApi.ts} 머리말 그대로. 조회 {@code group.read}, 승인·반려·비활성화·다시 열기 {@code group.manage}
 * (없으면 403 {@code PERMISSION_DENIED}). 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/groups")
public class GroupAdminController {

    private final GroupAdminService service;

    public GroupAdminController(GroupAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<GroupOpenRequestListView>> requests() {
        return noStore(service.requests());
    }

    @RequirePermission(Permission.GROUP_MANAGE)
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable String id, AdminActor actor) {
        service.approve(actor, id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.GROUP_MANAGE)
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable String id, @RequestBody(required = false) GroupAdminReasonRequest body, AdminActor actor) {
        service.reject(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<GroupAdminListView>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String closed, @RequestParam(required = false) String offline,
                                                                @RequestParam(required = false) String visibility, @RequestParam(required = false) String page,
                                                                @RequestParam(required = false) String size) {
        return noStore(service.list(q, status, closed, offline, visibility, page, size));
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupAdminDetailView>> detail(@PathVariable String id) {
        return noStore(service.detail(id));
    }

    @RequirePermission(Permission.GROUP_MANAGE)
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String id, @RequestBody(required = false) GroupAdminReasonRequest body, AdminActor actor) {
        service.deactivate(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.GROUP_MANAGE)
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable String id, @RequestBody(required = false) GroupAdminMemoRequest body, AdminActor actor) {
        service.reactivate(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
