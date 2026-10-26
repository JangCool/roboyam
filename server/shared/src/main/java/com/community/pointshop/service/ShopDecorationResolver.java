package com.community.pointshop.service;

import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.dto.response.AuthorDecorationView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.EquippedRow;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자 장식·아바타 표시(계약 「B」 장식 표시, 결정 8·14) — 글·댓글·목록·쪽지 상대·공개 프로필의 작성자 여러 명을 <b>한 번에</b> 푼다: 장착 표({@code user_equipped}) + 품목 하나를 붙인
 * 한 문장, 프로필 이미지 한 문장. 색·테두리는 품목의 <b>지금</b> 값(운영자가 색을 고치면 산 사람의 표시도 바뀐다). <b>기간이 끝난 장착은 빠진다</b>(null).
 * 아바타 주소는 늘 채운다(고르지 않았거나 카탈로그에 없으면 {@code basic-default}). 비회원은 부르는 쪽이 뺀다(이니셜).
 */
@Service
@Transactional(readOnly = true)
public class ShopDecorationResolver {

    private final UserItemStore userItems;
    private final PointShopMemberStore members;
    private final AvatarCatalog avatars;
    private final Clock clock;

    public ShopDecorationResolver(UserItemStore userItems, PointShopMemberStore members, AvatarCatalog avatars, ObjectProvider<Clock> clocks) {
        this.userItems = userItems;
        this.members = members;
        this.avatars = avatars;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 회원 ID → 표시(요청한 모든 ID 가 들어 있다 — 장식이 없으면 {@code decoration} null) */
    public Map<Long, AuthorLook> resolve(Collection<Long> userIds) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        Map<Long, AuthorLook> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Instant now = clock.instant();
        Map<Long, String[]> decorations = new HashMap<>();
        for (EquippedRow row : userItems.equippedOf(ids)) {
            if (!row.liveAt(now)) {
                continue;
            }
            String[] pair = decorations.computeIfAbsent(row.userId(), id -> new String[2]);
            if (row.slot() == EquipSlot.NICKNAME_COLOR) {
                pair[0] = row.color();
            } else {
                pair[1] = row.borderStyle();
            }
        }
        Map<Long, String> images = members.profileImages(ids);
        for (Long id : ids) {
            String[] pair = decorations.get(id);
            AuthorDecorationView decoration = pair == null || pair[0] == null && pair[1] == null ? null : new AuthorDecorationView(pair[0], pair[1]);
            out.put(id, new AuthorLook(decoration, avatars.urlOf(images.get(id))));
        }
        return out;
    }

    /** 한 명 */
    public AuthorLook look(long userId) {
        return resolve(Set.of(userId)).get(userId);
    }
}
