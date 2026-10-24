package com.community.debate.entity;

import java.util.Locale;

/** 하이라이트 토론의 입장 — 댓글 배지({@code comments.stance})와 회원의 글별 현재 입장({@code post_stances.stance}). V78 CHECK 와 같은 값 */
public enum DebateStance {
    /** 찬성 */
    PRO,
    /** 반대 */
    CON,
    /** 중립(V78) — 하이라이트의 「중립도 받기」가 꺼져 있으면 새로 고를 수 없다 */
    NEUTRAL;

    /** 표기 해석(대소문자·앞뒤 공백 무시). 비었으면 null, 모르는 값이면 {@link IllegalArgumentException} */
    public static DebateStance parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return valueOf(raw.strip().toUpperCase(Locale.ROOT));
    }

    /** 저장 값 → 입장(null 이면 null) */
    public static DebateStance ofNullable(String stored) {
        return stored == null ? null : valueOf(stored);
    }
}
