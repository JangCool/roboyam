package com.community.sanction.service;

import com.community.sanction.entity.SanctionKind;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 제재 단계 계산(정책 1·2) — 한 회원의 제재 전부와 지금 시각만으로 정한다(메모리 상태 없음, 부르는 쪽이 회원 행을 잠갔으면 그 아래에서 정확하다).
 *
 * <ul>
 *   <li>유효 = VOIDED 아님. 경고 수 {@code w} = 유효 경고 수, 최고 정지 단계 = 유효한 정지·영구 정지(진행 중·끝남·해제 모두)의 가장 높은 단계.</li>
 *   <li>다음 단계: 진행 중 영구 정지면 없음 · 정지가 없으면 {@code w < 2} 경고, 아니면 정지 3일 · 3일 → 7일 → 30일 → 영구 정지(영구 정지가 해제됐으면 다시 영구 정지).</li>
 *   <li>막힘: 진행 중 영구 정지 {@code ALREADY_BANNED} · 진행 중 기간 정지 {@code SUSPENDED}(영구 정지만 된다).</li>
 *   <li>선택지 상태: 막힘이 영구 정지거나 다음이 없으면 BLOCKED · 정지 중이면 영구 정지만(다음이 영구 정지면 NEXT, 아니면 SKIP), 그 밖 BLOCKED ·
 *       다음보다 낮으면 LOWER · 다음이면 NEXT · 높으면 SKIP. {@code requiresBan} = 영구 정지이거나 SKIP.</li>
 * </ul>
 * 화면 목업({@code sanction-mock.ts} {@code previewOf})과 같은 계산이다.
 */
public final class SanctionLadder {

    public static final String BLOCKED_ALREADY_BANNED = "ALREADY_BANNED";
    public static final String BLOCKED_SUSPENDED = "SUSPENDED";

    public static final String NEXT = "NEXT";
    public static final String SKIP = "SKIP";
    public static final String LOWER = "LOWER";
    public static final String BLOCKED = "BLOCKED";

    /** 유효한 경고 수의 위 끝 */
    public static final int WARNING_LIMIT = 2;

    public record Option(SanctionKind kind, int rank, String state, boolean requiresBan) {
    }

    /**
     * 회원 하나의 제재 현황.
     *
     * @param warningCount 유효한 경고 수 0~2
     * @param next         다음 단계(없으면 null) · {@code nextRank} 그 순위
     * @param running      진행 중인 정지·영구 정지(없으면 null)
     */
    public record Standing(int warningCount, String blockedReason, SanctionKind next, int nextRank, List<Option> options, SanctionRow running) {

        public Option option(SanctionKind kind) {
            return options.stream().filter(o -> o.kind() == kind).findFirst().orElseThrow();
        }
    }

    private SanctionLadder() {
    }

    public static Standing standing(List<SanctionRow> rows, Instant now) {
        List<SanctionRow> valid = rows.stream().filter(SanctionRow::valid).toList();
        int warnings = (int) valid.stream().filter(r -> r.kind().isWarning()).count();
        int top = valid.stream().filter(r -> r.kind().restricts()).mapToInt(r -> r.kind().step()).max().orElse(0);
        SanctionRow running = valid.stream().filter(r -> r.runningAt(now))
                .max(Comparator.comparing(SanctionRow::createdAt).thenComparingLong(SanctionRow::id)).orElse(null);
        boolean banned = running != null && running.kind() == SanctionKind.BAN;

        SanctionKind next = null;
        int nextRank = 0;
        if (!banned) {
            if (top == 0) {
                next = warnings < WARNING_LIMIT ? SanctionKind.WARNING : SanctionKind.SUSPEND_3;
                nextRank = next == SanctionKind.WARNING ? warnings + 1 : next.rank(null);
            } else {
                next = SanctionKind.LADDER.get(Math.min(top + 1, SanctionKind.LADDER.size() - 1));
                nextRank = next.rank(null);
            }
        }
        String blocked = banned ? BLOCKED_ALREADY_BANNED : running != null ? BLOCKED_SUSPENDED : null;

        List<Option> options = new ArrayList<>();
        for (SanctionKind kind : SanctionKind.LADDER) {
            int rank = kind.isWarning() ? Math.min(warnings + 1, WARNING_LIMIT) : kind.rank(null);
            String state;
            if (BLOCKED_ALREADY_BANNED.equals(blocked) || next == null) {
                state = BLOCKED;
            } else if (BLOCKED_SUSPENDED.equals(blocked)) {
                state = kind == SanctionKind.BAN ? (next == SanctionKind.BAN ? NEXT : SKIP) : BLOCKED;
            } else if (kind.isWarning() ? next != SanctionKind.WARNING : rank < nextRank) {
                state = LOWER;
            } else if (kind == next) {
                state = NEXT;
            } else {
                state = SKIP;
            }
            options.add(new Option(kind, rank, state, kind == SanctionKind.BAN || SKIP.equals(state)));
        }
        return new Standing(Math.min(WARNING_LIMIT, warnings), blocked, next, nextRank, List.copyOf(options), running);
    }
}
