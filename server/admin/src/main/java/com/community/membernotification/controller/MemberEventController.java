package com.community.membernotification.controller;

import com.community.membernotification.dto.request.MemberEventUpdateRequest;
import com.community.membernotification.dto.response.MemberEventView;
import com.community.membernotification.service.MemberEventAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 이벤트 관리 API — 읽기 {@code notification.read}, 바꾸기 {@code notification.manage}(운영자 이벤트와 같다) */
@RestController
@RequestMapping("/api/admin/member-events")
public class MemberEventController {

    private final MemberEventAdminService service;

    public MemberEventController(MemberEventAdminService service) {
        this.service = service;
    }

    /** 모든 회원 이벤트(준비 중 포함) */
    @RequirePermission(Permission.NOTIFICATION_READ)
    @GetMapping
    public ApiResponse<List<MemberEventView>> list() {
        return ApiResponse.success(service.list());
    }

    /** 이벤트 하나의 사용 여부·앱 알림·이메일·회원이 끌 수 있음을 바꾼다 — 바뀐 한 줄을 돌려준다 */
    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @PutMapping("/{code}")
    public ApiResponse<MemberEventView> update(@PathVariable String code, @RequestBody MemberEventUpdateRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, code, body));
    }
}
