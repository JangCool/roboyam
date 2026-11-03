package com.community.withdrawal.repository.master;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 화면·남은 일·완료 판정이 읽는 것(JDBC) — 사라지는 것(포인트·아이템·등급·출석), 남는 것(글·댓글 수, 결제 이력), 남은 일(주문·요청·결제 취소·송금·포인트샵 환불),
 * 배송완료 7일 안 주문, 마지막 거래 시각. 표를 읽기만 한다. 규칙의 정본은 docs/domain/commerce.md 17-1.
 */
@Repository
public class WithdrawalQueries {

    /** 회원 한 줄 — 익명화 전 값(이메일·닉네임은 완료 메일·연락처 보관에만 쓰고 응답·로그에 싣지 않는다) */
    public record Member(long id, String email, boolean emailVerified, String status, boolean hasPassword, Instant lastLoginAt, String nickname, String gradeKey,
                         String language) {

        @Override
        public String toString() {
            return "Member[id=" + id + ", status=" + status + "]";
        }
    }

    /** 사라지는 것 */
    public record Losses(long freePoints, long paidPoints, int items, int refundableItems, String gradeName, int attendanceStreak) {
    }

    /** 진행 중 주문 한 건 */
    public record Order(long id, String orderNo, String status, Instant deliveredAt) {
    }

    /** 끝나지 않은 결제 취소·송금 한 건 */
    public record Refund(long id, String label, String orderNo) {
    }

    /** 심사 중 포인트샵 환불 신청 */
    public record ShopRefund(long id, String itemName) {
    }

    /** 소셜 연결 한 줄(재가입 제한 해시용 — 지우기 전에 읽는다) */
    public record SocialIdentity(String provider, String providerUserId) {
    }

    private final JdbcTemplate jdbc;

    public WithdrawalQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 회원 줄(없거나 지워졌으면 비어 있다) */
    public Optional<Member> member(long userId) {
        return jdbc.query("SELECT u.id, u.email, u.email_verified_at IS NOT NULL AS email_verified, u.status, u.password_hash IS NOT NULL AND u.password_hash <> '' AS has_password, "
                        + "u.last_login_at, p.nickname, p.grade_key, d.language FROM users u LEFT JOIN profiles p ON p.user_id = u.id "
                        + "LEFT JOIN member_display_preferences d ON d.user_id = u.id WHERE u.id = ? AND u.deleted_at IS NULL",
                (rs, i) -> new Member(rs.getLong("id"), rs.getString("email"), rs.getBoolean("email_verified"), rs.getString("status"), rs.getBoolean("has_password"),
                        at(rs, "last_login_at"),
                        rs.getString("nickname"), rs.getString("grade_key"), rs.getString("language")), userId).stream().findFirst();
    }

    /** 회원 줄을 잠근다(탈퇴 신청·취소·완료를 줄 세운다). 상태를 돌려준다(없으면 비어 있다) */
    public Optional<String> lockStatus(long userId) {
        return jdbc.queryForList("SELECT status FROM users WHERE id = ? AND deleted_at IS NULL FOR UPDATE", String.class, userId).stream().findFirst();
    }

