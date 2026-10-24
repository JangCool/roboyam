package com.community.dm.dto.response;

/** 대화 상대 — user-web {@code DmPartner}. 탈퇴한 회원은 {@code id: null}·{@code withdrawn: true}·「탈퇴한 회원」. {@code memo} 는 내가 남긴 메모 */
public record DmPartnerView(String id, String nickname, String grade, String gradeLabel, String role, boolean withdrawn, String memo) {
}
