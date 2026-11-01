package com.community.hotdeal.repository.master;

import com.community.hotdeal.dto.DealSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 핫딜 설정 행({@code deal_settings}, id = 1) 읽기·쓰기 — 관리자 핫딜 설정 화면. 공개·관리자 서버의 짧은 사본은 shared {@code DealSettingsReader}(저장 뒤 {@code invalidate}).
 * 칸이 19개인 한 행이고 업무 상태 전이가 없어 엔티티 대신 JDBC 로 둔다(사본 읽기와 같은 칸 차례). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 */
@Repository
public class DealSettingsStore {

    private static final String COLUMNS = "end_request_threshold, end_grace_hours, end_request_daily_limit, end_request_per_author_limit, end_request_per_author_days, "
            + "keep_max_count, member_daily_post_limit, same_store_interval_minutes, auto_expire_days, price_max_won, discount_warn_percent, hot_like_threshold, "
            + "suspicion_min_posts, suspicion_window_days, store_suspicion_members, store_suspicion_window_days, store_suspicion_other_ratio_percent, og_fetch_enabled, "
            + "affiliate_mode, updated_at, updated_by";

    private final JdbcTemplate jdbc;

    public DealSettingsStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 저장된 값 + 마지막 저장(행이 V122 에서 만들어진 뒤 아무도 저장하지 않았으면 {@code updatedBy} null) */
    public record Stored(DealSettings settings, Instant updatedAt, Long updatedBy) {
    }

    public Stored read() {
        return read(false);
    }

    /** 행을 잠가 읽는다(동시 저장이 줄을 선다) */
    public Stored lock() {
        return read(true);
    }

    public void update(DealSettings s, long operatorId, Instant now) {
        jdbc.update("UPDATE deal_settings SET end_request_threshold = ?, end_grace_hours = ?, end_request_daily_limit = ?, end_request_per_author_limit = ?, "
                        + "end_request_per_author_days = ?, keep_max_count = ?, member_daily_post_limit = ?, same_store_interval_minutes = ?, auto_expire_days = ?, "
                        + "price_max_won = ?, discount_warn_percent = ?, hot_like_threshold = ?, suspicion_min_posts = ?, suspicion_window_days = ?, "
                        + "store_suspicion_members = ?, store_suspicion_window_days = ?, store_suspicion_other_ratio_percent = ?, og_fetch_enabled = ?, affiliate_mode = ?, "
                        + "updated_at = ?, updated_by = ? WHERE id = 1",
                s.endRequestThreshold(), s.endGraceHours(), s.endRequestDailyLimit(), s.endRequestPerAuthorLimit(), s.endRequestPerAuthorDays(), s.keepMaxCount(),
                s.memberDailyPostLimit(), s.sameStoreIntervalMinutes(), s.autoExpireDays(), s.priceMaxWon(), s.discountWarnPercent(), s.hotLikeThreshold(),
                s.suspicionMinPosts(), s.suspicionWindowDays(), s.storeSuspicionMembers(), s.storeSuspicionWindowDays(), s.storeSuspicionOtherRatioPercent(),
                s.ogFetchEnabled(), s.affiliateMode(), Timestamp.from(now), operatorId);
    }

    private Stored read(boolean forUpdate) {
        List<Stored> rows = jdbc.query("SELECT " + COLUMNS + " FROM deal_settings WHERE id = 1" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, i) -> new Stored(new DealSettings(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8),
                        rs.getInt(9), rs.getLong(10), rs.getInt(11), rs.getInt(12), rs.getInt(13), rs.getInt(14), rs.getInt(15), rs.getInt(16), rs.getInt(17),
                        rs.getBoolean(18), rs.getString(19)), rs.getTimestamp(20).toInstant(), (Long) rs.getObject(21)));
        if (rows.isEmpty()) {
            // V122 가 행을 넣는다 — 지워졌으면 기본값으로 다시 만든다
            jdbc.update("INSERT INTO deal_settings (id, updated_at) VALUES (1, now()) ON CONFLICT (id) DO NOTHING");
            return read(forUpdate);
        }
        return rows.get(0);
    }
}
