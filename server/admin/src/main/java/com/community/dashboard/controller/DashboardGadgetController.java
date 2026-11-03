package com.community.dashboard.controller;

import com.community.dashboard.dto.request.DashboardGadgetsRequest;
import com.community.dashboard.dto.response.gadget.DashboardGadgetsView;
import com.community.dashboard.service.DashboardGadgetService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 대시보드 가젯 데이터 묶음 조회 — {@code GET /api/admin/dashboard/gadgets?types=A,B&limit.A=5&days.B=30}.
 * 종류마다 권한이 달라 입구는 로그인만({@link AdminAuthenticated}) 보고 종류별 권한은 서비스가 본다(권한 없는 종류는 null, 한 종류만 요청했으면 403).
 * 운영자마다 볼 수 있는 값이 달라 응답은 {@code Cache-Control: no-store}. (docs/features/admin-dashboard-contract.md, admin-web {@code useDashboardGadgetApi.types.ts})
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@AdminAuthenticated
public class DashboardGadgetController {

    private static final String LIMIT_PREFIX = "limit.";
    private static final String DAYS_PREFIX = "days.";

    private final DashboardGadgetService gadgets;

    public DashboardGadgetController(DashboardGadgetService gadgets) {
        this.gadgets = gadgets;
    }

    /** 요청한 종류의 숫자·목록 + 센 시각 */
    @GetMapping("/gadgets")
    public ResponseEntity<ApiResponse<DashboardGadgetsView>> gadgets(@RequestParam MultiValueMap<String, String> params, AdminActor actor) {
        Map<String, String> limits = new HashMap<>();
        Map<String, String> days = new HashMap<>();
        params.forEach((name, values) -> {
            if (values.isEmpty() || values.get(0) == null) {
                return;
            }
            if (name.startsWith(LIMIT_PREFIX)) {
                limits.put(name.substring(LIMIT_PREFIX.length()), values.get(0));
            } else if (name.startsWith(DAYS_PREFIX)) {
                days.put(name.substring(DAYS_PREFIX.length()), values.get(0));
            }
        });
        DashboardGadgetsRequest request = new DashboardGadgetsRequest(params.getFirst("types"), limits, days);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(gadgets.load(actor, request)));
    }
}
