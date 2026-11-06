package com.community.pointshop.repository.master;

import static com.community.pointshop.repository.master.PointShopItemStore.instant;
import static com.community.pointshop.repository.master.PointShopItemStore.marks;
import static com.community.pointshop.repository.master.PointShopItemStore.ts;

import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.entity.UserItemStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 보유 아이템({@code user_items})·장착({@code user_equipped}) 읽기·쓰기 — {@code com.community.pointshop.service} 만 부른다.
 *
 * <p>JDBC 인 이유: {@link PointShopItemStore} 와 같고, 더해서 <b>변경권 한 장 쓰기</b>는 {@code FOR UPDATE SKIP LOCKED} 로 고른 한 줄을 조건부 UPDATE 하는 한 문장이다 —
 * 같은 회원의 동시 요청 둘이 같은 한 장을 쓰지 못한다(다른 장이 있으면 그 장을, 없으면 0 줄). 장착은 {@code INSERT … ON CONFLICT (user_id, slot) DO UPDATE}.
 */
@Repository
public class UserItemStore {

    /** 보유 줄 하나 */
    public record UserItemRow(long id, long userId, long itemId, long purchaseId, PointShopUsageType usageType, UserItemStatus status, Instant startsAt,
                              Instant expiresAt, Instant firstUsedAt, Instant usedAt, Instant revokedAt, Instant createdAt, Instant reservedAt, UploadTicketUse refType,
                              Long refId) {

        /** 기간제의 기간이 {@code now} 에 끝났나 */
        public boolean expiredAt(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }

    /** 내 아이템 목록 한 줄 — 보유 줄 + 품목의 지금 표시값 + 구매 사본(낸 값·상태·시각) + 장착 자리 */
    public record MyItemRow(UserItemRow item, PointShopItemKind kind, String name, String nameEn, String color, String borderStyle, String avatarId, int pricePaid,
                            PointShopPurchaseStatus purchaseStatus, Instant purchasedAt, EquipSlot equippedSlot) {
    }

    /** 장착 한 자리 — 품목의 지금 색·테두리(색을 고치면 산 사람의 표시도 바뀐다) */
    public record EquippedRow(long userId, EquipSlot slot, long userItemId, long itemId, Instant expiresAt, String color, String borderStyle) {

        public boolean liveAt(Instant now) {
            return expiresAt == null || expiresAt.isAfter(now);
        }
    }

    /** 품목별 내 보유 요약 재료 — 회수되지 않은 줄만 */
    public record HoldingRow(long itemId, PointShopUsageType usageType, UserItemStatus status, Instant expiresAt, Instant firstUsedAt, Instant purchasedAt,
                             PointShopPurchaseStatus purchaseStatus, long userItemId, boolean reserved) {
    }

    private static final String COLUMNS = "ui.id, ui.user_id, ui.item_id, ui.purchase_id, ui.usage_type, ui.status, ui.starts_at, ui.expires_at, ui.first_used_at, "
            + "ui.used_at, ui.revoked_at, ui.created_at, ui.reserved_at, ui.ref_type, ui.ref_id";

    private final JdbcTemplate jdbc;

    public UserItemStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 보유 ────────────────────────────────────────────────────────────────

    public void insert(UserItemRow row) {
        jdbc.update("INSERT INTO user_items (id, user_id, item_id, purchase_id, usage_type, status, starts_at, expires_at, created_at) VALUES (?, ?, ?, ?, ?, 'OWNED', ?, ?, ?)",
                row.id(), row.userId(), row.itemId(), row.purchaseId(), row.usageType().name(), ts(row.startsAt()), ts(row.expiresAt()), ts(row.createdAt()));
    }

    public Optional<UserItemRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.id = ?", UserItemStore::rowOf, id).stream().findFirst();
    }

    /** 장착·환불·고르기 전에 — 줄을 잠그고 읽는다 */
    public Optional<UserItemRow> findForUpdate(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.id = ? FOR UPDATE", UserItemStore::rowOf, id).stream().findFirst();
    }

    public Optional<UserItemRow> byPurchaseForUpdate(long purchaseId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.purchase_id = ? FOR UPDATE", UserItemStore::rowOf, purchaseId).stream().findFirst();
    }

    /** 그 회원이 그 영구 품목을 (회수되지 않게) 가졌나 */
    public boolean ownsPermanent(long userId, long itemId) {
        return !jdbc.queryForList("SELECT 1 FROM user_items WHERE user_id = ? AND item_id = ? AND usage_type = 'PERMANENT' AND status <> 'REVOKED'", Integer.class, userId,
                itemId).isEmpty();
    }

