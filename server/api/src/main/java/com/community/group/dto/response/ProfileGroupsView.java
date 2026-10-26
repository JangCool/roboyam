package com.community.group.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/users/{id}/groups} — 회원 프로필의 「소모임」 탭(community api-profile.md 「소모임 (6-4)」). 그 회원이 지금 멤버인 열린(ACTIVE) 소모임을
 * 가입이 최근인 순으로 최대 50개(더보기 없음).
 */
public record ProfileGroupsView(List<Row> items) {

    /**
     * 한 줄 — 소모임 목록 줄({@link GroupRowView}) 칸을 펼친 것 + <b>프로필 주인의</b> 역할·가입 시각. {@code myState} 는 <b>보는 사람</b>의 상태다.
     *
     * @param role     프로필 주인의 역할 LEADER · MANAGER · MEMBER(원본은 LEADER · MEMBER — 로보얌은 매니저를 가른다, 화면은 LEADER 만 「모임장」으로 표시)
     * @param joinedAt 프로필 주인이 가입한 시각(모임장은 개설 시각)
     */
    public record Row(@JsonUnwrapped GroupRowView group, String role, Instant joinedAt) {
    }
}
