package com.community.ad.controller;

import com.community.ad.dto.AdSlotAdminView;
import com.community.ad.dto.AdSlotTakedownRequest;
import com.community.ad.service.AdSlotAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
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
 * 광고 자리 관리(admin-web {@code useAdSlotApi} — {@code /ads/slots}, docs/features/ad-slot-manager-contract.md 「API」). 조회 {@code ad.read} · 여러 건 긴급 내림
 * {@code ad.takedown}(CSRF 는 공통 규칙). 글자 경로라 일정표의 {@code /{id}} 보다 먼저 잡힌다. 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/ads")
public class AdSlotAdminController {

    private final AdSlotAdminService service;

    public AdSlotAdminController(AdSlotAdminService service) {
        this.service = service;
    }

    /** 종류마다 요약 — 진행 중 · 한도(일정표 요약과 같은 셈) · 애드센스 FILLS|EMPTY|NA · 나가는 곳 */
    @RequirePermission(Permission.AD_READ)
    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<AdSlotAdminView.SummaryView>> summary() {
        return ok(service.summary());
    }

    /** 그 종류의 광고 카드 + 거르기마다 건수 — 모르는 종류 400 {@code fields.type}, 거르기(RUNNING·SCHEDULED·AWAITING_DEPOSIT·TAKEN_DOWN) 400 {@code fields.status} */
    @RequirePermission(Permission.AD_READ)
    @GetMapping("/slots/{type}")
    public ResponseEntity<ApiResponse<AdSlotAdminView.ListView>> list(@PathVariable String type, @RequestParam(required = false) String status) {
        return ok(service.list(type, status));
    }

    /** 여러 건 긴급 내림 — 건마다 단건 긴급 내림과 같은 규칙·감사·메일, 한 건이 실패해도 나머지는 내린다. 400 {@code fields.ids}·{@code fields.reason} */
    @RequirePermission(Permission.AD_TAKEDOWN)
    @PostMapping("/takedown")
    public ResponseEntity<ApiResponse<AdSlotAdminView.TakedownResult>> takedown(@RequestBody(required = false) AdSlotTakedownRequest body, AdminActor actor) {
        return ok(service.takedown(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