    /** 그 회원이 가진(회수되지 않은) 그 영구 품목 줄 — 잠근다(아바타 고르기) */
    public Optional<UserItemRow> ownedPermanentForUpdate(long userId, long itemId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.user_id = ? AND ui.item_id = ? AND ui.usage_type = 'PERMANENT' AND ui.status = 'OWNED' FOR UPDATE",
                UserItemStore::rowOf, userId, itemId).stream().findFirst();
    }

    /** 그 회원이 가진(회수되지 않은) 그 영구 품목 줄 — 잠그지 않는다(마지막 정지 아바타 되돌리기의 보유 확인 — 프로필 행을 먼저 잠근 쪽이 부른다) */
    public Optional<UserItemRow> ownedPermanent(long userId, long itemId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.user_id = ? AND ui.item_id = ? AND ui.usage_type = 'PERMANENT' AND ui.status = 'OWNED'",
                UserItemStore::rowOf, userId, itemId).stream().findFirst();
    }

    /** 기간제 — 그 회원이 가진 그 품목의 기간 중 가장 늦은 끝(지금 이후만). 또 사면 여기서 이어 붙는다 */
    public Optional<Instant> periodEnd(long userId, long itemId, Instant now) {
        Instant end = jdbc.queryForObject("SELECT max(expires_at) FROM user_items WHERE user_id = ? AND item_id = ? AND usage_type = 'PERIOD' AND status <> 'REVOKED' "
                + "AND expires_at > ?", (rs, i) -> instant(rs, 1), userId, itemId, ts(now));
        return Optional.ofNullable(end);
    }

    /** 그 회원의 보유 요약 재료(회수되지 않은 줄) — 품목 목록의 {@code mine}·아바타 목록의 보유·환불 판정 */
    public List<HoldingRow> holdings(long userId) {
        return jdbc.query("SELECT ui.item_id, ui.usage_type, ui.status, ui.expires_at, ui.first_used_at, p.created_at, p.status, ui.id, ui.reserved_at IS NOT NULL "
                + "FROM user_items ui JOIN point_shop_purchases p ON p.id = ui.purchase_id WHERE ui.user_id = ? AND ui.status <> 'REVOKED'",
                (rs, i) -> new HoldingRow(rs.getLong(1), PointShopUsageType.valueOf(rs.getString(2)), UserItemStatus.valueOf(rs.getString(3)), instant(rs, 4),
                        instant(rs, 5), instant(rs, 6), PointShopPurchaseStatus.valueOf(rs.getString(7)), rs.getLong(8), rs.getBoolean(9)), userId);
    }

    /** 내 아이템 목록 — 최근에 산 줄이 위 */
    public List<MyItemRow> myItems(long userId) {
        return jdbc.query("SELECT " + COLUMNS + ", i.kind, i.name, i.name_en, i.color, i.border_style, i.avatar_id, p.price_point, p.status, p.created_at, e.slot "
                + "FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id JOIN point_shop_purchases p ON p.id = ui.purchase_id "
                + "LEFT JOIN user_equipped e ON e.user_item_id = ui.id WHERE ui.user_id = ? ORDER BY ui.id DESC",
                (rs, i) -> new MyItemRow(rowOf(rs, i), PointShopItemKind.valueOf(rs.getString(16)), rs.getString(17), rs.getString(18), rs.getString(19), rs.getString(20),
                        rs.getString(21), rs.getInt(22), PointShopPurchaseStatus.valueOf(rs.getString(23)), instant(rs, 24),
                        rs.getString(25) == null ? null : EquipSlot.valueOf(rs.getString(25))), userId);
    }

    /** 그 보유 줄에 대기 중인 환불 신청이 있나(V87 — 있으면 장착·고르기·셀프 환불·변경권 사용을 막는다) */
    public boolean refundPending(long userItemId) {
        return !jdbc.queryForList("SELECT 1 FROM point_shop_refund_requests WHERE user_item_id = ? AND status = 'PENDING'", Integer.class, userItemId).isEmpty();
    }

    /** 처음 장착·사용·고른 시각을 남긴다(이미 있으면 그대로 — 지우지 않는다) */
    public void markFirstUsed(long id, Instant now) {
        jdbc.update("UPDATE user_items SET first_used_at = ? WHERE id = ? AND first_used_at IS NULL", ts(now), id);
    }

    /** 회수(환불) — 아직 회수되지 않았을 때만 */
    public boolean revoke(long id, Instant now) {
        return jdbc.update("UPDATE user_items SET status = 'REVOKED', revoked_at = ? WHERE id = ? AND status <> 'REVOKED'", ts(now), id) == 1;
    }

    // ── 변경권 ──────────────────────────────────────────────────────────────

    /** 안 쓴 변경권 수(환불 신청 대기 중인 장은 빼고) */
    public int countTickets(long userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE ui.user_id = ? AND ui.status = 'OWNED' "
                + "AND i.kind = 'NICKNAME_CHANGE_TICKET' AND NOT EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.user_item_id = ui.id AND r.status = 'PENDING')", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /**
     * 변경권 한 장을 쓴다 — 안 쓴 장 중 <b>먼저 산 것</b>(환불 기한이 먼저 끝나는 것)부터. 환불 신청 대기 중인 장은 쓰지 않는다. 다른 트랜잭션이 잡고 있는 장(동시 사용·환불 중)은 건너뛴다({@code SKIP LOCKED}) —
     * 같은 한 장이 두 번 쓰이지 않는다.
     *
     * @return 쓴 보유 줄 ID(쓸 장이 없으면 빈 값)
     */
    public Optional<Long> useTicket(long userId, Instant now) {
        return jdbc.queryForList("UPDATE user_items SET status = 'USED', used_at = ?, first_used_at = coalesce(first_used_at, ?) WHERE id = ("
                + "SELECT ui.id FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE ui.user_id = ? AND ui.status = 'OWNED' "
                + "AND i.kind = 'NICKNAME_CHANGE_TICKET' AND NOT EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.user_item_id = ui.id AND r.status = 'PENDING') ORDER BY ui.id LIMIT 1 FOR UPDATE OF ui SKIP LOCKED) AND status = 'OWNED' RETURNING id", Long.class, ts(now), ts(now),
                userId).stream().findFirst();
    }

    // ── 올리기 변경권(V157 — commerce-motion-avatar-contract 규칙 0) ─────────────
    // 예약 = OWNED 그대로 reserved_at·ref_type·ref_id 를 채운다 · 반환 = 셋을 지운다 · 사용 = USED + reserved_at 만 지운다(ref 는 남는다 — 어느 신청이 썼나) · 대체 = ref_id 만 옮긴다.

    private static final String FREE_UPLOAD_TICKET = "ui.user_id = ? AND ui.status = 'OWNED' AND ui.reserved_at IS NULL AND i.kind = ? "
            + "AND NOT EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.user_item_id = ui.id AND r.status = 'PENDING')";

    /** 쓸 수 있는 올리기 변경권 수 — 예약된 장·환불 신청 대기 중인 장은 빼고 */
    public int countUploadTickets(long userId, UploadTicketUse use) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE " + FREE_UPLOAD_TICKET, Integer.class,
                userId, use.kind().name());
        return count == null ? 0 : count;
    }

    /**
     * 변경권 한 장을 그 신청에 예약한다 — 쓸 수 있는 장 중 <b>먼저 산 것</b>부터. 다른 트랜잭션이 잡은 장(동시 신청·환불 중)은 건너뛴다({@code SKIP LOCKED}) —
     * 같은 한 장이 두 신청에 예약되지 않는다(신청 하나에 장 하나는 {@code ux_user_items_ref} 가 끝까지 막는다).
     *
     * @return 예약한 보유 줄 ID(쓸 장이 없으면 빈 값)
     */
    public Optional<Long> reserveUploadTicket(long userId, UploadTicketUse use, long requestId, Instant now) {
        return jdbc.queryForList("UPDATE user_items SET reserved_at = ?, ref_type = ?, ref_id = ? WHERE id = ("
                + "SELECT ui.id FROM user_items ui JOIN point_shop_items i ON i.id = ui.item_id WHERE " + FREE_UPLOAD_TICKET
                + " ORDER BY ui.id LIMIT 1 FOR UPDATE OF ui SKIP LOCKED) AND status = 'OWNED' AND reserved_at IS NULL RETURNING id", Long.class, ts(now), use.name(), requestId,
                userId, use.kind().name()).stream().findFirst();
    }

    /** 그 신청에 예약된(아직 쓰지 않은) 장 — 없으면 빈 값 */
    public Optional<UserItemRow> reservedFor(UploadTicketUse use, long requestId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.ref_type = ? AND ui.ref_id = ? AND ui.reserved_at IS NOT NULL", UserItemStore::rowOf, use.name(),
                requestId).stream().findFirst();
    }

    /** 그 신청에 이어진 장(예약 중이거나 그 신청이 쓴 장) — 없으면 빈 값 */
    public Optional<UserItemRow> ticketOf(UploadTicketUse use, long requestId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.ref_type = ? AND ui.ref_id = ?", UserItemStore::rowOf, use.name(), requestId).stream()
                .findFirst();
    }

    /** 대기 신청을 새 신청이 대체한다 — 예약된 장을 그대로 새 신청으로 옮긴다(한 장 더 들지 않는다). 옮겼으면 true */
    public boolean transferUploadTicket(UploadTicketUse use, long fromRequestId, long toRequestId) {
        return jdbc.update("UPDATE user_items SET ref_id = ? WHERE ref_type = ? AND ref_id = ? AND reserved_at IS NOT NULL AND status = 'OWNED'", toRequestId, use.name(),
                fromRequestId) == 1;
    }

    /** 반환(반려·본인 취소) — 예약을 푼다. 풀었으면 true(예약한 장이 없던 신청이면 false) */
    public boolean releaseUploadTicket(UploadTicketUse use, long requestId) {
        return jdbc.update("UPDATE user_items SET reserved_at = NULL, ref_type = NULL, ref_id = NULL WHERE ref_type = ? AND ref_id = ? AND reserved_at IS NOT NULL "
                + "AND status = 'OWNED'", use.name(), requestId) == 1;
    }

    /** 사용(승인) — 예약된 장을 USED 로. 썼으면 true(예약한 장이 없던 신청이면 false) */
    public boolean consumeUploadTicket(UploadTicketUse use, long requestId, Instant now) {
        return jdbc.update("UPDATE user_items SET status = 'USED', used_at = ?, first_used_at = coalesce(first_used_at, ?), reserved_at = NULL "
                + "WHERE ref_type = ? AND ref_id = ? AND reserved_at IS NOT NULL AND status = 'OWNED'", ts(now), ts(now), use.name(), requestId) == 1;
    }

    /** 여러 신청에 이어진 장 — 신청 ID → 보유 줄(관리자 목록의 변경권 칸) */
    public Map<Long, UserItemRow> ticketsOf(UploadTicketUse use, Collection<Long> requestIds) {
        Map<Long, UserItemRow> out = new HashMap<>();
        if (requestIds.isEmpty()) {
            return out;
        }
        List<Object> args = new ArrayList<>();
        args.add(use.name());
        args.addAll(requestIds);
        for (UserItemRow row : jdbc.query("SELECT " + COLUMNS + " FROM user_items ui WHERE ui.ref_type = ? AND ui.ref_id IN (" + marks(requestIds.size()) + ")",
                UserItemStore::rowOf, args.toArray())) {
            out.put(row.refId(), row);
        }
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
        return jdbc.query("SELECT e.user_id, e.slot, e.user_item_id, e.item_id, e.expires_at, i.color, i.border_style FROM user_equipped e "
                + "JOIN point_shop_items i ON i.id = e.item_id WHERE e.user_id IN (" + marks(userIds.size()) + ")",
                (rs, i) -> new EquippedRow(rs.getLong(1), EquipSlot.valueOf(rs.getString(2)), rs.getLong(3), rs.getLong(4), instant(rs, 5), rs.getString(6),
                        rs.getString(7)), new ArrayList<>(userIds).toArray());
    }

    /** 그 자리에 장착한다(있으면 바꾼다) — 품목 번호·기간 끝은 보유 줄의 사본 */
    public void equip(long userId, EquipSlot slot, long userItemId, long itemId, Instant expiresAt, Instant now) {
        jdbc.update("INSERT INTO user_equipped (user_id, slot, user_item_id, item_id, expires_at, equipped_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (user_id, slot) DO UPDATE SET user_item_id = EXCLUDED.user_item_id, item_id = EXCLUDED.item_id, expires_at = EXCLUDED.expires_at, "
                + "equipped_at = EXCLUDED.equipped_at", userId, slot.name(), userItemId, itemId, ts(expiresAt), ts(now));
    }

    public void unequip(long userId, EquipSlot slot) {
        jdbc.update("DELETE FROM user_equipped WHERE user_id = ? AND slot = ?", userId, slot.name());
    }

    /** 그 보유 줄의 장착을 푼다(환불) — 풀었으면 true */
    public boolean unequipItem(long userItemId) {
        return jdbc.update("DELETE FROM user_equipped WHERE user_item_id = ?", userItemId) > 0;
    }

    private static UserItemRow rowOf(ResultSet rs, int i) throws SQLException {
        String refType = rs.getString(14);
        long refId = rs.getLong(15);
        Long ref = rs.wasNull() ? null : refId;
        return new UserItemRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), PointShopUsageType.valueOf(rs.getString(5)),
                UserItemStatus.valueOf(rs.getString(6)), instant(rs, 7), instant(rs, 8), instant(rs, 9), instant(rs, 10), instant(rs, 11), instant(rs, 12),
                instant(rs, 13), refType == null ? null : UploadTicketUse.valueOf(refType), ref);
    }
}
