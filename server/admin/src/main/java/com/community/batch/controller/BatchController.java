package com.community.batch.controller;

import com.community.batch.dto.request.BatchLogSearchRequest;
import com.community.batch.dto.request.BatchScheduleRequest;
import com.community.batch.dto.request.BatchToggleRequest;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.dto.response.BatchLogDetailView;
import com.community.batch.dto.response.BatchLogPage;
import com.community.batch.service.BatchAdminService;
import com.community.batch.service.BatchQueryService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 API — 목록·켜기/끄기·cron 수정·지금 실행·실행 로그 (docs/features/roles-contract.md 「배치 인프라 결과」, docs/features/batch-contract.md).
 * 입구의 권한은 메서드마다 선언한다: 조회 {@code batch.read}, 켜기/끄기·cron {@code batch.manage}, 지금 실행 {@code batch.run}(민감 조치).
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

    /** 배치 목록(정의 + 설정 + 마지막 실행 요약) */
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

    /** 지금 실행한다 — 끝날 때까지 기다려 바뀐 목록 전체를 돌려준다. 이미 실행 중이면 409 */
    @RequirePermission(Permission.BATCH_RUN)
    @PostMapping("/batch-jobs/{jobName}/run")
    public ApiResponse<List<BatchJobView>> run(@PathVariable String jobName, AdminActor actor) {
        return ApiResponse.success(service.runNow(actor, jobName));
    }

    /** 실행 로그 한 쪽 — 조건은 쿼리 문자열 */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-logs")
    public ApiResponse<BatchLogPage> logs(BatchLogSearchRequest request) {
        return ApiResponse.success(queries.logs(request));
    }

    /** 실행 로그 한 줄의 상세 */
    @RequirePermission(Permission.BATCH_READ)
    @GetMapping("/batch-logs/{logId}")
    public ApiResponse<BatchLogDetailView> log(@PathVariable String logId) {
        return ApiResponse.success(queries.logDetail(logId));
    }
}
