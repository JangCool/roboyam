package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.StockChange;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QtyMovementKind;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyQtyStore;
import com.community.groupbuy.repository.master.GroupBuyQtyStore.After;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>수량을 바꾸는 유일한 길</b>(계약 「패키지」) — RESERVE·CONFIRM·RELEASE·CANCEL·ADD·REDUCE. 모두 부르는 쪽 트랜잭션 안에서({@code MANDATORY}).
 *
 * <ul>
 *   <li><b>공구 행 먼저</b>(MAIN 단품 수량 합으로 한 문장) → 단품 행(ID 오름차순, 한 줄씩) 차례로 조건부 UPDATE … RETURNING, 줄마다 이동 줄 하나(이후 값은 RETURNING 으로 — 결정 82).
 *       공구 행의 수량은 MAIN 단품만이다(추가 구성상품은 단품 행만 — 결정 88). 줄 차례와 관계없이 늘 공구 행을 먼저 잡아 교착을 막는다.</li>
 *   <li>공구·단품 행을 {@code FOR UPDATE} 로 잠그지 않는다 — 결제 확정({@code onPaid})이 이 길을 쓰므로(결정 37). 조건에 지면 빈 값·false 로 알린다.</li>
 *   <li>다 팔림 마감(결정 27·78)은 {@link #closeIfSoldOut} — 확정·줄이기와 같은 트랜잭션에서.</li>
 * </ul>
 */
@Service
public class GroupBuyQtyService {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyQtyService.class);

    private final GroupBuyQtyStore qty;
    private final GroupBuyStore groups;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;

    public GroupBuyQtyService(GroupBuyQtyStore qty, GroupBuyStore groups, SnowflakeIdGenerator ids, ApplicationEventPublisher events) {
        this.qty = qty;
        this.groups = groups;
        this.ids = ids;
        this.events = events;
    }

    /** 단품 ID 오름차순 — 단품 행 잠금 차례(결정 23·37) */
    static List<ItemRow> lockOrder(List<ItemRow> items) {
        return items.stream().sorted(Comparator.comparingLong(ItemRow::skuId)).toList();
    }

    private static int mainQty(List<ItemRow> items) {
        return items.stream().filter(ItemRow::main).mapToInt(ItemRow::qty).sum();
    }

    /**
     * 주문 줄마다 선점 — 공구(MAIN 합) → 단품 차례. 하나라도 모자라면 던진다(트랜잭션째 되돌려 주문 전체가 실패 — 결정 23).
     *
     * @throws GroupBuyException 모집 중이 아님 · 다 팔림/잠시 품절 · 단품 다 팔림/잠시 품절 · 판매 안 함
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(GroupBuyRow group, long participationId, List<ItemRow> items, Instant now) {
        After g = qty.groupReserve(group.id(), mainQty(items), now).orElseThrow(() -> groupShortage(group.id(), now));
        for (ItemRow item : lockOrder(items)) {
            After s = qty.skuReserve(item.skuId(), group.id(), item.qty()).orElseThrow(() -> skuShortage(item.skuId(), group.id()));
            movement(group.id(), item, participationId, QtyMovementKind.RESERVE, item.qty(), item.main() ? g : null, s, null, null, now);
        }
    }

    /**
     * 결제 확정 — 공구(MAIN 합) → 단품 차례로 선점을 확정 수량으로. 공구가 결제를 받는 상태가 아니거나 선점이 모자라면(불변식이 이미 깨짐) false — 부르는 쪽이 세이브포인트로 되돌리고 거절한다.
     * 추가 구성상품만의 주문은 없지만(단독 구매 불가), 있더라도 공구 상태는 늘 확인한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirm(long groupBuyId, long participationId, List<ItemRow> items, Instant now) {
        Optional<After> g = qty.groupConfirm(groupBuyId, mainQty(items));
        if (g.isEmpty()) {
            return false;
        }
        for (ItemRow item : lockOrder(items)) {
            Optional<After> s = qty.skuConfirm(item.skuId(), item.qty());
            if (s.isEmpty()) {
                return false;
            }
            movement(groupBuyId, item, participationId, QtyMovementKind.CONFIRM, item.qty(), item.main() ? g.get() : null, s.get(), null, null, now);
        }
        return true;
    }

    /** 선점 해제 — 공구(MAIN 합) → 단품. 선점이 모자라 해제하지 못한 줄이 있으면 false(로그 — 불변식 검사가 잡는다) */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(long groupBuyId, long participationId, List<ItemRow> items, Instant now) {
        int main = mainQty(items);
        Optional<After> g = main == 0 ? Optional.empty() : qty.groupRelease(groupBuyId, main);
        boolean all = main == 0 || g.isPresent();
        for (ItemRow item : lockOrder(items)) {
            Optional<After> s = qty.skuRelease(item.skuId(), item.qty());
            if (s.isEmpty()) {
                all = false;
            }
            After groupAfter = item.main() ? g.orElse(null) : null;
            if (groupAfter != null || s.isPresent()) {
                movement(groupBuyId, item, participationId, QtyMovementKind.RELEASE, item.qty(), groupAfter, s.orElse(null), null, null, now);
            }
            events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, item.skuId(), QtyMovementKind.RELEASE));
        }
        if (!all) {
            log.error("선점 해제할 수량이 모자란다 — 불변식 확인 필요 groupBuyId={} participationId={}", groupBuyId, participationId);
        }
        return all;
    }

    /**
     * 결제된 줄들의 수량 일부를 취소(CANCEL — 판매 수량 감소) — 공구(MAIN 합) → 단품. {@code requireAcceptingPayment} 면 공구가 모집 중·집계 중일 때만
     * (성사형 본인 즉시 취소 — 판정과 하나만 이긴다, 결정 45 — 추가 구성상품 줄만 취소해도 공구 상태를 확인한다).
     *
     * @param counts 주문 줄 → 취소 수량(0 줄은 없다)
     * @return 취소했으면 true(조건에 졌으면 false — 아무것도 바꾸지 않았다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancel(long groupBuyId, Map<ItemRow, Integer> counts, boolean requireAcceptingPayment, Long actorId, String reason, Instant now) {
        int main = counts.entrySet().stream().filter(e -> e.getKey().main()).mapToInt(Map.Entry::getValue).sum();
        Optional<After> g = Optional.empty();
        if (main > 0 || requireAcceptingPayment) {
            g = qty.groupCancel(groupBuyId, main, requireAcceptingPayment);
            if (g.isEmpty()) {
                return false;
            }
        }
        for (ItemRow item : lockOrder(List.copyOf(counts.keySet()))) {
            int count = counts.get(item);
            After s = qty.skuCancel(item.skuId(), count).orElseThrow(() -> new IllegalStateException("단품 확정 수량이 모자란다 — 불변식이 깨졌다 skuId=" + item.skuId()));
            movement(groupBuyId, item, item.participationId(), QtyMovementKind.CANCEL, count, item.main() ? g.orElse(null) : null, s, reason, actorId, now);
            events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, item.skuId(), QtyMovementKind.CANCEL));
        }
        return true;
    }

    /**
     * 추가 입고(결정 17·80) — 모집 중이고 다 팔리기 전에만. 단품 지정 없으면 공구 최대(MAIN). MAIN 단품을 지정하면 단품 최대와 공구 최대를 같은 수만큼(공구 행 → 단품 행),
     * 추가 구성상품은 단품 최대만(공구 행은 조건 확인·잠금만). 한도 없는 단품은 거절(공구 단위로).
     *
     * @throws GroupBuyException 모집 중이 아니거나 이미 다 팔렸다({@code NOT_RECRUITING}) · 한도 없는 단품({@code SKU_UNLIMITED}) · 단품 없음({@code NOT_FOUND})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockChange add(long groupBuyId, Long skuId, int count, String reason, Long actorId, Instant now) {
        SkuRow sku = skuId == null ? null : requireSku(groupBuyId, skuId);
        if (sku != null && sku.maxQty() == null) {
            throw GroupBuyException.skuUnlimited();
        }
        boolean groupLevel = sku == null || sku.main();
        After g = qty.groupAdd(groupBuyId, groupLevel ? count : 0).orElseThrow(GroupBuyException::notRecruiting);
        After s = null;
        if (sku != null) {
            s = qty.skuAdd(skuId, groupBuyId, count).orElseThrow(GroupBuyException::skuUnlimited);
        }
        qty.insertMovement(ids.nextId(), groupBuyId, skuId, null, null, QtyMovementKind.ADD, count, groupLevel ? g : null, s, reason, actorId, now);
        events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, skuId, QtyMovementKind.ADD));
        GroupBuyRow after = groups.find(groupBuyId).orElseThrow();
        return new StockChange(groupBuyId, after.maxQty(), after.addedQty(), s == null ? null : s.max(), false);
    }

    /**
     * 줄이기(결정 17·48·80) — 모집 중이고 하한(공구: 확정 + 선점·성사형 최소 수량 / 단품: 그 단품의 확정 + 선점) 아래로 가지 않을 때만. MAIN 단품은 공구 최대도 같이,
     * 추가 구성상품은 단품만. 줄인 결과 다 팔리면 같은 트랜잭션에서 CLOSING.
     *
     * @throws GroupBuyException 모집 중이 아님 · 하한 아래({@code QTY_BELOW_FLOOR} + 하한) · 한도 없는 단품
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockChange reduce(long groupBuyId, Long skuId, int count, String reason, Long actorId, Instant now) {
        SkuRow sku = skuId == null ? null : requireSku(groupBuyId, skuId);
        if (sku != null && sku.maxQty() == null) {
            throw GroupBuyException.skuUnlimited();
        }
        boolean groupLevel = sku == null || sku.main();
        Optional<After> reduced = qty.groupReduce(groupBuyId, groupLevel ? count : 0);
        if (reduced.isEmpty()) {
            GroupBuyRow now0 = groups.find(groupBuyId).orElseThrow(GroupBuyException::notFound);
            if (now0.status() != GroupBuyStatus.RECRUITING) {
                throw GroupBuyException.notRecruiting();
            }
            throw GroupBuyException.qtyBelowFloor(Math.max(1, Math.max(now0.currentQty() + now0.reservedQty(), now0.minQty())));
        }
        After s = null;
        if (sku != null) {
            Optional<After> skuReduced = qty.skuReduce(skuId, groupBuyId, count);
            if (skuReduced.isEmpty()) {
                // 공구 행은 이미 줄였다 — 던져서 트랜잭션째 되돌린다
                SkuRow fresh = requireSku(groupBuyId, skuId);
                throw GroupBuyException.qtyBelowFloor(Math.max(1, fresh.currentQty() + fresh.reservedQty()));
            }
            s = skuReduced.get();
        }
        qty.insertMovement(ids.nextId(), groupBuyId, skuId, null, null, QtyMovementKind.REDUCE, count, groupLevel ? reduced.get() : null, s, reason, actorId, now);
        boolean closed = closeIfSoldOut(groupBuyId, actorId, now);
        GroupBuyRow after = groups.find(groupBuyId).orElseThrow();
        return new StockChange(groupBuyId, after.maxQty(), after.addedQty(), s == null ? null : s.max(), closed);
    }

    /** 반품 기록(RETURN — 수량 불변, 폐기·공급처 반송·보관 — 결정 15·28). 처리 방법은 사유 칸에 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReturn(long groupBuyId, ItemRow item, int count, String disposition, Long actorId, Instant now) {
        After s = qty.skuTouch(item.skuId()).orElseThrow();
        movement(groupBuyId, item, item.participationId(), QtyMovementKind.RETURN, count, null, s, disposition, actorId, now);
    }

    /**
     * 다시 판매(RESTOCK — 결정 15·25): 공구가 모집 중이고 다 팔리기 전일 때만 확정 수량을 줄여 남은 수량을 늘린다(마감과 겨뤄도 하나만 이긴다 — 결정 27). MAIN 이면 공구 행도.
     *
     * @throws GroupBuyException 모집 중이 아니다({@code NOT_RESELLABLE})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(long groupBuyId, ItemRow item, int count, Long actorId, Instant now) {
        After g = qty.groupRestock(groupBuyId, item.main() ? count : 0).orElseThrow(GroupBuyException::notResellable);
        After s = qty.skuRestock(item.skuId(), count).orElseThrow(() -> new IllegalStateException("단품 확정 수량이 모자란다 — 불변식이 깨졌다 skuId=" + item.skuId()));
        movement(groupBuyId, item, item.participationId(), QtyMovementKind.RESTOCK, count, item.main() ? g : null, s, "RESELL", actorId, now);
        events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, item.skuId(), QtyMovementKind.RESTOCK));
    }

    /** 교환 출고(EXCHANGE_OUT — 판매 수량 그대로, 기록만 — 결정 54) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void exchangeOut(long groupBuyId, ItemRow item, int count, Long actorId, Instant now) {
        After s = qty.skuTouch(item.skuId()).orElseThrow();
        movement(groupBuyId, item, item.participationId(), QtyMovementKind.EXCHANGE_OUT, count, null, s, null, actorId, now);
    }

    /**
     * 공급 불가 단품의 최대를 확정 + 선점으로 줄인다(결정 53 — 줄이기 하한 예외: 모집 상태와 관계없이). MAIN 이면 공구 최대도 같은 수만큼(공구 최소 수량 아래로는 DB 가 막아 그만큼만).
     * 한도 없는 단품은 판매 중지만으로 끝(줄일 최대가 없다).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reduceForSupplyFailure(long groupBuyId, long skuId, String reason, Long actorId, Instant now) {
        SkuRow sku = requireSku(groupBuyId, skuId);
        if (sku.maxQty() == null) {
            return;
        }
        int count = sku.maxQty() - Math.max(1, sku.currentQty() + sku.reservedQty());
        if (count <= 0) {
            return;
        }
        After g = null;
        if (sku.main()) {
            GroupBuyRow group = groups.find(groupBuyId).orElseThrow();
            int groupCount = Math.min(count, group.maxQty() - Math.max(1, Math.max(group.minQty(), group.currentQty() + group.reservedQty())));
            if (groupCount > 0) {
                g = qty.groupReduceAnyStatus(groupBuyId, groupCount).orElse(null);
                if (g != null) {
                    qty.insertMovement(ids.nextId(), groupBuyId, null, null, null, QtyMovementKind.REDUCE, groupCount, g, null, reason, actorId, now);
                }
            }
        }
        After s = qty.skuReduce(skuId, groupBuyId, count).orElseThrow(() -> new IllegalStateException("공급 불가 단품을 줄이지 못했다 skuId=" + skuId));
        qty.insertMovement(ids.nextId(), groupBuyId, skuId, null, null, QtyMovementKind.REDUCE, count, null, s, reason, actorId, now);
    }

    /** 다 팔렸으면 그 자리에서 CLOSING + 상태 이력(결정 27·48·78). 바꿨으면 true */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean closeIfSoldOut(long groupBuyId, Long actorId, Instant now) {
        if (!qty.closeIfSoldOut(groupBuyId, now)) {
            return false;
        }
        groups.insertStatusHistory(ids.nextId(), groupBuyId, GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, "SOLD_OUT", actorId, now);
        events.publishEvent(new GroupBuyEvents.StatusChanged(groupBuyId, GroupBuyStatus.RECRUITING.name(), GroupBuyStatus.CLOSING.name(), "SOLD_OUT"));
        return true;
    }

    private SkuRow requireSku(long groupBuyId, long skuId) {
        return groups.skus(groupBuyId).stream().filter(s -> s.id() == skuId).findFirst().orElseThrow(GroupBuyException::notFound);
    }

    private void movement(long groupBuyId, ItemRow item, long participationId, QtyMovementKind kind, int count, After g, After s, String reason, Long actorId,
                          Instant now) {
        qty.insertMovement(ids.nextId(), groupBuyId, item.skuId(), participationId, item.id(), kind, count, g, s, reason, actorId, now);
    }

    /** 공구 선점 실패의 까닭 — 모집 중이 아니면 NOT_RECRUITING, 다 팔렸으면 SOLD_OUT, 결제 중 선점 때문이면 잠시 품절 */
    private GroupBuyException groupShortage(long groupBuyId, Instant now) {
        GroupBuyRow g = groups.find(groupBuyId).orElseThrow(GroupBuyException::notFound);
        if (g.status() != GroupBuyStatus.RECRUITING || now.isBefore(g.startAt()) || !now.isBefore(g.deadline())) {
            return GroupBuyException.notRecruiting();
        }
        return GroupBuyException.soldOut(g.currentQty() < g.maxQty());
    }

    private GroupBuyException skuShortage(long skuId, long groupBuyId) {
        SkuRow s = requireSku(groupBuyId, skuId);
        if (!s.onSale()) {
            return GroupBuyException.skuNotOnSale(skuId);
        }
        return GroupBuyException.skuSoldOut(skuId, s.maxQty() != null && s.currentQty() < s.maxQty());
    }
}
