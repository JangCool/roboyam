package com.community.hotdeal.controller;

import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealRuleView;
import com.community.hotdeal.service.HotdealDomainAdminService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 링크 규칙(admin-web {@code useHotdealDomainApi}) — 조회 {@code hotdeal.read}, 추가·고치기·지우기 {@code hotdeal.domain.manage} */
@RestController
@RequestMapping("/api/admin/hotdeal-domains")
public class HotdealDomainAdminController {

    private final HotdealDomainAdminService service;

    public HotdealDomainAdminController(HotdealDomainAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<HotdealRuleView.DomainList>> list(@RequestParam(required = false) String rule, @RequestParam(required = false) String q) {
        return HotdealAdminController.ok(service.list(rule, q));
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/impact")
    public ResponseEntity<ApiResponse<HotdealRuleView.Impact>> impact(@RequestParam(required = false) String domain) {
        return HotdealAdminController.ok(service.impact(domain));
    }

    @RequirePermission(Permission.HOTDEAL_DOMAIN_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<HotdealRuleView.Domain>> create(@RequestBody(required = false) HotdealAdminRequest.Domain body, AdminActor actor) {
        return HotdealAdminController.ok(service.create(actor, body));
    }

    @RequirePermission(Permission.HOTDEAL_DOMAIN_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HotdealRuleView.Domain>> update(@PathVariable String id, @RequestBody(required = false) HotdealAdminRequest.Domain body,
                                                                      AdminActor actor) {
        return HotdealAdminController.ok(service.update(actor, id, body));
    }

    @RequirePermission(Permission.HOTDEAL_DOMAIN_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
