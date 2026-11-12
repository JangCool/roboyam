package com.community.pointshop.repository.master;

import static com.community.pointshop.repository.master.PointShopItemStore.ts;

import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.pointshop.entity.QPointShopPurchaseEntity;
import com.community.pointshop.entity.QPointShopRefundRequestEntity;
import com.community.pointshop.entity.QUserEquippedEntity;
import com.community.pointshop.entity.QUserItemEntity;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.entity.UserItemEntity;
import com.community.pointshop.entity.UserItemStatus;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.community.zconfiguration.jpa.UniqueViolations;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 보유 아이템({@code user_items})·장착({@code user_equipped}) 읽기·쓰기 — {@code com.community.pointshop.service} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이 기본이다 — 회수·처음 사용·예약 반환·사용·대체는 조건부 UPDATE, 잠금은 보유 줄 표만 고르는 한 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE}
 * — 보유 줄을 FK 로 가리키는 장착·환불 신청은 그 줄을 먼저 잠근 흐름이 넣는다), 읽기는 한 줄 모양(프로젝션), 넣기는 넣고
 * 바로 내보낸다. Native SQL 로 남긴 것과 이유: <b>변경권 한 장 쓰기·올리기 변경권 예약</b>은 {@code FOR UPDATE OF ui SKIP LOCKED} 로 고른 한 줄을 조건부 UPDATE 하고 고친 줄을
 * {@code RETURNING} 으로 받는 한 문장이다 — 같은 회원의 동시 요청 둘이 같은 한 장을 쓰지 못하고(다른 장이 있으면 그 장을, 없으면 0 줄), 고른 줄과 고친 줄이 같은 문장의 결과여야 한다.
 * 장착은 {@code INSERT … ON CONFLICT (user_id, slot) DO UPDATE}(upsert).
 */
@Repository
public class UserItemStore extends IrobitRepositoryQDSL {

