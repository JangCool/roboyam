package com.community.rewardevent.dto;

/** 회원 한 명(admin-web {@code RewardMemberRef}) — 이메일은 {@code member.pii} 가 없으면 가려 준다(관리자 서버가 가리개를 넘긴다) */
public record RewardMemberRef(String id, String nickname, String email, boolean withdrawn) {
}
