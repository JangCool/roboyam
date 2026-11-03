package com.community.dashboard.controller;

import com.community.dashboard.dto.request.DashboardLayoutRequest;
import com.community.dashboard.dto.response.DashboardLayoutView;
import com.community.dashboard.service.DashboardLayoutService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 대시보드 구성 API — 로그인한 운영자 <b>본인</b>의 것만(경로에 ID 가 없다). 역할(권한)이 없어도 부를 수 있어 {@link AdminAuthenticated} 이고, 응답은 {@code Cache-Control: no-store}.
 * (docs/features/admin-dashboard-contract.md 「API 후보」, admin-web {@code useDashboardLayoutApi.types.ts})
 */
@RestController
@RequestMapping("/api/admin/me/dashboard")
@AdminAuthenticated
public class DashboardLayoutController {

    private final DashboardLayoutService layouts;

    public DashboardLayoutController(DashboardLayoutService layouts) {
        this.layouts = layouts;
    }

    /** 내 구성 — 저장한 적 없으면 {@code gadgets: null} */
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardLayoutView>> get(AdminActor actor) {
        return noStore(layouts.get(actor));
    }

    /** 구성을 저장한다 — 저장한 값을 돌려준다. 틀리면 400 {@code VALIDATION}({@code fields.gadgets[i].<칸>}) */
    @PutMapping
    public ResponseEntity<ApiResponse<DashboardLayoutView>> save(@RequestBody(required = false) DashboardLayoutRequest body, AdminActor actor) {
        return noStore(layouts.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
