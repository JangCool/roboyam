package com.community.operator.controller;

import com.community.operator.dto.request.OperatorCreateRequest;
import com.community.operator.dto.request.OperatorRejectRequest;
import com.community.operator.dto.request.OperatorStatusRequest;
import com.community.operator.dto.request.OperatorUpdateRequest;
import com.community.operator.dto.response.OperatorPage;
import com.community.operator.dto.response.OperatorView;
import com.community.operator.entity.OperatorTab;
import com.community.operator.service.OperatorService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 목록·만들기·수정·상태·반려 (docs/features/roles-contract.md). 입구의 권한은 메서드마다 선언한다(선언이 없으면 거절된다).
 * 요청 내용에 따라 달라지는 검사(역할을 줄 때의 권한 상승 방지, 상태 칸마다 다른 권한)는 서비스가 이어서 한다. 지우는 API 는 없다 — 「사용 중지」가 대신한다.
 */
@RestController
@RequestMapping("/api/admin/operators")
public class OperatorController {

    private final OperatorService service;

    public OperatorController(OperatorService service) {
        this.service = service;
    }

    /** 탭별 목록({@code tab} 기본 ACTIVE)과 탭마다 몇 명인지. {@code keyword} 는 이름·이메일 부분 일치 */
    @RequirePermission(Permission.OPERATOR_READ)
    @GetMapping
    public ApiResponse<OperatorPage> list(@RequestParam(defaultValue = "ACTIVE") OperatorTab tab, @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.list(tab, keyword));
    }

    /** 계정을 바로 만든다(승인된 상태). 역할을 주면 {@code role.grant} 도 필요하다 */
    @RequirePermission(Permission.OPERATOR_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<OperatorView>> create(@Valid @RequestBody OperatorCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 이름·역할·계정 기간을 고친다. {@code roleIds} 를 바꾸려면 {@code role.grant} 도 필요하다 */
    @RequirePermission(Permission.OPERATOR_MANAGE)
    @PutMapping("/{operatorId}")
    public ApiResponse<OperatorView> update(@PathVariable String operatorId, @Valid @RequestBody OperatorUpdateRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, operatorId, body));
    }

    /** 계정 상태를 바꾼다 — 잠금은 {@code operator.lock}, 나머지는 {@code operator.manage}(서비스가 바뀌는 칸을 보고 정한다) */
    @RequirePermission(anyOf = {Permission.OPERATOR_MANAGE, Permission.OPERATOR_LOCK})
    @PutMapping("/{operatorId}/status")
    public ApiResponse<OperatorView> changeStatus(@PathVariable String operatorId, @RequestBody OperatorStatusRequest body, AdminActor actor) {
        return ApiResponse.success(service.changeStatus(actor, operatorId, body));
    }

    /** 승인 대기(신청) 건을 반려한다 — 사유는 선택. 본문이 없어도 된다 */
    @RequirePermission(Permission.OPERATOR_MANAGE)
    @PostMapping("/{operatorId}/rejection")
    public ResponseEntity<Void> reject(@PathVariable String operatorId, @Valid @RequestBody(required = false) OperatorRejectRequest body, AdminActor actor) {
        service.reject(actor, operatorId, body);
        return ResponseEntity.noContent().build();
    }
}
