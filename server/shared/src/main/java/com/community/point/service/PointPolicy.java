package com.community.point.service;

import com.community.point.dto.PointRules;
import com.community.point.entity.PointSettingEntity;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 포인트 정책의 <b>짧은 메모리 사본</b> — 글·댓글·추천마다 정책 행을 읽지 않게 한다(게시판 사본 {@code BoardCatalog} 와 같은 방식).
 *
 * <ul>
 *   <li>{@value #TTL_SECONDS}초마다 다시 읽는다 — 관리자 앱에서 저장하면 공개 서버(다른 프로세스)에는 <b>최대 그만큼 늦게</b> 닿는다(계약의 「15초 안에 모든 서버」).
 *       저장한 서버 안에서는 {@link #invalidate()} 로 바로 비운다.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다. 행이 없으면 기본값({@link PointRules#DEFAULTS}).</li>
 *   <li>규칙은 앞으로의 지급에만 적용된다(이미 지급한 포인트는 그대로, 소급 없음).</li>
 * </ul>
 */
@Component
public class PointPolicy {

    private static final Logger log = LoggerFactory.getLogger(PointPolicy.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final JdbcTemplate jdbc;
    private volatile Cached cached;

    public PointPolicy(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public PointRules current() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current.rules;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current.rules;
            }
            try {
                PointRules loaded = load();
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("포인트 정책을 다시 읽지 못해 이전 값을 쓴다 원인={}", e.getClass().getSimpleName());
                cached = new Cached(current.rules, now - TTL_NANOS + RETRY_NANOS);
                return current.rules;
            }
        }
    }

    /**
     * 정책 행을 읽는다. Native SQL(JDBC)인 이유: 적립은 글쓰기 트랜잭션의 세이브포인트 안에서 이 값을 읽는데, 저장소(JPA) 호출은 트랜잭션 가로채기가 실패를 업무 트랜잭션 전체의
     * 「롤백만 가능」으로 표시해 글쓰기까지 깨뜨린다 — JDBC 로 읽으면 실패해도 세이브포인트만 되돌리면 된다.
     */
    private PointRules load() {
        List<PointRules> rows = jdbc.query("select attendance_points, post_points, post_daily_cap, comment_points, comment_daily_cap, popular_reward_points, "
                + "popular_reward_top_n, like_bonus_threshold, like_bonus_points, like_bonus_daily_limit, deal_end_request_points, deal_end_request_daily_limit, "
                + "signup_points, nickname_ticket_price from point_settings where id = ?", (rs, i) -> new PointRules(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getInt(13),
                rs.getInt(14)), PointSettingEntity.ROW_ID);
        return rows.isEmpty() ? PointRules.DEFAULTS : rows.get(0);
    }

    /** 사본을 버린다 — 다음 {@link #current()} 가 다시 읽는다 */
    public void invalidate() {
        cached = null;
    }

    private record Cached(PointRules rules, long loadedAt) {
    }
}
