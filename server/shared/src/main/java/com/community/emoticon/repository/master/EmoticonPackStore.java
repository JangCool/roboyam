package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonSaleWish;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 팩·판·로보티콘(토큰 ID)·판의 차례 읽기·쓰기({@code emoticon_packs}·{@code emoticon_pack_revisions}·{@code emoticons}·{@code emoticon_revision_items}, V142).
 * {@code com.community.emoticon.service} 만 부른다.
 *
 * <p>Native SQL(JDBC)인 이유: (1) 받기는 팩 행을 잠근 뒤 받은 수를 올리고 같은 트랜잭션·같은 연결에서 작가 포인트({@code PointLedgerWriter}, JDBC)를 적립한다 — 잠금 차례(팩 → 잔액)를
 * 지킨다(포인트 상점 {@code PointShopItemStore} 와 같은 방식). (2) 판·팩이 서로 가리키는 외래 키(커밋 때 확인)·태그 배열({@code text[]})·낸 판 고치기 금지 트리거를 그대로 쓴다.
 * (3) 시험이 스프링 문맥 없이 실제 PostgreSQL 위에서 서비스를 손으로 조립한다(commerce.md 18절). 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class EmoticonPackStore {

    /** 팩 한 줄(판매 중단한 운영자 이름 포함 — 없으면 null) */
    public record PackRow(long id, EmoticonPackSource source, Long creatorId, EmoticonPackStatus status, int price, Long liveRevisionId, int acquireCount,
                          Instant publishedAt, String suspendReason, Instant suspendedAt, Long suspendedBy, String suspendedByName, Instant createdAt, Instant updatedAt) {
    }

    /** 판 한 줄 — {@code version} 이 null 이면 아직 내지 않은 임시 판 */
    public record RevisionRow(long id, long packId, Integer version, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish,
                              String priceRequestMemo, Instant createdAt, Instant submittedAt) {

        public boolean sealed() {
            return version != null;
        }
    }

    /** 판의 차례 한 줄 — 로보티콘(토큰 ID)·그림·이름표 */
    public record ItemRow(long revisionId, int position, long emoticonId, long imageId, String label) {
    }

    private static final String PACK_COLUMNS = "p.id, p.source, p.creator_id, p.status, p.price, p.live_revision_id, p.acquire_count, p.published_at, p.suspend_reason, "
            + "p.suspended_at, p.suspended_by, o.operator_name, p.created_at, p.updated_at";
    private static final String PACK_FROM = " FROM emoticon_packs p LEFT JOIN operators o ON o.id = p.suspended_by";
    private static final String REVISION_COLUMNS = "id, pack_id, version, name, description, tags, cover_image_id, sale_wish, price_request_memo, created_at, submitted_at";

    private final JdbcTemplate jdbc;

    public EmoticonPackStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 팩 ──────────────────────────────────────────────────────────────────

    public Optional<PackRow> find(long id) {
        return jdbc.query("SELECT " + PACK_COLUMNS + PACK_FROM + " WHERE p.id = ?", EmoticonPackStore::packOf, id).stream().findFirst();
    }

    /** 바꾸기 전에 — 팩 행을 잠근다(받기의 받은 수 · 심사 처리 · 저장이 같은 팩에서 줄을 선다) */
    public Optional<PackRow> lock(long id) {
        if (jdbc.queryForList("SELECT id FROM emoticon_packs WHERE id = ? FOR UPDATE", Long.class, id).isEmpty()) {
            return Optional.empty();
        }
        return find(id);
    }

    /** 읽는 동안 바뀌지 않게 — 팩 행 공유 잠금(유료 결제 준비가 가격·판매 상태를 읽는다 — 가격 바꾸기·판매 중단과 줄을 선다) */
    public Optional<PackRow> lockShare(long id) {
        if (jdbc.queryForList("SELECT id FROM emoticon_packs WHERE id = ? FOR SHARE", Long.class, id).isEmpty()) {
            return Optional.empty();
        }
        return find(id);
    }

    public List<PackRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + PACK_COLUMNS + PACK_FROM + " WHERE p.id IN (" + marks(ids.size()) + ")", EmoticonPackStore::packOf, ids.toArray());
    }

    /** 작가의 팩 — 최근 고친 것 먼저 */
    public List<PackRow> byCreator(long creatorId) {
        return jdbc.query("SELECT " + PACK_COLUMNS + PACK_FROM + " WHERE p.creator_id = ? ORDER BY p.updated_at DESC, p.id DESC", EmoticonPackStore::packOf, creatorId);
    }

    /** 새 팩 — 상점의 판({@code liveRevisionId})은 같은 트랜잭션에서 넣는다(외래 키는 커밋 때 본다) */
    public void insert(long id, EmoticonPackSource source, Long creatorId, EmoticonPackStatus status, Long liveRevisionId, Instant publishedAt, Instant now) {
        jdbc.update("INSERT INTO emoticon_packs (id, source, creator_id, status, price, live_revision_id, acquire_count, published_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 0, ?, 0, ?, ?, ?)", id, source.name(), creatorId, status.name(), liveRevisionId, ts(publishedAt), Timestamp.from(now),
                Timestamp.from(now));
    }

    public void setStatus(long id, EmoticonPackStatus status, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET status = ?, updated_at = ? WHERE id = ?", status.name(), Timestamp.from(now), id);
    }

    /** 고친 시각만 */
    public void touch(long id, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET updated_at = ? WHERE id = ?", Timestamp.from(now), id);
    }

    /**
     * 상점에 판을 건다(승인·운영 팩 고치기) — 판매 중으로, 처음 나온 시각은 처음 한 번만, 판매 중단 기록은 지운다(판매 중단 팩의 새 판을 승인하면 다시 판매).
     */
    public void publish(long id, long liveRevisionId, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET live_revision_id = ?, status = 'APPROVED', published_at = coalesce(published_at, ?), suspend_reason = NULL, suspended_at = NULL, "
                + "suspended_by = NULL, updated_at = ? WHERE id = ?", liveRevisionId, Timestamp.from(now), Timestamp.from(now), id);
    }

    /** 상점의 판만 바꾼다(운영 팩 고치기 — 판매 중단이면 그대로 판매 중단) */
    public void replaceLive(long id, long liveRevisionId, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET live_revision_id = ?, updated_at = ? WHERE id = ?", liveRevisionId, Timestamp.from(now), id);
    }

    public void suspend(long id, String reason, long operatorId, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET status = 'SUSPENDED', suspend_reason = ?, suspended_at = ?, suspended_by = ?, updated_at = ? WHERE id = ?", reason,
                Timestamp.from(now), operatorId, Timestamp.from(now), id);
    }

    public void resume(long id, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET status = 'APPROVED', suspend_reason = NULL, suspended_at = NULL, suspended_by = NULL, updated_at = ? WHERE id = ?",
                Timestamp.from(now), id);
    }

    /** 가격을 바꾼다(승인·운영자 가격 바꾸기 — 팩 행을 잠근 트랜잭션에서). 유료 가격은 트리거가 설정의 열린 가격 단계인지 한 번 더 본다(V143) */
    public void setPrice(long id, int price, Instant now) {
        jdbc.update("UPDATE emoticon_packs SET price = ?, updated_at = ? WHERE id = ?", price, Timestamp.from(now), id);
    }

    /** 받은 수 + 1(처음 받기 — 팩 행을 잠근 트랜잭션에서) */
    public int incrementAcquire(long id) {
        return jdbc.queryForObject("UPDATE emoticon_packs SET acquire_count = acquire_count + 1 WHERE id = ? RETURNING acquire_count", Integer.class, id);
    }

    // ── 판 ──────────────────────────────────────────────────────────────────

    public Optional<RevisionRow> revision(long id) {
        return jdbc.query("SELECT " + REVISION_COLUMNS + " FROM emoticon_pack_revisions WHERE id = ?", EmoticonPackStore::revisionOf, id).stream().findFirst();
    }

    public Map<Long, RevisionRow> revisions(Collection<Long> ids) {
        Map<Long, RevisionRow> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT " + REVISION_COLUMNS + " FROM emoticon_pack_revisions WHERE id IN (" + marks(ids.size()) + ")", rs -> {
            RevisionRow row = revisionOf(rs, 0);
            out.put(row.id(), row);
        }, ids.toArray());
        return out;
    }

    /** 아직 내지 않은 임시 판(팩마다 하나) */
    public Optional<RevisionRow> draftOf(long packId) {
        return jdbc.query("SELECT " + REVISION_COLUMNS + " FROM emoticon_pack_revisions WHERE pack_id = ? AND version IS NULL", EmoticonPackStore::revisionOf, packId)
                .stream().findFirst();
    }

    /** 마지막으로 낸 판 */
    public Optional<RevisionRow> latestSealed(long packId) {
        return jdbc.query("SELECT " + REVISION_COLUMNS + " FROM emoticon_pack_revisions WHERE pack_id = ? AND version IS NOT NULL ORDER BY version DESC LIMIT 1",
                EmoticonPackStore::revisionOf, packId).stream().findFirst();
    }

    /** 팩마다 마지막으로 낸 판 */
    public Map<Long, RevisionRow> latestSealed(Collection<Long> packIds) {
        Map<Long, RevisionRow> out = new LinkedHashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT DISTINCT ON (pack_id) " + REVISION_COLUMNS + " FROM emoticon_pack_revisions WHERE pack_id IN (" + marks(packIds.size())
                + ") AND version IS NOT NULL ORDER BY pack_id, version DESC", rs -> {
                    RevisionRow row = revisionOf(rs, 0);
                    out.put(row.packId(), row);
                }, packIds.toArray());
        return out;
    }

    /** 팩마다 임시 판 */
    public Map<Long, RevisionRow> drafts(Collection<Long> packIds) {
        Map<Long, RevisionRow> out = new LinkedHashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT " + REVISION_COLUMNS + " FROM emoticon_pack_revisions WHERE pack_id IN (" + marks(packIds.size()) + ") AND version IS NULL", rs -> {
            RevisionRow row = revisionOf(rs, 0);
            out.put(row.packId(), row);
        }, packIds.toArray());
        return out;
    }

    public int maxVersion(long packId) {
        Integer max = jdbc.queryForObject("SELECT coalesce(max(version), 0) FROM emoticon_pack_revisions WHERE pack_id = ?", Integer.class, packId);
        return max == null ? 0 : max;
    }

    /** 임시 판을 넣는다(판을 내려면 차례를 넣은 뒤 {@link #seal}) */
    public void insertDraft(long id, long packId, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish, String memo,
                            Instant now) {
        jdbc.update("INSERT INTO emoticon_pack_revisions (id, pack_id, name, description, tags, cover_image_id, sale_wish, price_request_memo, created_at) "
                        + "VALUES (?, ?, ?, ?, ?::text[], ?, ?, ?, ?)", id, packId, name, description, tags.toArray(String[]::new), coverImageId, saleWish.name(), memo,
                Timestamp.from(now));
    }

    /** 임시 판 칸을 바꾼다(낸 판은 트리거가 막는다) */
    public void updateDraft(long id, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish, String memo) {
        jdbc.update("UPDATE emoticon_pack_revisions SET name = ?, description = ?, tags = ?::text[], cover_image_id = ?, sale_wish = ?, price_request_memo = ? WHERE id = ?",
                name, description, tags.toArray(String[]::new), coverImageId, saleWish.name(), memo, id);
    }

    /** 판을 낸다 — 그 뒤로 판 칸·차례를 고치지 못한다(트리거) */
    public void seal(long id, int version, Instant at) {
        jdbc.update("UPDATE emoticon_pack_revisions SET version = ?, submitted_at = ? WHERE id = ? AND version IS NULL", version, Timestamp.from(at), id);
    }

    // ── 로보티콘 · 차례 ─────────────────────────────────────────────────────

    public void insertEmoticon(long id, long packId, Instant now) {
        jdbc.update("INSERT INTO emoticons (id, pack_id, created_at) VALUES (?, ?, ?)", id, packId, Timestamp.from(now));
    }

    /** 임시 판의 차례를 통째로 바꾼다 */
    public void replaceItems(long revisionId, long packId, List<ItemRow> items) {
        jdbc.update("DELETE FROM emoticon_revision_items WHERE revision_id = ?", revisionId);
        for (ItemRow item : items) {
            jdbc.update("INSERT INTO emoticon_revision_items (revision_id, position, pack_id, emoticon_id, image_id, label) VALUES (?, ?, ?, ?, ?, ?)", revisionId,
                    item.position(), packId, item.emoticonId(), item.imageId(), item.label());
        }
    }

    /** 판마다 차례(차례대로) */
    public Map<Long, List<ItemRow>> items(Collection<Long> revisionIds) {
        Map<Long, List<ItemRow>> out = new LinkedHashMap<>();
        if (revisionIds.isEmpty()) {
            return out;
        }
        revisionIds.forEach(id -> out.put(id, new ArrayList<>()));
        jdbc.query("SELECT revision_id, position, emoticon_id, image_id, label FROM emoticon_revision_items WHERE revision_id IN (" + marks(revisionIds.size())
                + ") ORDER BY revision_id, position", rs -> {
                    ItemRow row = new ItemRow(rs.getLong(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getString(5));
                    out.computeIfAbsent(row.revisionId(), k -> new ArrayList<>()).add(row);
                }, revisionIds.toArray());
        return out;
    }

    public List<ItemRow> items(long revisionId) {
        return items(List.of(revisionId)).getOrDefault(revisionId, List.of());
    }

    /** 이 팩의 어느 판에서 이 그림을 쓰던 로보티콘(새 판에서도 같은 ID — 계약 S1) */
    public Optional<Long> emoticonByImage(long packId, long imageId) {
        return jdbc.queryForList("SELECT emoticon_id FROM emoticon_revision_items WHERE pack_id = ? AND image_id = ? ORDER BY revision_id DESC LIMIT 1", Long.class, packId,
                imageId).stream().findFirst();
    }

    /** 이 팩의 어느 판에서 같은 지문의 그림을 쓰던 로보티콘(같은 파일을 다시 올려도 같은 ID) */
    public Optional<Long> emoticonByHash(long packId, String contentHash) {
        return jdbc.queryForList("SELECT i.emoticon_id FROM emoticon_revision_items i JOIN emoticon_images m ON m.id = i.image_id WHERE i.pack_id = ? AND m.content_hash = ? "
                + "ORDER BY i.revision_id DESC LIMIT 1", Long.class, packId, contentHash).stream().findFirst();
    }

    /** 이 팩의 로보티콘 ID 들 */
    public Set<Long> emoticonIds(long packId) {
        return new HashSet<>(jdbc.queryForList("SELECT id FROM emoticons WHERE pack_id = ?", Long.class, packId));
    }

    /** 이 팩의 판들이 쓰는 그림(로보티콘·대표) */
    public Set<Long> imageIds(long packId) {
        Set<Long> out = new HashSet<>(jdbc.queryForList("SELECT image_id FROM emoticon_revision_items WHERE pack_id = ?", Long.class, packId));
        out.addAll(jdbc.queryForList("SELECT cover_image_id FROM emoticon_pack_revisions WHERE pack_id = ? AND cover_image_id IS NOT NULL", Long.class, packId));
        return out;
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    static String marks(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    static Timestamp ts(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }

    static Instant instant(ResultSet rs, int i) throws SQLException {
        Timestamp at = rs.getTimestamp(i);
        return at == null ? null : at.toInstant();
    }

    static List<String> tags(ResultSet rs, int i) throws SQLException {
        Array array = rs.getArray(i);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String::valueOf).collect(Collectors.toList());
    }

    static PackRow packOf(ResultSet rs, int i) throws SQLException {
        return new PackRow(rs.getLong(1), EmoticonPackSource.valueOf(rs.getString(2)), rs.getObject(3, Long.class), EmoticonPackStatus.valueOf(rs.getString(4)),
                rs.getInt(5), rs.getObject(6, Long.class), rs.getInt(7), instant(rs, 8), rs.getString(9), instant(rs, 10), rs.getObject(11, Long.class), rs.getString(12),
                instant(rs, 13), instant(rs, 14));
    }

    static RevisionRow revisionOf(ResultSet rs, int i) throws SQLException {
        return new RevisionRow(rs.getLong(1), rs.getLong(2), rs.getObject(3, Integer.class), rs.getString(4), rs.getString(5), tags(rs, 6), rs.getObject(7, Long.class),
                EmoticonSaleWish.valueOf(rs.getString(8)), rs.getString(9), instant(rs, 10), instant(rs, 11));
    }
}
