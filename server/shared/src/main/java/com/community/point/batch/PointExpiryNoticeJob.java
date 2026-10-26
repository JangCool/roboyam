package com.community.point.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchParamDefinition;
import com.community.batch.core.BatchParamType;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.repository.master.PointLotBatchQueries;
import com.community.point.repository.master.PointLotBatchQueries.ExpiringSummary;
import com.community.point.repository.master.PointLotBatchQueries.NoticedLot;
import com.community.point.service.PointPolicy;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 포인트 소멸 예정 알림 Java 배치 {@code point-expiry-notice}(docs/domain/commerce.md 14 「포인트 소멸 예정(30일 전) — 회원 앱」, 15-5 「소멸 예정 알림 — Java 배치, 매일」).
 *
 * <ul>
 *   <li>대상: 무상이고 남은 양이 있고 소멸일이 <b>지금부터 {@code point_settings.expiry_notice_days} 일 안</b>({@code 지금 < expires_at ≤ 지금 + 일수})이며 아직 알리지 않은
 *       ({@code expiry_noticed_at IS NULL}) 묶음이 있는 회원 — 회원 ID 키셋으로 덩어리(작업별 {@code chunk_size}, 기본 500명). 일수가 0 이면 알림이 꺼져 아무것도 하지 않는다(SKIP).</li>
 *   <li>회원마다 <b>한 트랜잭션(덩어리)</b>에서: 그 묶음들에 알린 시각을 조건부로 적고(같은 묶음으로 두 번 알리지 않는다 — 처음부터 다시 돌려도 안전) 새로 적은 묶음이 있으면 회원 알림
 *       {@code POINTS_EXPIRING} <b>한 건</b> — 그 회원의 알림 기간 안 무상 묶음 전부(이미 알린 것 포함)의 <b>가장 가까운 소멸일</b>(한국 날짜)과 <b>소멸 예정 합</b>, 일수.</li>
 *   <li><b>회원당 최소 간격</b>(사용자 확정 2026-10-26): {@code point_settings.expiry_notice_interval_days}(기본 7, 0 = 없음) 안에 이미 알림을 받은 회원은 고르지 않는다 — 그 회원의 새 묶음은
 *       표시하지 않고 남겨 간격이 지난 뒤의 알림에 함께 담는다. 간격은 <b>한국 날짜</b>로 센다(7 이면 1일에 받은 회원은 8일에 다시 받는다 — 실행 시각이 몇 초 늦어도 하루 밀리지 않게).
 *       알림 기간보다 길면 묶음이 알림 없이 소멸할 수 있어 min(간격, 알림 일수 − 1)을 쓴다. 마지막 알림 시각은 {@code point_balances.expiry_notice_sent_at} — 조건부로 적어
 *       같은 때 도는 실행이 겹쳐도 한 번만 알린다.</li>
 *   <li>회원 알림 규칙은 발행기({@link MemberNotificationPublisher#pointsExpiring})가 지킨다: 회원 이벤트 설정(사용·앱 알림·이메일 — 관리자 이벤트 › 회원), 탈퇴·삭제 회원은 받지 않음
 *       (정지·동결 회원은 받는다 — 포인트는 그대로 소멸하므로). 꺼져 있거나 받을 수 없는 회원이어도 묶음은 알린 것으로 적는다(다음 날 다시 고르지 않게).</li>
 *   <li>알림은 발행기의 세이브포인트 안에서 만든다 — 알림 행을 못 만들어도 덩어리는 깨지지 않는다(그 회원 묶음은 알린 것으로 남고 ERROR 로그). 덩어리 자체가 실패하면 회원 하나씩 다시
 *       해 실패한 회원만 남긴다(대상 {@code MEMBER}, 회원 ID).</li>
 *   <li>파라미터 {@code userId}(선택) — 그 회원만.</li>
 * </ul>
 */
@Component
public class PointExpiryNoticeJob implements BatchJob {

    /** 실패 건의 대상 종류 — 회원 */
    public static final String TARGET = "MEMBER";
    public static final String PARAM_USER = "userId";

    private final PointLotBatchQueries queries;
    private final PointPolicy policy;
    private final MemberNotificationPublisher publisher;
    private final Clock clock;

    public PointExpiryNoticeJob(PointLotBatchQueries queries, PointPolicy policy, MemberNotificationPublisher publisher, ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.policy = policy;
        this.publisher = publisher;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.POINT_EXPIRY_NOTICE;
    }

    @Override
    public List<BatchParamDefinition> params() {
        return List.of(new BatchParamDefinition(PARAM_USER, BatchParamType.LONG, false));
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        int days = policy.lots().expiryNoticeDays();
        if (days <= 0) {
            step.record(0, 0, 0);
            return BatchResult.of(new MessageRef("batch.result.pointExpiryNotice.off"));
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Window window = new Window(now, now.plus(Duration.ofDays(days)), days, restSince(now, Math.min(policy.lots().expiryNoticeIntervalDays(), days - 1)));
        Long userId = context.parameters().longValue(PARAM_USER).orElse(null);
        step.chunks(source(window, userId), processor(window));
        long notified = step.processedCount();
        return BatchResult.of(notified == 0 ? new MessageRef("batch.result.pointExpiryNotice.none", String.valueOf(days))
                : new MessageRef("batch.result.pointExpiryNotice", String.valueOf(notified), String.valueOf(days)));
    }

    /** 알림 기간 — {@code from < expires_at ≤ until}. {@code restSince} 이후에 알림을 받은 회원은 쉰다(null = 간격 없음) */
    record Window(Instant from, Instant until, int days, Instant restSince) {
    }

    /** 간격 {@code interval} 일 — 오늘(한국 날짜)에서 {@code interval − 1} 일 앞 0시. 그 뒤에 받았으면 아직 쉰다. 0 이하면 간격 없음 */
    static Instant restSince(Instant now, int interval) {
        if (interval <= 0) {
            return null;
        }
        return now.atZone(ServiceTime.ZONE).toLocalDate().minusDays(interval - 1L).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    ChunkSource<Long> source(Window window, Long userId) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<Long> next(String afterKey, int size) {
                return queries.noticeMembers(window.from(), window.until(), window.restSince(), afterKey == null ? null : Long.valueOf(afterKey), userId, size);
            }

            @Override
            public List<Long> byIds(List<String> targetIds) {
                return queries.noticeMembersByIds(window.from(), window.until(), window.restSince(), targetIds.stream().map(Long::valueOf).toList());
            }

            @Override
            public String keyOf(Long item) {
                return Long.toString(item);
            }

            @Override
            public String targetIdOf(Long item) {
                return Long.toString(item);
            }
        };
    }

    /** 회원마다 묶음 표시 → (새로 표시한 묶음이 있으면) 알림 한 건. 처리 건수 = 새로 표시한 회원 수 */
    ChunkProcessor<Long> processor(Window window) {
        return members -> {
            long handled = 0;
            for (Long member : members) {
                List<NoticedLot> marked = queries.markNoticed(member, window.from(), window.until(), window.from());
                if (marked.isEmpty()) {
                    continue; // 고른 뒤 다른 실행이 이미 알렸거나 묶음이 줄었다
                }
                if (!queries.claimNotice(member, window.from(), window.restSince())) {
                    continue; // 고른 뒤 다른 실행이 먼저 알렸다 — 방금 표시한 묶음은 그 알림의 합에 들어 있다
                }
                Optional<ExpiringSummary> summary = queries.expiringSummary(member, window.from(), window.until());
                if (summary.isPresent() && summary.get().amount() > 0) {
                    publisher.pointsExpiring(member, summary.get().amount(), summary.get().firstExpiresAt().atZone(ServiceTime.ZONE).toLocalDate(), window.days());
                }
                handled++;
            }
            return handled;
        };
    }
}
