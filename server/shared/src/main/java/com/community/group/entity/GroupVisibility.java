package com.community.group.entity;

/** 공개 범위 — 공개 소모임(비멤버도 글을 읽는다) · 비공개 소모임(기본). 범위 변경·공개 요청은 5단계 */
public enum GroupVisibility {
    PUBLIC,
    PRIVATE
}