    /** 보유 줄 하나 */
    public record UserItemRow(long id, long userId, long itemId, Long purchaseId, PointShopUsageType usageType, UserItemStatus status, Instant startsAt,
                              Instant expiresAt, Instant firstUsedAt, Instant usedAt, Instant revokedAt, Instant createdAt, Instant reservedAt, UploadTicketUse refType,
                              Long refId) {

        /** 기간제의 기간이 {@code now} 에 끝났나 */
        public boolean expiredAt(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }

    /**
     * 내 아이템 목록 한 줄 — 보유 줄 + 품목의 지금 표시값 + 구매 사본(낸 값·상태·시각) + 장착 자리. 보상 이벤트로 받은 줄(V163 — 구매 없음)은 낸 값 0 · 구매 상태 null ·
     * 「산 시각」 자리에 받은 시각, {@code granted} 참
     */
    public record MyItemRow(UserItemRow item, PointShopItemKind kind, String name, String nameEn, String color, String borderStyle, String avatarId, int pricePaid,
                            PointShopPurchaseStatus purchaseStatus, Instant purchasedAt, EquipSlot equippedSlot, boolean granted) {
    }

    /** 장착 한 자리 — 품목의 지금 색·테두리(색을 고치면 산 사람의 표시도 바뀐다) */
    public record EquippedRow(long userId, EquipSlot slot, long userItemId, long itemId, Instant expiresAt, String color, String borderStyle) {

        public boolean liveAt(Instant now) {
            return expiresAt == null || expiresAt.isAfter(now);
        }
    }

    /** 품목별 내 보유 요약 재료 — 회수되지 않은 줄만. 보상 이벤트로 받은 줄은 구매 칸(시각·상태)이 null — 환불 판정에서 늘 「환불 불가」다 */
    public record HoldingRow(long itemId, PointShopUsageType usageType, UserItemStatus status, Instant expiresAt, Instant firstUsedAt, Instant purchasedAt,
                             PointShopPurchaseStatus purchaseStatus, long userItemId, boolean reserved) {
    }

    private static final QUserItemEntity UI = QUserItemEntity.userItemEntity;
    private static final QPointShopItemEntity I = QPointShopItemEntity.pointShopItemEntity;
    private static final QPointShopPurchaseEntity P = QPointShopPurchaseEntity.pointShopPurchaseEntity;
    private static final QUserEquippedEntity E = QUserEquippedEntity.userEquippedEntity;
    private static final QPointShopRefundRequestEntity R = QPointShopRefundRequestEntity.pointShopRefundRequestEntity;

    private static final Expression<?>[] ROW = {UI.id, UI.userId, UI.itemId, UI.purchaseId, UI.usageType, UI.status, UI.startsAt, UI.expiresAt, UI.firstUsedAt,
            UI.usedAt, UI.revokedAt, UI.createdAt, UI.reservedAt, UI.refType, UI.refId};

    private final UserItemRepository userItems;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public UserItemStore(JPAQueryFactory query, UserItemRepository userItems, EntityManager entityManager, DataSource dataSource) {
        super(query);
        this.userItems = userItems;
        this.entityManager = entityManager;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 보유 ────────────────────────────────────────────────────────────────

    public void insert(UserItemRow row) {
        UniqueViolations.inserting(() -> { // 영구 품목 부분 유일 색인 위반은 DuplicateKeyException(구매 서비스가 「이미 가짐」으로 바꾼다)
            userItems.persist(UserItemEntity.own(row.id(), row.userId(), row.itemId(), row.purchaseId(), row.usageType(), row.startsAt(), row.expiresAt(),
                    row.createdAt()));
            userItems.flush();
        });
    }

    public Optional<UserItemRow> find(long id) {
        return one(rows().where(UI.id.eq(id)));
    }

    /** 장착·환불·고르기 전에 — 줄을 잠그고 읽는다 */
    public Optional<UserItemRow> findForUpdate(long id) {
        return query.select(UI.id).from(UI).where(UI.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() != null ? one(rows().where(UI.id.eq(id))) : Optional.empty();
    }

    public Optional<UserItemRow> byPurchaseForUpdate(long purchaseId) {
        List<Long> locked = query.select(UI.id).from(UI).where(UI.purchaseId.eq(purchaseId)).orderBy(UI.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
        return locked.isEmpty() ? Optional.empty() : one(rows().where(UI.id.in(locked), UI.purchaseId.eq(purchaseId)).orderBy(UI.id.asc()));
    }

    /** 그 회원이 그 영구 품목을 (회수되지 않게) 가졌나 */
    public boolean ownsPermanent(long userId, long itemId) {
        return query.selectOne().from(UI).where(UI.userId.eq(userId), UI.itemId.eq(itemId), UI.usageType.eq(PointShopUsageType.PERMANENT),
                UI.status.ne(UserItemStatus.REVOKED)).fetchFirst() != null;
    }

    /** 그 회원이 가진(회수되지 않은) 그 영구 품목 줄 — 잠근다(아바타 고르기) */
    public Optional<UserItemRow> ownedPermanentForUpdate(long userId, long itemId) {
        // 조건 잠금(id 차례) — PostgreSQL 이 잠근 뒤 조건을 다시 본다. 칸은 조건을 넣어 다시 읽는다
        List<Long> locked = query.select(UI.id).from(UI).where(ownedPermanentLine(userId, itemId)).orderBy(UI.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
        return locked.isEmpty() ? Optional.empty() : one(rows().where(UI.id.in(locked), ownedPermanentLine(userId, itemId)).orderBy(UI.id.asc()));
    }

    /** 그 회원이 가진(회수되지 않은) 그 영구 품목 줄 — 잠그지 않는다(마지막 정지 아바타 되돌리기의 보유 확인 — 프로필 행을 먼저 잠근 쪽이 부른다) */
    public Optional<UserItemRow> ownedPermanent(long userId, long itemId) {
        return one(rows().where(ownedPermanentLine(userId, itemId)));
    }

    private static BooleanExpression ownedPermanentLine(long userId, long itemId) {
        return UI.userId.eq(userId).and(UI.itemId.eq(itemId)).and(UI.usageType.eq(PointShopUsageType.PERMANENT)).and(UI.status.eq(UserItemStatus.OWNED));
    }

    /** 기간제 — 그 회원이 가진 그 품목의 기간 중 가장 늦은 끝(지금 이후만). 또 사면 여기서 이어 붙는다 */
    public Optional<Instant> periodEnd(long userId, long itemId, Instant now) {
        return Optional.ofNullable(query.select(UI.expiresAt.max()).from(UI).where(UI.userId.eq(userId), UI.itemId.eq(itemId),
                UI.usageType.eq(PointShopUsageType.PERIOD), UI.status.ne(UserItemStatus.REVOKED), UI.expiresAt.gt(now)).fetchOne());
    }

    /** 그 회원의 보유 요약 재료(회수되지 않은 줄) — 품목 목록의 {@code mine}·아바타 목록의 보유·환불 판정 */
    public List<HoldingRow> holdings(long userId) {
        return query.select(UI.itemId, UI.usageType, UI.status, UI.expiresAt, UI.firstUsedAt, P.createdAt, P.status, UI.id, UI.reservedAt)
                .from(UI).leftJoin(P).on(P.id.eq(UI.purchaseId)).where(UI.userId.eq(userId), UI.status.ne(UserItemStatus.REVOKED)).fetch().stream() // 지급 줄(구매 없음)도 보유다 — 구매 칸은 null
                .map(t -> new HoldingRow(t.get(UI.itemId), t.get(UI.usageType), t.get(UI.status), t.get(UI.expiresAt), t.get(UI.firstUsedAt), t.get(P.createdAt),
                        t.get(P.status), t.get(UI.id), t.get(UI.reservedAt) != null))
                .toList();
    }

    /** 내 아이템 목록 — 최근에 산 줄이 위 */
    public List<MyItemRow> myItems(long userId) {
        Expression<?>[] columns = Stream.concat(Stream.of(ROW), Stream.of(I.kind, I.name, I.nameEn, I.color, I.borderStyle, I.avatarId, P.pricePoint, P.status,
                P.createdAt, E.slot)).toArray(Expression<?>[]::new);
        return query.select(columns).from(UI).join(I).on(I.id.eq(UI.itemId)).leftJoin(P).on(P.id.eq(UI.purchaseId)).leftJoin(E).on(E.userItemId.eq(UI.id))
                .where(UI.userId.eq(userId)).orderBy(UI.id.desc()).fetch().stream()
                .map(t -> new MyItemRow(rowOf(t), t.get(I.kind), t.get(I.name), t.get(I.nameEn), t.get(I.color), t.get(I.borderStyle), t.get(I.avatarId),
                        t.get(P.pricePoint) == null ? 0 : t.get(P.pricePoint), t.get(P.status), t.get(P.createdAt) != null ? t.get(P.createdAt) : t.get(UI.createdAt),
                        t.get(E.slot), t.get(UI.purchaseId) == null))
                .toList();
    }

    /** 그 보유 줄에 대기 중인 환불 신청이 있나(V87 — 있으면 장착·고르기·셀프 환불·변경권 사용을 막는다) */
    public boolean refundPending(long userItemId) {
        return query.selectOne().from(R).where(R.userItemId.eq(userItemId), R.status.eq(PointShopRefundRequestStatus.PENDING)).fetchFirst() != null;
    }

    /** 처음 장착·사용·고른 시각을 남긴다(이미 있으면 그대로 — 지우지 않는다) */
    public void markFirstUsed(long id, Instant now) {
        query.update(UI).set(UI.firstUsedAt, now).where(UI.id.eq(id), UI.firstUsedAt.isNull()).execute();
    }

    /** 회수(환불) — 아직 회수되지 않았을 때만 */
    public boolean revoke(long id, Instant now) {
        return query.update(UI).set(UI.status, UserItemStatus.REVOKED).set(UI.revokedAt, now).where(UI.id.eq(id), UI.status.ne(UserItemStatus.REVOKED)).execute() == 1;
    }

    // ── 보상 이벤트 지급(V163 — event-admin-contract 「보상 줄」 SHOP_ITEM) ─────────
    // 지급 줄은 구매가 없고(purchase_id NULL) 그 줄 결과(grant_result_id + 순번)를 가리킨다 — 셀프·예외 환불은 구매 기준이라 이 줄에 닿지 않는다.
    // Native SQL(JDBC)인 이유: 보상 줄 처리는 줄마다 JDBC 세이브포인트(IsolatedSideWork) 안에서 돈다 — 그 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용이 된다
    // (repository.md 「인정하는 사유」). 넣기는 INSERT … ON CONFLICT DO NOTHING — 이미 가진 영구 아이템(ux_user_items_permanent)·같은 줄의 같은 순번(ux_user_items_grant)은
    // 실패 대신 0 줄이다.

    /** 지급 한 줄을 넣는다(가진 상태) — 넣었으면 1, 유일 색인에 걸려 넣지 않았으면 0 */
    public int grantFromEvent(long id, long userId, long itemId, long resultId, int seq, PointShopUsageType usageType, Instant startsAt, Instant expiresAt, Instant now) {
        return jdbc.update("INSERT INTO user_items (id, user_id, item_id, purchase_id, grant_result_id, grant_seq, usage_type, status, starts_at, expires_at, created_at) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, ?, 'OWNED', ?, ?, ?) ON CONFLICT DO NOTHING", id, userId, itemId, resultId, seq, usageType.name(), Timestamp.from(startsAt),
                expiresAt == null ? null : Timestamp.from(expiresAt), Timestamp.from(now));
    }

    /** 그 줄 결과로 지급한 줄 중 쓴 것(처음 사용 시각이 있거나·쓴 상태·대기 신청에 예약된 것)이 있나 — 회수 전 판정 */
    public boolean grantedUsed(long resultId) {
        return !jdbc.queryForList("SELECT 1 FROM user_items WHERE grant_result_id = ? AND status <> 'REVOKED' AND (first_used_at IS NOT NULL OR status = 'USED' "
                + "OR reserved_at IS NOT NULL) LIMIT 1", Integer.class, resultId).isEmpty();
    }

    /** 그 줄 결과로 지급한 줄 중 안 쓴 것(가진 상태 · 처음 사용 시각 없음 · 예약 없음)을 회수한다 — 회수한 줄 수 */
    public int revokeGrantedUnused(long resultId, Instant now) {
        return jdbc.update("UPDATE user_items SET status = 'REVOKED', revoked_at = ? WHERE grant_result_id = ? AND status = 'OWNED' AND first_used_at IS NULL "
                + "AND reserved_at IS NULL", Timestamp.from(now), resultId);
    }

    // ── 변경권 ──────────────────────────────────────────────────────────────

    /** 환불 신청 대기 중인 장이 아니다 */
    private static BooleanExpression noPendingRefund() {
        return JPAExpressions.selectOne().from(R).where(R.userItemId.eq(UI.id), R.status.eq(PointShopRefundRequestStatus.PENDING)).notExists();
    }

    /** 안 쓴 변경권 수(환불 신청 대기 중인 장은 빼고) */
    public int countTickets(long userId) {
        Long count = query.select(UI.count()).from(UI).join(I).on(I.id.eq(UI.itemId))
                .where(UI.userId.eq(userId), UI.status.eq(UserItemStatus.OWNED), I.kind.eq(PointShopItemKind.NICKNAME_CHANGE_TICKET), noPendingRefund()).fetchOne();
        return count == null ? 0 : count.intValue();
    }

    /**
     * 변경권 한 장을 쓴다 — 안 쓴 장 중 <b>먼저 산 것</b>(환불 기한이 먼저 끝나는 것)부터. 환불 신청 대기 중인 장은 쓰지 않는다. 다른 트랜잭션이 잡고 있는 장(동시 사용·환불 중)은 건너뛴다({@code SKIP LOCKED}) —
     * 같은 한 장이 두 번 쓰이지 않는다.
     *
     * <p>Native SQL 인 이유: {@code UPDATE … WHERE id = (SELECT … FOR UPDATE OF ui SKIP LOCKED) … RETURNING id} — 고르기·잠그기·고치기·고친 줄 받기가 한 문장이어야 한다(클래스 설명).
     *
     * @return 쓴 보유 줄 ID(쓸 장이 없으면 빈 값)
     */
    public Optional<Long> useTicket(long userId, Instant now) {
        return firstLong(entityManager.createNativeQuery("UPDATE user_items SET status = 'USED', used_at = ?1, first_used_at = coalesce(first_used_at, ?1) WHERE id = ("
                        + "SELECT ui.id FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE ui.user_id = ?2 AND ui.status = 'OWNED' "
                        + "AND i.kind = 'NICKNAME_CHANGE_TICKET' AND NOT EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.user_item_id = ui.id AND r.status = 'PENDING') "
                        + "ORDER BY ui.id LIMIT 1 FOR UPDATE OF ui SKIP LOCKED) AND status = 'OWNED' RETURNING id")
                .setParameter(1, ts(now)).setParameter(2, userId).getResultList());
    }

    // ── 올리기 변경권(V157 — commerce-motion-avatar-contract 규칙 0) ─────────────
    // 예약 = OWNED 그대로 reserved_at·ref_type·ref_id 를 채운다 · 반환 = 셋을 지운다 · 사용 = USED + reserved_at 만 지운다(ref 는 남는다 — 어느 신청이 썼나) · 대체 = ref_id 만 옮긴다.

    /** 쓸 수 있는 올리기 변경권 수 — 예약된 장·환불 신청 대기 중인 장은 빼고 */
    public int countUploadTickets(long userId, UploadTicketUse use) {
        Long count = query.select(UI.count()).from(UI).join(I).on(I.id.eq(UI.itemId))
                .where(UI.userId.eq(userId), UI.status.eq(UserItemStatus.OWNED), UI.reservedAt.isNull(), I.kind.eq(use.kind()), noPendingRefund()).fetchOne();
        return count == null ? 0 : count.intValue();
    }

    /**
     * 변경권 한 장을 그 신청에 예약한다 — 쓸 수 있는 장 중 <b>먼저 산 것</b>부터. 다른 트랜잭션이 잡은 장(동시 신청·환불 중)은 건너뛴다({@code SKIP LOCKED}) —
     * 같은 한 장이 두 신청에 예약되지 않는다(신청 하나에 장 하나는 {@code ux_user_items_ref} 가 끝까지 막는다).
     *
     * <p>Native SQL 인 이유: {@link #useTicket} 과 같다({@code FOR UPDATE OF ui SKIP LOCKED} 로 고른 한 줄의 조건부 UPDATE + {@code RETURNING}).
     *
     * @return 예약한 보유 줄 ID(쓸 장이 없으면 빈 값)
     */
    public Optional<Long> reserveUploadTicket(long userId, UploadTicketUse use, long requestId, Instant now) {
        return firstLong(entityManager.createNativeQuery("UPDATE user_items SET reserved_at = ?1, ref_type = ?2, ref_id = ?3 WHERE id = ("
                        + "SELECT ui.id FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE ui.user_id = ?4 AND ui.status = 'OWNED' AND ui.reserved_at IS NULL "
                        + "AND i.kind = ?5 AND NOT EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.user_item_id = ui.id AND r.status = 'PENDING') "
                        + "ORDER BY ui.id LIMIT 1 FOR UPDATE OF ui SKIP LOCKED) AND status = 'OWNED' AND reserved_at IS NULL RETURNING id")
                .setParameter(1, ts(now)).setParameter(2, use.name()).setParameter(3, requestId).setParameter(4, userId).setParameter(5, use.kind().name())
                .getResultList());
    }

    /** 그 신청에 예약된(아직 쓰지 않은) 장 — 없으면 빈 값 */
    public Optional<UserItemRow> reservedFor(UploadTicketUse use, long requestId) {
        return one(rows().where(UI.refType.eq(use), UI.refId.eq(requestId), UI.reservedAt.isNotNull()));
    }

    /** 그 신청에 이어진 장(예약 중이거나 그 신청이 쓴 장) — 없으면 빈 값 */
    public Optional<UserItemRow> ticketOf(UploadTicketUse use, long requestId) {
        return one(rows().where(UI.refType.eq(use), UI.refId.eq(requestId)));
    }

    /** 예약 중인 그 신청의 장(OWNED) */
    private static BooleanExpression reservedOwned(UploadTicketUse use, long requestId) {
        return UI.refType.eq(use).and(UI.refId.eq(requestId)).and(UI.reservedAt.isNotNull()).and(UI.status.eq(UserItemStatus.OWNED));
    }

    /** 대기 신청을 새 신청이 대체한다 — 예약된 장을 그대로 새 신청으로 옮긴다(한 장 더 들지 않는다). 옮겼으면 true */
    public boolean transferUploadTicket(UploadTicketUse use, long fromRequestId, long toRequestId) {
        return query.update(UI).set(UI.refId, toRequestId).where(reservedOwned(use, fromRequestId)).execute() == 1;
    }

    /** 반환(반려·본인 취소) — 예약을 푼다. 풀었으면 true(예약한 장이 없던 신청이면 false) */
    public boolean releaseUploadTicket(UploadTicketUse use, long requestId) {
        return query.update(UI).setNull(UI.reservedAt).setNull(UI.refType).setNull(UI.refId).where(reservedOwned(use, requestId)).execute() == 1;
    }

    /** 사용(승인) — 예약된 장을 USED 로. 썼으면 true(예약한 장이 없던 신청이면 false) */
    public boolean consumeUploadTicket(UploadTicketUse use, long requestId, Instant now) {
        return query.update(UI).set(UI.status, UserItemStatus.USED).set(UI.usedAt, now).set(UI.firstUsedAt, UI.firstUsedAt.coalesce(now)).setNull(UI.reservedAt)
                .where(reservedOwned(use, requestId)).execute() == 1;
    }

    /** 여러 신청에 이어진 장 — 신청 ID → 보유 줄(관리자 목록의 변경권 칸) */
    public Map<Long, UserItemRow> ticketsOf(UploadTicketUse use, Collection<Long> requestIds) {
        Map<Long, UserItemRow> out = new HashMap<>();
        if (requestIds.isEmpty()) {
            return out;
        }
        rows().where(UI.refType.eq(use), UI.refId.in(requestIds)).fetch().stream().map(UserItemStore::rowOf).forEach(row -> out.put(row.refId(), row));
        return out;
    }

    // ── 장착 ────────────────────────────────────────────────────────────────

    /** 그 회원의 장착 자리(만료된 것 포함 — 거르는 것은 부르는 쪽) */
    public List<EquippedRow> equipped(long userId) {
        return equippedOf(List.of(userId));
    }

    /** 여러 회원의 장착 자리 — 작성자 장식 표시(표 하나 + 품목 하나) */
    public List<EquippedRow> equippedOf(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return query.select(E.userId, E.slot, E.userItemId, E.itemId, E.expiresAt, I.color, I.borderStyle).from(E).join(I).on(I.id.eq(E.itemId))
                .where(E.userId.in(userIds)).fetch().stream()
                .map(t -> new EquippedRow(t.get(E.userId), t.get(E.slot), t.get(E.userItemId), t.get(E.itemId), t.get(E.expiresAt), t.get(I.color), t.get(I.borderStyle)))
                .toList();
    }

    /**
     * 그 자리에 장착한다(있으면 바꾼다) — 품목 번호·기간 끝은 보유 줄의 사본.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT (user_id, slot) DO UPDATE}(upsert) — 「찾아보고 없으면 넣는다」로 나누면 동시 장착이 유일 위반으로 깨진다.
     */
    public void equip(long userId, EquipSlot slot, long userItemId, long itemId, Instant expiresAt, Instant now) {
        entityManager.createNativeQuery("INSERT INTO user_equipped (user_id, slot, user_item_id, item_id, expires_at, equipped_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6) "
                        + "ON CONFLICT (user_id, slot) DO UPDATE SET user_item_id = EXCLUDED.user_item_id, item_id = EXCLUDED.item_id, expires_at = EXCLUDED.expires_at, "
                        + "equipped_at = EXCLUDED.equipped_at")
                .setParameter(1, userId).setParameter(2, slot.name()).setParameter(3, userItemId).setParameter(4, itemId).setParameter(5, ts(expiresAt))
                .setParameter(6, ts(now)).executeUpdate();
    }

    public void unequip(long userId, EquipSlot slot) {
        query.delete(E).where(E.userId.eq(userId), E.slot.eq(slot)).execute();
    }

    /** 그 보유 줄의 장착을 푼다(환불) — 풀었으면 true */
    public boolean unequipItem(long userItemId) {
        return query.delete(E).where(E.userItemId.eq(userItemId)).execute() > 0;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> rows() {
        return query.select(ROW).from(UI);
    }

    /** 맞는 줄의 첫 줄 — LIMIT 을 붙이지 않는다(잠그는 질의는 맞는 줄을 모두 잠근다 — JDBC 때와 같다) */
    private static Optional<UserItemRow> one(JPAQuery<Tuple> select) {
        return select.fetch().stream().findFirst().map(UserItemStore::rowOf);
    }

    private static UserItemRow rowOf(Tuple t) {
        return new UserItemRow(t.get(UI.id), t.get(UI.userId), t.get(UI.itemId), t.get(UI.purchaseId), t.get(UI.usageType), t.get(UI.status), t.get(UI.startsAt),
                t.get(UI.expiresAt), t.get(UI.firstUsedAt), t.get(UI.usedAt), t.get(UI.revokedAt), t.get(UI.createdAt), t.get(UI.reservedAt), t.get(UI.refType),
                t.get(UI.refId));
    }

    /** native {@code RETURNING} 한 칸 결과의 첫 값 */
    private static Optional<Long> firstLong(List<?> rows) {
        return rows.stream().findFirst().map(v -> ((Number) v).longValue());
    }
}
