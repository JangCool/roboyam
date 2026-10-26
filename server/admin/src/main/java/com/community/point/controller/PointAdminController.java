package com.community.point.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.point.dto.request.PointAdjustRequest;
import com.community.point.dto.request.PointSettingsRequest;
import com.community.point.dto.response.PointAdjustResultView;
import com.community.point.dto.response.PointLedgerPageView;
import com.community.point.dto.response.PointMemberCandidateView;
import com.community.point.dto.response.PointMemberLotsView;
import com.community.point.dto.response.PointSettingsResponse;
import com.community.point.service.PointAdjustService;
import com.community.point.service.PointLedgerQueryService;
import com.community.point.service.PointMemberLotsService;
import com.community.point.service.PointSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 포인트 API — admin-web {@code usePointApi.ts} 머리말 그대로(docs/features/point-contract.md, commerce-stage1-contract 「1-1」). 권한: 원장·정책·회원 묶음 조회 {@code point.read},
 * 정책 저장 {@code point.settings}, 조정 대상 후보·수동 지급/차감 {@code point.adjust}(문턱을 넘는 조정은 서비스가 {@code point.adjust.large} 도 본다). 이메일은 {@code member.pii} 가 없으면 가린다. 회원 정보가 담긴 응답이라 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin")
public class PointAdminController {

    private final PointLedgerQueryService ledger;
    private final PointAdjustService adjusts;
    private final PointSettingsService settings;
    private final PointMemberLotsService memberLots;

    public PointAdminController(PointLedgerQueryService ledger, PointAdjustService adjusts, PointSettingsService settings, PointMemberLotsService memberLots) {
        this.ledger = ledger;
        this.adjusts = adjusts;
        this.settings = settings;
        this.memberLots = memberLots;
    }

    /** 전체 원장 한 쪽 + 같은 조건의 합계 — 시각은 순간(UTC ISO), {@code reasons} 는 반복(또는 쉼표), {@code q} 는 닉네임·이메일·회원번호 */
    @RequirePermission(Permission.POINT_READ)
    @GetMapping("/points/ledger")
    public ResponseEntity<ApiResponse<PointLedgerPageView>> ledger(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                   @RequestParam(required = false) String kind, @RequestParam(required = false) List<String> reasons,
                                                                   @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
                                                                   @RequestParam(required = false) Integer size, AdminActor actor) {
        return noStore(ledger.ledger(actor, from, to, kind, reasons, q, page, size));
    }

    /** 조정 대상 회원 찾기(최대 20명, 지금 잔액 포함) */
    @RequirePermission(Permission.POINT_ADJUST)
    @GetMapping("/points/member-candidates")
    public ResponseEntity<ApiResponse<List<PointMemberCandidateView>>> candidates(@RequestParam(required = false) String q, AdminActor actor) {
        return noStore(ledger.candidates(actor, q));
    }

    /** 수동 지급·차감 — 실제 반영값과 잔액 */
    @RequirePermission(Permission.POINT_ADJUST)
    @PostMapping("/members/{id}/points")
    public ResponseEntity<ApiResponse<PointAdjustResultView>> adjust(@PathVariable String id, @RequestBody(required = false) PointAdjustRequest body, AdminActor actor) {
        return noStore(adjusts.adjust(actor, id, body));
    }

    /**
     * 회원 상세의 포인트 묶음 — 잔액 나눔·소멸 예정·미회수·묶음 한 쪽({@code active} true = 남은 것만·차감 순서, false = 전체·최근 받은 순, {@code page} 0부터, {@code size} 1~50 기본 10)·
     * 최근 미회수 기록 5건
     */
    @RequirePermission(Permission.POINT_READ)
    @GetMapping("/members/{id}/point-lots")
    public ResponseEntity<ApiResponse<PointMemberLotsView>> memberLots(@PathVariable String id, @RequestParam(required = false) String active,
                                                                       @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(memberLots.lots(id, active, page, size));
    }

    @RequirePermission(Permission.POINT_READ)
    @GetMapping("/settings/points")
    public ResponseEntity<ApiResponse<PointSettingsResponse>> loadSettings() {
        return noStore(settings.load());
    }

    /** 정책 14칸을 통째로 저장 — 저장된 값을 돌려준다 */
    @RequirePermission(Permission.POINT_SETTINGS)
    @PutMapping("/settings/points")
    public ResponseEntity<ApiResponse<PointSettingsResponse>> saveSettings(@RequestBody(required = false) PointSettingsRequest body, AdminActor actor) {
        return noStore(settings.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
