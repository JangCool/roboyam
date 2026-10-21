package com.community.grade.controller;

import com.community.grade.dto.request.GradesSaveRequest;
import com.community.grade.dto.response.GradePreviewResponse;
import com.community.grade.dto.response.GradesResponse;
import com.community.grade.service.GradeService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 등급 설정 API — 목록+옵션 조회, 일괄 저장, 저장 전 영향 미리보기 (docs/features/roles-contract.md 2단계). 입구의 권한은 메서드마다 선언한다(선언이 없으면 거절된다).
 * 요청 본문의 검사는 칸 경로({@code grades[i].label} …)와 검사 순서가 계약이라 {@code @Valid} 가 아니라 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/grades")
public class GradeController {

    private final GradeService service;

    public GradeController(GradeService service) {
        this.service = service;
    }

    /** 등급 목록(서열 순서)·등급별 회원 수·옵션. 회원 조회·역할 관리 화면도 같은 목록을 읽으므로 grade.read 말고 member.read · role.read 로도 읽을 수 있다 */
    @RequirePermission(anyOf = {Permission.GRADE_READ, Permission.MEMBER_READ, Permission.ROLE_READ})
    @GetMapping
    public ApiResponse<GradesResponse> load() {
        return ApiResponse.success(service.load());
    }

    /** 등급 목록과 옵션을 통째로 저장한다 — 서버가 지금 목록과 비교해 추가·삭제·순서·수정을 처리하고 저장된 모양을 돌려준다 */
    @RequirePermission(Permission.GRADE_MANAGE)
    @PutMapping
    public ApiResponse<GradesResponse> save(@RequestBody GradesSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }

    /** 저장하지 않고, 이 기준으로 저장하면 승급될 회원 수와 삭제로 이동할 회원 수를 센다 */
    @RequirePermission(Permission.GRADE_MANAGE)
    @PostMapping("/preview")
    public ApiResponse<GradePreviewResponse> preview(@RequestBody GradesSaveRequest body) {
        return ApiResponse.success(service.preview(body));
    }
}
