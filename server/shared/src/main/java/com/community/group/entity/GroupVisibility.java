package com.community.group.entity;

/** 공개 범위 — 공개 소모임(비멤버도 글을 읽는다) · 비공개 소모임(기본). 범위 변경·공개 요청은 5단계(api-groups.md 11절) */
public enum GroupVisibility {
    PUBLIC,
    PRIVATE;

    /** 글자 → 값(앞뒤 공백 무시, 대소문자 그대로) — 두 값이 아니면 null */
    public static GroupVisibility parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        for (GroupVisibility v : values()) {
            if (v.name().equals(text)) {
                return v;
            }
        }
        return null;
    }
}
