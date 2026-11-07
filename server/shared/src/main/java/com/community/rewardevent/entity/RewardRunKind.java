package com.community.rewardevent.entity;

/** 지급 실행 종류 — 운영자 지급 · 실패만 다시 · 회수 · 선착순 자동(날마다 한 줄) */
public enum RewardRunKind {
    GRANT, RETRY, REVOKE, AUTO
}
