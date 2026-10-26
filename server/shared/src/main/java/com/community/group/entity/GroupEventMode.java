package com.community.group.entity;

import java.util.Arrays;
import java.util.Optional;

/** 일정의 진행 방식 — 온라인(모임 링크는 채팅 탭) · 오프라인(장소 필수, 개인 책임 고지) */
public enum GroupEventMode {
    ONLINE,
    OFFLINE;

    public static Optional<GroupEventMode> of(String raw) {
        String value = raw == null ? "" : raw.strip();
        return Arrays.stream(values()).filter(m -> m.name().equals(value)).findFirst();
    }
}
