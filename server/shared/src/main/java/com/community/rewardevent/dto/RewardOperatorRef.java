package com.community.rewardevent.dto;

/** 운영자 한 명(admin-web {@code RewardEventOperatorRef}) — 이름은 지금 운영자 이름(지운 운영자면 null) */
public record RewardOperatorRef(String id, String name) {
}
