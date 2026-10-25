package com.community.restriction.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.restriction.dto.request.MemberRestrictionCreateRequest;
import com.community.restriction.dto.request.MemberRestrictionLiftRequest;
import com.community.restriction.dto.response.MemberRestrictionView;
import com.community.restriction.service.MemberRestrictionAdminService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 기능 제한 API(관리자 회원 상세 「기능 제한」 — docs/features/commerce-foundation-contract.md 「0-4」, commerce.md 12-3). 조회 {@code member.read}, 걸기·해제 {@code sanction.manage}.
 * 걸기는 201 + 만든 한 건. 입력 검사는 서비스가 칸별로 한다({@code fields.type|until|reason|adminMemo}).
 */
@RestController
@RequestMapping("/api/admin/members/{memberId}/restrictions")
public class MemberRestrictionController {

    private final MemberRestrictionAdminService service;

    public MemberRestrictionController(MemberRestrictionAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping
    public ApiResponse<List<MemberRestrictionView>> list(@PathVariable String memberId) {
        return ApiResponse.success(service.list(memberId));
    }

    @RequirePermission(Permission.SANCTION_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<MemberRestrictionView>> grant(@PathVariable String memberId, @RequestBody MemberRestrictionCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.grant(actor, memberId, body)));
    }

    @RequirePermission(Permission.SANCTION_MANAGE)
    @PostMapping("/{restrictionId}/lift")
    public ApiResponse<MemberRestrictionView> lift(@PathVariable String memberId, @PathVariable String restrictionId, @RequestBody MemberRestrictionLiftRequest body,
                                                   AdminActor actor) {
        return ApiResponse.success(service.lift(actor, memberId, restrictionId, body.reason()));
    }
}
