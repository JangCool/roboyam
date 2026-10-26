package com.community.report.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.post.dto.response.AdminPostBoardOptionsView;
import com.community.post.service.AdminPostQueryService;
import com.community.report.dto.request.ReportBulkDismissRequest;
import com.community.report.dto.request.ReportDismissRequest;
import com.community.report.dto.request.ReportResolveRequest;
import com.community.report.dto.response.ReportBulkResultView;
import com.community.report.dto.response.ReportCaseDetailView;
import com.community.report.dto.response.ReportCaseListView;
import com.community.report.dto.response.ReportSummaryView;
import com.community.report.service.AdminReportQueryService;
import com.community.report.service.ReportCaseActionService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 관리 API(관리자 앱) — 읽기 {@code report.read}, 처리 {@code report.manage}, 쓰기는 CSRF, 모든 본문 응답은 {@code Cache-Control: no-store}.
 * 조치·무시·다시 열기는 처리 뒤의 사건 상세를 돌려준다. 규칙은 {@link AdminReportQueryService}·{@link ReportCaseActionService}, 계약은 docs/features/report-admin-contract.md.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportQueryService queries;
    private final ReportCaseActionService actions;
    private final AdminPostQueryService posts;

    public AdminReportController(AdminReportQueryService queries, ReportCaseActionService actions, AdminPostQueryService posts) {
        this.queries = queries;
        this.actions = actions;
        this.posts = posts;
    }

    /**
     * 사건 목록 — {@code status}(OPEN·PENDING·IN_REVIEW·RESOLVED·DISMISSED·ALL)·{@code targetType}·{@code reason}·{@code searchField}(TITLE·AUTHOR·CASE_NO)·{@code q}·
     * {@code from}·{@code to}(마지막 신고 UTC ISO)·{@code board}·{@code postId}·{@code groupId}(소모임 사건 — V95)·{@code sort}(newest·oldest·most)·{@code page}(0부터)·{@code size}(기본 20, 최대 50)
     */
    @RequirePermission(Permission.REPORT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<ReportCaseListView>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String targetType,
                                                                @RequestParam(required = false) String reason, @RequestParam(required = false) String searchField,
                                                                @RequestParam(required = false) String q, @RequestParam(required = false) String from,
                                                                @RequestParam(required = false) String to, @RequestParam(required = false) String board,
                                                                @RequestParam(required = false) String postId, @RequestParam(required = false) String groupId,
                                                                @RequestParam(required = false) String sort,
                                                                @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(queries.list(status, targetType, reason, searchField, q, from, to, board, postId, groupId, sort, page, size));
    }

    /** 열린 사건 수 — {@code { pending, inReview }} */
    @RequirePermission(Permission.REPORT_READ)
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReportSummaryView>> summary() {
        return noStore(queries.summary());
    }

    /** 게시판 고르기(목록 거르기) — 게시글 관리 {@code GET /api/admin/posts/board-options} 와 같은 모양·같은 서비스(켜진 게시판·말머리). 권한만 {@code report.read} */
    @RequirePermission(Permission.REPORT_READ)
    @GetMapping("/board-options")
    public ResponseEntity<ApiResponse<AdminPostBoardOptionsView>> boardOptions() {
        return noStore(posts.boardOptions());
    }

    /** 사건 상세 — 처리 권한이 있고 처리 대기면 검토 중이 된다(검토자 = 나). 없는 사건 404 REPORT_CASE_NOT_FOUND */
    @RequirePermission(Permission.REPORT_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportCaseDetailView>> detail(@PathVariable String id, AdminActor actor) {
        return noStore(queries.open(actor, id));
    }

    /** 조치 — {@code { action: HIDE|DELETE|KEEP_HIDDEN|DEACTIVATE(소모임 사건만), reasonCode, note }} */
    @RequirePermission(Permission.REPORT_MANAGE)
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<ReportCaseDetailView>> resolve(@PathVariable String id, @RequestBody(required = false) ReportResolveRequest body,
                                                                     AdminActor actor) {
        actions.resolve(actor, id, body);
        return noStore(queries.detail(id));
    }

    /** 무시 — {@code { reasonCode?, note?, falseReport }}. 자동 숨김이었으면 복구 */
    @RequirePermission(Permission.REPORT_MANAGE)
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<ReportCaseDetailView>> dismiss(@PathVariable String id, @RequestBody(required = false) ReportDismissRequest body,
                                                                     AdminActor actor) {
        actions.dismiss(actor, id, body);
        return noStore(queries.detail(id));
    }

    /** 일괄 무시 — {@code { ids(1~50), falseReport, note? }} → {@code { done, failed }}(사건마다 따로) */
    @RequirePermission(Permission.REPORT_MANAGE)
    @PostMapping("/dismiss")
    public ResponseEntity<ApiResponse<ReportBulkResultView>> bulkDismiss(@RequestBody(required = false) ReportBulkDismissRequest body, AdminActor actor) {
        return noStore(actions.bulkDismiss(actor, body));
    }

    /** 무시한 사건 다시 열기 */
    @RequirePermission(Permission.REPORT_MANAGE)
    @PostMapping("/{id}/reopen")
    public ResponseEntity<ApiResponse<ReportCaseDetailView>> reopen(@PathVariable String id, AdminActor actor) {
        actions.reopen(actor, id);
        return noStore(queries.detail(id));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
