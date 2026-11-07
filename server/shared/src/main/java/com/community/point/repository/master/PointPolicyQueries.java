package com.community.point.repository.master;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.entity.PointSettingEntity;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포인트 정책 행({@code point_settings}, id = {@link PointSettingEntity#ROW_ID}) 읽기 — 메모리 사본 {@code PointPolicy} 가 부른다. 관리자 저장은 엔티티 저장소 {@link PointSettingRepository} 다.
 *
 * <p>Native SQL(JDBC)인 이유: 적립은 글쓰기 트랜잭션의 <b>JDBC 세이브포인트 안에서</b>({@code IsolatedSideWork}) 이 값을 읽는데, 저장소(JPA) 호출은 트랜잭션 가로채기가 실패를 업무
 * 트랜잭션 전체의 「롤백만 가능」으로 표시해 글쓰기까지 깨뜨린다 — JDBC 로 읽으면 실패해도 세이브포인트만 되돌리면 된다(repository.md 「세이브포인트 안에서 도는 읽기」).
 */
@Repository
public class PointPolicyQueries {

    /** 정책 한 벌 — 지급·조건 칸과 묶음 칸 */
    public record PolicyRow(PointRules rules, PointLotRules lots) {
    }

    private final JdbcTemplate jdbc;

    public PointPolicyQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 정책 행(없으면 비었다 — 부르는 쪽이 기본값을 쓴다) */
    public Optional<PolicyRow> load() {
        List<PolicyRow> rows = jdbc.query("select attendance_points, post_points, post_daily_cap, comment_points, comment_daily_cap, popular_reward_points, "
                + "popular_reward_top_n, like_bonus_threshold, like_bonus_points, like_bonus_daily_limit, deal_end_request_points, deal_end_request_daily_limit, "
                + "signup_points, free_valid_days, restored_lot_valid_days, expiry_notice_days, lot_tracking_started_at, adjust_large_threshold, "
                + "expiry_notice_interval_days, post_min_length, comment_min_length, comment_own_post_excluded, comment_once_per_post, write_interval_seconds, "
                + "daily_total_cap, attendance_streak_days, attendance_streak_bonus "
                + "from point_settings where id = ?", (rs, i) -> {
                    PointRules rules = new PointRules(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8),
                            rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getInt(13), rs.getInt(20), rs.getInt(21), rs.getBoolean(22),
                            rs.getBoolean(23), rs.getInt(24), rs.getInt(25), rs.getInt(26), rs.getInt(27));
                    Timestamp started = rs.getTimestamp(17);
                    PointLotRules lots = new PointLotRules(rs.getInt(14), rs.getInt(15), rs.getInt(16), rs.getInt(19), started == null ? null : started.toInstant(),
                            rs.getInt(18));
                    return new PolicyRow(rules, lots);
                }, PointSettingEntity.ROW_ID);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
