package com.community.notificationtemplate.catalog;

import java.util.Arrays;
import java.util.Optional;

/** 문구 대상의 종류 — 이벤트(운영자 · 회원) 또는 필수 안내 메일(이벤트가 아닌 꼭 가는 메일 — 받는 사람이 끌 수 없다) */
public enum MessageTargetType {

    EVENT,
    REQUIRED_MAIL;

    public static Optional<MessageTargetType> of(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
