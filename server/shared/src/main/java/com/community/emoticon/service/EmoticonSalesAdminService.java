package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonPurchaseView;
import com.community.emoticon.entity.EmoticonPurchaseStatus;
import com.community.emoticon.entity.EmoticonRefundKind;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonPurchaseNotFoundException;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.ListedRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.PurchaseRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.SalesQuery;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로보티콘 판매 내역 · 예외 환불(emoticon-contract S3 — 권한 {@code emoticon.sales}, admin-web {@code /robotikon/sales}).
 *
 * <ul>
 *   <li><b>목록</b>({@link #list}): 결제 완료 계열(PAID·REFUNDED)만, 최근 결제 먼저. 거르기 {@code from}(이상)·{@code to}(미만 — UTC ISO 순간, 틀리면 {@code 400 fields.from|to})·{@code packId}·
 *       {@code status}(PAID·REFUNDED — 모르는 값은 전체), 쪽 {@code page}(0~)·{@code size}(1~100, 기본 20). {@code paidAmount} = 거른 결과 전체의 보유 중(환불 뺀) 금액 합.
 *       팩 이름은 산 때의 사본, 대표 그림은 지금 상점 판.</li>
 *   <li><b>예외 환불</b>({@link #refund}): 사유 5~200자({@code 400 fields.reason}) → 없는 판매 404 {@code EMOTICON_SALE_NOT_FOUND} → 이미 환불 409 {@code EMOTICON_SALE_ALREADY_REFUNDED}
 *       → 환불 한 길({@code EmoticonPurchaseService.refund} — 쓴 구매·기한 지난 구매도) + 감사 {@code EMOTICON_SALE_REFUND}(돈 관련, 같은 트랜잭션).</li>
 * </ul>
 */
@Service
public class EmoticonSalesAdminService {

    public static final String TARGET = "EMOTICON_PURCHASE";
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final EmoticonPurchaseStore purchases;
    private final EmoticonPackStore packs;
    private final EmoticonReadStore reads;
    private final EmoticonPurchaseService purchaseService;
    private final AuditLogService audit;
    private final Clock clock;

    public EmoticonSalesAdminService(EmoticonPurchaseStore purchases, EmoticonPackStore packs, EmoticonReadStore reads, EmoticonPurchaseService purchaseService,
                                     AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.purchases = purchases;
        this.packs = packs;
        this.reads = reads;
        this.purchaseService = purchaseService;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public EmoticonPurchaseView.SalePage list(String fromText, String toText, String packIdText, String statusText, Integer page, Integer size) {
        Instant from = instant("from", fromText);
        Instant to = instant("to", toText);
        Long packId = null;
        if (packIdText != null && !packIdText.isBlank()) {
            long parsed = EmoticonShopService.parse(packIdText.trim());
            if (parsed < 0) {
                return new EmoticonPurchaseView.SalePage(List.of(), 0, 0);
            }
            packId = parsed;
        }
        EmoticonPurchaseStatus status = "PAID".equals(statusText) ? EmoticonPurchaseStatus.PAID : "REFUNDED".equals(statusText) ? EmoticonPurchaseStatus.REFUNDED : null;
        int limit = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = page == null || page < 0 ? 0 : page * limit;
        SalesQuery q = new SalesQuery(from, to, packId, status, offset, limit);
        long[] totals = purchases.salesTotals(q);
        return new EmoticonPurchaseView.SalePage(rows(purchases.sales(q)), totals[0], totals[1]);
    }

    @Transactional
    public EmoticonPurchaseView.SaleRefundResult refund(long operatorId, String ip, String idText, String reasonText) {
        String reason = EmoticonPackAdminService.reason(reasonText);
        long id = EmoticonShopService.parse(idText);
        PurchaseRow p = id < 0 ? null : purchases.lock(id).filter(x -> x.paidAt() != null).orElse(null);
        if (p == null) {
            throw new EmoticonPurchaseNotFoundException(EmoticonPurchaseNotFoundException.SALE);
        }
        if (p.status() != EmoticonPurchaseStatus.PAID) {
            throw new EmoticonConflictException(EmoticonConflictException.SALE_ALREADY_REFUNDED);
        }
        purchaseService.refund(p, EmoticonRefundKind.EXCEPTION, reason, operatorId);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_SALE_REFUND, AuditResult.SUCCESS).target(TARGET, Long.toString(p.id()))
                .with("packId", Long.toString(p.packId())).with("buyer", Long.toString(p.userId())).with("amount", p.amount()).with("reason", reason)
                .with("used", p.usedAt() != null).with("withinSelfRefund", EmoticonPurchaseService.blockedBy(p, clock.instant()) == null));
        return new EmoticonPurchaseView.SaleRefundResult(rows(List.of(purchases.listed(p.id()).orElseThrow())).get(0));
    }

    private List<EmoticonPurchaseView.SaleRow> rows(List<ListedRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, PackRow> packById = packs.byIds(rows.stream().map(r -> r.purchase().packId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PackRow::id, x -> x));
        Map<Long, String> covers = purchaseService.covers(packById);
        Set<Long> people = new HashSet<>();
        rows.forEach(r -> {
            people.add(r.purchase().userId());
            if (r.purchase().creatorId() != null) {
                people.add(r.purchase().creatorId());
            }
        });
        Map<Long, MemberRef> members = reads.members(people);
        Instant now = clock.instant();
        return rows.stream().map(r -> {
            PurchaseRow p = r.purchase();
            MemberRef buyer = members.get(p.userId());
            MemberRef creator = p.creatorId() == null ? null : members.get(p.creatorId());
            EmoticonPurchaseView.SelfRefund self = null;
            if (p.status() == EmoticonPurchaseStatus.PAID) {
                String blocked = EmoticonPurchaseService.blockedBy(p, now);
                self = new EmoticonPurchaseView.SelfRefund(blocked == null, p.refundUntil(), blocked);
            }
            EmoticonPurchaseView.Refund refund = p.refundedAt() == null ? null
                    : new EmoticonPurchaseView.Refund(p.refundKind().name(), p.refundedAt(), r.refundedByName(), p.refundReason());
            return new EmoticonPurchaseView.SaleRow(Long.toString(p.id()), p.paidAt(), Long.toString(p.packId()), p.packName(), covers.get(p.packId()),
                    p.creatorId() == null ? null : new EmoticonAdminView.CreatorRef(Long.toString(p.creatorId()), creator == null ? null : creator.nickname(),
                            creator == null || creator.withdrawn()),
                    new EmoticonPurchaseView.Buyer(Long.toString(p.userId()), buyer == null ? null : buyer.nickname(), buyer == null || buyer.withdrawn()), p.amount(),
                    r.paymentId(), p.usedAt(), p.status().name(), self, refund,
                    // 이 판매가 들어간 정산 달(확정된 줄 — S4). 확정 전이면 null(지금 환불하면 그 달 정산에서 빠진다), 확정 뒤 환불은 환불한 달에서 뺀다
                    r.settlementMonth() == null ? null : String.format("%04d-%02d", r.settlementMonth().getYear(), r.settlementMonth().getMonthValue()));
        }).toList();
    }

    private static Instant instant(String field, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw ValidationException.of(field, new MessageRef("emoticon.sales.instant"));
        }
    }
}
