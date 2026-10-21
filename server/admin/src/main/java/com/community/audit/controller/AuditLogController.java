package com.community.audit.controller;

import com.community.audit.dto.request.AuditSearchRequest;
import com.community.audit.dto.response.AuditOptions;
import com.community.audit.dto.response.AuditPage;
import com.community.audit.service.AuditQueryService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회 API — <b>읽기만 있다</b>(고치거나 지우는 API 가 없다). 권한은 모두 {@code audit.read}. 기록에는 운영 정보가 들어 있어 응답은 {@code Cache-Control: no-store}.
 * (docs/features/roles-contract.md 「4단계 구현 결과」, admin-web {@code useAuditApi.types.ts})
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditQueryService queries;

    public AuditLogController(AuditQueryService queries) {
        this.queries = queries;
    }

    /** 거른 목록 한 쪽 — 발생 시각 최근순. 조건은 쿼리 문자열 */
    @RequirePermission(Permission.AUDIT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AuditPage>> search(AuditSearchRequest request) {
        return noStore(queries.search(request));
    }

    /** 거르기 선택지(작업·대상 종류·결과) — 이름은 요청 언어 */
    @RequirePermission(Permission.AUDIT_READ)
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<AuditOptions>> options() {
        return noStore(queries.options());
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
