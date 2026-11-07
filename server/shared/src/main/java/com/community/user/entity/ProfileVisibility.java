package com.community.user.entity;

/**
 * 회원 프로필 공개 범위 — {@code profiles.profile_visibility} 의 CHECK 와 같은 값이다(V160). docs/features/profile-privacy-contract.md 「정책」.
 */
public enum ProfileVisibility {
    /** 다 보인다(기본값) */
    PUBLIC,
    /** 본인이 아닌 사람에게 한 줄 소개·가입일·활동 수치·작성글·댓글·소모임 목록·글 보기의 작성자 소개를 숨긴다 */
    PRIVATE
}