    /** 사라지는 것 — 잔액·아이템·등급 이름·연속 출석 */
    public Losses losses(long userId, LocalDate today, Instant now) {
        long[] balance = jdbc.query("SELECT free_balance, paid_balance FROM point_balances WHERE user_id = ?", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, userId)
                .stream().findFirst().orElse(new long[] {0, 0});
        Integer items = jdbc.queryForObject("SELECT count(*) FROM user_items WHERE user_id = ? AND status = 'OWNED' AND (expires_at IS NULL OR expires_at > ?)", Integer.class,
                userId, Timestamp.from(now));
        Integer refundDays = jdbc.queryForList("SELECT refund_days FROM point_shop_settings ORDER BY id LIMIT 1", Integer.class).stream().findFirst().orElse(7);
        Integer refundable = jdbc.queryForObject("SELECT count(*) FROM user_items i JOIN point_shop_purchases p ON p.id = i.purchase_id WHERE i.user_id = ? AND i.status = 'OWNED' "
                + "AND i.first_used_at IS NULL AND p.status = 'COMPLETED' AND p.created_at > ? "
                + "AND NOT EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.purchase_id = p.id AND r.status = 'PENDING')", Integer.class, userId,
                Timestamp.from(now.minus(Duration.ofDays(refundDays == null ? 7 : refundDays))));
        String gradeName = jdbc.queryForList("SELECT g.label FROM profiles p JOIN grades g ON g.grade_key = p.grade_key WHERE p.user_id = ?", String.class, userId).stream()
                .findFirst().orElse(null);
        Integer streak = jdbc.queryForList("SELECT streak FROM point_attendances WHERE user_id = ? AND day >= ? ORDER BY day DESC LIMIT 1", Integer.class, userId,
                java.sql.Date.valueOf(today.minusDays(1))).stream().findFirst().orElse(0);
        return new Losses(balance[0], balance[1], items == null ? 0 : items, refundable == null ? 0 : refundable, gradeName, streak == null ? 0 : streak);
    }

    /** 충전(유상) 묶음 중 만든 지 {@code days} 일이 지난 것의 남은 양 합 — 충전 환불 수수료(청약철회 기간 경과)의 기준(9-3). 충전이 없는 지금은 늘 0 */
    public long paidLotsOlderThan(long userId, int days, Instant now) {
        Long sum = jdbc.queryForObject("SELECT coalesce(sum(remaining_amount), 0) FROM point_lots WHERE user_id = ? AND kind = 'PAID' AND remaining_amount > 0 "
                + "AND created_at <= ?", Long.class, userId, Timestamp.from(now.minus(Duration.ofDays(days))));
        return sum == null ? 0 : sum;
    }

    /** 남는 글·댓글 수 */
    public int[] contentCounts(long userId) {
        Integer posts = jdbc.queryForObject("SELECT count(*) FROM posts WHERE author_id = ? AND deleted_at IS NULL", Integer.class, userId);
        Integer comments = jdbc.queryForObject("SELECT count(*) FROM comments WHERE author_id = ? AND deleted_at IS NULL", Integer.class, userId);
        return new int[] {posts == null ? 0 : posts, comments == null ? 0 : comments};
    }

    /**
     * 마지막 거래 시각 — 결제 주문(결제 완료·취소 완료), 공구 환불, 사업자 신청 건 결제·입금 확인 중 가장 늦은 것. 결제 이력이 없으면 비어 있다(연락용 이메일을 저장하지 않는다 — 17-2).
     * 포인트샵 구매는 포인트 거래라 돈 거래 이력으로 보지 않는다.
     */
    public Optional<Instant> lastTransactionAt(long userId) {
        OffsetDateTime at = jdbc.queryForObject("SELECT greatest("
                + "(SELECT max(o.paid_at) FROM payment_orders o WHERE o.user_id = ? AND o.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')), "
                + "(SELECT max(c.completed_at) FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.user_id = ? AND c.status = 'SUCCEEDED'), "
                + "(SELECT max(p.refunded_at) FROM group_buy_participations p WHERE p.user_id = ?), "
                + "(SELECT max(greatest(b.paid_at, b.deposit_confirmed_at)) FROM biz_applications b WHERE b.user_id = ? AND (b.paid_at IS NOT NULL OR b.deposit_confirmed_at IS NOT NULL)))",
                OffsetDateTime.class, userId, userId, userId, userId);
        return Optional.ofNullable(at).map(OffsetDateTime::toInstant);
    }

    /** 진행 중 공구 주문(PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED) */
    public List<Order> ordersInProgress(long userId) {
        return jdbc.query("SELECT id, order_no, status, delivered_at FROM group_buy_participations WHERE user_id = ? AND status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', "
                + "'PREPARING', 'SHIPPED') ORDER BY id", WithdrawalQueries::order, userId);
    }

