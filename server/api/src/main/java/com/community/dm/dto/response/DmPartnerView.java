package com.community.dm.dto.response;

import com.community.pointshop.dto.response.AuthorDecorationView;

/**
 * 대화 상대 — user-web {@code DmPartner}. 탈퇴한 회원은 {@code id: null}·{@code withdrawn: true}·「탈퇴한 회원」. {@code memo} 는 내가 남긴 메모.
 * {@code decoration}(장착한 닉네임 색·프로필 테두리 — 만료는 빠진다, 없으면 null)·{@code avatarUrl}(프로필 아바타 — 늘 채운다, 탈퇴는 null)은 포인트 상점 표시(커머스 1-2).
 * {@code business} 는 사업자 뱃지(사업자 신원 {@code APPROVED} — 재심사 중·반려·정지·탈퇴는 false). 소모임 화면의 사람 칸도 같은 모양이다({@code GroupUsers}).
 */
public record DmPartnerView(String id, String nickname, String grade, String gradeLabel, String role, boolean withdrawn, String memo, AuthorDecorationView decoration,
                            String avatarUrl, boolean business) {
}
