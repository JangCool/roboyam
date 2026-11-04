package com.community.analytics.controller;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.service.VisitAnalyticsAdminService;
import com.community.analytics.service.VisitDiagnosticsAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「방문자 통계」(admin-web {@code useVisitAnalyticsApi}, docs/features/site-analytics-contract.md 「「방문자 통계」 화면」) — 모두 {@code site.read}, 응답 {@code Cache-Control: no-store}
 * (결과 캐시는 서버 메모리 — {@link VisitAnalyticsAdminService}). 기간은 한국 날짜 {@code from}·{@code to}(양끝 포함, 최대 90일 — 틀리면 400 {@code fields.from}, 오늘 뒤면 {@code fields.to}).
 * 질의 값은 글자로 받아 서비스가 검사한다(형식 오류도 400 {@code fields.<칸>} — 스프링 변환 오류가 아니라).
 */
@RestController
@RequestMapping("/api/admin/analytics/visits")
public class VisitAnalyticsAdminController {

    private final VisitAnalyticsAdminService service;
    private final VisitDiagnosticsAdminService diagnostics;

    public VisitAnalyticsAdminController(VisitAnalyticsAdminService service, VisitDiagnosticsAdminService diagnostics) {
        this.service = service;
        this.diagnostics = diagnostics;
    }

    /** 합계·직전 기간·추이·수집 스위치·지금 접속 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Summary>> summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ok(service.summary(from, to));
    }

    /** 페이지 순위 한 쪽 — kind(화면 종류)·q(경로 일부 또는 게시판 이름 일부)·page(0부터)·size(기본 20, 최대 100) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/pages")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.PagePage>> pages(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                          @RequestParam(required = false) String kind, @RequestParam(required = false) String q,
                                                                          @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return ok(service.pages(from, to, kind, q, page, size));
    }

    /** 페이지 서랍 — 일별 조회·들어온 곳/나간 곳 5(path 필수, 틀리면 400 {@code fields.path}) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/pages/detail")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.PageDetail>> pageDetail(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                                 @RequestParam(required = false) String path) {
        return ok(service.pageDetail(from, to, path));
    }

    /** 유입 경로 — detail(기본 true) false 면 분류·사이트만(일별 추이·캠페인은 빈 배열) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/referrers")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Referrers>> referrers(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                               @RequestParam(required = false) String detail) {
        return ok(service.referrers(from, to, detail));
    }

    /** 이동 흐름 — path 가 없으면 기간에 들어온 이동이 가장 많은 페이지 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/flows")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Flows>> flows(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                       @RequestParam(required = false) String path) {
        return ok(service.flows(from, to, path));
    }

    /** 요일 × 시 168칸(합 + 더한 칸 수) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/time")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Time>> time(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ok(service.time(from, to));
    }

    /** 실시간 — 지금 접속 · 최근 30분 분당 페이지뷰 · 최근 5분 페이지·유입(원본 기록이 꺼져 있으면 지금 접속만) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/realtime")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Realtime>> realtime() {
        return ok(service.realtime());
    }

    // ── 2단계(site-analytics-contract 「2단계」) ──────────────────────────────

    /** 환경 — 방문(세션 첫 페이지) 기준 기기·브라우저·OS·화면 너비 + 일별 기기 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/environment")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Environment>> environment(@RequestParam(required = false) String from,
                                                                                   @RequestParam(required = false) String to) {
        return ok(service.environment(from, to));
    }

    /** 우리 사이트 검색어 — 합 · 상위 20 · 결과 없는 검색어 상위 20 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Search>> search(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ok(service.search(from, to));
    }

    /** 봇·크롤러 — 오늘 · 기간 합(사람 페이지뷰 함께) · 종류별 · 일별 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/bots")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Bots>> bots(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ok(service.bots(from, to));
    }

    /** 수집 진단 — 기간 없음(최근 24시간), 상태·문제는 서버 판정({@link VisitDiagnosticsAdminService}) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.Health>> health() {
        return ok(diagnostics.health());
    }

    /** 정보주체 요청 — 최신 차례 한 쪽(page 0부터 · size 기본 20, 최대 100) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<VisitAnalyticsAdmin.ForgetPage>> requests(@RequestParam(required = false) String page,
                                                                               @RequestParam(required = false) String size) {
        return ok(diagnostics.requests(page, size));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
