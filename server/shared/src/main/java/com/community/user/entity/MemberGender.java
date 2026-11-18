package com.community.user.entity;

import java.util.Optional;

/**
 * 회원 성별 — {@code profiles.gender} 의 CHECK 와 같은 값이다(V250). 선택 항목이고 기본은 {@link #NONE}(밝히지 않음).
 * <b>다른 회원·공개 프로필에 내보내지 않는다</b> — 로그인한 본인 응답({@code /api/auth/me}·{@code /api/me/profile})에만 싣고 사이트 로고 고르기에만 쓴다
 * (docs/features/site-logo-library-contract.md 「정책」).
 */
public enum MemberGender {
    MALE,
    FEMALE,
    /** 밝히지 않음(기본값) */
    NONE;

    /** 요청의 글자 → 값. 없거나 틀리면 비어 있다 — 대소문자·공백을 봐주지 않는다 */
    public static Optional<MemberGender> of(String text) {
        for (MemberGender gender : values()) {
            if (gender.name().equals(text)) {
                return Optional.of(gender);
            }
        }
        return Optional.empty();
    }
}
