package com.community.audit.controller;

import com.community.audit.dto.request.AuditRetentionSaveRequest;
import com.community.audit.dto.request.AuditSearchRequest;
import com.community.audit.dto.response.AuditOptions;
import com.community.audit.dto.response.AuditPage;
import com.community.audit.dto.response.AuditRetentionView;
import com.community.audit.service.AuditQueryService;
import com.community.audit.service.AuditRetentionService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회 API — 기록을 고치거나 지우는 API 가 없다. 목록·선택지·보관 기간 조회는 {@code audit.read}, 보관 기간 저장은 <b>최고 관리자만</b>(권한 코드 없이
 * {@code @AdminAuthenticated} 로 들어와 서비스가 루트가 아니면 403). 기록에는 운영 정보가 들어 있어 응답은 {@code Cache-Control: no-store}.
 * (docs/features/roles-contract.md 「4단계 구현 결과」, commerce-foundation-contract.md 「0-5」, admin-web {@code useAuditApi.types.ts}·{@code useAuditRetentionApi})
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditQueryService queries;
    private final AuditRetentionService retention;

    public AuditLogController(AuditQueryService queries, AuditRetentionService retention) {
        this.queries = queries;
        this.retention = retention;
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

    /** 보관 분류별 기간과 이 운영자가 바꿀 수 있는지 */
    @RequirePermission(Permission.AUDIT_READ)
    @GetMapping("/retention")
    public ResponseEntity<ApiResponse<AuditRetentionView>> retention(AdminActor actor) {
        return noStore(retention.view(actor));
    }

    /** 보관 기간 저장 — 최고 관리자만(서비스가 막는다), 세 분류 모두 */
    @AdminAuthenticated
    @PutMapping("/retention")
    public ResponseEntity<ApiResponse<AuditRetentionView>> saveRetention(AdminActor actor, @RequestBody(required = false) AuditRetentionSaveRequest body) {
        return noStore(retention.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
