package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.FulfillmentStore.CarrierRow;
import com.community.groupbuy.repository.master.FulfillmentStore.ShipmentRow;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 발주·준비중·발송·배송완료·송장 정정(commerce.md 10-3, 계약 결정 7·9·11·47·49·53·64) — 관리자 {@code commerce.order.manage}(컨트롤러는 2b-4).
 *
 * <ul>
 *   <li><b>발주</b>는 공구 단위(성사된 마감 후 발주 공구 CLOSED_SUCCESS → ORDERED). 준비중·발송·배송완료는 주문 단위이고 <b>일괄은 줄마다 따로 트랜잭션</b> — 행 단위 결과(부분 성공).</li>
 *   <li>발송은 주문 한 번에(결정 47): CONFIRMED·PREPARING, 마감 후 발주 공구는 발주 뒤, 열린 요청이 있거나 공급 불가 답을 기다리는 주문은 막는다(10-3·결정 53). 발송 때 처음 배송비를 고정한다(결정 43).
 *       첫 발송이 공구를 ORDERED → SHIPPING 으로. 한 번에 200줄까지.</li>
 *   <li>송장은 숫자·영문만 남겨 6~30자(결정 11). 택배사는 켜진 것만.</li>
 *   <li>에스크로 배송 정보 등록·수정은 커밋 뒤 대기열(결정 64 — {@link GroupBuyEscrowWorker}). 배송완료는 운영자가(자동 배송완료 없음 — 결정 7).</li>
 * </ul>
 */
@Service
public class FulfillmentService {

    /** 한 번에 발송 처리할 수 있는 줄(화면 안 표 — 계약 화면 목록) */
    public static final int SHIP_BATCH_MAX = 200;

    /** 일괄 처리 한 줄 결과 — 실패면 오류 코드 */
    public record RowResult(long participationId, boolean ok, String code) {
    }

    public record BatchResult(int succeeded, int failed, List<RowResult> results) {

        static BatchResult of(List<RowResult> results) {
            int ok = (int) results.stream().filter(RowResult::ok).count();
            return new BatchResult(ok, results.size() - ok, List.copyOf(results));
        }
    }

    /** 발송 한 줄 — 주문·택배사·송장 */
    public record ShipLine(long participationId, String carrierCode, String trackingNo) {
    }

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final FulfillmentStore store;
    private final GroupBuyRequestStore requests;
    private final GroupBuyEscrowWorker escrow;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public FulfillmentService(ParticipationStore participations, GroupBuyStore groups, FulfillmentStore store, GroupBuyRequestStore requests, GroupBuyEscrowWorker escrow,
                              GroupBuyAudit audit, SnowflakeIdGenerator ids, ApplicationEventPublisher events, PlatformTransactionManager transactions,
                              ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.store = store;
        this.requests = requests;
        this.escrow = escrow;
        this.audit = audit;
        this.ids = ids;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 택배사(결정 11) ─────────────────────────────────────────────────────

    public List<CarrierRow> carriers() {
        return store.carriers();
    }

    /**
     * 택배사 목록을 통째로 저장 — 코드는 영문 대문자로 시작 2~20자, 이름 1~40자, 조회 주소는 {@code https://} 이고 {@code {no}} 한 번. 빠진 코드는 지우되 발송·회수 이력이 있으면 409(끄기만).
     */
    public List<CarrierRow> saveCarriers(List<CarrierRow> list, Long operatorId, String ip) {
        Set<String> codes = new HashSet<>();
        for (CarrierRow c : list) {
            boolean urlOk = c.trackingUrl() != null && c.trackingUrl().startsWith("https://") && c.trackingUrl().split("\\{no}", -1).length == 2
                    && c.trackingUrl().length() <= 300;
            if (c.code() == null || !c.code().matches("[A-Z][A-Z0-9_]{1,19}") || c.name() == null || c.name().isBlank() || c.name().strip().length() > 40 || !urlOk
                    || !codes.add(c.code())) {
                throw ValidationException.of("carriers", new MessageRef("commerce.carrier.invalid"));
            }
        }
        tx.executeWithoutResult(status -> {
            Instant now = now();
            List<String> inUse = store.carriers().stream().map(CarrierRow::code).filter(code -> !codes.contains(code)).filter(store::carrierInUse).toList();
            if (!inUse.isEmpty()) {
                throw GroupBuyException.carrierInUse(inUse);
            }
            store.carriers().stream().map(CarrierRow::code).filter(code -> !codes.contains(code)).forEach(store::deleteCarrier);
            for (CarrierRow c : list) {
                store.upsertCarrier(new CarrierRow(c.code(), c.name().strip(), c.trackingUrl(), c.enabled(), c.sortOrder()), operatorId, now);
            }
            audit.record(operatorId, AuditActions.COMMERCE_CARRIER_SAVE, GroupBuyAudit.TARGET_CARRIER, "ALL", ip, Map.of("codes", List.copyOf(codes)));
        });
        return store.carriers();
    }

    /** 송장 번호 정리 — 숫자·영문만 남겨 6~30자(결정 11), 아니면 400 {@code GROUP_BUY_TRACKING_INVALID} */
    public static String normalizeTracking(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.length() < 6 || cleaned.length() > 30) {
            throw GroupBuyException.trackingInvalid();
        }
        return cleaned;
    }

