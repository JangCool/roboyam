package com.community.withdrawal.repository.master;

import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizRefundEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.grade.entity.QGradeEntity;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.QPointBalanceEntity;
import com.community.point.entity.QPointLotEntity;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.pointshop.entity.QPointShopPurchaseEntity;
import com.community.pointshop.entity.QPointShopRefundRequestEntity;
import com.community.pointshop.entity.QPointShopSettingEntity;
import com.community.pointshop.entity.QUserItemEntity;
import com.community.pointshop.entity.UserItemStatus;
import com.community.post.entity.QPostEntity;
import com.community.social.entity.SocialProvider;
import com.community.user.entity.QMemberDisplayPreferenceEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.QUserIdentityEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
 * 탈퇴 화면·남은 일·완료 판정이 읽는 것 — 사라지는 것(포인트·아이템·등급·출석), 남는 것(글·댓글 수, 결제 이력), 남은 일(주문·요청·결제 취소·송금·포인트샵 환불),
 * 배송완료 7일 안 주문, 마지막 거래 시각. 표를 읽기만 한다(회원 줄 잠금 하나만 예외). 규칙의 정본은 docs/domain/commerce.md 17-1.
 *
 * <p>엔티티가 있는 표(회원·프로필·표시 설정·포인트 잔액·묶음·아이템·포인트샵·등급·글·댓글·소셜 연결)는 QueryDSL 이다. Native SQL 로 남긴 것과 이유: 엔티티가 없는 표
 * ({@code point_attendances}·{@code payment_orders}·{@code payment_cancels}·{@code group_buy_participations}·{@code group_buy_requests}·{@code commerce_bank_transfers}·
 * {@code emoticon_*}·{@code avatar_upload_requests})를 읽는 문장, 회원 줄의 {@code FOR UPDATE}({@link #lockStatus}).
 */
@Repository
public class WithdrawalQueries extends RoboyamRepositoryQDSL {

    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QMemberDisplayPreferenceEntity DISPLAY = QMemberDisplayPreferenceEntity.memberDisplayPreferenceEntity;
    private static final QPointBalanceEntity BALANCE = QPointBalanceEntity.pointBalanceEntity;
    private static final QPointLotEntity LOT = QPointLotEntity.pointLotEntity;
    private static final QUserItemEntity ITEM = QUserItemEntity.userItemEntity;
    private static final QPointShopSettingEntity SHOP_SETTING = QPointShopSettingEntity.pointShopSettingEntity;
    private static final QPointShopPurchaseEntity PURCHASE = QPointShopPurchaseEntity.pointShopPurchaseEntity;
    private static final QPointShopRefundRequestEntity REFUND = QPointShopRefundRequestEntity.pointShopRefundRequestEntity;
    private static final QPointShopItemEntity SHOP_ITEM = QPointShopItemEntity.pointShopItemEntity;
    private static final QGradeEntity GRADE = QGradeEntity.gradeEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;
    private static final QUserIdentityEntity IDENTITY = QUserIdentityEntity.userIdentityEntity;
    private static final QBizApplicationEntity BIZ_APP = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizRefundEntity BIZ_REFUND = QBizRefundEntity.bizRefundEntity;

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

    public WithdrawalQueries(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 회원 줄(없거나 지워졌으면 비어 있다) */
    public Optional<Member> member(long userId) {
        // 비밀번호가 있는가는 DB 에서 판정한다(해시를 읽어 오지 않는다)
        BooleanExpression hasPassword = USER.passwordHash.isNotNull().and(USER.passwordHash.ne(""));
        var hasPasswordValue = new CaseBuilder().when(hasPassword).then(true).otherwise(false);
        Tuple row = query.select(USER.id, USER.email, USER.emailVerifiedAt, USER.status, hasPasswordValue, USER.lastLoginAt, PROFILE.nickname, PROFILE.gradeKey,
                        DISPLAY.language).from(USER)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(USER.id))
                .leftJoin(DISPLAY).on(DISPLAY.userId.eq(USER.id))
                .where(USER.id.eq(userId), USER.deletedAt.isNull()).fetchFirst();
        if (row == null) {
            return Optional.empty();
        }
        UserStatus status = row.get(USER.status);
        return Optional.of(new Member(row.get(USER.id), row.get(USER.email), row.get(USER.emailVerifiedAt) != null, status == null ? null : status.name(),
                Boolean.TRUE.equals(row.get(hasPasswordValue)), row.get(USER.lastLoginAt), row.get(PROFILE.nickname), row.get(PROFILE.gradeKey), row.get(DISPLAY.language)));
    }

    /**
     * 회원 줄을 잠근다(탈퇴 신청·취소·완료를 줄 세운다). 상태를 돌려준다(없으면 비어 있다).
     *
     * <p>Native SQL 인 이유: {@code FOR UPDATE}(JPA {@code PESSIMISTIC_WRITE} 는 {@code FOR NO KEY UPDATE})로 잡는다 — 회원 행을 FK 로 참조하는 쓰기(글·댓글·알림·포인트 줄 넣기 등)도
     * 탈퇴 완료가 끝날 때까지 기다리게 하는 지금 잠금 세기를 그대로 둔다.
     */
    public Optional<String> lockStatus(long userId) {
        return jdbc.queryForList("SELECT status FROM users WHERE id = ? AND deleted_at IS NULL FOR UPDATE", String.class, userId).stream().findFirst();
    }

    /** 사라지는 것 — 잔액·아이템·등급 이름·연속 출석 */
    public Losses losses(long userId, LocalDate today, Instant now) {
        Tuple balance = query.select(BALANCE.freeBalance, BALANCE.paidBalance).from(BALANCE).where(BALANCE.userId.eq(userId)).fetchFirst();
        long free = balance == null ? 0 : balance.get(BALANCE.freeBalance);
        long paid = balance == null ? 0 : balance.get(BALANCE.paidBalance);
        Long items = query.select(ITEM.count()).from(ITEM)
                .where(ITEM.userId.eq(userId), ITEM.status.eq(UserItemStatus.OWNED), ITEM.expiresAt.isNull().or(ITEM.expiresAt.gt(now))).fetchOne();
        Integer refundDays = query.select(SHOP_SETTING.refundDays).from(SHOP_SETTING).orderBy(SHOP_SETTING.id.asc()).fetchFirst();
        Instant refundFrom = now.minus(Duration.ofDays(refundDays == null ? 7 : refundDays));
        Long refundable = query.select(ITEM.count()).from(ITEM).join(PURCHASE).on(PURCHASE.id.eq(ITEM.purchaseId))
                .where(ITEM.userId.eq(userId), ITEM.status.eq(UserItemStatus.OWNED), ITEM.firstUsedAt.isNull(), PURCHASE.status.eq(PointShopPurchaseStatus.COMPLETED),
                        PURCHASE.createdAt.gt(refundFrom),
                        JPAExpressions.selectOne().from(REFUND).where(REFUND.purchaseId.eq(PURCHASE.id), REFUND.status.eq(PointShopRefundRequestStatus.PENDING)).notExists())
                .fetchOne();
        String gradeName = query.select(GRADE.label).from(PROFILE).join(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey)).where(PROFILE.userId.eq(userId)).fetchFirst();
        // Native SQL 인 이유: point_attendances 는 엔티티가 없는 표다
        Integer streak = jdbc.queryForList("SELECT streak FROM point_attendances WHERE user_id = ? AND day >= ? ORDER BY day DESC LIMIT 1", Integer.class, userId,
                java.sql.Date.valueOf(today.minusDays(1))).stream().findFirst().orElse(0);
        return new Losses(free, paid, items == null ? 0 : Math.toIntExact(items), refundable == null ? 0 : Math.toIntExact(refundable), gradeName,
                streak == null ? 0 : streak);
    }

    /** 충전(유상) 묶음 중 만든 지 {@code days} 일이 지난 것의 남은 양 합 — 충전 환불 수수료(청약철회 기간 경과)의 기준(9-3). 충전이 없는 지금은 늘 0 */
    public long paidLotsOlderThan(long userId, int days, Instant now) {
        Long sum = query.select(LOT.remainingAmount.sum().coalesce(0L)).from(LOT)
                .where(LOT.userId.eq(userId), LOT.kind.eq(PointLotKind.PAID), LOT.remainingAmount.gt(0L), LOT.createdAt.loe(now.minus(Duration.ofDays(days)))).fetchOne();
        return sum == null ? 0 : sum;
    }

    /** 남는 글·댓글 수 */
    public int[] contentCounts(long userId) {
        Long posts = query.select(POST.count()).from(POST).where(POST.authorId.eq(userId), POST.deletedAt.isNull()).fetchOne();
        Long comments = query.select(COMMENT.id.count()).from(COMMENT).where(COMMENT.authorId.eq(userId), COMMENT.deletedAt.isNull()).fetchOne();
        return new int[] {posts == null ? 0 : Math.toIntExact(posts), comments == null ? 0 : Math.toIntExact(comments)};
    }

    /**
     * 마지막 거래 시각 — 결제 주문(결제 완료·취소 완료), 공구 환불, 사업자 신청 건 결제·입금 확인 중 가장 늦은 것. 결제 이력이 없으면 비어 있다(연락용 이메일을 저장하지 않는다 — 17-2).
     * 포인트샵 구매는 포인트 거래라 돈 거래 이력으로 보지 않는다.
     * 로보티콘 <b>작가 쪽</b> 돈 거래(내 팩이 팔리거나 환불된 시각 · 정산 지급 기록)도 넣는다(V148 — 정산 정보 없이 탈퇴한 작가의 보류 정산을 운영자가 분리 보관 연락 이메일로 연락해 끝내므로,
     * 판매 수익이 있는 작가는 직접 결제한 적이 없어도 연락처를 남긴다).
     *
     * <p>Native SQL 인 이유: 사업자 신청 건 말고 읽는 표가 모두 엔티티가 없는 커머스 표다. 사업자 신청 건 결제·입금 확인 시각은 엔티티 질의({@link #bizLastPaidAt})로 따로 읽어
     * 더 늦은 쪽을 고른다({@code greatest} 와 같이 null 은 건너뛴다).
     */
    public Optional<Instant> lastTransactionAt(long userId) {
        OffsetDateTime at = jdbc.queryForObject("SELECT greatest("
                + "(SELECT max(o.paid_at) FROM payment_orders o WHERE o.user_id = ? AND o.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')), "
                + "(SELECT max(c.completed_at) FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.user_id = ? AND c.status = 'SUCCEEDED'), "
                + "(SELECT max(p.refunded_at) FROM group_buy_participations p WHERE p.user_id = ?), "
                + "(SELECT max(greatest(e.paid_at, e.refunded_at)) FROM emoticon_purchases e WHERE e.creator_id = ? AND e.paid_at IS NOT NULL), "
                + "(SELECT max(s.paid_at) FROM emoticon_settlements s WHERE s.creator_id = ?))",
                OffsetDateTime.class, userId, userId, userId, userId, userId);
        return later(Optional.ofNullable(at).map(OffsetDateTime::toInstant).orElse(null), bizLastPaidAt(userId));
    }

    /** 사업자 신청 건의 가장 늦은 결제·입금 확인 시각(예전 {@code max(greatest(paid_at, deposit_confirmed_at))} 과 같다) — 없으면 null */
    private Instant bizLastPaidAt(long userId) {
        Tuple t = query.select(BIZ_APP.paidAt.max(), BIZ_APP.depositConfirmedAt.max()).from(BIZ_APP).where(BIZ_APP.userId.eq(userId)).fetchOne();
        return t == null ? null : later(t.get(0, Instant.class), t.get(1, Instant.class)).orElse(null);
    }

    private static Optional<Instant> later(Instant a, Instant b) {
        if (a == null || b == null) {
            return Optional.ofNullable(a != null ? a : b);
        }
        return Optional.of(a.isAfter(b) ? a : b);
    }

    /**
     * 사업자 신청 건의 환불 대기 금액 합 — 요청됨(REQUESTED)이거나 승인됐지만 아직 끝나지 않은(APPROVED · 완료 시각 없음) 환불의 승인 금액(없으면 제안 금액). 탈퇴 화면의 「포기와 무관하게 환불된다」 금액.
     *
     */
    public long bizRefundWaitingTotal(long businessProfileId) {
        Long waiting = query.select(Expressions.numberTemplate(Long.class, "sum({0})", BIZ_REFUND.approvedAmount.coalesce(BIZ_REFUND.proposedAmount))).from(BIZ_REFUND)
                .join(BIZ_APP).on(BIZ_APP.id.eq(BIZ_REFUND.applicationId))
                .where(BIZ_APP.businessProfileId.eq(businessProfileId), BIZ_REFUND.status.eq(BizRefundStatus.REQUESTED)
                        .or(BIZ_REFUND.status.eq(BizRefundStatus.APPROVED).and(BIZ_REFUND.completedAt.isNull())))
                .fetchOne();
        return waiting == null ? 0 : waiting;
    }

    /** 진행 중 공구 주문(PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED) — Native SQL 인 이유: 공구 주문 표는 엔티티가 없다 */
    public List<Order> ordersInProgress(long userId) {
        return jdbc.query("SELECT id, order_no, status, delivered_at FROM group_buy_participations WHERE user_id = ? AND status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', "
                + "'PREPARING', 'SHIPPED') ORDER BY id", WithdrawalQueries::order, userId);
    }

    /** 배송완료 뒤 {@code days} 일(단순 변심 반품 기한) 안의 주문 — Native SQL 인 이유: 공구 주문 표는 엔티티가 없다 */
    public List<Order> deliveredWithin(long userId, int days, Instant now) {
        return jdbc.query("SELECT id, order_no, status, delivered_at FROM group_buy_participations WHERE user_id = ? AND status = 'DELIVERED' AND delivered_at IS NOT NULL "
                + "AND delivered_at > ? ORDER BY delivered_at, id", WithdrawalQueries::order, userId, Timestamp.from(now.minus(Duration.ofDays(days))));
    }

    /** 열린 취소·반품 요청(AWAITING_FEE·REQUESTED·APPROVED·RECEIVED) — 주문번호로 식별. Native SQL 인 이유: 공구 요청·주문 표는 엔티티가 없다 */
    public List<Order> openRequests(long userId) {
        return jdbc.query("SELECT r.id, p.order_no, r.status, p.delivered_at FROM group_buy_requests r JOIN group_buy_participations p ON p.id = r.participation_id "
                + "WHERE r.user_id = ? AND r.status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED') ORDER BY r.id", WithdrawalQueries::order, userId);
    }

    /** 끝나지 않은 결제 취소(PENDING·SENDING·GAVE_UP·MANUAL) — 주문 이름, 공구 주문이면 주문번호. Native SQL 인 이유: 결제 표는 엔티티가 없다 */
    public List<Refund> openPaymentCancels(long userId) {
        return jdbc.query("SELECT c.id, o.order_name, (SELECT min(p.order_no) FROM group_buy_participations p WHERE p.payment_order_id = o.id) AS order_no "
                + "FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.user_id = ? AND c.status IN ('PENDING', 'SENDING', 'GAVE_UP', 'MANUAL') "
                + "ORDER BY c.id", (rs, i) -> new Refund(rs.getLong(1), rs.getString(2), rs.getString(3)), userId);
    }

    /** 송금 대기 환불(계좌 받기 전·송금 전) — Native SQL 인 이유: 송금 표는 엔티티가 없다 */
    public List<Refund> openBankTransfers(long userId) {
        return jdbc.query("SELECT t.id, t.status, p.order_no FROM commerce_bank_transfers t LEFT JOIN group_buy_participations p ON p.id = t.participation_id "
                + "WHERE t.user_id = ? AND t.status IN ('AWAITING_ACCOUNT', 'READY') ORDER BY t.id", (rs, i) -> new Refund(rs.getLong(1), rs.getString(2), rs.getString(3)), userId);
    }

    /** 심사 중 포인트샵 환불 신청 */
    public List<ShopRefund> pendingShopRefunds(long userId) {
        return query.select(Projections.constructor(ShopRefund.class, REFUND.id, SHOP_ITEM.name)).from(REFUND)
                .join(PURCHASE).on(PURCHASE.id.eq(REFUND.purchaseId))
                .join(SHOP_ITEM).on(SHOP_ITEM.id.eq(PURCHASE.itemId))
                .where(REFUND.userId.eq(userId), REFUND.status.eq(PointShopRefundRequestStatus.PENDING))
                .orderBy(REFUND.id.asc()).fetch();
    }

    /** 소셜 연결(지우기 전에 읽는다) */
    public List<SocialIdentity> socialIdentities(long userId) {
        return query.select(IDENTITY.provider, IDENTITY.providerUserId).from(IDENTITY).where(IDENTITY.userId.eq(userId)).fetch().stream()
                .map(row -> {
                    SocialProvider provider = row.get(IDENTITY.provider);
                    return new SocialIdentity(provider == null ? null : provider.name(), row.get(IDENTITY.providerUserId));
                })
                .toList();
    }

    /**
     * 닉네임 이미지·아바타 올리기(V157) 저장소 키(지우기 전에 읽는다 — 디스크 파일 삭제용).
     *
     * <p>Native SQL 인 이유: {@code avatar_upload_requests} 는 엔티티가 없는 표라 두 표를 {@code UNION ALL} 한 문장으로 읽는다.
     */
    public List<String> nicknameImageKeys(long userId) {
        return jdbc.query("SELECT image_key, still_key FROM nickname_image_requests WHERE user_id = ? UNION ALL SELECT image_key, still_key FROM avatar_upload_requests WHERE user_id = ?",
                        (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, userId, userId)
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
