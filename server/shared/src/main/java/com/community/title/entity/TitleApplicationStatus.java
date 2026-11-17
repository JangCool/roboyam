package com.community.title.entity;

import java.util.Optional;

/** 칭호 신청 상태({@code title_applications.status}) */
public enum TitleApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public static Optional<TitleApplicationStatus> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (TitleApplicationStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.strip())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