    /** 배송완료 뒤 {@code days} 일(단순 변심 반품 기한) 안의 주문 */
    public List<Order> deliveredWithin(long userId, int days, Instant now) {
        return jdbc.query("SELECT id, order_no, status, delivered_at FROM group_buy_participations WHERE user_id = ? AND status = 'DELIVERED' AND delivered_at IS NOT NULL "
                + "AND delivered_at > ? ORDER BY delivered_at, id", WithdrawalQueries::order, userId, Timestamp.from(now.minus(Duration.ofDays(days))));
    }

    /** 열린 취소·반품 요청(AWAITING_FEE·REQUESTED·APPROVED·RECEIVED) — 주문번호로 식별 */
    public List<Order> openRequests(long userId) {
        return jdbc.query("SELECT r.id, p.order_no, r.status, p.delivered_at FROM group_buy_requests r JOIN group_buy_participations p ON p.id = r.participation_id "
                + "WHERE r.user_id = ? AND r.status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED') ORDER BY r.id", WithdrawalQueries::order, userId);
    }

    /** 끝나지 않은 결제 취소(PENDING·SENDING·GAVE_UP·MANUAL) — 주문 이름, 공구 주문이면 주문번호 */
    public List<Refund> openPaymentCancels(long userId) {
        return jdbc.query("SELECT c.id, o.order_name, (SELECT min(p.order_no) FROM group_buy_participations p WHERE p.payment_order_id = o.id) AS order_no "
                + "FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.user_id = ? AND c.status IN ('PENDING', 'SENDING', 'GAVE_UP', 'MANUAL') "
                + "ORDER BY c.id", (rs, i) -> new Refund(rs.getLong(1), rs.getString(2), rs.getString(3)), userId);
    }

    /** 송금 대기 환불(계좌 받기 전·송금 전) */
    public List<Refund> openBankTransfers(long userId) {
        return jdbc.query("SELECT t.id, t.status, p.order_no FROM commerce_bank_transfers t LEFT JOIN group_buy_participations p ON p.id = t.participation_id "
                + "WHERE t.user_id = ? AND t.status IN ('AWAITING_ACCOUNT', 'READY') ORDER BY t.id", (rs, i) -> new Refund(rs.getLong(1), rs.getString(2), rs.getString(3)), userId);
    }

    /** 심사 중 포인트샵 환불 신청 */
    public List<ShopRefund> pendingShopRefunds(long userId) {
        return jdbc.query("SELECT r.id, i.name FROM point_shop_refund_requests r JOIN point_shop_purchases p ON p.id = r.purchase_id JOIN point_shop_items i ON i.id = p.item_id "
                + "WHERE r.user_id = ? AND r.status = 'PENDING' ORDER BY r.id", (rs, i) -> new ShopRefund(rs.getLong(1), rs.getString(2)), userId);
    }

    /** 소셜 연결(지우기 전에 읽는다) */
    public List<SocialIdentity> socialIdentities(long userId) {
        return jdbc.query("SELECT provider, provider_user_id FROM user_identities WHERE user_id = ?", (rs, i) -> new SocialIdentity(rs.getString(1), rs.getString(2)), userId);
    }

    /** 닉네임 이미지 저장소 키(지우기 전에 읽는다 — 디스크 파일 삭제용) */
    public List<String> nicknameImageKeys(long userId) {
        return jdbc.query("SELECT image_key, still_key FROM nickname_image_requests WHERE user_id = ?", (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, userId)
                .stream().flatMap(keys -> java.util.stream.Stream.of(keys[0], keys[1])).filter(key -> key != null && !key.isBlank()).toList();
    }

    private static Order order(ResultSet rs, int i) throws SQLException {
        return new Order(rs.getLong(1), rs.getString(2), rs.getString(3), at(rs, "delivered_at"));
    }

    private static Instant at(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
