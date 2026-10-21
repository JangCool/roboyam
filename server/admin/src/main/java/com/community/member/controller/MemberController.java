package com.community.member.controller;

import com.community.member.dto.request.MemberSearchRequest;
import com.community.member.dto.response.MemberDetailView;
import com.community.member.dto.response.MemberPage;
import com.community.member.dto.response.MemberRoleRef;
import com.community.member.service.MemberService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 조회 API(읽기 전용) — 목록·상세·역할 선택지 (docs/features/roles-contract.md 2단계). 입구의 권한은 메서드마다 선언한다(선언이 없으면 거절된다).
 * 이메일을 가릴지는 요청한 운영자의 {@code member.pii} 로 서비스가 정한다. 회원 조치(상태·등급·역할 부여)는 이 단계 밖이다.
 */
@RestController
@RequestMapping("/api/admin/members")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    /** 거른 목록 한 쪽 — 조건은 쿼리 문자열({@link MemberSearchRequest}) */
    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping
    public ApiResponse<MemberPage> list(MemberSearchRequest request, AdminActor actor) {
        return ApiResponse.success(service.list(actor, request));
    }

    /** 역할 고르개가 쓸 회원 역할 목록 */
    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/role-options")
    public ApiResponse<List<MemberRoleRef>> roleOptions() {
        return ApiResponse.success(service.roleOptions());
    }

    /** 회원 한 명의 상세 — 열람은 감사 기록으로 남는다 */
    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/{memberId}")
    public ApiResponse<MemberDetailView> detail(@PathVariable String memberId, AdminActor actor) {
        return ApiResponse.success(service.detail(actor, memberId));
    }
}
