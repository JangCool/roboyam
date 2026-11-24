package com.community.operator.controller;

import com.community.operator.dto.request.OperatorMemberCreateRequest;
import com.community.operator.dto.request.OperatorMemberLinkRequest;
import com.community.operator.dto.response.OperatorMemberAccountView;
import com.community.operator.dto.response.OperatorMemberCandidates;
import com.community.operator.dto.response.OperatorMemberCreateResult;
import com.community.operator.service.OperatorMemberAccountAdminService;
import com.community.operator.service.OperatorMemberCreateService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 회원 계정(admin-web {@code useOperatorMemberAccountApi.ts}, docs/features/operator-member-account-contract.md 「API > 관리자」) — 운영자 서랍 「사이트 계정」 탭.
 *
 * <ul>
 *   <li>{@code GET /api/admin/operators/{operatorId}/member-account}({@code operator.read}) → {@code OperatorMemberAccountView} · 404 {@code OPERATOR_NOT_FOUND}</li>
 *   <li>{@code GET …/member-account/candidates?q=}({@code operator.read}) → {@code OperatorMemberCandidates} — 회원 검색(닉네임 · 이메일 · 회원번호)</li>
 *   <li>{@code PUT …/member-account { userId }}({@code operator.manage}) → 같은 모양 · 404 {@code MEMBER_NOT_FOUND} · 409 {@code OPERATOR_MEMBER_NOT_ELIGIBLE}(+ {@code ineligibleReasons})·
 *       {@code OPERATOR_MEMBER_ALREADY_LINKED}·{@code OPERATOR_ROOT_PROTECTED} — 같은 이메일이 아니어도 된다(2026-11-24)</li>
 *   <li>{@code DELETE …/member-account}({@code operator.manage}) → 같은 모양 · 409 {@code OPERATOR_MEMBER_NOT_LINKED}·{@code OPERATOR_ROOT_PROTECTED}</li>
 *   <li>{@code POST …/member-account { email, nickname, passwordMode, password }}({@code operator.manage}) → 201 {@code OperatorMemberCreateResult} — 새 계정을 만들어 잇는다</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/operators/{operatorId}/member-account")
public class OperatorMemberAccountController {

    private final OperatorMemberAccountAdminService service;
    private final OperatorMemberCreateService creator;

    public OperatorMemberAccountController(OperatorMemberAccountAdminService service, OperatorMemberCreateService creator) {
        this.service = service;
        this.creator = creator;
    }

    @RequirePermission(Permission.OPERATOR_READ)
    @GetMapping
    public ApiResponse<OperatorMemberAccountView> get(@PathVariable String operatorId) {
        return ApiResponse.success(service.get(operatorId));
    }

    /**
     * 이을 회원 찾기(같은 이메일이 아니어도 된다 — 세션 정리 결정 2026-11-24) — {@code OperatorMemberCandidates}(최대 20건) · 400 {@code VALIDATION fields.q}(앞뒤 공백 자르고 2~100자) ·
     * 404 {@code OPERATOR_NOT_FOUND}
     */
    @RequirePermission(Permission.OPERATOR_READ)
    @GetMapping("/candidates")
    public ApiResponse<OperatorMemberCandidates> candidates(@PathVariable String operatorId, @RequestParam(required = false) String q) {
        return ApiResponse.success(service.searchCandidates(operatorId, q));
    }

    @RequirePermission(Permission.OPERATOR_MANAGE)
    @PutMapping
    public ApiResponse<OperatorMemberAccountView> link(@PathVariable String operatorId, @RequestBody(required = false) OperatorMemberLinkRequest body, AdminActor actor) {
        return ApiResponse.success(service.link(actor, operatorId, body));
    }

    /**
     * 새 회원 계정을 만들고 바로 잇는다 — 201 {@code OperatorMemberCreateResult} · 400 {@code VALIDATION}({@code fields.email|nickname|passwordMode|password}) ·
     * 404 {@code OPERATOR_NOT_FOUND} · 409 {@code OPERATOR_ROOT_PROTECTED}·{@code OPERATOR_MEMBER_ALREADY_LINKED}·{@code EMAIL_TAKEN}·{@code NICKNAME_TAKEN}·{@code REJOIN_BLOCKED}
     */
    @RequirePermission(Permission.OPERATOR_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<OperatorMemberCreateResult>> create(@PathVariable String operatorId, @RequestBody(required = false) OperatorMemberCreateRequest body,
                                                                          AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(creator.create(actor, operatorId, body)));
    }

    @RequirePermission(Permission.OPERATOR_MANAGE)
    @DeleteMapping
    public ApiResponse<OperatorMemberAccountView> unlink(@PathVariable String operatorId, AdminActor actor) {
        return ApiResponse.success(service.unlink(actor, operatorId));
    }
}
