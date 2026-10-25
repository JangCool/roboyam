package com.community.withdrawal.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.withdrawal.dto.RevealedContact;
import com.community.withdrawal.dto.request.WithdrawnContactRevealRequest;
import com.community.withdrawal.dto.response.WithdrawnContactView;
import com.community.withdrawal.service.WithdrawnContactAdminService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 탈퇴 회원 연락용 이메일(분리 보관) API — 상태·열람 기록은 {@code member.read}, 값 열람은 전용 권한 {@code member.withdrawn.contact} + 사유(감사, 이메일 없음).
 * 응답은 {@code Cache-Control: no-store}(열람 응답에 이메일 원문이 있다). 계약 「0-4」, commerce.md 17-2.
 */
@RestController
@RequestMapping("/api/admin/members/{memberId}/withdrawn-contact")
public class WithdrawnContactController {

    private final WithdrawnContactAdminService service;

    public WithdrawnContactController(WithdrawnContactAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<WithdrawnContactView>> view(@PathVariable String memberId) {
        return noStore(service.view(memberId));
    }

    @RequirePermission(Permission.MEMBER_WITHDRAWN_CONTACT)
    @PostMapping("/reveal")
    public ResponseEntity<ApiResponse<RevealedContact>> reveal(@PathVariable String memberId, @RequestBody WithdrawnContactRevealRequest body, AdminActor actor) {
        return noStore(service.reveal(actor, memberId, body.reason()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
