package com.community.sanction.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 게시판 제재 사유 코드(docs/features/board-sanction-contract.md 「규칙 > 사유」) — 스팸·광고 · 비방·인신공격 · 투자 권유 · 게시판 주제와 맞지 않음 · 기타(덧붙임 5자 이상 필수).
 * 값은 표 {@code board_sanctions.reason_code}(V137 CHECK)와 같다. 이름은 번들 {@code member.boardSanction.reason.<코드>}.
 */
public enum BoardSanctionReason {

    SPAM,
    ABUSE,
    INVESTMENT,
    OFF_TOPIC,
    OTHER;

    public static Optional<BoardSanctionReason> of(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String code = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(reason -> reason.name().equals(code)).findFirst();
    }
}