    String requireCarrier(String code) {
        return store.carriers().stream().filter(c -> c.code().equals(code) && c.enabled()).map(CarrierRow::code).findFirst()
                .orElseThrow(GroupBuyException::trackingInvalid);
    }

    // ── 발주(공구 단위) ─────────────────────────────────────────────────────

    /** 발주 — 성사된 마감 후 발주 공구만(CLOSED_SUCCESS → ORDERED). 아니면 409 {@code GROUP_BUY_NOT_ORDERABLE} */
    public void markOrdered(long groupBuyId, Long operatorId, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = now();
            if (!groups.markOrdered(groupBuyId, now)) {
                throw GroupBuyException.notOrderable();
            }
            groups.insertStatusHistory(ids.nextId(), groupBuyId, GroupBuyStatus.CLOSED_SUCCESS, GroupBuyStatus.ORDERED, "ORDERED", operatorId, now);
            audit.record(operatorId, AuditActions.GROUPBUY_FULFILLMENT, GroupBuyAudit.TARGET_GROUP_BUY, groupBuyId, ip, Map.of("step", "ORDERED"));
            events.publishEvent(new GroupBuyEvents.StatusChanged(groupBuyId, GroupBuyStatus.CLOSED_SUCCESS.name(), GroupBuyStatus.ORDERED.name(), "ORDERED"));
        });
    }

    // ── 주문 단위 일괄 ──────────────────────────────────────────────────────

    /** 준비중(CONFIRMED → PREPARING) 일괄 — 마감 후 발주 공구는 발주 뒤 */
    public BatchResult prepare(List<Long> participationIds, Long operatorId, String ip) {
        BatchResult result = batch(requireBatch(participationIds), id -> {
            ParticipationRow p = participations.lock(id).orElseThrow(GroupBuyException::orderNotFound);
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
            if (!g.immediate() && g.status() != GroupBuyStatus.ORDERED && g.status() != GroupBuyStatus.SHIPPING) {
                throw GroupBuyException.orderNotPreparable();
            }
            if (!participations.prepare(id, now())) {
                throw GroupBuyException.orderNotPreparable();
            }
            participations.insertHistory(ids.nextId(), p, p.status(), ParticipationStatus.PREPARING, "PREPARE", operatorId, now());
            return null;
        });
        auditBatch("PREPARE", result, operatorId, ip);
        return result;
    }

    /** 발송 일괄(≤ 200줄) — 줄마다 따로 트랜잭션, 끝나면 에스크로 배송 정보 등록을 커밋 밖에서 한 번 시도(실패는 대기열이 이어 받는다) */
    public BatchResult ship(List<ShipLine> lines, Long operatorId, String ip) {
        if (lines == null || lines.isEmpty() || lines.size() > SHIP_BATCH_MAX) {
            throw ValidationException.of("rows", new MessageRef("groupbuy.fulfillment.batch.invalid"));
        }
        List<Long> shipments = new ArrayList<>();
        List<RowResult> results = new ArrayList<>();
        for (ShipLine line : lines) {
            try {
                Long shipmentId = tx.execute(status -> shipOne(line, operatorId));
                shipments.add(shipmentId);
                results.add(new RowResult(line.participationId(), true, null));
            } catch (CommonException e) {
                results.add(new RowResult(line.participationId(), false, e.getErrorCode()));
            }
        }
        escrow.runFor(shipments);
        BatchResult result = BatchResult.of(results);
        auditBatch("SHIP", result, operatorId, ip);
        return result;
    }

    /** 한 줄 발송 — 부르는 쪽 트랜잭션 안(일괄은 줄마다 새 트랜잭션, 취소 요청 「이미 발송함」 거절은 그 트랜잭션) */
    long shipOne(ShipLine line, Long operatorId) {
        Instant now = now();
        String carrier = requireCarrier(line.carrierCode());
        String tracking = normalizeTracking(line.trackingNo());
        ParticipationRow p = participations.lock(line.participationId()).orElseThrow(GroupBuyException::orderNotFound);
        GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
        boolean groupReady = g.immediate() ? g.status() != GroupBuyStatus.CANCELED && g.status() != GroupBuyStatus.DRAFT
                : g.status() == GroupBuyStatus.ORDERED || g.status() == GroupBuyStatus.SHIPPING;
        if (!groupReady || !EnumSet.of(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING).contains(p.status()) || requests.hasOpen(p.id())
                || requests.choicePending(p.id())) {
            throw GroupBuyException.orderNotShippable();
        }
        if (!participations.ship(p.id(), now)) {
            throw GroupBuyException.orderNotShippable();
        }
        participations.insertHistory(ids.nextId(), p, p.status(), ParticipationStatus.SHIPPED, "SHIP", operatorId, now);
        long shipmentId = ids.nextId();
        store.insertShipment(shipmentId, p.id(), g.id(), "ORIGINAL", null, carrier, tracking, operatorId, now);
        store.enqueueEscrow(ids.nextId(), shipmentId, "LOGISTICS", now);
        if (groups.markShipping(g.id(), now)) {
            groups.insertStatusHistory(ids.nextId(), g.id(), GroupBuyStatus.ORDERED, GroupBuyStatus.SHIPPING, "FIRST_SHIPMENT", operatorId, now);
        }
        return shipmentId;
    }

    /** 교환 재발송(결정 54) — 요청 서비스가 부른다(그 트랜잭션 안). 발송 줄 ID */
    long shipExchange(ParticipationRow p, long requestId, String carrierCode, String trackingNo, Long operatorId, Instant now) {
        String carrier = requireCarrier(carrierCode);
        String tracking = normalizeTracking(trackingNo);
        long shipmentId = ids.nextId();
        store.insertShipment(shipmentId, p.id(), p.groupBuyId(), "EXCHANGE", requestId, carrier, tracking, operatorId, now);
        store.enqueueEscrow(ids.nextId(), shipmentId, "LOGISTICS", now);
        return shipmentId;
    }

    /** 배송완료(SHIPPED → DELIVERED) 일괄 — 청약철회 7일이 여기서 시작한다 */
    public BatchResult deliver(List<Long> participationIds, Long operatorId, String ip) {
        BatchResult result = batch(requireBatch(participationIds), id -> {
            Instant now = now();
            ParticipationRow p = participations.lock(id).orElseThrow(GroupBuyException::orderNotFound);
            if (!participations.deliver(id, now)) {
                throw GroupBuyException.orderState();
            }
            store.markDelivered(id, now);
            participations.insertHistory(ids.nextId(), p, ParticipationStatus.SHIPPED, ParticipationStatus.DELIVERED, "DELIVER", operatorId, now);
            return null;
        });
        auditBatch("DELIVER", result, operatorId, ip);
        return result;
    }

    /** 송장 정정(SHIPPED·DELIVERED) — 에스크로 배송 정보를 다시 보낸다(등록됐으면 수정 — 결정 14) */
    public void correctTracking(long participationId, String carrierCode, String trackingNo, Long operatorId, String ip) {
        String carrier = requireCarrier(carrierCode);
        String tracking = normalizeTracking(trackingNo);
        Long shipmentId = tx.execute(status -> {
            Instant now = now();
            ParticipationRow p = participations.lock(participationId).orElseThrow(GroupBuyException::orderNotFound);
            ShipmentRow s = store.original(participationId).orElseThrow(GroupBuyException::orderState);
            if (p.status() != ParticipationStatus.SHIPPED && p.status() != ParticipationStatus.DELIVERED) {
                throw GroupBuyException.orderState();
            }
            store.correctTracking(s.id(), carrier, tracking, now);
            store.enqueueEscrow(ids.nextId(), s.id(), "LOGISTICS", now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("step", "TRACKING_CORRECT");
            detail.put("carrier", carrier);
            audit.record(operatorId, AuditActions.GROUPBUY_FULFILLMENT, GroupBuyAudit.TARGET_ORDER, participationId, ip, detail);
            return s.id();
        });
        escrow.runFor(List.of(shipmentId));
    }

    private static List<Long> requireBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > SHIP_BATCH_MAX) {
            throw ValidationException.of("ids", new MessageRef("groupbuy.fulfillment.batch.invalid"));
        }
        return ids;
    }

    private BatchResult batch(List<Long> participationIds, Function<Long, Void> one) {
        List<RowResult> results = new ArrayList<>();
        for (long id : participationIds) {
            try {
                tx.execute(status -> one.apply(id));
                results.add(new RowResult(id, true, null));
            } catch (CommonException e) {
                results.add(new RowResult(id, false, e.getErrorCode()));
            }
        }
        return BatchResult.of(results);
    }

    private void auditBatch(String step, BatchResult result, Long operatorId, String ip) {
        if (result.succeeded() > 0) {
            audit.record(operatorId, AuditActions.GROUPBUY_FULFILLMENT, GroupBuyAudit.TARGET_ORDER, "BATCH", ip, Map.of("step", step, "succeeded", result.succeeded(),
                    "failed", result.failed()));
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
