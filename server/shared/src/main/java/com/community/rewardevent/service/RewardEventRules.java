package com.community.rewardevent.service;

import com.community.rewardevent.entity.RewardEditScope;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardKind;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 보상 이벤트 규칙 한 곳(docs/features/event-admin-contract.md 「상태·방식」·「대상 규칙」·「보상 줄」) — 상태·범위·한도·추첨·고액 판정. 상태가 없는 계산만 둔다.
 */
public final class RewardEventRules {

    public static final int NAME_MAX = 60;
    public static final int MEMO_MAX = 500;
    public static final int NOTICE_URL_MAX = 500;
    public static final int TIER_NAME_MAX = 20;
    public static final int TIERS_MAX = 20;
    public static final int LINES_MAX = 10;
    public static final int QUOTA_MAX = 100_000;
    public static final int POINTS_MAX = 100_000;
    public static final int QUANTITY_MAX = 10;
    public static final int MANUAL_NAME_MAX = 40;
    public static final int MANUAL_METHOD_MAX = 200;
    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 200;
    public static final int DELIVERED_MEMO_MAX = 200;
    public static final int ACTIVE_DAYS_MAX = 365;
    public static final int POST_REF_MAX = 300;
    public static final int IDEMPOTENCY_KEY_MAX = 100;
    /** 붙여 넣기·추가·회수 한 번에 */
    public static final int BATCH_MAX = 500;
    public static final int MEMBER_SEARCH_LIMIT = 20;
    public static final int PAGE_SIZE_MAX = 100;

    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,19}");

    private RewardEventRules() {
    }

    /** 읽을 때의 상태 — 끝 시각이 지난 진행 중은 마감(배치 없음 — 다음 쓰기 때 고정한다) */
    public static RewardEventStatus effectiveStatus(RewardEventEntity event, Instant now) {
        return effectiveStatus(event.getStatus(), event.getEndAt(), now);
    }

    public static RewardEventStatus effectiveStatus(RewardEventStatus status, Instant endAt, Instant now) {
        return status == RewardEventStatus.ACTIVE && !endAt.isAfter(now) ? RewardEventStatus.CLOSED : status;
    }

    /** 고치기 범위 — 준비 FULL · 진행 중 ACTIVE · 마감 CLOSED · 지급 끝·취소 MEMO */
    public static RewardEditScope editScope(RewardEventStatus status) {
        return switch (status) {
            case DRAFT -> RewardEditScope.FULL;
            case ACTIVE -> RewardEditScope.ACTIVE;
            case CLOSED -> RewardEditScope.CLOSED;
            default -> RewardEditScope.MEMO;
        };
    }

    /** 준비·진행 중·마감(아직 열린 이벤트) */
    public static boolean open(RewardEventStatus status) {
        return status == RewardEventStatus.DRAFT || status == RewardEventStatus.ACTIVE || status == RewardEventStatus.CLOSED;
    }

    /**
     * 글 주소 또는 번호 → 글 번호. 숫자만이면 그대로, 주소면 경로·쿼리에서 마지막 숫자 묶음(1~19자리)을 쓴다({@code /boards/free/123?x=1} → 123).
     */
    public static Optional<Long> parsePostRef(String ref) {
        if (ref == null) {
            return Optional.empty();
        }
        String v = ref.strip();
        if (v.isEmpty() || v.length() > POST_REF_MAX) {
            return Optional.empty();
        }
        Matcher m = DIGITS.matcher(v);
        String last = null;
        while (m.find()) {
            boolean boundedLeft = m.start() == 0 || !Character.isDigit(v.charAt(m.start() - 1));
            boolean boundedRight = m.end() == v.length() || !Character.isDigit(v.charAt(m.end()));
            if (boundedLeft && boundedRight) {
                last = m.group();
            }
        }
        if (last == null) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(last);
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 문자열 번호 → long(형식이 틀리면 빈 값) */
    public static Optional<Long> parseId(String text) {
        if (text == null || !DIGITS.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 등수 하나의 1인 포인트 합 */
    public static long pointsOf(Collection<LineRow> tierLines) {
        return tierLines.stream().filter(l -> l.kind() == RewardKind.POINT).mapToLong(l -> l.points() == null ? 0 : l.points()).sum();
    }

    /**
     * 고액 권한({@code point.adjust.large})이 필요한가 — 1인 포인트 합(등수 중 가장 큰 것)이 문턱을 넘거나, 일괄 방식에 포인트 보상이 있다(event-admin-contract 「보상 줄」).
     *
     * @param pointsByTier 등수마다 1인 포인트 합(대상이 있는 등수만 넘긴다)
     */
    public static boolean largeRequired(RewardEventMode mode, Collection<Long> pointsByTier, int threshold) {
        long max = pointsByTier.stream().mapToLong(Long::longValue).max().orElse(0);
        if (mode == RewardEventMode.BULK && max > 0) {
            return true;
        }
        return max > threshold;
    }

    /** 등수별 1인 포인트 합 */
    public static Map<Long, Long> pointsByTier(List<LineRow> lines) {
        Map<Long, Long> out = new java.util.LinkedHashMap<>();
        for (LineRow l : lines) {
            out.merge(l.tierId(), l.kind() == RewardKind.POINT && l.points() != null ? (long) l.points() : 0L, Long::sum);
        }
        return out;
    }

    /**
     * 추첨 — 회원 번호 오름차순 후보를 추첨 번호(seed)의 난수로 섞어 앞에서 {@code count} 명. 같은 후보와 번호면 같은 결과다({@link Random} 의 선형 합동 생성기와
     * {@link Collections#shuffle(List, Random)} 의 차례는 자바 명세가 정한다).
     */
    public static List<Long> draw(List<Long> candidatesAscending, long seed, int count) {
        List<Long> order = new ArrayList<>(candidatesAscending);
        Collections.sort(order);
        Collections.shuffle(order, new Random(seed));
        return List.copyOf(order.subList(0, Math.min(count, order.size())));
    }

    /** 추첨 번호 표시 — 16자리 16진수 */
    public static String seedText(long seed) {
        return String.format("%016x", seed);
    }

    /** 공백을 떼고 연속 공백을 한 칸으로 */
    public static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    public static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }
}
