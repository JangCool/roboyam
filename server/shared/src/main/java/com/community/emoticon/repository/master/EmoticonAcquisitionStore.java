package com.community.emoticon.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 보유({@code emoticon_acquisitions}, V140 · V142) — (회원, 팩) 하나에 줄 하나. 출처 {@code kind} = {@code FREE}(무료 받기) · {@code PURCHASE}(유료 구매 — {@code purchase_id}).
 * 빼거나 환불하면 {@code removed_at} 만 찍고 줄은 남긴다(다시 가지면 지운다) — 받기 보상·받은 수는 그 줄의 처음 한 번만이다. 토큰 검사·내 로보티콘·팩의 {@code acquired} 는
 * 출처와 상관없이 이 표의 「지금 가진 줄」을 본다. Native SQL 인 이유는 {@link EmoticonPackStore} 와 같다.
 *
 * <p><b>작가 자기 팩(후속 결정 1, 2026-11-05)</b>: 작가는 상점에 나온(판매 중·판매 중단) 자기 팩을 사거나 받지 않아도 가진 것으로 본다 — <b>파생 규칙</b>
 * ({@code emoticon_packs.creator_id = 나 AND status IN ('APPROVED','SUSPENDED')}, {@link #CREATOR_OWNED})이고 받기 줄을 만들지 않는다. 그래서 받은 수·받기 보상·판매·정산에 들어가지 않고,
 * 팩 주인이 바뀌거나(새 주인이 곧바로 가진다) 판매 중단돼도(가진 사람처럼 계속 쓴다) 따로 맞출 줄이 없다. 「가졌는가」를 묻는 곳({@link #ownedAmong} · {@link #owned} ·
 * {@link #creatorOwns})이 모두 이 규칙을 같이 쓴다 — 토큰 검사·내 로보티콘·팩의 {@code acquired}·결제 준비·결제 확정.
 */
@Repository
public class EmoticonAcquisitionStore {

    public static final String FREE = "FREE";
    public static final String PURCHASE = "PURCHASE";

    /** 보유 한 줄 — {@code removedAt} 이 있으면 지금은 가지지 않았다(목록에서 뺐다 · 환불했다) */
    public record AcquisitionRow(long id, long userId, long packId, String kind, Long purchaseId, Instant firstAcquiredAt, Instant acquiredAt, Instant removedAt) {

        public boolean owned() {
            return removedAt == null;
        }

        public boolean purchased() {
            return PURCHASE.equals(kind);
        }
    }

    /** 지금 가진 팩 한 개 — 산 팩이면 구매 ID·결제 금액, 내가 만든 팩이면 {@code creator}(가진 시각 = 상점에 처음 나온 시각) */
    public record OwnedPack(long packId, Instant acquiredAt, Long purchaseId, int purchaseAmount, boolean creator) {
    }

    /** 작가 자기 팩의 파생 규칙(별칭 {@code k} = {@code emoticon_packs}) — 상점에 나온 팩(판매 중·판매 중단)의 작가 */
    public static final String CREATOR_OWNED = "k.status IN ('APPROVED', 'SUSPENDED')";

    private final JdbcTemplate jdbc;

    public EmoticonAcquisitionStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<AcquisitionRow> find(long userId, long packId) {
        return jdbc.query("SELECT id, user_id, pack_id, kind, purchase_id, first_acquired_at, acquired_at, removed_at FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?",
                (rs, i) -> new AcquisitionRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), (Long) rs.getObject(5, Long.class),
                        EmoticonPackStore.instant(rs, 6), EmoticonPackStore.instant(rs, 7), EmoticonPackStore.instant(rs, 8)), userId, packId).stream().findFirst();
    }

    /** 무료 받기 — 새 줄 */
    public void insert(long id, long userId, long packId, Instant now) {
        jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, first_acquired_at, acquired_at) VALUES (?, ?, ?, 'FREE', ?, ?)", id, userId, packId,
                Timestamp.from(now), Timestamp.from(now));
    }

    /** 유료 구매 — 새 줄 */
    public void insertPurchase(long id, long userId, long packId, long purchaseId, Instant now) {
        jdbc.update("INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, purchase_id, first_acquired_at, acquired_at) VALUES (?, ?, ?, 'PURCHASE', ?, ?, ?)", id,
                userId, packId, purchaseId, Timestamp.from(now), Timestamp.from(now));
    }

    /** 환불해 보유를 뺐던 산 줄을 새 구매로 다시 가진다 */
    public boolean repurchase(long id, long purchaseId, Instant now) {
        return jdbc.update("UPDATE emoticon_acquisitions SET purchase_id = ?, acquired_at = ?, removed_at = NULL WHERE id = ? AND kind = 'PURCHASE' AND removed_at IS NOT NULL",
                purchaseId, Timestamp.from(now), id) == 1;
    }

    /** 뺀 무료 팩을 다시 받는다(받은 시각만 새로) */
    public void restore(long id, Instant now) {
        jdbc.update("UPDATE emoticon_acquisitions SET acquired_at = ?, removed_at = NULL WHERE id = ? AND kind = 'FREE' AND removed_at IS NOT NULL", Timestamp.from(now), id);
    }

    /** 환불했던 산 줄 — 그 팩이 무료가 된 뒤 받으면 무료 받기로 다시 가진다(받은 수·보상은 줄마다 처음 한 번이라 새로 생기지 않는다) */
    public void restoreAsFree(long id, Instant now) {
        jdbc.update("UPDATE emoticon_acquisitions SET kind = 'FREE', purchase_id = NULL, acquired_at = ?, removed_at = NULL WHERE id = ? AND kind = 'PURCHASE' "
                + "AND removed_at IS NOT NULL", Timestamp.from(now), id);
    }

    /** 무료로 받은 팩을 목록에서 뺀다 — 가지고 있을 때만(바뀐 줄 수). 산 팩은 빼지 않는다(환불은 구매 내역) */
    public int remove(long userId, long packId, Instant now) {
        return jdbc.update("UPDATE emoticon_acquisitions SET removed_at = ? WHERE user_id = ? AND pack_id = ? AND kind = 'FREE' AND removed_at IS NULL", Timestamp.from(now),
                userId, packId);
    }

    /** 환불 — 그 구매로 가진 줄의 보유를 뺀다(바뀐 줄 수) */
    public int revokePurchase(long purchaseId, Instant now) {
        return jdbc.update("UPDATE emoticon_acquisitions SET removed_at = ? WHERE purchase_id = ? AND kind = 'PURCHASE' AND removed_at IS NULL", Timestamp.from(now), purchaseId);
    }

    /** 이 팩들 중 회원이 지금 가진 것(받기·구매 줄 + 내가 만든 상점 팩) */
    public Set<Long> ownedAmong(long userId, Collection<Long> packIds) {
        if (packIds.isEmpty()) {
            return Set.of();
        }
        String marks = EmoticonPackStore.marks(packIds.size());
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        args.addAll(packIds);
        args.add(userId);
        args.addAll(packIds);
        return new HashSet<>(jdbc.queryForList("SELECT pack_id FROM emoticon_acquisitions WHERE user_id = ? AND removed_at IS NULL AND pack_id IN (" + marks + ") "
                + "UNION SELECT k.id FROM emoticon_packs k WHERE k.creator_id = ? AND " + CREATOR_OWNED + " AND k.id IN (" + marks + ")", Long.class, args.toArray()));
    }

    /** 회원이 상점에 나온 그 팩의 작가인가(작가 자기 팩 — 사지 않아도 가진다) */
    public boolean creatorOwns(long userId, long packId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM emoticon_packs k WHERE k.id = ? AND k.creator_id = ? AND " + CREATOR_OWNED, Integer.class, packId, userId);
        return n != null && n > 0;
    }

    /**
     * 회원이 지금 가진 팩(최근 가진 것 먼저) — 산 팩이면 구매 ID·결제 금액. 내가 만든 상점 팩은 받기 줄이 없어도 들어가고({@code creator}, 가진 시각 = 처음 나온 시각),
     * 같은 팩에 받기 줄이 남아 있어도(규칙 전 개발 데이터) 한 번만 — 작가 쪽으로 센다.
     */
    public Map<Long, OwnedPack> owned(long userId) {
        Map<Long, OwnedPack> out = new LinkedHashMap<>();
        jdbc.query("SELECT pack_id, at, purchase_id, amount, creator FROM ("
                + "SELECT a.pack_id, a.acquired_at AS at, a.purchase_id, coalesce(p.amount, 0) AS amount, false AS creator, a.id AS tie FROM emoticon_acquisitions a "
                + "LEFT JOIN emoticon_purchases p ON p.id = a.purchase_id WHERE a.user_id = ? AND a.removed_at IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM emoticon_packs k WHERE k.id = a.pack_id AND k.creator_id = a.user_id AND " + CREATOR_OWNED + ") "
                + "UNION ALL SELECT k.id, coalesce(k.published_at, k.created_at), NULL, 0, true, k.id FROM emoticon_packs k WHERE k.creator_id = ? AND " + CREATOR_OWNED
                + ") x ORDER BY at DESC, tie DESC", rs -> {
                    out.put(rs.getLong(1), new OwnedPack(rs.getLong(1), rs.getTimestamp(2).toInstant(), (Long) rs.getObject(3, Long.class), rs.getInt(4), rs.getBoolean(5)));
                }, userId, userId);
        return out;
    }
}
