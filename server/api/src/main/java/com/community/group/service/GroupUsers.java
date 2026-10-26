package com.community.group.service;

import com.community.dm.dto.response.DmPartnerView;
import com.community.group.dto.response.GroupPersonView;

/**
 * 공개 사이트 소모임 응답의 사람 칸 — 로보얌 쪽지 상대({@link DmPartnerView}, user-web {@code GroupUser})와 같은 모양으로 바꾼다
 * ({@code id|null · nickname · grade(키) · gradeLabel · role(USER) · withdrawn · memo(늘 null) · decoration · avatarUrl}). 탈퇴한 회원은 {@code id: null}·{@code withdrawn: true}·「탈퇴한 회원」.
 */
public final class GroupUsers {

    static final String ROLE_USER = "USER";

    private GroupUsers() {
    }

    public static DmPartnerView of(GroupPersonView person) {
        if (person == null) {
            return null;
        }
        if (person.withdrawn()) {
            return new DmPartnerView(null, person.nickname(), null, null, ROLE_USER, true, null, null, null);
        }
        return new DmPartnerView(person.id(), person.nickname(), person.grade() == null ? null : person.grade().key(),
                person.grade() == null ? null : person.grade().label(), ROLE_USER, false, null, person.decoration(), person.avatarUrl());
    }
}
