package com.community.dashboard.repository.master;

import com.community.analytics.store.AnalyticsDatabase;
import com.community.group.entity.QGroupEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.post.entity.QPostEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 대시보드의 통계 가젯 읽기(site-analytics-contract) — <b>집계 표만</b> 읽는다(원본 GROUP BY 없음). 질의마다 쓰는 기본 키·인덱스를 주석으로 적는다.
 *
 * <ul>
 *   <li>방문 표({@code analytics_*}): 분석 전용 연결({@code analyticsJdbc}) — 지금은 메인 DB 라 부르는 쪽 읽기 트랜잭션에 참여한다.</li>
 *   <li>알림 발송·게시판 활동 표와 이름 붙이기(글 제목 등): 메인 DB. 이름 붙이기(글·소모임·포인트 상점 품목·공구)는 QueryDSL,
 *       알림 발송·게시판 활동 일별 표는 엔티티가 없어 네이티브 SQL 이다.</li>
 * </ul>
 */
@Repository
public class DashboardAnalyticsStore extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;
    private static final QPointShopItemEntity ITEM = QPointShopItemEntity.pointShopItemEntity;
    private static final QGroupBuyEntity GROUP_BUY = QGroupBuyEntity.groupBuyEntity;

    public record Hour(Instant hour, long visitors, long newVisitors, long pageViews) {
    }

    public record Page(String path, String kind, long views) {
    }

    public record Count(String key, long count) {
    }

    public record ChannelDay(String channel, long sent, long failed) {
    }

    public record Failure(long id, String channel, String reasonCode, Instant failedAt) {
    }

    public record SiteDay(LocalDate day, long posts, long comments, long signups) {
    }

    public record BoardDay(long boardId, long posts, long comments) {
    }

    /** 이름 — 영어 이름이 없으면 null */
    public record Name(String name, String nameEn) {
    }

    private final JdbcTemplate analytics;
    private final JdbcTemplate main;

    public DashboardAnalyticsStore(AnalyticsDatabase analyticsDb, DataSource dataSource, JPAQueryFactory query) {
        super(query);
        this.analytics = analyticsDb.jdbc();
        this.main = new JdbcTemplate(dataSource);
    }

    // ── 방문 ─────────────────────────────────────────────────────────────────

    /** [from, to) 의 시간 칸 — PK(hour) 범위 */
    public List<Hour> hours(Instant from, Instant to) {
        return analytics.query("SELECT hour, visitors, new_visitors, page_views FROM analytics_visit_hourly WHERE hour >= ? AND hour < ? ORDER BY hour",
                (rs, i) -> new Hour(rs.getTimestamp(1).toInstant(), rs.getLong(2), rs.getLong(3), rs.getLong(4)), ts(from), ts(to));
    }

    /** 지금 접속 — ix_analytics_visit_active_seen(last_seen) 범위 COUNT */
    public long activeSince(Instant since) {
        Long n = analytics.queryForObject("SELECT count(*) FROM analytics_visit_active WHERE last_seen >= ?", Long.class, ts(since));
        return n == null ? 0 : n;
    }

    /** 그 날 많이 본 페이지 — PK(day, path, kind) 앞부분으로 그 날 줄만 읽어 정렬(하루의 서로 다른 경로 수만큼) */
    public List<Page> topPages(LocalDate day, int limit) {
        return analytics.query("SELECT path, kind, views FROM analytics_page_daily WHERE day = ? ORDER BY views DESC, path LIMIT ?",
                (rs, i) -> new Page(rs.getString(1), rs.getString(2), rs.getLong(3)), Date.valueOf(day), limit);
    }

    /** 그 날 유입처 분류별 방문 — PK(day, referrer_group, referrer_domain) 앞부분 */
    public List<Count> referrerGroups(LocalDate day) {
        return analytics.query("SELECT referrer_group, sum(visits) FROM analytics_referrer_daily WHERE day = ? GROUP BY referrer_group",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), Date.valueOf(day));
    }

    /** 그 날 유입 도메인 상위 — 도메인이 없는 분류(직접·캠페인)는 빠진다 */
    public List<Count> referrerDomains(LocalDate day, int limit) {
        return analytics.query("SELECT referrer_domain, sum(visits) AS n FROM analytics_referrer_daily WHERE day = ? AND referrer_domain <> '' "
                        + "GROUP BY referrer_domain ORDER BY n DESC, referrer_domain LIMIT ?",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), Date.valueOf(day), limit);
    }

    // ── 알림 발송 ────────────────────────────────────────────────────────────

    /** 그 날 채널별 — PK(day, channel) */
    public List<ChannelDay> notificationDay(LocalDate day) {
        return main.query("SELECT channel, sent, failed FROM notification_send_daily WHERE day = ? ORDER BY channel",
                (rs, i) -> new ChannelDay(rs.getString(1), rs.getLong(2), rs.getLong(3)), Date.valueOf(day));
    }

    /** 최근 실패 — ix_notification_send_failures_at(failed_at DESC) 차례로 LIMIT */
    public List<Failure> recentFailures(int limit) {
        return main.query("SELECT id, channel, reason_code, failed_at FROM notification_send_failures ORDER BY failed_at DESC LIMIT ?",
                (rs, i) -> new Failure(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), limit);
    }

    // ── 게시판 활동 ──────────────────────────────────────────────────────────

    /** [from, to] 날 줄 — PK(day) 범위 */
    public List<SiteDay> siteDays(LocalDate from, LocalDate to) {
        return main.query("SELECT day, posts, comments, signups FROM site_activity_daily WHERE day >= ? AND day <= ? ORDER BY day",
                (rs, i) -> new SiteDay(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3), rs.getLong(4)), Date.valueOf(from), Date.valueOf(to));
    }

    /** 그 날 게시판별 — PK(day, board_id) 앞부분, 글 + 댓글 많은 차례 */
    public List<BoardDay> boardDay(LocalDate day, int limit) {
        return main.query("SELECT board_id, posts, comments FROM board_activity_daily WHERE day = ? ORDER BY posts + comments DESC, board_id LIMIT ?",
                (rs, i) -> new BoardDay(rs.getLong(1), rs.getLong(2), rs.getLong(3)), Date.valueOf(day), limit);
    }

    // ── 이름 붙이기(상위 N 개만, PK 로) ──────────────────────────────────────

    /** 글 제목 — posts PK(id, 월 범위 파티션). 지워진 글은 빠진다 */
    public Map<Long, Name> postTitles(Collection<Long> ids) {
        Map<Long, Name> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(POST.id, POST.title).from(POST).where(POST.id.in(ids), POST.deletedAt.isNull()).fetch()) {
            out.put(t.get(POST.id), new Name(t.get(POST.title), null));
        }
        return out;
    }

    /** 공구 제목 — group_buys PK(제목이 비었어도 그대로 — 예전과 같다) */
    public Map<Long, Name> groupBuyTitles(Collection<Long> ids) {
        Map<Long, Name> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(GROUP_BUY.id, GROUP_BUY.title).from(GROUP_BUY).where(GROUP_BUY.id.in(ids)).fetch()) {
            out.put(t.get(GROUP_BUY.id), new Name(t.get(GROUP_BUY.title), null));
        }
        return out;
    }

    /** 소모임 이름 — groups PK */
    public Map<Long, Name> groupNames(Collection<Long> ids) {
        Map<Long, Name> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(GROUP.id, GROUP.name).from(GROUP).where(GROUP.id.in(ids)).fetch()) {
            out.put(t.get(GROUP.id), new Name(t.get(GROUP.name), null));
        }
        return out;
    }

    /** 포인트 상점 품목 이름 — point_shop_items PK */
    public Map<Long, Name> pointShopNames(Collection<Long> ids) {
        Map<Long, Name> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(ITEM.id, ITEM.name, ITEM.nameEn).from(ITEM).where(ITEM.id.in(ids)).fetch()) {
            out.put(t.get(ITEM.id), new Name(t.get(ITEM.name), t.get(ITEM.nameEn)));
        }
        return out;
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }
}
