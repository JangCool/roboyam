package com.community.batch.core;

import static com.community.batch.core.BatchJdbc.ts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 짧은 주기 워커의 기록({@code worker_heartbeats}) — 실행마다 로그를 쌓지 않고 워커마다 한 줄만 고친다(docs/domain/commerce.md 15-4). 멈춤 알람(기대 주기의 3배 동안 성공 없음)은
 * 4단계에서 이 표를 읽어 만든다(결정 16 — 0단계는 기록만).
 *
 * <ul>
 *   <li>{@link #success}: 마지막 성공 시각·처리 건수. 일이 없을 때(건수 0)는 그 워커의 마지막 성공 기록 뒤 {@code roboyam.batch.heartbeat-idle-seconds}(기본 30초)가 지났을 때만
 *       쓴다 — 2초마다 도는 워커가 매번 쓰지 않게. 일을 했으면 늘 쓴다.</li>
 *   <li>{@link #failure}: 마지막 오류(가린 한 줄 — {@link BatchErrorText})와 시각. 늘 쓴다.</li>
 *   <li>기록이 실패해도 워커를 멈추지 않는다(경고 로그만) — 덧붙은 기록이다.</li>
 * </ul>
 * Native SQL 인 이유: {@code INSERT … ON CONFLICT} 는 PostgreSQL 전용이다({@link BatchJdbc}).
 */
@Component
public class WorkerHeartbeats {

    private static final Logger log = LoggerFactory.getLogger(WorkerHeartbeats.class);

    private final JdbcTemplate jdbc;
    private final int nodeId;
    private final Duration idleInterval;
    private final Clock clock;
    private final Map<String, Instant> lastSuccessWrite = new ConcurrentHashMap<>();

    @Autowired
    public WorkerHeartbeats(DataSource dataSource, @Value("${roboyam.node-id}") int nodeId, @Value("${roboyam.batch.heartbeat-idle-seconds:30}") long idleSeconds) {
        this(dataSource, nodeId, Duration.ofSeconds(idleSeconds), Clock.systemUTC());
    }

    public WorkerHeartbeats(DataSource dataSource, int nodeId, Duration idleInterval, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.nodeId = nodeId;
        this.idleInterval = idleInterval;
        this.clock = clock;
    }

    /**
     * 한 바퀴를 성공적으로 돌았다.
     *
     * @return 실제로 기록했으면 true(한가해서 건너뛰었거나 기록에 실패하면 false)
     */
    public boolean success(String workerKey, long count) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant last = lastSuccessWrite.get(workerKey);
        if (count == 0 && last != null && now.isBefore(last.plus(idleInterval))) {
            return false;
        }
        try {
            jdbc.update("INSERT INTO worker_heartbeats (worker_key, node_id, last_success_at, last_count, updated_at) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (worker_key) DO UPDATE SET node_id = EXCLUDED.node_id, last_success_at = EXCLUDED.last_success_at, "
                    + "last_count = EXCLUDED.last_count, updated_at = EXCLUDED.updated_at", workerKey, nodeId, ts(now), count, ts(now));
            lastSuccessWrite.put(workerKey, now);
            return true;
        } catch (DataAccessException e) {
            log.warn("워커 기록을 남기지 못했다 worker={} 원인={}", workerKey, e.getClass().getSimpleName());
            return false;
        }
    }

    /** 한 바퀴가 실패했다 — 가린 오류 한 줄을 남긴다. 기록했으면 true */
    public boolean failure(String workerKey, Throwable error) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            jdbc.update("INSERT INTO worker_heartbeats (worker_key, node_id, last_error, last_error_at, updated_at) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (worker_key) DO UPDATE SET node_id = EXCLUDED.node_id, last_error = EXCLUDED.last_error, "
                    + "last_error_at = EXCLUDED.last_error_at, updated_at = EXCLUDED.updated_at", workerKey, nodeId, BatchErrorText.of(error), ts(now), ts(now));
            return true;
        } catch (DataAccessException e) {
            log.warn("워커 오류 기록을 남기지 못했다 worker={} 원인={}", workerKey, e.getClass().getSimpleName());
            return false;
        }
    }
}
