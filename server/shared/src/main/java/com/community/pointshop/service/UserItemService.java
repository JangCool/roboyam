package com.community.pointshop.service;

import com.community.pointshop.dto.response.EquippedNicknameColorView;
import com.community.pointshop.dto.response.EquippedProfileBorderView;
import com.community.pointshop.dto.response.MyEquippedView;
import com.community.pointshop.dto.response.MyItemListView;
import com.community.pointshop.dto.response.MyItemView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.UserItemStatus;
import com.community.pointshop.exception.PointShopBlockedException;
import com.community.pointshop.exception.PointShopRefundPendingException;
import com.community.pointshop.exception.UserItemExpiredException;
import com.community.pointshop.exception.UserItemNotEquippableException;
import com.community.pointshop.exception.UserItemNotFoundException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore.RequestRow;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.EquippedRow;
import com.community.pointshop.repository.master.UserItemStore.MyItemRow;
import com.community.pointshop.repository.master.UserItemStore.UserItemRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 아이템 — 목록·장착·해제(계약 「B」 {@code user_items}·{@code user_equipped}, 「1-2 화면 목업」 API 후보).
 *
 * <ul>
 *   <li><b>만료</b>는 저장하지 않고 읽을 때 {@code expires_at ≤ 지금} 으로 계산한다(결정 6). 장착한 기간제가 끝나면 장착 자리는 남아 있어도 <b>해제로 보인다</b>(배치 없음).</li>
 *   <li><b>정지 중</b>(정지·영구 정지)이면 장착·해제 모두 {@code 403 ACCOUNT_RESTRICTED} — 프로필 수정·아바타 고르기와 같다(사용자 2026-10-26: 정지는 이용 제한). 목록은 읽는다.</li>
 *   <li><b>장착</b>({@link #equip}): 내 줄(아니면 404) · 가진 줄 · 소모형·아바타가 아님 · 자리와 종류가 같음(아니면 {@code 409 USER_ITEM_NOT_EQUIPPABLE}) · 기간이 남음
 *       (아니면 {@code 409 USER_ITEM_EXPIRED}). 자리마다 하나 — 다른 줄을 장착하면 바꾼다. 장착하면 {@code first_used_at} 을 채운다(풀어도 지우지 않는다 — 써 보고 환불을 막는다, 7-1).</li>
 *   <li>기간제를 이어 산 줄(시작이 앞 줄의 끝)은 앞 줄이 끝난 뒤 다시 장착해야 보인다 — 자동으로 넘겨 붙이지 않는다(미리 장착해도 되고, 그때 사용으로 본다).</li>
 *   <li>환불 판정은 {@link PointShopRules}(지금 설정의 기한).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class UserItemService {

    private final UserItemStore userItems;
    private final PointShopItemStore items;
    private final PointShopMemberStore members;
    private final PointShopPurchaseStore purchases;
    private final AvatarCatalog avatars;
    private final Clock clock;

    public UserItemService(UserItemStore userItems, PointShopItemStore items, PointShopMemberStore members, PointShopPurchaseStore purchases, AvatarCatalog avatars,
                           ObjectProvider<Clock> clocks) {
        this.userItems = userItems;
        this.purchases = purchases;
        this.items = items;
        this.members = members;
        this.avatars = avatars;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 내 아이템 — {@code GET /api/me/items}(최근에 산 줄이 위, 회수된 줄도 보인다) */
    public MyItemListView myItems(long userId) {
        Instant now = now();
        int refundDays = items.refundDays();
        String selectedAvatar = avatars.selected(members.profileImage(userId)).map(AvatarCatalog.Avatar::id).orElse(null);
        boolean suspended = members.state(userId).map(PointShopMemberStore.MemberState::suspended).orElse(false);
        List<MyItemRow> rows = userItems.myItems(userId);
        Map<Long, RequestRow> requests = purchases.latestRequests(rows.stream().map(row -> row.item().purchaseId()).toList());
        List<MyItemView> list = rows.stream().map(row -> view(row, requests.get(row.item().purchaseId()), suspended, refundDays, selectedAvatar, now)).toList();
        return new MyItemListView(refundDays, suspended, list);
    }

    /**
     * 한 줄 — {@code refundable} 은 「지금 [환불]·[환불 신청]을 누를 수 있나」: 조건(안 씀 + 기한 안)에 더해 신청이 대기 중이 아니고, 정지 중이면 거절된 적이 없어야 한다.
     */
    private static MyItemView view(MyItemRow row, RequestRow request, boolean suspended, int refundDays, String selectedAvatar, Instant now) {
        UserItemRow item = row.item();
        boolean expired = item.expiredAt(now);
        boolean revoked = item.status() == UserItemStatus.REVOKED;
        boolean inUse = row.kind() == PointShopItemKind.PROFILE_AVATAR && item.status() == UserItemStatus.OWNED && row.avatarId() != null
                && row.avatarId().equals(selectedAvatar);
        boolean pending = request != null && request.status() == PointShopRefundRequestStatus.PENDING;
        boolean rejectedWhileSuspended = suspended && request != null && request.status() == PointShopRefundRequestStatus.REJECTED;
        boolean refundable = !pending && !rejectedWhileSuspended
                && PointShopRules.selfRefundable(row.purchaseStatus(), item.status(), item.firstUsedAt(), row.purchasedAt(), refundDays, now);
        return new MyItemView(Long.toString(item.id()), Long.toString(item.itemId()), Long.toString(item.purchaseId()), row.kind(), item.usageType(), row.name(),
                row.nameEn(), row.color(), row.borderStyle(), row.avatarId(), row.avatarId() == null ? null : AvatarCatalog.URL_PREFIX + row.avatarId() + ".svg", inUse,
                item.status(), expired, row.equippedSlot() != null && !expired && !revoked, item.startsAt(), item.expiresAt(), item.firstUsedAt(), row.purchasedAt(),
                row.pricePaid(), refundable, revoked ? null : PointShopRules.refundUntil(row.purchasedAt(), refundDays), PointShopPurchaseService.requestView(request));
    }

    /** 지금 장착 — 기간이 끝난 자리는 null */
    public MyEquippedView equipped(long userId) {
        Instant now = now();
        EquippedNicknameColorView color = null;
        EquippedProfileBorderView border = null;
        for (EquippedRow row : userItems.equipped(userId)) {
            if (!row.liveAt(now)) {
                continue;
            }
            if (row.slot() == EquipSlot.NICKNAME_COLOR) {
                color = new EquippedNicknameColorView(Long.toString(row.userItemId()), Long.toString(row.itemId()), row.color(), row.expiresAt());
            } else {
                border = new EquippedProfileBorderView(Long.toString(row.userItemId()), Long.toString(row.itemId()), row.borderStyle(), row.expiresAt());
            }
        }
        return new MyEquippedView(color, border);
    }

    /**
     * 장착 — {@code PUT /api/me/equipped/{slot} { userItemId }}.
     *
     * @throws PointShopBlockedException     정지 중(403 {@code ACCOUNT_RESTRICTED}) — 줄을 보기 전에 판정한다
     * @throws UserItemNotFoundException      없거나 남의 줄(404)
     * @throws UserItemNotEquippableException 회수·소모형·아바타·자리와 종류가 다름(409)
     * @throws PointShopRefundPendingException 환불 신청이 대기 중(409 — 정지가 풀린 뒤에도)
     * @throws UserItemExpiredException       기간이 끝났다(409)
     */
    @Transactional
    public MyEquippedView equip(long userId, EquipSlot slot, long userItemId) {
        assertNotSuspended(userId);
        UserItemRow item = userItems.findForUpdate(userItemId).filter(row -> row.userId() == userId).orElseThrow(UserItemNotFoundException::new);
        ItemRow product = items.find(item.itemId()).orElseThrow(UserItemNotFoundException::new);
        Optional<EquipSlot> itemSlot = product.kind().slot();
        if (item.status() != UserItemStatus.OWNED || item.usageType() == PointShopUsageType.CONSUMABLE || itemSlot.isEmpty() || itemSlot.get() != slot) {
            throw new UserItemNotEquippableException();
        }
        if (userItems.refundPending(item.id())) {
            throw new PointShopRefundPendingException(); // 환불 신청 대기 중 — 쓰면 신청 조건이 깨진다
        }
        Instant now = now();
        if (item.expiredAt(now)) {
            throw new UserItemExpiredException();
        }
        userItems.equip(userId, slot, item.id(), item.itemId(), item.expiresAt(), now);
        userItems.markFirstUsed(item.id(), now);
        return equipped(userId);
    }

    /** 해제 — {@code DELETE /api/me/equipped/{slot}}(비어 있어도 200). 정지 중이면 {@code 403 ACCOUNT_RESTRICTED} */
    @Transactional
    public MyEquippedView unequip(long userId, EquipSlot slot) {
        assertNotSuspended(userId);
        userItems.unequip(userId, slot);
        return equipped(userId);
    }

    /** 정지·영구 정지 중이면 403 — 탈퇴했거나 없는 회원은 401(아바타 고르기와 같은 판정) */
    private void assertNotSuspended(long userId) {
        if (members.state(userId).filter(state -> !state.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED)).suspended()) {
            throw new PointShopBlockedException(PointShopBlockedException.ACCOUNT_RESTRICTED);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
