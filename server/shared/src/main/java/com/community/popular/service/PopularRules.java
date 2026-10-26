package com.community.popular.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardGadget;
import com.community.board.service.BoardFields;
import java.util.List;
import java.util.Locale;

/**
 * 인기글의 고정 값 — 기간 · 주 시작 요일 · 뺀 사유. 숫자 규칙 값(가중치·감쇠·최소값·한 사람 최대)은 설정 표({@code popular_settings}, V74)이고
 * 그 범위는 관리자 {@code PopularSettingsService} 가 정한다(DB CHECK 와 같다).
 */
public final class PopularRules {

    public static final String REALTIME = "REALTIME";
    public static final String YESTERDAY = "YESTERDAY";
    public static final String WEEK = "WEEK";
    /** 기간 탭 — 화면 차례 */
    public static final List<String> PERIODS = List.of(REALTIME, YESTERDAY, WEEK);

    public static final String MONDAY = "MONDAY";
    public static final String SUNDAY = "SUNDAY";
    public static final List<String> WEEK_STARTS = List.of(MONDAY, SUNDAY);

    /** 인기글에서 뺀 사유(V74 CHECK 와 같다) — 화면 차례. OTHER 면 메모 필수 */
    public static final List<String> EXCLUSION_REASONS = List.of("INAPPROPRIATE", "PROMOTION", "MANIPULATION", "DISPUTE", "OTHER");
    public static final String REASON_OTHER = "OTHER";
    /** 뺀 사유 메모 최대 글자 수 */
    public static final int EXCLUSION_MEMO_MAX = 100;

    private PopularRules() {
    }

    /** 기간 표기(대소문자·앞뒤 공백 무시). 비어 있으면 실시간, 모르는 값이면 null */
    public static String period(String raw) {
        if (raw == null || raw.isBlank()) {
            return REALTIME;
        }
        String period = raw.strip().toUpperCase(Locale.ROOT);
        return PERIODS.contains(period) ? period : null;
    }

    /**
     * 인기글에 오를 수 있는 게시판인가 — 켜져 있고 「가젯 노출 금지」에 인기글(POPULAR)이 없으며, 설정 「운영진 전용 게시판 제외」가 켜져 있으면 운영진 전용
     * (유효 글쓰기 자격 STAFF)이 아니다. 소모임 게시판(종류 GROUP)의 글은 오르지 않는다(소모임 2단계 — 공개로 전환된 소모임 글을 섞는 일은 6단계 「최신글 소모임 배지」와 함께).
     * DB 배치의 후보 조건(R__batch_24 {@code popular_rank_candidates} — {@code p.group_id IS NULL})과 같은 판정 — 한쪽을 고치면 다른 쪽도 고친다.
     *
     * @param board 게시판 사본(없는 게시판이면 null → false)
     */
    public static boolean rankableBoard(EffectiveBoard board, boolean excludeStaffBoards) {
        return board != null && board.active() && !BoardFields.KIND_GROUP.equals(board.kind()) && !board.excludedFrom(BoardGadget.POPULAR)
                && !(excludeStaffBoards && BoardFields.STAFF.equals(board.writeMin()));
    }
}
