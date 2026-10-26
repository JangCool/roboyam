package com.community.group.controller;

import com.community.group.dto.request.GroupRuleSaveRequests;
import com.community.group.dto.response.GroupRuleViews;
import com.community.group.service.GroupSettingsAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소모임 설정 API — admin-web {@code useGroupSettingsApi.ts} 머리말 그대로. 조회·이력 {@code group.read}, 저장 {@code group.settings}(없으면 403 {@code PERMISSION_DENIED}).
 * 구획마다 GET · PUT(그 구획의 칸 모두, 응답은 GET 과 같다). 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/group-settings")
public class GroupSettingsController {

    private final GroupSettingsAdminService service;

    public GroupSettingsController(GroupSettingsAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/creation-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Creation>> creation() {
        return GroupAdminController.noStore(service.creation());
    }

    @RequirePermission(Permission.GROUP_SETTINGS)
    @PutMapping("/creation-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Creation>> saveCreation(@RequestBody(required = false) GroupRuleSaveRequests.Creation body, AdminActor actor) {
        return GroupAdminController.noStore(service.saveCreation(actor, body));
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/manager-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Manager>> manager() {
        return GroupAdminController.noStore(service.manager());
    }

    @RequirePermission(Permission.GROUP_SETTINGS)
    @PutMapping("/manager-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Manager>> saveManager(@RequestBody(required = false) GroupRuleSaveRequests.Manager body, AdminActor actor) {
        return GroupAdminController.noStore(service.saveManager(actor, body));
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/visibility-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Visibility>> visibility() {
        return GroupAdminController.noStore(service.visibility());
    }

    @RequirePermission(Permission.GROUP_SETTINGS)
    @PutMapping("/visibility-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Visibility>> saveVisibility(@RequestBody(required = false) GroupRuleSaveRequests.Visibility body,
                                                                               AdminActor actor) {
        return GroupAdminController.noStore(service.saveVisibility(actor, body));
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/policy-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Policy>> policy() {
        return GroupAdminController.noStore(service.policy());
    }

    @RequirePermission(Permission.GROUP_SETTINGS)
    @PutMapping("/policy-rules")
    public ResponseEntity<ApiResponse<GroupRuleViews.Policy>> savePolicy(@RequestBody(required = false) GroupRuleSaveRequests.Policy body, AdminActor actor) {
        return GroupAdminController.noStore(service.savePolicy(actor, body));
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<GroupRuleViews.HistoryPage>> history(@RequestParam(required = false) String section,
                                                                          @RequestParam(required = false) String page,
                                                                          @RequestParam(required = false) String size) {
        return GroupAdminController.noStore(service.history(section, page, size));
    }
}
