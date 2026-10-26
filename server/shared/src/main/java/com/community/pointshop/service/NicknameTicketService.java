package com.community.pointshop.service;

import com.community.pointshop.dto.NicknameTicketInfo;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopSaleState;
import com.community.pointshop.exception.NicknameTicketRequiredException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.UserItemStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 변경권(member-mypage-contract 「후속 작업: 닉네임 변경권」, 계약 결정 1·2) — 포인트 상점 소모형 품목({@code NICKNAME_CHANGE_TICKET}). 사기·환불은 다른 품목과 같다.
 *
 * <ul>
 *   <li><b>쓰기</b>({@link #useOne}): 가입 뒤 1번의 무료 변경을 쓴 회원이 닉네임을 바꿀 때 api {@code MyProfileService} 가 <b>같은 트랜잭션</b>에서(프로필 행 쓰기 잠금 뒤, 닉네임이 실제로 바뀔 때만)
 *       부른다. 안 쓴 장 중 먼저 산 것부터 한 장을 USED 로 — {@code SKIP LOCKED} 조건부 UPDATE 라 <b>동시 요청 둘이 한 장을 두 번 쓰지 못한다</b>. 없으면 {@code 409 NICKNAME_TICKET_REQUIRED}.
 *       닉네임 저장이 실패하면(중복 등) 트랜잭션째 되돌아가 변경권도 그대로다.</li>
 *   <li><b>값</b>({@link #price}): 지금 파는 변경권 품목의 가격(정본 — 결정 2, {@code point_settings.nickname_ticket_price} 는 V86 이 옮기고 지웠다). 파는 것이 없으면 시드 품목
 *       ({@value #SEED_CODE}), 그것도 없으면 null.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class NicknameTicketService {

    /** V86 이 넣은 변경권 품목 코드 */
    public static final String SEED_CODE = "nickname-change";

    private final UserItemStore userItems;
    private final PointShopItemStore items;
    private final Clock clock;

    public NicknameTicketService(UserItemStore userItems, PointShopItemStore items, ObjectProvider<Clock> clocks) {
        this.userItems = userItems;
        this.items = items;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 안 쓴 변경권 수 */
    public int count(long userId) {
        return userItems.countTickets(userId);
    }

    /** 한 장 값(P) — 변경권 품목이 없으면 null */
    public Integer price() {
        return ticketItem().map(ItemRow::price).orElse(null);
    }

    /** 장수 + 값 + 사러 갈 품목 */
    public NicknameTicketInfo info(long userId) {
        Optional<ItemRow> item = ticketItem();
        return new NicknameTicketInfo(count(userId), item.map(ItemRow::price).orElse(null), item.map(row -> Long.toString(row.id())).orElse(null));
    }

    /**
     * 변경권 한 장을 쓴다 — 부르는 쪽(닉네임 변경)의 트랜잭션 안에서.
     *
     * @return 쓴 보유 줄 ID
     * @throws NicknameTicketRequiredException 쓸 변경권이 없다(409, {@code ticketCount}·{@code ticketPrice})
     */
    @Transactional
    public long useOne(long userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return userItems.useTicket(userId, now).orElseThrow(() -> required(userId));
    }

    /** 「변경권이 필요하다」 오류(무료를 다 썼는데 {@code useTicket} 없이 바꾸려 할 때도 api 가 던진다) */
    public NicknameTicketRequiredException required(long userId) {
        return new NicknameTicketRequiredException(count(userId), price());
    }

    /** 지금 파는 변경권 품목(순서 → ID 차례 첫째), 없으면 시드 품목 */
    private Optional<ItemRow> ticketItem() {
        Instant now = clock.instant();
        List<ItemRow> tickets = items.list(PointShopItemKind.NICKNAME_CHANGE_TICKET, null);
        Optional<ItemRow> onSale = tickets.stream().filter(row -> PointShopCatalogService.saleState(row, now) == PointShopSaleState.ON_SALE)
                .min(Comparator.comparingInt(ItemRow::sortOrder).thenComparingLong(ItemRow::id));
        return onSale.isPresent() ? onSale : tickets.stream().filter(row -> SEED_CODE.equals(row.code())).findFirst();
    }
}
