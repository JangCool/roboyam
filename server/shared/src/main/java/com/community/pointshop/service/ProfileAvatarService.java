package com.community.pointshop.service;

import com.community.pointshop.dto.response.AvatarCatalogView;
import com.community.pointshop.dto.response.AvatarCategoryView;
import com.community.pointshop.dto.response.AvatarItemView;
import com.community.pointshop.dto.response.SelectedAvatarView;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.exception.AvatarNotFoundException;
import com.community.pointshop.exception.AvatarNotOwnedException;
import com.community.pointshop.exception.PointShopBlockedException;
import com.community.pointshop.exception.PointShopRefundPendingException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.PointShopMemberStore.MemberState;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.HoldingRow;
import com.community.pointshop.repository.master.UserItemStore.UserItemRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 아바타(결정 14, 계약 「B」 고른 아바타·「1-2 화면 목업」 아바타) — 카탈로그 목록과 고르기.
 *
 * <ul>
 *   <li><b>목록</b>({@link #catalog}): 328줄(카탈로그 차례) + 유료의 지금 가격·판매 여부, 로그인했으면 내 보유·고른 것·환불 가능 여부·잔액.</li>
 *   <li><b>고르기</b>({@link #select}): 무료(basic·hobby)는 누구나(사업자 전용 계정 포함), 유료는 <b>환불하지 않고 가진 것만</b>({@code 409 AVATAR_NOT_OWNED}). 유료를 고르면 그 보유 줄의
 *       {@code first_used_at} 을 채운다(셀프 환불 불가 — 7-3 「프로필로 설정한 적 없음」). 같은 것을 다시 골라도 200. 저장은 {@code profiles.profile_image = avatar:<id>}.
 *       자격: 회원(401) → 정지 {@code 403 ACCOUNT_RESTRICTED} → 이메일 인증 전 {@code 403 EMAIL_NOT_VERIFIED} → 형식 {@code 400 fields.avatarId} → 카탈로그에 없음 {@code 404 AVATAR_NOT_FOUND}.</li>
 *   <li>잠금 차례: 보유 줄 → 프로필 행(예외 환불과 같은 차례).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProfileAvatarService {

    public static final Pattern AVATAR_ID = Pattern.compile("^[a-z0-9-]{1,60}$");

    private final AvatarCatalog avatars;
    private final PointShopItemStore items;
    private final UserItemStore userItems;
    private final PointShopMemberStore members;
    private final Clock clock;

    public ProfileAvatarService(AvatarCatalog avatars, PointShopItemStore items, UserItemStore userItems, PointShopMemberStore members, ObjectProvider<Clock> clocks) {
        this.avatars = avatars;
        this.items = items;
        this.userItems = userItems;
        this.members = members;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 아바타 목록 — {@code GET /api/avatars}.
     *
     * @param userId 로그인한 회원(비로그인은 null — 보유·고른 것·잔액 없이)
     */
    public AvatarCatalogView catalog(Long userId) {
        Instant now = now();
        int refundDays = items.refundDays();
        Map<String, ItemRow> byAvatar = items.avatarItems();
        Map<Long, HoldingRow> holdings = new HashMap<>();
        String selectedId = null;
        long balance = 0;
        if (userId != null) {
            for (HoldingRow h : userItems.holdings(userId)) {
                if (h.usageType() == PointShopUsageType.PERMANENT) {
                    holdings.put(h.itemId(), h);
                }
            }
            selectedId = avatars.selected(members.profileImage(userId)).map(AvatarCatalog.Avatar::id).orElse(null);
            balance = members.balance(userId);
        }
        List<AvatarCategoryView> categories = avatars.categories().stream().map(c -> new AvatarCategoryView(c.id(), c.free(), c.count())).toList();
        String selected = selectedId;
        List<AvatarItemView> list = avatars.all().stream().map(avatar -> {
            boolean isSelected = avatar.id().equals(selected);
            if (avatar.free()) {
                return new AvatarItemView(avatar.id(), avatar.name(), avatar.category(), true, null, null, true, userId != null, isSelected, false, avatar.url());
            }
            ItemRow item = byAvatar.get(avatar.id());
            HoldingRow held = item == null ? null : holdings.get(item.id());
            boolean refundable = held != null && !userItems.refundPending(held.userItemId()) && PointShopRules.selfRefundable(held.purchaseStatus(), held.status(), held.firstUsedAt(), held.purchasedAt(), refundDays, now);
            return new AvatarItemView(avatar.id(), avatar.name(), avatar.category(), false, item == null ? null : item.price(),
                    item == null ? null : Long.toString(item.id()), item != null && item.active(), held != null, isSelected, refundable, avatar.url());
        }).toList();
        return new AvatarCatalogView(balance, selectedId, refundDays, categories, list);
    }

    /** 그 회원의 아바타 주소 — 고르지 않았거나 카탈로그에 없으면 {@code basic-default} */
    public String avatarUrl(long userId) {
        return avatars.urlOf(members.profileImage(userId));
    }

    /**
     * 아바타를 고른다 — {@code PUT /api/me/profile/avatar { avatarId }}.
     *
     * @return 고른 아바타 ID·주소(api 가 {@code MyProfile.avatarUrl} 로)
     */
    @Transactional
    public SelectedAvatarView select(long userId, String avatarId) {
        MemberState state = members.state(userId).filter(s -> !s.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (state.suspended()) {
            throw new PointShopBlockedException(PointShopBlockedException.ACCOUNT_RESTRICTED);
        }
        if (!state.emailVerified()) {
            throw new PointShopBlockedException(PointShopBlockedException.EMAIL_NOT_VERIFIED);
        }
        String id = avatarId == null ? "" : avatarId.strip();
        if (!AVATAR_ID.matcher(id).matches()) {
            throw ValidationException.of("avatarId", new MessageRef("pointshop.avatar.avatarId.format"));
        }
        AvatarCatalog.Avatar avatar = avatars.find(id).orElseThrow(AvatarNotFoundException::new);
        Instant now = now();
        if (!avatar.free()) {
            ItemRow item = items.avatarItems().get(avatar.id());
            UserItemRow held = item == null ? null : userItems.ownedPermanentForUpdate(userId, item.id()).orElse(null);
            if (held == null) {
                throw new AvatarNotOwnedException();
            }
            if (userItems.refundPending(held.id())) {
                throw new PointShopRefundPendingException(); // 환불 신청 대기 중 — 고르면 신청 조건이 깨진다
            }
            userItems.markFirstUsed(held.id(), now);
        }
        if (!members.setProfileImage(userId, AvatarCatalog.profileImageOf(avatar.id()), now)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED); // 프로필 행이 없는 회원(가입이 끝나지 않음)
        }
        return new SelectedAvatarView(avatar.id(), avatar.url());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
