package com.community.batch.controller;

import com.community.batch.dto.request.BatchFailureSearchRequest;
import com.community.batch.dto.request.BatchLogSearchRequest;
import com.community.batch.dto.request.BatchRunRequestBody;
import com.community.batch.dto.request.BatchScheduleRequest;
import com.community.batch.dto.request.BatchToggleRequest;
import com.community.batch.dto.response.BatchFailurePage;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.dto.response.BatchLogPage;
import com.community.batch.dto.response.BatchRunDetailView;
import com.community.batch.dto.response.BatchRunRequestView;
import com.community.batch.service.BatchAdminService;
import com.community.batch.service.BatchQueryService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 API — 목록·켜기/끄기·cron 수정·지금 실행(요청)·실행 로그·실행 상세·건별 실패·다시 실행(요청) (docs/features/roles-contract.md 「배치 인프라 결과」,
 * docs/features/batch-contract.md 「0-2 보강」). 입구의 권한은 메서드마다 선언한다: 조회 {@code batch.read}, 켜기/끄기·cron {@code batch.manage},
 * 지금 실행·다시 실행 {@code batch.run}(민감 조치). 실행 요청은 202 로 요청 줄을 돌려주고 화면은 {@code GET /batch-requests/{requestId}} 로 따라간다.
 */
@RestController
@RequestMapping("/api/admin")
public class BatchController {

    private final BatchQueryService queries;
    private final BatchAdminService service;

    public BatchController(BatchQueryService queries, BatchAdminService service) {
        this.queries = queries;
        this.service = service;
    }

    /** 배치 목록(정의 + 설정 + 마지막 실행 요약 + 끝나지 않은 요청 + 파라미터·단계) */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-jobs")
    public ApiResponse<List<BatchJobView>> jobs() {
        return ApiResponse.success(queries.list());
    }

    /** 켜고 끈다 — 바뀐 목록 전체를 돌려준다 */
    @RequirePermission(Permission.BATCH_MANAGE)
    @PutMapping("/batch-jobs/{jobName}")
    public ApiResponse<List<BatchJobView>> toggle(@PathVariable String jobName, @Valid @RequestBody BatchToggleRequest body, AdminActor actor) {
        return ApiResponse.success(service.toggle(actor, jobName, body.enabled()));
    }

    /** cron 을 바꾼다(재기동 없이 반영) — 바뀐 목록 전체를 돌려준다 */
    @RequirePermission(Permission.BATCH_MANAGE)
    @PutMapping("/batch-jobs/{jobName}/schedule")
    public ApiResponse<List<BatchJobView>> schedule(@PathVariable String jobName, @Valid @RequestBody BatchScheduleRequest body, AdminActor actor) {
        return ApiResponse.success(service.changeSchedule(actor, jobName, body.schedule()));
    }

    /** 지금 실행(파라미터 지정 실행)을 요청한다 — 202 + 요청 줄. 실행 중이거나 끝나지 않은 요청이 있으면 409 */
    @RequirePermission(Permission.BATCH_RUN)
    @PostMapping("/batch-jobs/{jobName}/run")
    public ResponseEntity<ApiResponse<BatchRunRequestView>> run(@PathVariable String jobName, @RequestBody(required = false) BatchRunRequestBody body, AdminActor actor) {
        return ResponseEntity.accepted().body(ApiResponse.success(service.requestRun(actor, jobName, body)));
    }

    /** 실행 요청 한 줄(상태 따라가기) */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-requests/{requestId}")
    public ApiResponse<BatchRunRequestView> request(@PathVariable String requestId) {
        return ApiResponse.success(queries.request(requestId));
    }

    /** 실행 로그 한 쪽 — 조건은 쿼리 문자열 */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-logs")
    public ApiResponse<BatchLogPage> logs(BatchLogSearchRequest request) {
        return ApiResponse.success(queries.logs(request));
    }

    /** 실행 상세 — 단계·건수·파라미터·이어 받기·다시 실행 가능 여부 */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-logs/{logId}")
    public ApiResponse<BatchRunDetailView> log(@PathVariable String logId) {
        return ApiResponse.success(queries.detail(logId));
    }

    /** 실행의 건별 실패 한 쪽 — 조건은 쿼리 문자열({@code resolved·page·size}) */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-logs/{logId}/failures")
    public ApiResponse<BatchFailurePage> failures(@PathVariable String logId, BatchFailureSearchRequest request) {
        return ApiResponse.success(queries.failures(logId, request));
    }

    /** 「실패 지점부터 다시」를 요청한다 — 202 + 요청 줄 */
    @RequirePermission(Permission.BATCH_RUN)
    @PostMapping("/batch-logs/{logId}/resume")
    public ResponseEntity<ApiResponse<BatchRunRequestView>> resume(@PathVariable String logId, AdminActor actor) {
        return ResponseEntity.accepted().body(ApiResponse.success(service.requestResume(actor, logId)));
    }

    /** 「실패 건만 다시」를 요청한다 — 202 + 요청 줄 */
    @RequirePermission(Permission.BATCH_RUN)
    @PostMapping("/batch-logs/{logId}/retry-failures")
    public ResponseEntity<ApiResponse<BatchRunRequestView>> retryFailures(@PathVariable String logId, AdminActor actor) {
        return ResponseEntity.accepted().body(ApiResponse.success(service.requestRetryFailures(actor, logId)));
    }
}
