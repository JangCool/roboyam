package com.community.emoticon.controller;

import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonSettlementConfirmRequest;
import com.community.emoticon.dto.request.EmoticonSettlementPaidRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.service.EmoticonSettlementService;
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
 * 관리자 로보티콘 작가 정산(emoticon-contract S4 — admin-web {@code useEmoticonAdminApi} 의 {@code settlements}·{@code settlement}·{@code confirmSettlements}·{@code holdSettlement}·
 * {@code unholdSettlement}·{@code markSettlementPaid}). 권한 {@code emoticon.settlement}(계좌 원문 보기만 {@code emoticon.payout.pii}), 작가·금액이 실려 {@code no-store}.
 * <ul>
 *   <li>{@code GET /api/admin/emoticons/settlements?month=YYYY-MM} → {@code { month, items, totals, months, rules }}(달이 없거나 고를 수 있는 달 밖이면 지급을 앞둔 달)</li>
 *   <li>{@code GET …/settlements/{id}} → 줄 + {@code packs} + {@code events} · {@code 404 EMOTICON_SETTLEMENT_NOT_FOUND}</li>
 *   <li>{@code POST …/settlements/confirm { ids }} → {@code { confirmed, skipped, skippedItems }} · ids 없음·200개 넘음 {@code 400 fields.ids}</li>
 *   <li>{@code POST …/{id}/hold { reason }} · {@code …/unhold} · {@code …/paid { transferDate, memo }} → 상세</li>
 *   <li>{@code POST …/{id}/account/reveal { reason }}(emoticon.payout.pii) → {@code { type, bank, number, holder, businessNumber, expiresAt }}(S2 와 같은 모양) — 처리 기록 ACCOUNT_VIEWED</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/emoticons/settlements")
public class EmoticonSettlementAdminController {

    private final EmoticonSettlementService settlements;

    public EmoticonSettlementAdminController(EmoticonSettlementService settlements) {
        this.settlements = settlements;
    }

    @RequirePermission(Permission.EMOTICON_SETTLEMENT)
    @GetMapping
    public ResponseEntity<ApiResponse<EmoticonSettlementView.AdminList>> list(@RequestParam(required = false) String month) {
        return noStore(settlements.list(month));
    }

    @RequirePermission(Permission.EMOTICON_SETTLEMENT)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmoticonSettlementView.AdminDetail>> detail(@PathVariable String id) {
        return noStore(settlements.detail(id));
    }

    @RequirePermission(Permission.EMOTICON_SETTLEMENT)
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<EmoticonSettlementView.BulkResult>> confirm(@RequestBody(required = false) EmoticonSettlementConfirmRequest body, AdminActor actor) {
        return noStore(settlements.confirm(actor.operatorId(), actor.ip(), body));
    }

    @RequirePermission(Permission.EMOTICON_SETTLEMENT)
    @PostMapping("/{id}/hold")
    public ResponseEntity<ApiResponse<EmoticonSettlementView.AdminDetail>> hold(@PathVariable String id, @RequestBody(required = false) EmoticonReviewDecisionRequest body,
                                                                               AdminActor actor) {
        return noStore(settlements.hold(actor.operatorId(), actor.ip(), id, body == null ? null : body.reason()));
    }

    @RequirePermission(Permission.EMOTICON_SETTLEMENT)
    @PostMapping("/{id}/unhold")
    public ResponseEntity<ApiResponse<EmoticonSettlementView.AdminDetail>> unhold(@PathVariable String id, AdminActor actor) {
        return noStore(settlements.unhold(actor.operatorId(), actor.ip(), id));
    }

    @RequirePermission(Permission.EMOTICON_SETTLEMENT)
    @PostMapping("/{id}/paid")
    public ResponseEntity<ApiResponse<EmoticonSettlementView.AdminDetail>> paid(@PathVariable String id, @RequestBody(required = false) EmoticonSettlementPaidRequest body,
                                                                               AdminActor actor) {
        return noStore(settlements.markPaid(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(Permission.EMOTICON_PAYOUT_PII)
    @PostMapping("/{id}/account/reveal")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PayoutAccountReveal>> reveal(@PathVariable String id,
                                                                                    @RequestBody(required = false) EmoticonReviewDecisionRequest body, AdminActor actor) {
        return noStore(settlements.revealAccount(actor.operatorId(), actor.ip(), id, body == null ? null : body.reason()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
