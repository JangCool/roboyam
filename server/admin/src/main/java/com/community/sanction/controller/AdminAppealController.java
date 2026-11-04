package com.community.sanction.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.request.AppealAnswerRequest;
import com.community.sanction.dto.response.AppealDetailView;
import com.community.sanction.dto.response.AppealListView;
import com.community.sanction.dto.response.AppealSummaryView;
import com.community.sanction.service.AdminAppealService;
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
 * 관리자 이의제기 API(admin-web {@code useAppealApi.ts}) — 목록·요약·상세 {@code sanction.read}, 인용·기각 {@code appeal.manage}(바뀐 상세를 돌려준다).
 * 쓰기는 CSRF, 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/appeals")
public class AdminAppealController {

    private final AdminAppealService service;

    public AdminAppealController(AdminAppealService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AppealListView>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String sort,
                                                            @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(service.list(status, sort, page, size));
    }

    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AppealSummaryView>> summary() {
        return noStore(service.summary());
    }

    /** 상세 — 없으면 404 {@code APPEAL_NOT_FOUND} */
    @RequirePermission(Permission.SANCTION_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppealDetailView>> detail(@PathVariable String id) {
        return noStore(service.detail(id));
    }

    /** 인용 — 제재 무효, 진행 중이던 정지는 즉시 풀린다 */
    @RequirePermission(Permission.APPEAL_MANAGE)
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<AppealDetailView>> accept(@PathVariable String id, @RequestBody(required = false) AppealAnswerRequest body, AdminActor actor) {
        return noStore(service.answer(actor, id, true, body));
    }

    /** 기각 — 제재는 그대로 */
    @RequirePermission(Permission.APPEAL_MANAGE)
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AppealDetailView>> reject(@PathVariable String id, @RequestBody(required = false) AppealAnswerRequest body, AdminActor actor) {
        return noStore(service.answer(actor, id, false, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
