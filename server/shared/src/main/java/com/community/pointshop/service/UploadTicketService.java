package com.community.pointshop.service;

import com.community.pointshop.entity.PointShopSaleState;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.exception.UploadTicketRequiredException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.UserItemRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 올리기 변경권(닉네임 이미지 변경권·아바타 변경권 — commerce-motion-avatar-contract 규칙 0, community {@code api-nickname-image.md} 「변경권」 방식). 사기·환불은 다른 품목과 같다.
 *
 * <ul>
 *   <li><b>예약</b>({@link #reserve}): 신청을 만드는 <b>같은 트랜잭션</b>에서 쓸 수 있는 장 중 먼저 산 것 한 장을 그 신청에 예약한다 — {@code SKIP LOCKED} 조건부 UPDATE 라
 *       동시 신청 둘이 한 장을 함께 잡지 못하고, 신청 하나에 장 하나({@code ux_user_items_ref}). 없으면 {@code 409 …_TICKET_REQUIRED}({@code ticketCount}·{@code ticketPrice}).</li>
 *   <li><b>대체</b>({@link #transfer}): 새 신청이 대기 신청을 대체하면 예약된 장을 그대로 새 신청으로 옮긴다(한 장 더 들지 않는다).</li>
 *   <li><b>반환</b>({@link #release}): 반려·본인 취소 — 같은 장이 다시 쓸 수 있는 장이 된다(환불 기한 판정은 산 시각 그대로).</li>
 *   <li><b>사용</b>({@link #consume}): 승인 — 예약된 장이 USED 가 된다(셀프 환불 불가). 적용 뒤 해제·지우기는 돌려주지 않는다(부르지 않는다).</li>
 *   <li>예약된 장은 쓸 수도(다른 신청) 환불할 수도({@code 409 POINT_SHOP_REFUND_NOT_ALLOWED blockedBy RESERVED}) 없다. 환불 신청 대기 중인 장은 예약하지 않는다.</li>
 *   <li><b>값</b>({@link #price}): 지금 파는 그 변경권 품목의 가격(순서 → ID 첫째), 없으면 시드 품목, 그것도 없으면 null.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class UploadTicketService {

    /** 장 수 · 값 · 사러 갈 품목(없으면 null) */
    public record Info(int count, Integer price, String itemId) {
    }

    private final UserItemStore userItems;
    private final PointShopItemStore items;
    private final Clock clock;

    public UploadTicketService(UserItemStore userItems, PointShopItemStore items, ObjectProvider<Clock> clocks) {
        this.userItems = userItems;
        this.items = items;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 쓸 수 있는 장 수(예약된 장·환불 신청 대기 중인 장은 빼고) */
    public int count(long userId, UploadTicketUse use) {
        return userItems.countUploadTickets(userId, use);
    }

    /** 한 장 값(P) — 품목이 없으면 null */
    public Integer price(UploadTicketUse use) {
        return ticketItem(use).map(ItemRow::price).orElse(null);
    }

    public Info info(long userId, UploadTicketUse use) {
        Optional<ItemRow> item = ticketItem(use);
        return new Info(count(userId, use), item.map(ItemRow::price).orElse(null), item.map(row -> Long.toString(row.id())).orElse(null));
    }

    /** 「변경권이 필요하다」 오류 */
    public UploadTicketRequiredException required(long userId, UploadTicketUse use) {
        return new UploadTicketRequiredException(use, count(userId, use), price(use));
    }

    /**
     * 그 신청에 한 장을 예약한다 — 부르는 쪽(신청 만들기)의 트랜잭션 안에서.
     *
     * @return 예약한 보유 줄 ID
     * @throws UploadTicketRequiredException 예약할 장이 없다(409)
     */
    @Transactional
    public long reserve(long userId, UploadTicketUse use, long requestId) {
        return userItems.reserveUploadTicket(userId, use, requestId, now()).orElseThrow(() -> required(userId, use));
    }

    /** 대기 신청을 새 신청이 대체 — 예약된 장을 넘긴다. 넘겼으면 true(앞 신청에 예약이 없었으면 false — 이 규칙 전의 닉네임 이미지 신청) */
    @Transactional
    public boolean transfer(UploadTicketUse use, long fromRequestId, long toRequestId) {
        return userItems.transferUploadTicket(use, fromRequestId, toRequestId);
    }

    /** 반환(반려·본인 취소). 돌려줬으면 true(예약이 없던 신청이면 false) */
    @Transactional
    public boolean release(UploadTicketUse use, long requestId) {
        return userItems.releaseUploadTicket(use, requestId);
    }

    /** 사용(승인). 썼으면 true(예약이 없던 신청이면 false) */
    @Transactional
    public boolean consume(UploadTicketUse use, long requestId) {
        return userItems.consumeUploadTicket(use, requestId, now());
    }

    /** 그 신청에 지금 예약된 장이 있나 */
    public boolean reserved(UploadTicketUse use, long requestId) {
        return userItems.reservedFor(use, requestId).isPresent();
    }

    /** 여러 신청에 이어진 장(예약 중이거나 그 신청이 쓴 장) */
    public Map<Long, UserItemRow> ticketsOf(UploadTicketUse use, Collection<Long> requestIds) {
        return userItems.ticketsOf(use, requestIds);
    }

    private Optional<ItemRow> ticketItem(UploadTicketUse use) {
        Instant now = clock.instant();
        List<ItemRow> tickets = items.list(use.kind(), null);
        Optional<ItemRow> onSale = tickets.stream().filter(row -> PointShopCatalogService.saleState(row, now) == PointShopSaleState.ON_SALE)
                .min(Comparator.comparingInt(ItemRow::sortOrder).thenComparingLong(ItemRow::id));
        return onSale.isPresent() ? onSale : tickets.stream().filter(row -> use.seedCode().equals(row.code())).findFirst();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
