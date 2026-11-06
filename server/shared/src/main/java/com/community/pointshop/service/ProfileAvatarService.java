package com.community.pointshop.service;

import com.community.avatarupload.entity.AvatarUploadStatus;
import com.community.avatarupload.repository.master.AvatarUploadStore;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 아바타(결정 14, 계약 「B」 고른 아바타·「1-2 화면 목업」 아바타 · commerce-motion-avatar-contract 움직이는 아바타·내가 올린 아바타·하나만 규칙) — 카탈로그 목록과 고르기.
 *
 * <ul>
 *   <li><b>목록</b>({@link #catalog}): 카탈로그 336줄(차례 그대로 — 움직이는 아바타는 {@code animated}·{@code stillUrl}) + 유료의 지금 가격·판매 여부, 로그인했으면 내 보유·고른 것·
 *       환불 가능 여부·잔액. 승인된 「내가 올린 아바타」가 있으면 카테고리 {@code upload} 한 줄({@code id: upload-mine}, {@code itemId: null}, 상점에 없음)을 무료 다음에 둔다.</li>
 *   <li><b>고르기</b>({@link #select}): 무료(basic·hobby)는 누구나(사업자 전용 계정 포함), 유료(정지·움직이는 것)는 <b>환불하지 않고 가진 것만</b>({@code 409 AVATAR_NOT_OWNED}),
 *       {@code upload-mine} 은 승인된 그림이 있을 때만(없으면 409 — 다시 쓸 때 변경권이 들지 않는다). 유료를 고르면 그 보유 줄의 {@code first_used_at} 을 채운다(셀프 환불 불가 —
 *       7-3 「프로필로 설정한 적 없음」). 같은 것을 다시 골라도 200. 저장은 {@code profiles.profile_image = avatar:<id>}, <b>정지 아바타면 마지막 정지 아바타도</b> 그것으로(규칙 3).
 *       자격: 회원(401) → 정지 {@code 403 ACCOUNT_RESTRICTED} → 이메일 인증 전 {@code 403 EMAIL_NOT_VERIFIED} → 형식 {@code 400 fields.avatarId} → 카탈로그에 없음 {@code 404 AVATAR_NOT_FOUND}.
 *       움직이는 아바타를 고르면 움직이는 닉네임 이미지는 <b>쉰다</b>(저장하지 않는다 — 「움직이는 아바타 사용 중 && 닉네임 이미지가 움직임」에서 나온다, 규칙 2).</li>
 *   <li><b>마지막 정지 아바타로</b>({@link #fallBackToStill}·{@link #yieldToNicknameImage}): 닉네임 이미지 다시 쓰기·움직이는 닉네임 이미지 승인·쓰는 중인 내가 올린 아바타 해제·지우기 때
 *       ({@code profiles.last_still_avatar} — 지금도 고를 수 있는 정지 아바타일 때만, 아니면 {@code basic-default}).</li>
 *   <li>잠금 차례: 고르기 = 보유 줄 → 프로필 행(예외 환불과 같은 차례). 되돌리기 = 부르는 쪽이 잡은 프로필 행(보유 줄은 잠그지 않고 읽는다).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProfileAvatarService {

    public static final Pattern AVATAR_ID = Pattern.compile("^[a-z0-9-]{1,60}$");
    /** 내가 올린 아바타 줄의 이름(카탈로그 이름처럼 한글 — 영어 화면도 한글) */
    public static final String UPLOAD_NAME = "내가 올린 아바타";

    private final AvatarCatalog avatars;
    private final PointShopItemStore items;
    private final UserItemStore userItems;
    private final PointShopMemberStore members;
    private final AvatarUploadStore uploads;
    private final AvatarLooks looks;
    private final Clock clock;

    public ProfileAvatarService(AvatarCatalog avatars, PointShopItemStore items, UserItemStore userItems, PointShopMemberStore members, AvatarUploadStore uploads,
                                AvatarLooks looks, ObjectProvider<Clock> clocks) {
        this.avatars = avatars;
        this.items = items;
        this.userItems = userItems;
        this.members = members;
        this.uploads = uploads;
        this.looks = looks;
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
        AvatarUploadStore.Row upload = null;
        if (userId != null) {
            for (HoldingRow h : userItems.holdings(userId)) {
                if (h.usageType() == PointShopUsageType.PERMANENT) {
                    holdings.put(h.itemId(), h);
                }
            }
            upload = uploads.byStatus(userId, AvatarUploadStatus.APPROVED).filter(AvatarUploadStore.Row::hasFiles).orElse(null);
            String image = members.profileImage(userId);
            selectedId = AvatarCatalog.isUpload(image) ? (upload == null ? null : AvatarCatalog.UPLOAD_ID)
                    : avatars.selected(image).map(AvatarCatalog.Avatar::id).orElse(null);
            balance = members.balance(userId);
        }
        String selected = selectedId;
        List<AvatarCategoryView> categories = new ArrayList<>();
        for (AvatarCatalog.Category c : avatars.categories()) {
            if (upload != null && !c.free() && categories.stream().noneMatch(v -> v.id().equals(AvatarCatalog.UPLOAD_CATEGORY))) {
                categories.add(new AvatarCategoryView(AvatarCatalog.UPLOAD_CATEGORY, false, upload.animated(), 1));
            }
            categories.add(new AvatarCategoryView(c.id(), c.free(), c.animated(), c.count()));
        }
        List<AvatarItemView> list = new ArrayList<>();
        for (AvatarCatalog.Avatar avatar : avatars.all()) {
            if (upload != null && !avatar.free() && list.stream().noneMatch(v -> v.id().equals(AvatarCatalog.UPLOAD_ID))) {
                AvatarLooks.Look look = looks.upload(upload);
                list.add(new AvatarItemView(AvatarCatalog.UPLOAD_ID, UPLOAD_NAME, AvatarCatalog.UPLOAD_CATEGORY, false, null, null, false, true,
                        AvatarCatalog.UPLOAD_ID.equals(selected), false, look.url(), look.animated(), look.stillUrl()));
            }
            boolean isSelected = avatar.id().equals(selected);
            if (avatar.free()) {
                list.add(new AvatarItemView(avatar.id(), avatar.name(), avatar.category(), true, null, null, true, userId != null, isSelected, false, avatar.url(),
                        avatar.animated(), avatar.stillUrl()));
                continue;
            }
            ItemRow item = byAvatar.get(avatar.id());
            HoldingRow held = item == null ? null : holdings.get(item.id());
            boolean refundable = held != null && !userItems.refundPending(held.userItemId()) && PointShopRules.selfRefundable(held.purchaseStatus(), held.status(), held.firstUsedAt(), held.purchasedAt(), refundDays, now);
            list.add(new AvatarItemView(avatar.id(), avatar.name(), avatar.category(), false, item == null ? null : item.price(),
                    item == null ? null : Long.toString(item.id()), item != null && item.active(), held != null, isSelected, refundable, avatar.url(), avatar.animated(),
                    avatar.stillUrl()));
        }
        return new AvatarCatalogView(balance, selectedId, refundDays, categories, list);
    }

    /** 그 회원의 지금 아바타 그림 — 고르지 않았거나 풀 수 없으면 {@code basic-default} */
    public AvatarLooks.Look look(long userId) {
        return looks.of(userId, members.profileImage(userId));
    }

    /** 그 회원의 아바타 주소 — 고르지 않았거나 카탈로그에 없으면 {@code basic-default} */
    public String avatarUrl(long userId) {
        return look(userId).url();
    }

    /**
     * 아바타를 고른다 — {@code PUT /api/me/profile/avatar { avatarId }}.
     *
     * @return 고른 아바타 ID·주소·정지 그림(api 가 {@code MyProfile.avatarUrl}·{@code avatarStillUrl} 로)
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
        Instant now = now();
        if (AvatarCatalog.UPLOAD_ID.equals(id)) {
            // 내가 올린 아바타 — 승인된 그림이 있을 때만(다시 쓸 때 변경권 없음). 프로필 행 → 신청 차례(심사와 같다)
            members.lockAvatar(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
            AvatarUploadStore.Row upload = uploads.byStatus(userId, AvatarUploadStatus.APPROVED).filter(AvatarUploadStore.Row::hasFiles)
                    .orElseThrow(AvatarNotOwnedException::new);
            return applyUpload(userId, upload, now);
        }
        AvatarCatalog.Avatar avatar = avatars.find(id).orElseThrow(AvatarNotFoundException::new);
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
        if (!members.setAvatar(userId, AvatarCatalog.profileImageOf(avatar.id()), avatar.animated() ? null : avatar.id(), now)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED); // 프로필 행이 없는 회원(가입이 끝나지 않음)
        }
        return new SelectedAvatarView(avatar.id(), avatar.url(), avatar.stillUrl(), avatar.animated());
    }

    /**
     * 승인된 내가 올린 아바타를 프로필 아바타로 쓴다(고르기·승인 — 부르는 쪽이 프로필 행을 잠갔다). 정지 그림이면 마지막 정지 아바타도 이것.
     */
    @Transactional
    public SelectedAvatarView applyUpload(long userId, AvatarUploadStore.Row upload, Instant now) {
        if (!members.setAvatar(userId, AvatarCatalog.profileImageOf(AvatarCatalog.UPLOAD_ID), upload.animated() ? null : AvatarCatalog.UPLOAD_ID, now)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        AvatarLooks.Look look = looks.upload(upload);
        return new SelectedAvatarView(look.id(), look.url(), look.stillUrl(), look.animated());
    }

    /**
     * 움직이는 닉네임 이미지를 쓰게 됐다(다시 쓰기·움직이는 GIF 승인 — 규칙 3) — 지금 아바타가 움직이면 마지막 정지 아바타로 돌아간다. 부르는 쪽이 프로필 행을 잠갔다.
     *
     * @return 아바타를 바꿨으면 true(정지 아바타를 쓰는 중이었으면 false — 그대로)
     */
    @Transactional
    public boolean yieldToNicknameImage(long userId) {
        if (!look(userId).animated()) {
            return false;
        }
        fallBackToStill(userId);
        return true;
    }

    /**
     * 프로필 아바타를 마지막으로 쓴 정지 아바타로 — 지금도 고를 수 있는 것(무료 · 가진 정지 유료 · 정지 그림인 승인된 내가 올린 아바타)이 아니면 {@code basic-default}. 부르는 쪽이 프로필 행을 잠갔다.
     *
     * @return 돌아간 아바타 ID
     */
    @Transactional
    public String fallBackToStill(long userId) {
        String target = lastStill(userId);
        members.setAvatar(userId, AvatarCatalog.profileImageOf(target), null, now());
        return target;
    }

    /** 돌아갈 정지 아바타 ID — {@code profiles.last_still_avatar} 를 지금 기준으로 다시 본다 */
    String lastStill(long userId) {
        String value = members.avatarState(userId).map(PointShopMemberStore.AvatarState::lastStillAvatar).orElse(null);
        if (value == null) {
            return AvatarCatalog.DEFAULT_ID;
        }
        if (AvatarCatalog.UPLOAD_ID.equals(value)) {
            boolean stillUpload = uploads.byStatus(userId, AvatarUploadStatus.APPROVED).filter(AvatarUploadStore.Row::hasFiles).filter(r -> !r.animated()).isPresent();
            return stillUpload ? value : AvatarCatalog.DEFAULT_ID;
        }
        AvatarCatalog.Avatar avatar = avatars.find(value).orElse(null);
        if (avatar == null || avatar.animated()) {
            return AvatarCatalog.DEFAULT_ID;
        }
        if (avatar.free()) {
            return avatar.id();
        }
        ItemRow item = items.avatarItems().get(avatar.id());
        UserItemRow held = item == null ? null : userItems.ownedPermanent(userId, item.id()).orElse(null);
        return held == null || userItems.refundPending(held.id()) ? AvatarCatalog.DEFAULT_ID : avatar.id();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
