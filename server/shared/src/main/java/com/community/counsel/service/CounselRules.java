package com.community.counsel.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.zconfiguration.time.ServiceTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * AI 연애상담소 고정 규칙(ai-love-counsel-contract 「정책」·「결정」) — 공개 서버(글쓰기 상한 · 상담 칸 · 순위)와 관리자 서버(실행기 · 설정)가 같이 본다.
 */
public final class CounselRules {

    /** 답 시한 — 사연 등록 + 10분(공개 응답 {@code answerDueAt}) */
    public static final Duration ANSWER_DUE = Duration.ofMinutes(10);
    /** 이만큼 지나도 답하지 못한 상담사는 그만둔다(실행기가 더 부르지 않고, 상담 칸에서 빠진다) */
    public static final Duration GIVE_UP = Duration.ofMinutes(60);
    /** 실행기가 새 사연을 찾는 범위(글을 쓴 뒤) — 이보다 오래된 상담 글은 판정하지 않는다 */
    public static final Duration TRIAGE_LOOKBACK = Duration.ofHours(24);
    /** AI 분류 시도 상한 — 넘으면 낱말 목록만으로 통과시킨다(상담 프롬프트에도 위기 대응 지침이 있다) */
    public static final int RISK_CHECK_MAX = 3;
    /** 상담사 하나가 한 사연에 답을 만들어 보는 횟수 상한(실패 포함) */
    public static final int ANSWER_ATTEMPTS = 2;
    /** 상담 성향 한 마디 최대 글자(V274 {@code counsel_style varchar(20)}) */
    public static final int STYLE_MAX = 20;
    /** 상담사 수 상한(관리자 설정) — 화면은 셋을 가정한다 */
    public static final int COUNSELORS_MAX = 5;
    /** 하루 상한 범위(V274 CHECK) */
    public static final int MEMBER_DAILY_MAX = 10;
    public static final int SITE_DAILY_MAX = 5000;

    /**
     * 1차 위험 낱말 목록(즉시 판정 — 공백을 걷고 소문자로 맞춘 글에서 찾는다). 자살·자해 · 폭력·협박·스토킹 · 성폭력·불법 촬영. 걸리면 AI 답을 만들지 않고 도움 안내(109·1366)만 보인다.
     * 판정이 넉넉하게 걸리는 편이 안전하다(놓치는 것보다 낫다) — 2차는 AI 분류.
     */
    static final List<String> RISK_WORDS = List.of(
            "자살", "죽고싶", "죽고만싶", "죽어버리고", "죽어버릴", "죽을래", "살기싫", "살고싶지않", "사라지고싶", "극단적선택", "목숨을끊", "목숨끊", "스스로목숨",
            "자해", "손목을긋", "손목긋", "칼로긋", "투신", "뛰어내리", "목을매", "목매", "유서", "수면제를모아", "약을모아",
            "데이트폭력", "가정폭력", "폭행", "때려요", "때렸어", "때렸다", "맞았어요", "맞았다", "주먹으로", "목을졸", "목졸", "죽이겠다", "죽여버리", "협박",
            "감금", "스토킹", "스토커", "따라다녀", "성폭력", "성폭행", "강간", "추행", "불법촬영", "몰카", "몰래찍", "리벤지", "유포하겠", "뿌리겠다");

    private CounselRules() {
    }

    /** 1차 위험 낱말 — 걸린 낱말(없으면 비어 있다) */
    public static Optional<String> riskWord(String... texts) {
        StringBuilder all = new StringBuilder();
        for (String t : texts) {
            if (t != null) {
                all.append(t).append(' ');
            }
        }
        String hay = all.toString().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return RISK_WORDS.stream().filter(hay::contains).findFirst();
    }

    /** 사이트의 「AI 상담소」 말머리 ID 전부(꺼진 말머리 포함 — 예전 글도 상담 글이다). 소모임 게시판은 뺀다 */
    public static Set<Long> counselPrefixIds(BoardCatalogSnapshot s) {
        Set<Long> out = new HashSet<>();
        for (EffectiveBoard b : s.boards()) {
            for (BoardPrefixInfo p : b.prefixes()) {
                if (p.counsel() && !p.common()) {
                    out.add(p.id());
                }
            }
        }
        return out;
    }

    /** 그 게시판에 「AI 상담소」 말머리가 있는가(켜진 것) */
    public static boolean hasCounsel(EffectiveBoard b) {
        return b.prefixes().stream().anyMatch(p -> p.counsel() && !p.common() && p.active());
    }

    /** 그 말머리가 「AI 상담소」인가 */
    public static boolean counselPrefix(EffectiveBoard b, Long prefixId) {
        return prefixId != null && b.prefix(prefixId).map(p -> p.counsel() && !p.common()).orElse(false);
    }

    /** 그 시각이 든 주의 월요일(한국 날짜 — 월 00시 ~ 일 24시) */
    public static LocalDate weekStart(Instant at) {
        return ServiceTime.dateOf(at).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 오늘(한국 날짜) 0시 */
    public static Instant startOfDay(Instant now) {
        return ServiceTime.dateOf(now).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 큰 나머지 방식 반올림 — 합이 100(모두 0 이면 모두 0). 나머지가 같으면 앞 차례가 먼저 */
    public static int[] percents(int[] values) {
        int[] out = new int[values.length];
        long total = 0;
        for (int v : values) {
            total += Math.max(0, v);
        }
        if (total == 0) {
            return out;
        }
        double[] rest = new double[values.length];
        int sum = 0;
        for (int i = 0; i < values.length; i++) {
            double raw = Math.max(0, values[i]) * 100.0 / total;
            out[i] = (int) Math.floor(raw);
            rest[i] = raw - out[i];
            sum += out[i];
        }
        for (int left = 100 - sum; left > 0; left--) {
            int best = -1;
            for (int i = 0; i < values.length; i++) {
                if (rest[i] >= 0 && (best < 0 || rest[i] > rest[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            out[best]++;
            rest[best] = -1;
        }
        return out;
    }
}
