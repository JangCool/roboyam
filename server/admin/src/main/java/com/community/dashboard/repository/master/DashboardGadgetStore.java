package com.community.dashboard.repository.master;

import com.community.dashboard.dto.response.gadget.DashboardGroupRequestsView;
import com.community.dashboard.dto.response.gadget.DashboardHotdealStatusView;
import com.community.dashboard.dto.response.gadget.DashboardHotdealTopView;
import com.community.dashboard.dto.response.gadget.DashboardOperatorSecurityView;
import com.community.dashboard.dto.response.gadget.DashboardPointShopView;
import com.community.dashboard.dto.response.gadget.DashboardRecentSignupsView;
import com.community.dashboard.dto.response.gadget.DashboardReportsView;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 대시보드 가젯의 숫자·목록 읽기(admin-dashboard-contract 「서버 부담 원칙」). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>원칙: 숫자 = 상태별 COUNT 한 번(부분 인덱스·상태 인덱스·Snowflake ID 범위로) · 목록 = 최대 10행(인덱스 차례로 LIMIT) · 추이 = 이미 있는 일별 집계 표만(원본 GROUP BY 없음).
 * 「오늘」은 서비스 날짜(한국 시간) 0시부터이고, 만든 시각 인덱스가 없는 표는 Snowflake ID 의 시각 하한({@link SnowflakeIdGenerator#floorIdAt})으로 PK 범위를 센다.
 *
 * <p>Native SQL 인 이유: 여러 업무 표를 칸 몇 개만 읽고, 상태별 건수를 {@code count(*) FILTER} 로 한 번에 세며, 파티션 표({@code posts})를 ID 범위로 자른다.
 * 질의마다 쓰는 인덱스를 주석으로 적는다 — 표·인덱스를 바꾸면 여기도 본다.
 */
@Repository
public class DashboardGadgetStore {

    /** 살아 있는 글(관리자 목록 — 숨김 포함) — 인자 1개(now). {@code AdminDealQueries.ALIVE_POST} 와 같다 */
    private static final String ALIVE_POST = "p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?)";
    /** 공개 글 — 인자 2개(now, now). {@code AdminDealQueries.PUBLIC_POST} 와 같다 */
    private static final String PUBLIC_POST = ALIVE_POST + " AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= ?)) AND p.group_id IS NULL";
    /** 진행 중 딜 — 인자 3개(now, autoExpireDays, now - autoExpireDays). {@code AdminDealQueries.live()} · shared {@code DealQueries.LIVE_DEAL} 과 같다 */
    private static final String LIVE_DEAL = "d.status = 'ACTIVE' AND (d.ends_at IS NULL OR d.ends_at > ?) AND (d.ends_at IS NOT NULL OR ? = 0 OR d.created_at > ?)";

    /** 예약된 광고 건 한 줄(유형 = 한도가 있는 상품 — {@code AdStatsAdminStore} 의 유형과 같다) */
    public record AdBooking(long id, String title, String product, Instant startAt, Instant endAt) {
    }

    /** 포인트 상점 판매 시작·끝 */
    public record ShopSale(long id, String name, String nameEn, Instant startAt, Instant endAt) {
    }

    /** 배치 실행 한 줄 */
    public record BatchRun(long id, String jobKey, Instant at, String errorMessage, String messageKey, String messageArgs) {
    }

    /** 감사 기록 한 줄 */
    public record AuditRow(long id, String actorName, String action, Instant at) {
    }

    /** 닉네임 이미지 심사 대기 한 줄 */
    public record NicknameImageRow(long id, String nickname, String imageKey) {
    }

    /** 일별 광고 합 */
    public record AdDay(LocalDate day, long impressions, long clicks) {
    }

    private final JdbcTemplate jdbc;

    public DashboardGadgetStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 신고 ────────────────────────────────────────────────────────────────

    /** 대기·검토 중 건수 — ix_report_cases_status_last(status, …) */
    public long[] reportCounts() {
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE status = 'PENDING'), count(*) FILTER (WHERE status = 'IN_REVIEW') FROM report_cases "
                + "WHERE status IN ('PENDING', 'IN_REVIEW')", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    /**
     * 오래된 대기 사건(먼저 신고된 차례) — 대기 사건은 상태 인덱스로 고르고(대기 건수만큼), 사건마다 대표 사유(그 사건의 신고 몇 건 — ix_reports_case)와
     * 자동 숨김 기록(ix_report_events_case)을 본다.
     */
    public List<DashboardReportsView.Oldest> oldestPendingReports(int limit) {
        return jdbc.query("SELECT c.id, c.target_type, c.first_reported_at, "
                + "(SELECT r.reason FROM reports r WHERE r.case_id = c.id GROUP BY r.reason ORDER BY count(*) DESC, r.reason LIMIT 1), "
                + "EXISTS (SELECT 1 FROM report_events e WHERE e.case_id = c.id AND e.type = 'AUTO_HIDDEN') "
                + "FROM report_cases c WHERE c.status = 'PENDING' ORDER BY c.first_reported_at, c.id LIMIT ?",
                (rs, i) -> new DashboardReportsView.Oldest(Long.toString(rs.getLong(1)), rs.getString(4) == null ? "OTHER" : rs.getString(4), rs.getString(2),
                        instant(rs, 3), rs.getBoolean(5)), limit);
    }

    // ── 글 ──────────────────────────────────────────────────────────────────

    /** 최근 글 ID(공개 상태로 쓴 글, 지운 글 제외) — 파티션마다 PK 를 거꾸로 읽어 합친다 */
    public List<Long> recentPostIds(int limit) {
        return jdbc.queryForList("SELECT id FROM posts WHERE deleted_at IS NULL AND status = 'PUBLISHED' ORDER BY id DESC LIMIT ?", Long.class, limit);
    }

    /** 열린 신고 사건(대기·검토 중)이 있는 글 — ux_report_cases_open_target(target_type, target_id) 부분 인덱스 */
    public Set<Long> openlyReportedPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        String in = postIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        return new HashSet<>(jdbc.queryForList("SELECT target_id FROM report_cases WHERE target_type = 'POST' AND status IN ('PENDING', 'IN_REVIEW') AND target_id IN (" + in + ")",
                Long.class, postIds.toArray()));
    }

    // ── 소모임 ──────────────────────────────────────────────────────────────

    /** 개설 신청 대기 건수 — ix_groups_status_opened(status, …) */
    public long pendingGroups() {
        return count("SELECT count(*) FROM groups WHERE status = 'PENDING'");
    }

    /** 최근 개설 신청(대기 중, 최근 차례) — 대기 건만 상태 인덱스로 고른 뒤 정렬 */
    public List<DashboardGroupRequestsView.Recent> recentGroupRequests(int limit) {
        return jdbc.query("SELECT g.id, g.name, p.nickname, g.requested_at FROM groups g LEFT JOIN profiles p ON p.user_id = coalesce(g.creator_id, g.leader_id) "
                + "WHERE g.status = 'PENDING' ORDER BY g.requested_at DESC, g.id DESC LIMIT ?",
                (rs, i) -> new DashboardGroupRequestsView.Recent(Long.toString(rs.getLong(1)), rs.getString(2), nz(rs.getString(3)), instant(rs, 4)), limit);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /**
     * 최근 가입 회원(탈퇴 제외) — ix_users_created(created_at DESC, id DESC). 가입 방법은 관리자 회원 목록과 같다(비밀번호가 있으면 EMAIL, 아니면 가장 먼저 연결한 소셜 —
     * ix_user_identities_user).
     */
    public List<DashboardRecentSignupsView.Item> recentSignups(int limit) {
        return jdbc.query("SELECT u.id, p.nickname, CASE WHEN u.password_hash IS NOT NULL THEN 'EMAIL' ELSE "
                + "(SELECT i.provider FROM user_identities i WHERE i.user_id = u.id ORDER BY i.created_at, i.id LIMIT 1) END, u.created_at "
                + "FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.status <> 'WITHDRAWN' ORDER BY u.created_at DESC, u.id DESC LIMIT ?",
                (rs, i) -> new DashboardRecentSignupsView.Item(Long.toString(rs.getLong(1)), nz(rs.getString(2)), rs.getString(3) == null ? "EMAIL" : rs.getString(3),
                        instant(rs, 4)), limit);
    }

    /**
     * 회원 숫자 {가입, 탈퇴, 정지 중} — 세 COUNT 를 한 번에, 모두 인덱스로만 센다.
     *
     * <ul>
     *   <li>가입: 범위 {@code [from, to)} 에 만든 회원(지금 상태와 무관 — 그날 가입한 사실을 센다) — {@code ix_users_created(created_at DESC, id DESC)} 범위.</li>
     *   <li>탈퇴: 범위에 탈퇴를 **완료**한 회원({@code status = 'WITHDRAWN'}, 탈퇴 완료 시각 = {@code deleted_at} — 회원 탈퇴의 완료 처리 {@code WithdrawalEraseStore} 가 쓴다) —
     *       부분 인덱스 {@code ix_users_withdrawn_at(deleted_at) WHERE status = 'WITHDRAWN'}(admin V6).</li>
     *   <li>정지 중: 기간 정지·영구 정지({@code SUSPENDED · BANNED} — 관리자 회원 목록의 「정지」 거르기와 같은 뜻) — 부분 인덱스
     *       {@code ix_users_suspended(id) WHERE status IN ('SUSPENDED', 'BANNED')}(admin V6).</li>
     *   <li>탈퇴 대기: 탈퇴를 신청해 남은 일이 끝나기를 기다리는 예약({@code member_withdrawals.status = 'SCHEDULED'}) — 부분 인덱스
     *       {@code ux_member_withdrawals_scheduled(user_id) WHERE status = 'SCHEDULED'}(shared V132).</li>
     * </ul>
     */
    public long[] memberStats(Instant from, Instant to) {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM users WHERE created_at >= ? AND created_at < ?), "
                        + "(SELECT count(*) FROM users WHERE status = 'WITHDRAWN' AND deleted_at >= ? AND deleted_at < ?), "
                        + "(SELECT count(*) FROM users WHERE status IN ('SUSPENDED', 'BANNED')), "
                        + "(SELECT count(*) FROM member_withdrawals WHERE status = 'SCHEDULED')",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, ts(from), ts(to), ts(from), ts(to));
    }

    /** 닉네임 이미지 심사 대기 건수 — ix_nickname_image_requests_status(status, id) */
    public long pendingNicknameImages() {
        return count("SELECT count(*) FROM nickname_image_requests WHERE status = 'PENDING'");
    }

    /** 심사 대기 오래된 차례 — 같은 인덱스(status, id) 차례 */
    public List<NicknameImageRow> oldestNicknameImages(int limit) {
        return jdbc.query("SELECT r.id, p.nickname, coalesce(r.still_key, r.image_key) FROM nickname_image_requests r LEFT JOIN profiles p ON p.user_id = r.user_id "
                + "WHERE r.status = 'PENDING' ORDER BY r.id LIMIT ?", (rs, i) -> new NicknameImageRow(rs.getLong(1), nz(rs.getString(2)), rs.getString(3)), limit);
    }

    // ── 핫딜 ────────────────────────────────────────────────────────────────

    /**
     * 핫딜 현황 — 진행 중 딜(ix_post_deals_key 등 status = 'ACTIVE' 부분 인덱스 범위)과 오늘 등록(글 ID = Snowflake — PK 범위)만 읽어 살아 있는 글(posts PK)과 잇는다.
     *
     * @param todayStart    오늘(한국 날짜) 0시
     * @param tomorrowStart 내일 0시 — 오늘 마감 = 진행 중이고 마감 시각이 이보다 앞
     */
    public DashboardHotdealStatusView hotdealStatus(Instant now, Instant todayStart, Instant tomorrowStart, int autoExpireDays) {
        Timestamp n = ts(now);
        Timestamp expireFrom = ts(now.minus(Duration.ofDays(Math.max(0, autoExpireDays))));
        long todayId = SnowflakeIdGenerator.floorIdAt(todayStart);
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE " + LIVE_DEAL + "), "
                        + "count(*) FILTER (WHERE " + LIVE_DEAL + " AND d.flagged_at IS NOT NULL), "
                        + "count(*) FILTER (WHERE " + LIVE_DEAL + " AND d.ends_at < ?), "
                        + "count(*) FILTER (WHERE d.post_id >= ?) "
                        + "FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE (d.status = 'ACTIVE' OR d.post_id >= ?) AND " + ALIVE_POST,
                (rs, i) -> new DashboardHotdealStatusView(rs.getLong(1), rs.getLong(4), rs.getLong(3), rs.getLong(2)),
                n, autoExpireDays, expireFrom, n, autoExpireDays, expireFrom, n, autoExpireDays, expireFrom, ts(tomorrowStart), todayId, todayId, n);
    }

    /** 지금 인기 핫딜 — 최근 7일 등록(글 ID 범위)의 진행 중 딜 · 공개 글을 추천 많은 차례로 */
    public List<DashboardHotdealTopView.Item> hotdealTop(Instant now, int autoExpireDays, int limit) {
        Timestamp n = ts(now);
        long fromId = SnowflakeIdGenerator.floorIdAt(now.minus(Duration.ofDays(7)));
        return jdbc.query("SELECT d.post_id, d.store_name, p.title, p.like_count, d.deal_price FROM post_deals d JOIN posts p ON p.id = d.post_id "
                        + "WHERE d.post_id >= ? AND " + LIVE_DEAL + " AND " + PUBLIC_POST + " ORDER BY p.like_count DESC, d.post_id DESC LIMIT ?",
                (rs, i) -> new DashboardHotdealTopView.Item(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), rs.getInt(4), (Long) rs.getObject(5)),
                fromId, n, autoExpireDays, ts(now.minus(Duration.ofDays(Math.max(0, autoExpireDays)))), n, n, limit);
    }

    // ── 광고 ────────────────────────────────────────────────────────────────

    /** 일별 광고 합 — 일별 집계 표(ad_daily_stats, ix_ads_day)만 읽는다. 범위 {@code [from, to]} 끝 포함, 값이 없는 날은 빠진다 */
    public List<AdDay> adDays(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT day, sum(impressions), sum(clicks) FROM ad_daily_stats WHERE day BETWEEN ? AND ? GROUP BY day ORDER BY day",
                (rs, i) -> new AdDay(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3)), Date.valueOf(from), Date.valueOf(to));
    }

    /**
     * 범위 {@code [from, to)} 안에 시작하거나 끝나는 예약된 광고 건(SCHEDULED — ix_ba_scheduled(start_at, end_at) 부분 인덱스). 핫딜 글 게시 건은 광고가 아니라 뺀다.
     */
    public List<AdBooking> adBookings(Instant from, Instant to) {
        Timestamp f = ts(from);
        Timestamp t = ts(to);
        return jdbc.query("SELECT a.id, a.title, x.product, a.start_at, a.end_at FROM biz_applications a "
                        + "LEFT JOIN LATERAL (SELECT i.product FROM biz_application_items i WHERE i.application_id = a.id AND i.product <> 'HOTDEAL_POST' "
                        + "ORDER BY i.product LIMIT 1) x ON true "
                        + "WHERE a.status = 'SCHEDULED' AND a.kind <> 'HOTDEAL_POST' AND ((a.start_at >= ? AND a.start_at < ?) OR (a.end_at >= ? AND a.end_at < ?))",
                (rs, i) -> new AdBooking(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4), instant(rs, 5)), f, t, f, t);
    }

    // ── 포인트 ──────────────────────────────────────────────────────────────

    /** 범위 {@code [from, to)} 의 지급(EARN 합)·사용(USE 합, 양수) — ix_point_ledger_created(created_at DESC, id DESC) 범위 */
    public long[] pointFlow(Instant from, Instant to) {
        return jdbc.queryForObject("SELECT coalesce(sum(delta) FILTER (WHERE kind = 'EARN'), 0), coalesce(-sum(delta) FILTER (WHERE kind = 'USE'), 0) "
                + "FROM point_ledger WHERE created_at >= ? AND created_at < ?", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, ts(from), ts(to));
    }

    /** 범위 {@code [from, to)} 에 만료될 무료 포인트 남은 양 — ix_point_lots_expiry(expires_at) WHERE kind = 'FREE' AND remaining_amount > 0 */
    public long pointsExpiring(Instant from, Instant to) {
        Long n = jdbc.queryForObject("SELECT coalesce(sum(remaining_amount), 0) FROM point_lots WHERE kind = 'FREE' AND remaining_amount > 0 AND expires_at >= ? AND expires_at < ?",
                Long.class, ts(from), ts(to));
        return n == null ? 0 : n;
    }

    /** 그 시각 뒤의 포인트 상점 구매 수 — 구매 ID(Snowflake) PK 범위 */
    public long pointShopPurchasesSince(Instant from) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM point_shop_purchases WHERE id >= ?", Long.class, SnowflakeIdGenerator.floorIdAt(from));
        return n == null ? 0 : n;
    }

    /** 남은 수량이 적은 판매 중 품목(수량 제한 품목만) — 품목 표는 작다(운영자가 만든 수십 줄) */
    public List<DashboardPointShopView.LowStock> lowStockItems(Instant now, int maxStock, int limit) {
        Timestamp n = ts(now);
        return jdbc.query("SELECT id, name, name_en, stock FROM point_shop_items WHERE active AND stock IS NOT NULL AND stock <= ? "
                        + "AND (sale_start_at IS NULL OR sale_start_at <= ?) AND (sale_end_at IS NULL OR sale_end_at > ?) ORDER BY stock, sort_order, id LIMIT ?",
                (rs, i) -> new DashboardPointShopView.LowStock(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), rs.getInt(4)), maxStock, n, n, limit);
    }

    /** 범위 {@code [from, to)} 안에 판매를 시작하거나 끝내는 사용 중 품목 */
    public List<ShopSale> shopSales(Instant from, Instant to) {
        Timestamp f = ts(from);
        Timestamp t = ts(to);
        return jdbc.query("SELECT id, name, name_en, sale_start_at, sale_end_at FROM point_shop_items WHERE active "
                        + "AND ((sale_start_at >= ? AND sale_start_at < ?) OR (sale_end_at >= ? AND sale_end_at < ?))",
                (rs, i) -> new ShopSale(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4), instant(rs, 5)), f, t, f, t);
    }

    // ── 배치·감사·운영자 ─────────────────────────────────────────────────────

    /** 그 시각 뒤에 시작해 실패한 실행(최근 차례) — ix_batch_runs_started(started_at DESC, id DESC) 범위 */
    public List<BatchRun> failedBatchRuns(Instant since, int limit) {
        return jdbc.query("SELECT id, job_key, coalesce(finished_at, started_at), error_message, message_key, message_args FROM batch_runs "
                + "WHERE started_at >= ? AND status = 'FAIL' ORDER BY started_at DESC, id DESC LIMIT ?",
                (rs, i) -> new BatchRun(rs.getLong(1), rs.getString(2), instant(rs, 3), rs.getString(4), rs.getString(5), rs.getString(6)), ts(since), limit);
    }

    /** 실행 중(시작한 차례) — ux_batch_runs_running(job_key) WHERE status = 'RUNNING' 부분 인덱스(배치마다 하나) */
    public List<BatchRun> runningBatchRuns(int limit) {
        return jdbc.query("SELECT id, job_key, started_at FROM batch_runs WHERE status = 'RUNNING' ORDER BY started_at, id LIMIT ?",
                (rs, i) -> new BatchRun(rs.getLong(1), rs.getString(2), instant(rs, 3), null, null, null), limit);
    }

    /** 최근 운영자 작업(성공) — ix_audit_logs_occurred_id(occurred_at DESC, id DESC) 차례로 거른다 */
    public List<AuditRow> recentOperatorAudits(int limit) {
        return jdbc.query("SELECT a.id, o.operator_name, a.action, a.occurred_at FROM audit_logs a LEFT JOIN operators o ON o.id = a.actor_id "
                + "WHERE a.actor_type = 'OPERATOR' AND a.result = 'SUCCESS' ORDER BY a.occurred_at DESC, a.id DESC LIMIT ?",
                (rs, i) -> new AuditRow(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4)), limit);
    }

    /** 운영자 보안 — 운영자 표는 작다(수십 줄). 2차 인증 미등록 = 승인·사용 중·만료 아님 운영자 중 operator_mfa(PK) 에 등록이 없는 사람 */
    public DashboardOperatorSecurityView operatorSecurity() {
        return jdbc.queryForObject("SELECT count(*), count(*) FILTER (WHERE o.locked), "
                + "count(*) FILTER (WHERE o.approved AND o.enabled AND NOT o.expired AND NOT EXISTS "
                + "(SELECT 1 FROM operator_mfa m WHERE m.operator_id = o.id AND m.enrolled_at IS NOT NULL)) FROM operators o",
                (rs, i) -> new DashboardOperatorSecurityView(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
