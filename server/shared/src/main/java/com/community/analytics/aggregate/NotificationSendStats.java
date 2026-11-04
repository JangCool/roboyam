package com.community.analytics.aggregate;

import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.store.SqlArrays;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.annotation.PreDestroy;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 발송 결과 세기(대시보드 「알림 상태」 — docs/features/site-analytics-contract.md 「수집·쓰기」). 발송 지점(메일 {@code MailDispatcher} 경유 전부 · Slack · Mattermost
 * {@code NotificationChannelPostSender})이 한 통마다 {@link #sent}/{@link #failed} 를 부르면 메모리에 세고, <b>1분마다(그리고 종료 때) 두 문장</b>으로 메인 DB 에 몰아 쓴다.
 *
 * <ul>
 *   <li>설정 「알림 발송 기록」({@code notificationSends})이 꺼져 있으면 세지 않는다(설정은 30초 사본).</li>
 *   <li>쓰기: {@code notification_send_daily} 에 (날짜, 채널)별 더하기 upsert 한 문장 + 실패 줄 {@code notification_send_failures} 여러 줄 한 문장 — 한 트랜잭션.
 *       <b>서버가 여러 대여도 정확</b>: 한 발송은 한 서버에서만 일어나 그 서버만 센다. 더하기 upsert 는 행 잠금으로 차례가 정해져 서로 덮어쓰지 않는다.</li>
 *   <li>수신자·본문·외부 응답은 담지 않는다 — 채널 · 사유 코드({@code ChannelPostResult} 코드) · 출처(채널 도구 ID, 메일은 null)만.</li>
 *   <li>쓰기가 실패하면(트랜잭션째 되돌려졌다) 다음 번에 다시 쓰도록 메모리에 되돌린다. 실패 줄은 {@value #MAX_FAILURES}개까지만 들고 넘치면 오래된 것부터 버린다(합은 그대로 센다).</li>
 *   <li>서버가 갑자기 꺼지면 아직 쓰지 않은 최대 1분치를 잃는다(통계라 허용 — 계약).</li>
 * </ul>
 */
@Component
public class NotificationSendStats {

    private static final Logger log = LoggerFactory.getLogger(NotificationSendStats.class);

    public static final String EMAIL = "EMAIL";
    public static final String SLACK = "SLACK";
    public static final String MATTERMOST = "MATTERMOST";

    static final int MAX_FAILURES = 1000;
    private static final int SOURCE_MAX = 40;
    private static final int REASON_MAX = 40;

    private record Key(LocalDate day, String channel) {
    }

    private record Failure(long id, Instant at, String channel, String reason, String source) {
    }

    private final StatsCollectionSettings settings;
    private final SnowflakeIdGenerator ids;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private final Object lock = new Object();
    private Map<Key, long[]> counts = new LinkedHashMap<>();
    private ArrayDeque<Failure> failures = new ArrayDeque<>();
    private boolean overflowWarned;

    public NotificationSendStats(StatsCollectionSettings settings, SnowflakeIdGenerator ids, DataSource dataSource, PlatformTransactionManager transactions) {
        this.settings = settings;
        this.ids = ids;
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(transactions);
    }

    /** 한 통 보냈다 */
    public void sent(String channel) {
        record(channel, true, null, null);
    }

    /**
     * 한 통 보내지 못했다.
     *
     * @param reasonCode {@code ChannelPostResult} 의 코드(문구 {@code notification.channel.test.<코드>})
     * @param source     Slack·Mattermost 도구 ID(메일은 null)
     */
    public void failed(String channel, String reasonCode, String source) {
        record(channel, false, reasonCode, source);
    }

    private void record(String channel, boolean ok, String reasonCode, String source) {
        try {
            if (channel == null || !settings.current().notificationSends()) {
                return;
            }
            Instant now = Instant.now();
            Key key = new Key(ServiceTime.dateOf(now), channel);
            Failure failure = ok ? null : new Failure(ids.nextId(), now, channel, cut(reasonCode == null ? "ERROR" : reasonCode, REASON_MAX), cut(source, SOURCE_MAX));
            synchronized (lock) {
                long[] n = counts.computeIfAbsent(key, k -> new long[2]);
                n[ok ? 0 : 1]++;
                if (failure != null) {
                    failures.addLast(failure);
                    trimFailures();
                }
            }
        } catch (RuntimeException e) {
            // 세기가 발송을 깨면 안 된다
            log.warn("알림 발송 결과를 세지 못했다: {}", e.getClass().getSimpleName());
        }
    }

    /** 1분마다 몰아 쓴다 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flush() {
        Map<Key, long[]> takenCounts;
        ArrayDeque<Failure> takenFailures;
        synchronized (lock) {
            if (counts.isEmpty() && failures.isEmpty()) {
                return;
            }
            takenCounts = counts;
            takenFailures = failures;
            counts = new LinkedHashMap<>();
            failures = new ArrayDeque<>();
        }
        try {
            tx.executeWithoutResult(status -> {
                write(takenCounts);
                writeFailures(new ArrayList<>(takenFailures));
            });
        } catch (RuntimeException e) {
            log.warn("알림 발송 기록을 쓰지 못해 다음에 다시 쓴다: {}", e.getClass().getSimpleName());
            synchronized (lock) {
                takenCounts.forEach((k, v) -> {
                    long[] n = counts.computeIfAbsent(k, x -> new long[2]);
                    n[0] += v[0];
                    n[1] += v[1];
                });
                takenFailures.descendingIterator().forEachRemaining(failures::addFirst);
                trimFailures();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        flush();
    }

    private void write(Map<Key, long[]> taken) {
        if (taken.isEmpty()) {
            return;
        }
        List<Object> days = new ArrayList<>();
        List<Object> channels = new ArrayList<>();
        List<Object> sent = new ArrayList<>();
        List<Object> failed = new ArrayList<>();
        taken.forEach((k, v) -> {
            days.add(k.day());
            channels.add(k.channel());
            sent.add(v[0]);
            failed.add(v[1]);
        });
        // (날짜, 채널) 차례로 넣어 서버끼리 잠그는 차례를 같게 한다(교착 방지)
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO notification_send_daily (day, channel, sent, failed) "
                    + "SELECT t.day, t.channel, t.sent, t.failed FROM unnest(?::date[], ?::text[], ?::bigint[], ?::bigint[]) AS t(day, channel, sent, failed) "
                    + "ORDER BY t.day, t.channel "
                    + "ON CONFLICT (day, channel) DO UPDATE SET sent = notification_send_daily.sent + EXCLUDED.sent, "
                    + "failed = notification_send_daily.failed + EXCLUDED.failed");
            ps.setArray(1, SqlArrays.text(con, days));
            ps.setArray(2, SqlArrays.text(con, channels));
            ps.setArray(3, SqlArrays.text(con, sent));
            ps.setArray(4, SqlArrays.text(con, failed));
            return ps;
        });
    }

    private void writeFailures(List<Failure> list) {
        if (list.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO notification_send_failures (id, failed_at, channel, reason_code, source) "
                    + "SELECT * FROM unnest(?::bigint[], ?::timestamptz[], ?::text[], ?::text[], ?::text[]) ON CONFLICT (id) DO NOTHING");
            ps.setArray(1, SqlArrays.text(con, list.stream().map(Failure::id).toList()));
            ps.setArray(2, SqlArrays.text(con, list.stream().map(Failure::at).toList()));
            ps.setArray(3, SqlArrays.text(con, list.stream().map(Failure::channel).toList()));
            ps.setArray(4, SqlArrays.text(con, list.stream().map(Failure::reason).toList()));
            ps.setArray(5, SqlArrays.text(con, list.stream().map(Failure::source).toList()));
            return ps;
        });
    }

    /** lock 안에서 부른다 */
    private void trimFailures() {
        boolean dropped = false;
        while (failures.size() > MAX_FAILURES) {
            failures.pollFirst();
            dropped = true;
        }
        if (dropped && !overflowWarned) {
            overflowWarned = true;
            log.warn("알림 발송 실패 줄이 {}개를 넘어 오래된 것부터 버린다(일별 합은 그대로 센다)", MAX_FAILURES);
        }
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
