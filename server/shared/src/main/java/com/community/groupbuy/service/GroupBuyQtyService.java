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
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
 *   <li>공구 행 → 옵션 행(ID 오름차순, 한 줄씩) 차례로 조건부 UPDATE … RETURNING, 줄마다 이동 줄 하나(이후 값은 RETURNING 으로 — 결정 82).</li>
 *   <li>공구·옵션 행을 {@code FOR UPDATE} 로 잠그지 않는다 — 결제 확정({@code onPaid})이 이 길을 쓰므로(결정 37). 조건에 지면 빈 값·false 로 알린다.</li>
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

    /** 공구 행 → 옵션 ID 오름차순(옵션 없는 줄 먼저) — 잠금 차례(결정 23·37) */
    static List<ItemRow> lockOrder(List<ItemRow> items) {
        return items.stream().sorted(Comparator.comparing(ItemRow::optionId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    }

    /**
     * 주문 줄마다 선점 — 하나라도 모자라면 던진다(트랜잭션째 되돌려 주문 전체가 실패 — 결정 23).
     *
     * @throws GroupBuyException 모집 중이 아님 · 다 팔림/잠시 품절 · 옵션 다 팔림/잠시 품절
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(GroupBuyRow group, long participationId, List<ItemRow> items, Instant now) {
        for (ItemRow item : lockOrder(items)) {
            After g = qty.groupReserve(group.id(), item.qty(), now).orElseThrow(() -> groupShortage(group.id(), now));
            After o = null;
            if (item.optionId() != null) {
                o = qty.optionReserve(item.optionId(), group.id(), item.qty()).orElseThrow(() -> optionShortage(item.optionId(), group.id()));
            }
            movement(group.id(), item, participationId, QtyMovementKind.RESERVE, item.qty(), g, o, null, null, now);
        }
    }

    /**
     * 결제 확정 — 줄마다 선점을 확정 수량으로. 공구가 결제를 받는 상태가 아니거나 선점이 모자라면(불변식이 이미 깨짐) false — 부르는 쪽이 세이브포인트로 되돌리고 거절한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirm(long groupBuyId, long participationId, List<ItemRow> items, Instant now) {
        for (ItemRow item : lockOrder(items)) {
            Optional<After> g = qty.groupConfirm(groupBuyId, item.qty());
            if (g.isEmpty()) {
                return false;
            }
            After o = null;
            if (item.optionId() != null) {
                Optional<After> option = qty.optionConfirm(item.optionId(), item.qty());
                if (option.isEmpty()) {
                    return false;
                }
                o = option.get();
            }
            movement(groupBuyId, item, participationId, QtyMovementKind.CONFIRM, item.qty(), g.get(), o, null, null, now);
        }
        return true;
    }

    /** 선점 해제 — 줄마다. 선점이 모자라 해제하지 못한 줄이 있으면 false(로그 — 불변식 검사가 잡는다) */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(long groupBuyId, long participationId, List<ItemRow> items, Instant now) {
        boolean all = true;
        for (ItemRow item : lockOrder(items)) {
            Optional<After> g = qty.groupRelease(groupBuyId, item.qty());
            Optional<After> o = item.optionId() == null ? Optional.empty() : qty.optionRelease(item.optionId(), item.qty());
            if (g.isEmpty() || (item.optionId() != null && o.isEmpty())) {
                log.error("선점 해제할 수량이 모자란다 — 불변식 확인 필요 groupBuyId={} participationId={}", groupBuyId, participationId);
                all = false;
                if (g.isEmpty()) {
                    continue;
                }
            }
            movement(groupBuyId, item, participationId, QtyMovementKind.RELEASE, item.qty(), g.get(), o.orElse(null), null, null, now);
            events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, item.optionId(), QtyMovementKind.RELEASE));
        }
        return all;
    }

    /**
     * 결제된 줄의 수량 일부를 취소(CANCEL — 판매 수량 감소). {@code requireAcceptingPayment} 면 공구가 모집 중·집계 중일 때만(성사형 본인 즉시 취소 — 판정과 하나만 이긴다).
     *
     * @return 취소했으면 true(조건에 졌으면 false — 아무것도 바꾸지 않았다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancel(long groupBuyId, ItemRow item, int count, boolean requireAcceptingPayment, Long actorId, String reason, Instant now) {
        Optional<After> g = qty.groupCancel(groupBuyId, count, requireAcceptingPayment);
        if (g.isEmpty()) {
            return false;
        }
        After o = null;
        if (item.optionId() != null) {
            Optional<After> option = qty.optionCancel(item.optionId(), count);
            if (option.isEmpty()) {
                throw new IllegalStateException("옵션 확정 수량이 모자란다 — 불변식이 깨졌다 optionId=" + item.optionId());
            }
            o = option.get();
        }
        movement(groupBuyId, item, item.participationId(), QtyMovementKind.CANCEL, count, g.get(), o, reason, actorId, now);
        events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, item.optionId(), QtyMovementKind.CANCEL));
        return true;
    }

    /**
     * 추가 입고(결정 17·80) — 모집 중이고 다 팔리기 전에만. 옵션을 지정하면 옵션 최대와 공구 최대를 같은 수만큼(공구 행 → 옵션 행). 한도 없는 옵션은 거절(공구 단위로).
     *
     * @throws GroupBuyException 모집 중이 아니거나 이미 다 팔렸다({@code NOT_RECRUITING}) · 한도 없는 옵션({@code OPTION_UNLIMITED}) · 옵션 없음({@code NOT_FOUND})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockChange add(long groupBuyId, Long optionId, int count, String reason, Long actorId, Instant now) {
        GroupBuyStore.OptionRow option = optionId == null ? null : requireOption(groupBuyId, optionId);
        if (option != null && option.maxQty() == null) {
            throw GroupBuyException.optionUnlimited();
        }
        After g = qty.groupAdd(groupBuyId, count).orElseThrow(GroupBuyException::notRecruiting);
        After o = null;
        if (option != null) {
            o = qty.optionAdd(optionId, groupBuyId, count).orElseThrow(GroupBuyException::optionUnlimited);
        }
        qty.insertMovement(ids.nextId(), groupBuyId, optionId, null, null, QtyMovementKind.ADD, count, g, o, reason, actorId, now);
        events.publishEvent(new GroupBuyEvents.QtyReleased(groupBuyId, optionId, QtyMovementKind.ADD));
        GroupBuyRow after = groups.find(groupBuyId).orElseThrow();
        return new StockChange(groupBuyId, g.max(), after.addedQty(), o == null ? null : o.max(), false);
    }

    /**
     * 줄이기(결정 17·48·80) — 모집 중이고 하한(공구: 확정 + 선점·성사형 최소 수량 / 옵션: 그 옵션의 확정 + 선점) 아래로 가지 않을 때만. 줄인 결과 다 팔리면 같은 트랜잭션에서 CLOSING.
     *
     * @throws GroupBuyException 모집 중이 아님 · 하한 아래({@code QTY_BELOW_FLOOR} + 하한) · 한도 없는 옵션
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockChange reduce(long groupBuyId, Long optionId, int count, String reason, Long actorId, Instant now) {
        GroupBuyStore.OptionRow option = optionId == null ? null : requireOption(groupBuyId, optionId);
        if (option != null && option.maxQty() == null) {
            throw GroupBuyException.optionUnlimited();
        }
        Optional<After> reduced = qty.groupReduce(groupBuyId, count);
        if (reduced.isEmpty()) {
            GroupBuyRow now0 = groups.find(groupBuyId).orElseThrow(GroupBuyException::notFound);
            if (now0.status() != GroupBuyStatus.RECRUITING) {
                throw GroupBuyException.notRecruiting();
            }
            throw GroupBuyException.qtyBelowFloor(Math.max(1, Math.max(now0.currentQty() + now0.reservedQty(), now0.minQty())));
        }
        After g = reduced.get();
        After o = null;
        if (option != null) {
            Optional<After> optionReduced = qty.optionReduce(optionId, groupBuyId, count);
            if (optionReduced.isEmpty()) {
                // 공구 행은 이미 줄였다 — 던져서 트랜잭션째 되돌린다
                GroupBuyStore.OptionRow fresh = requireOption(groupBuyId, optionId);
                throw GroupBuyException.qtyBelowFloor(Math.max(1, fresh.currentQty() + fresh.reservedQty()));
            }
            o = optionReduced.get();
        }
        qty.insertMovement(ids.nextId(), groupBuyId, optionId, null, null, QtyMovementKind.REDUCE, count, g, o, reason, actorId, now);
        boolean closed = closeIfSoldOut(groupBuyId, actorId, now);
        GroupBuyRow after = groups.find(groupBuyId).orElseThrow();
        return new StockChange(groupBuyId, g.max(), after.addedQty(), o == null ? null : o.max(), closed);
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

    private GroupBuyStore.OptionRow requireOption(long groupBuyId, long optionId) {
        return groups.options(groupBuyId).stream().filter(o -> o.id() == optionId).findFirst().orElseThrow(GroupBuyException::notFound);
    }

    private void movement(long groupBuyId, ItemRow item, long participationId, QtyMovementKind kind, int count, After g, After o, String reason, Long actorId,
                          Instant now) {
        qty.insertMovement(ids.nextId(), groupBuyId, item.optionId(), participationId, item.id(), kind, count, g, o, reason, actorId, now);
    }

    /** 공구 선점 실패의 까닭 — 모집 중이 아니면 NOT_RECRUITING, 다 팔렸으면 SOLD_OUT, 결제 중 선점 때문이면 잠시 품절 */
    private GroupBuyException groupShortage(long groupBuyId, Instant now) {
        GroupBuyRow g = groups.find(groupBuyId).orElseThrow(GroupBuyException::notFound);
        if (g.status() != GroupBuyStatus.RECRUITING || now.isBefore(g.startAt()) || !now.isBefore(g.deadline())) {
            return GroupBuyException.notRecruiting();
        }
        return GroupBuyException.soldOut(g.currentQty() < g.maxQty());
    }

    private GroupBuyException optionShortage(long optionId, long groupBuyId) {
        GroupBuyStore.OptionRow o = requireOption(groupBuyId, optionId);
        return GroupBuyException.optionSoldOut(optionId, o.maxQty() != null && o.currentQty() < o.maxQty());
    }
}
