package com.community.bizapplication.entity;

/** 운영자가 광고주 대신 정지·다시 켜기·중단할 때의 요청 경로(V127 — biz-pause-contract 규칙 11) — 전화 · 메일 · 기타 */
public enum BizPauseChannel {
    PHONE, EMAIL, OTHER
}
