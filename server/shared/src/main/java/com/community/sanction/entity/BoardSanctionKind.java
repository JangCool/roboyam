package com.community.sanction.entity;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 게시판 제재 단계(docs/features/board-sanction-contract.md 결정 2) — 경고 · 쓰기 제한 1일 · 3일 · 7일. 처리자가 자유롭게 고르고, 권장 단계({@link #recommended})는 안내일 뿐 막지 않는다.
 * 값은 표 {@code board_sanctions.kind}(V137 CHECK)와 같고 한 번 정하면 바꾸지 않는다.
 */
public enum BoardSanctionKind {

    WARNING(null),
    RESTRICT_1(Duration.ofDays(1)),
    RESTRICT_3(Duration.ofDays(3)),
    RESTRICT_7(Duration.ofDays(7));

    /** 권장 단계의 차례 — 유효한(VOIDED 아닌) 제재 수 n 이면 n 번째(그 뒤는 마지막) */
    public static final List<BoardSanctionKind> LADDER = List.of(values());

    private final Duration length;

    BoardSanctionKind(Duration length) {
        this.length = length;
    }

    /** 쓰기 제한의 길이 — 경고는 null */
    public Duration length() {
        return length;
    }

    public boolean isWarning() {
        return this == WARNING;
    }

    /** 그 게시판의 새 글·댓글을 막는 제재(쓰기 제한) */
    public boolean restricts() {
        return this != WARNING;
    }

    /** 권장 단계 — 그 게시판의 유효한 제재 수(경고 + 쓰기 제한, 해제된 것 포함 · 무효 제외)로 경고 → 1일 → 3일 → 7일, 그 뒤 7일(user-web {@code UtilBoardSanction.recommendedOf}) */
    public static BoardSanctionKind recommended(long validCount) {
        int n = (int) Math.max(0, Math.min(validCount, LADDER.size() - 1));
        return LADDER.get(n);
    }

    public static Optional<BoardSanctionKind> of(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String code = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(kind -> kind.name().equals(code)).findFirst();
    }
}
