package com.community.dm.dto.response;

import com.community.pointshop.dto.response.AuthorDecorationView;

/**
 * 대화 상대 — user-web {@code DmPartner}. 탈퇴한 회원은 {@code id: null}·{@code withdrawn: true}·「탈퇴한 회원」. {@code memo} 는 내가 남긴 메모.
 * {@code decoration}(장착한 닉네임 색·프로필 테두리 — 만료는 빠진다, 없으면 null)·{@code avatarUrl}(프로필 아바타 — 늘 채운다, 탈퇴는 null)은 포인트 상점 표시(커머스 1-2).
 */
public record DmPartnerView(String id, String nickname, String grade, String gradeLabel, String role, boolean withdrawn, String memo, AuthorDecorationView decoration,
                            String avatarUrl) {
}
