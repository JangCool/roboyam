package com.community.point.repository.master;

import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 잔액 잠금·원장·묶음·미회수 기록 쓰기와 그 판정에 필요한 읽기 — {@code PointService} 만 부른다.
 *
 * <p>Native SQL(JDBC)인 이유: (1) 적립·회수는 글·댓글쓰기 트랜잭션의 세이브포인트 안에서 해야 해(실패가 글쓰기를 깨지 않게 — {@code IsolatedSideWork}) 영속성 컨텍스트를 거치지 않는다.
 * (2) 잔액 행은 「없으면 0 으로 만들고 잠근다」({@code INSERT … ON CONFLICT DO NOTHING} + {@code SELECT … FOR UPDATE}) — 같은 회원의 지급·차감이 이 행에서 줄을 서므로 잔액이 음수가 되거나
 * 하루 상한을 넘지 않는다. 묶음·원장·미회수 기록은 <b>이 잠금 아래에서만</b> 고친다(묶음 행은 따로 잠그지 않는다). (3) 추천 보너스의 「인정된 추천」 수는 추천 표(해시 파티션)·회원·등급을
 * 잇는 한 문장이다. 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class PointLedgerWriter {

    /** 잔액 행 — 무상·유상·미회수 */
    public record Balance(long free, long paid, long debt) {
    }

    /** 원장 한 줄(판정에 필요한 칸만) */
    public record Entry(long id, long userId, PointKind kind, String reason, int delta, long freeDelta, long paidDelta) {
    }

    /** 묶음 하나 */
    public record Lot(long id, long userId, PointLotKind kind, long original, long remaining, Instant expiresAt, Instant createdAt) {

        /** {@code now} 에 소멸일이 지났나(소멸일 ≤ 지금 — 유상은 늘 아니다) */
        public boolean expiredAt(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }

    /** 새로 만들 묶음 */
    public record NewLot(long id, long userId, PointLotKind kind, long amount, Instant expiresAt, String sourceReason, String sourceRefType, Long sourceRefId,
                         Long sourceLedgerId, Long restoredFromLotId, Instant createdAt) {
    }

    /** 원장 한 줄이 묶음 하나에 반영된 양(±) */
    public record EntryLot(long lotId, long amount) {
    }

    /** 미회수 기록 하나 */
    public record Shortfall(long id, String reason, long requested, long applied) {

        public long shortAmount() {
            return requested - applied;
        }
    }

    private static final String LOT_COLUMNS = "id, user_id, kind, original_amount, remaining_amount, expires_at, created_at";
    private static final String ENTRY_COLUMNS = "id, user_id, kind, reason, delta, free_delta, paid_delta";

    private final JdbcTemplate jdbc;

    public PointLedgerWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 잔액 ────────────────────────────────────────────────────────────────

    /** 잔액 행을 (없으면 0 으로 만들고) 잠근 뒤 지금 값을 돌려준다 — 트랜잭션이 끝날 때까지 같은 회원의 다른 지급·차감은 기다린다 */
    public Balance lockBalance(long userId, Instant now) {
        jdbc.update("insert into point_balances (user_id, free_balance, paid_balance, revoke_debt, updated_at) values (?, 0, 0, 0, ?) on conflict (user_id) do nothing",
                userId, Timestamp.from(now));
        return jdbc.queryForObject("select free_balance, paid_balance, revoke_debt from point_balances where user_id = ? for update",
                (rs, i) -> new Balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)), userId);
    }

    /** 잔액 행을 바꾼다(잠근 뒤에만) — 합계 칸 {@code balance} 는 DB 가 계산한다 */
    public void saveBalance(long userId, long free, long paid, long debt, Instant now) {
        jdbc.update("update point_balances set free_balance = ?, paid_balance = ?, revoke_debt = ?, updated_at = ? where user_id = ?", free, paid, debt,
                Timestamp.from(now), userId);
    }

    // ── 원장 ────────────────────────────────────────────────────────────────

    /** 원장 한 줄(잔액 행을 잠근 뒤에만) */
    public void insertEntry(long id, long userId, PointKind kind, String reason, int delta, long freeDelta, long paidDelta, long balanceAfter, String note,
                            String refType, Long refId, Long actorOperatorId, Instant now) {
        jdbc.update("insert into point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, note, ref_type, ref_id, actor_operator_id, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, userId, kind.name(), reason, delta, freeDelta, paidDelta, balanceAfter, note, refType, refId,
                actorOperatorId, Timestamp.from(now));
    }

    public Optional<Entry> entry(long id) {
        return jdbc.query("select " + ENTRY_COLUMNS + " from point_ledger where id = ?", PointLedgerWriter::entryOf, id).stream().findFirst();
    }

    /** 그 회원의 그 사유·대상 줄(사유·대상은 유일 — 많아야 한 줄) */
    public Optional<Entry> entry(long userId, String reason, String refType, long refId) {
        return jdbc.query("select " + ENTRY_COLUMNS + " from point_ledger where user_id = ? and reason = ? and ref_type = ? and ref_id = ?", PointLedgerWriter::entryOf,
                userId, reason, refType, refId).stream().findFirst();
    }

    /** 같은 사유·대상 줄의 ID(멱등) */
    public Optional<Long> entryId(String reason, String refType, long refId) {
        return jdbc.queryForList("select id from point_ledger where reason = ? and ref_type = ? and ref_id = ?", Long.class, reason, refType, refId).stream().findFirst();
    }

    /** 같은 사유·대상 줄이 이미 있는가(멱등) */
    public boolean exists(String reason, String refType, long refId) {
        return entryId(reason, refType, refId).isPresent();
    }

    /** 회원이 {@code sinceId} 뒤에(오늘) 그 사유로 받은 횟수 — 지운 글·댓글도 센다(지급 줄은 지워지지 않는다) */
    public int countSince(long userId, String reason, long sinceId) {
        Integer count = jdbc.queryForObject("select count(*) from point_ledger where user_id = ? and reason = ? and id >= ?", Integer.class, userId, reason, sinceId);
        return count == null ? 0 : count;
    }

    /**
     * 회원이 {@code sinceId} 뒤에(오늘) 그 사유들로 <b>지급한 양</b>의 합 — 적립(EARN) 줄의 금액(미회수 상계 전 양, 지운 글·댓글로 받은 것도 센다). 하루 총 한도(커머스 1-3)가 본다.
     */
    public long earnedSince(long userId, Collection<String> reasons, long sinceId) {
        String in = reasons.stream().map(reason -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[reasons.size() + 2];
        args[0] = userId;
        int i = 1;
        for (String reason : reasons) {
            args[i++] = reason;
        }
        args[i] = sinceId;
        Long sum = jdbc.queryForObject("select coalesce(sum(delta), 0) from point_ledger where user_id = ? and kind = 'EARN' and reason in (" + in + ") and id >= ?",
                Long.class, args);
        return sum == null ? 0 : sum;
    }

    /** 회원이 그 사유들로 마지막으로 포인트를 받은 줄의 시각(없으면 빈 값) — 작성 간격(커머스 1-3)이 본다. 줄은 포인트를 받았을 때만 생긴다 */
    public Optional<Instant> lastEarnedAt(long userId, Collection<String> reasons) {
        String in = reasons.stream().map(reason -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[reasons.size() + 1];
        args[0] = userId;
        int i = 1;
        for (String reason : reasons) {
            args[i++] = reason;
        }
        return jdbc.query("select created_at from point_ledger where user_id = ? and kind = 'EARN' and reason in (" + in + ") order by id desc limit 1",
                (rs, n) -> rs.getTimestamp(1).toInstant(), args).stream().findFirst();
    }

    /**
     * 회원이 그 글의 (다른) 댓글로 댓글 적립(COMMENT)을 받은 적이 있나 — 지운 댓글로 받은 것도 센다(댓글 행은 지워도 남는다). 같은 글 댓글 적립 1회(커머스 1-3)가 본다.
     * 댓글 표는 글 번호로 나뉘어 있어 그 글의 칸만 읽는다.
     */
    public boolean commentEarnedOnPost(long userId, long postId, long exceptCommentId) {
        return !jdbc.queryForList("select 1 from comments c join point_ledger l on l.reason = 'COMMENT' and l.ref_type = 'COMMENT' and l.ref_id = c.id "
                + "where c.post_id = ? and c.id <> ? and l.user_id = ? limit 1", Integer.class, postId, exceptCommentId, userId).isEmpty();
    }

    // ── 묶음 ────────────────────────────────────────────────────────────────

    public void insertLot(NewLot lot) {
        jdbc.update("insert into point_lots (id, user_id, kind, original_amount, remaining_amount, expires_at, source_reason, source_ref_type, source_ref_id, "
                + "source_ledger_id, restored_from_lot_id, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", lot.id(), lot.userId(), lot.kind().name(),
                lot.amount(), lot.amount(), lot.expiresAt() == null ? null : Timestamp.from(lot.expiresAt()), lot.sourceReason(), lot.sourceRefType(),
                lot.sourceRefId(), lot.sourceLedgerId(), lot.restoredFromLotId(), Timestamp.from(lot.createdAt()));
    }

    /** 남은 양을 더하거나 뺀다(0 ≤ 남은 양 ≤ 원래 양 — DB CHECK) */
    public void changeRemaining(long lotId, long delta) {
        jdbc.update("update point_lots set remaining_amount = remaining_amount + ? where id = ?", delta, lotId);
    }

    public Optional<Lot> lot(long id) {
        return jdbc.query("select " + LOT_COLUMNS + " from point_lots where id = ?", PointLedgerWriter::lotOf, id).stream().findFirst();
    }

    public List<Lot> lots(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String in = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.query("select " + LOT_COLUMNS + " from point_lots where id in (" + in + ")", PointLedgerWriter::lotOf, ids.toArray());
    }

    /** 쓸 수 있는 묶음 — 남은 양이 있고 소멸일이 지나지 않은 그 종류의 묶음(차례는 {@code PointDeductionPolicy} 가 정한다) */
    public List<Lot> usableLots(long userId, Collection<PointLotKind> kinds, Instant now) {
        String in = kinds.stream().map(kind -> "'" + kind.name() + "'").collect(Collectors.joining(", "));
        return jdbc.query("select " + LOT_COLUMNS + " from point_lots where user_id = ? and kind in (" + in + ") and remaining_amount > 0 "
                + "and (expires_at is null or expires_at > ?) order by kind, expires_at, created_at, id", PointLedgerWriter::lotOf, userId, Timestamp.from(now));
    }

    /** 소멸일이 지났는데 남은 양이 있는 무상 묶음(소멸일 순) */
    public List<Lot> dueLots(long userId, Instant now) {
        return jdbc.query("select " + LOT_COLUMNS + " from point_lots where user_id = ? and kind = 'FREE' and remaining_amount > 0 and expires_at <= ? "
                + "order by expires_at, created_at, id", PointLedgerWriter::lotOf, userId, Timestamp.from(now));
    }

    /** 그 적립 줄이 만든 묶음 */
    public Optional<Long> lotMadeBy(long ledgerId) {
        return jdbc.queryForList("select id from point_lots where source_ledger_id = ?", Long.class, ledgerId).stream().findFirst();
    }

    public void insertEntryLot(long ledgerId, long lotId, long amount) {
        jdbc.update("insert into point_ledger_lots (ledger_id, lot_id, amount) values (?, ?, ?)", ledgerId, lotId, amount);
    }

    /** 원장 한 줄이 반영된 묶음들(묶음 ID 순) */
    public List<EntryLot> entryLots(long ledgerId) {
        return jdbc.query("select lot_id, amount from point_ledger_lots where ledger_id = ? order by lot_id", (rs, i) -> new EntryLot(rs.getLong(1), rs.getLong(2)),
                ledgerId);
    }

    // ── 미회수 기록 ─────────────────────────────────────────────────────────

    public void insertShortfall(long id, long userId, String reason, String refType, long refId, long requested, long applied, Instant now) {
        jdbc.update("insert into point_revoke_shortfalls (id, user_id, reason, ref_type, ref_id, requested, applied, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)", id,
                userId, reason, refType, refId, requested, applied, Timestamp.from(now));
    }

    public boolean shortfallExists(String reason, String refType, long refId) {
        return !jdbc.queryForList("select 1 from point_revoke_shortfalls where reason = ? and ref_type = ? and ref_id = ?", Integer.class, reason, refType, refId)
                .isEmpty();
    }

    /** 그 회원의 그 대상에 대한 미회수 기록(사유 중 하나) */
    public List<Shortfall> shortfalls(long userId, Collection<String> reasons, String refType, long refId) {
        String in = reasons.stream().map(reason -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[reasons.size() + 3];
        args[0] = userId;
        int i = 1;
        for (String reason : reasons) {
            args[i++] = reason;
        }
        args[i++] = refType;
        args[i] = refId;
        return jdbc.query("select id, reason, requested, applied from point_revoke_shortfalls where user_id = ? and reason in (" + in + ") and ref_type = ? and ref_id = ? "
                + "order by id", (rs, n) -> new Shortfall(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4)), args);
    }

    public void deleteShortfall(long id) {
        jdbc.update("delete from point_revoke_shortfalls where id = ?", id);
    }

    // ── 받는 사람·추천 판정 ──────────────────────────────────────────────────

    /** 포인트를 받을 수 있는 회원인가 — 정상(ACTIVE) 회원만(정지·영구 정지·탈퇴는 받지 않는다) */
    public boolean earnable(long userId) {
        return !jdbc.queryForList("select 1 from users where id = ? and status = 'ACTIVE' and deleted_at is null", Integer.class, userId).isEmpty();
    }

    /** 되돌려 줄 수 있는 회원인가 — 탈퇴·삭제되지 않은 회원(정지 회원 포함) */
    public boolean restorable(long userId) {
        return !jdbc.queryForList("select 1 from users where id = ? and status <> 'WITHDRAWN' and deleted_at is null", Integer.class, userId).isEmpty();
    }

    /**
     * 인정된 추천을 {@code limit} 개까지만 센다 — 작성자가 아닌 정상 회원이고 등급이 가장 낮은 등급(새싹)보다 높은 회원의 추천. 등급·상태는 지금 값이다.
     *
     * @param refType {@code POST} · {@code COMMENT}
     */
    public int recognizedLikes(String refType, long refId, long authorId, int limit) {
        String likes = "POST".equals(refType) ? "select l.user_id from post_likes l where l.post_id = ?" : "select l.user_id from comment_likes l where l.comment_id = ?";
        Integer count = jdbc.queryForObject("select count(*) from (" + likes + " and l.user_id <> ? and exists (" + RECOGNIZED + ") limit ?) x", Integer.class,
                refId, authorId, limit);
        return count == null ? 0 : count;
    }

    /** 이 회원의 추천이 인정되는가(위와 같은 조건) */
    /** AI 회원인가(ai-members-contract 정책 10 — AI 계정은 활동 포인트를 받지 않고, AI 의 추천은 보너스 문턱에 들지 않는다) */
    public boolean aiMember(long userId) {
        return !jdbc.queryForList("select 1 from ai_member_profiles where user_id = ?", Integer.class, userId).isEmpty();
    }

    public boolean recognizedLiker(long userId) {
        return !jdbc.queryForList("select 1 from (select ?::bigint as user_id) l where exists (" + RECOGNIZED + ")", Integer.class, userId).isEmpty();
    }

    /**
     * 무료 로보티콘 팩 받기 보상의 「인정된 받기」(emoticon-contract 가정 6) — 인정된 추천({@link #recognizedLiker})과 같은 회원 조건(정상 회원 · 등급이 가장 낮은 등급보다 높다) +
     * 이메일 인증. 본인(작가)이 아닌지는 부르는 쪽이 본다.
     */
    public boolean recognizedAcquirer(long userId) {
        return !jdbc.queryForList("select 1 from (select ?::bigint as user_id) l where exists (" + RECOGNIZED + " and u.email_verified_at is not null)", Integer.class,
                userId).isEmpty();
    }

    /**
     * 운영팀 계정인가(point-board-tier-contract 「AI 회원·운영팀 계정은 적립 안 함」) — 운영자에 이어진 회원 계정({@code operator_member_accounts}, V141). 운영자가 잠겼거나 승인 전이어도
     * 이어진 계정이면 운영팀 계정으로 본다(적립 쪽은 넓게 막는다).
     */
    public boolean opsMember(long userId) {
        return !jdbc.queryForList("select 1 from operator_member_accounts where user_id = ?", Integer.class, userId).isEmpty();
    }

    // ── 보류 지급(V238 point_pending_grants — point-board-tier-contract 「보류 확정 방식」) ─────────────
    // 세이브포인트 안(추천·우수 글 선정·삭제·숨김의 같은 트랜잭션)에서 쓰므로 JDBC 다(클래스 머리말 (1)). 확정 배치의 행 잠금은 엔티티 저장소(PointPendingGrantStore)가 한다.

    /** 그 사유·대상의 보류가 대기 중이거나 이미 확정됐나(취소된 것은 세지 않는다 — 다시 선정하면 새로 보류한다) */
    public boolean pendingOpen(String reason, String refType, long refId) {
        return !jdbc.queryForList("select 1 from point_pending_grants where reason = ? and ref_type = ? and ref_id = ? and status in ('PENDING', 'RELEASED') limit 1",
                Integer.class, reason, refType, refId).isEmpty();
    }

    /** 회원이 {@code sinceId} 뒤에(오늘) 그 사유로 시작해 아직 대기 중인 보류 수 — 추천 보너스 하루 횟수가 원장 줄과 함께 센다 */
    public int pendingCountSince(long userId, String reason, long sinceId) {
        Integer count = jdbc.queryForObject("select count(*) from point_pending_grants where user_id = ? and reason = ? and status = 'PENDING' and id >= ?",
                Integer.class, userId, reason, sinceId);
        return count == null ? 0 : count;
    }

    /** 보류 한 줄 — 같은 (사유, 대상)에 대기 중인 줄이 있으면 넣지 않는다(유일 부분 색인). 넣었으면 true */
    public boolean insertPending(long id, long userId, String reason, String refType, long refId, int amount, Instant releaseAt, Instant now) {
        return jdbc.update("insert into point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, status, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?) on conflict (reason, ref_type, ref_id) where status = 'PENDING' do nothing", id, userId, reason, refType, refId,
                amount, Timestamp.from(releaseAt), Timestamp.from(now)) > 0;
    }

    /**
     * 그 대상의 대기 중인 보류를 취소한다(삭제·숨김·선정 취소의 같은 트랜잭션).
     *
     * @param reason 그 사유만(null 이면 모든 사유)
     * @return 취소한 줄 수
     */
    public int cancelPending(String refType, long refId, String reason, String cancelReason, Instant now) {
        if (reason == null) {
            return jdbc.update("update point_pending_grants set status = 'CANCELLED', cancel_reason = ?, settled_at = ? where ref_type = ? and ref_id = ? and status = 'PENDING'",
                    cancelReason, Timestamp.from(now), refType, refId);
        }
        return jdbc.update("update point_pending_grants set status = 'CANCELLED', cancel_reason = ?, settled_at = ? "
                + "where ref_type = ? and ref_id = ? and reason = ? and status = 'PENDING'", cancelReason, Timestamp.from(now), refType, refId, reason);
    }

    /**
     * 보류를 확정할 때 대상이 아직 보이는가 — 글: 공개(임시저장 아님)·지우지 않음·예약 삭제 시각 전·숨김 아님(임시조치가 끝났으면 보임). 댓글: 지우지 않음·숨김 아님.
     * 판정은 {@code PostEntity#isGoneAt}·{@code isHiddenAt}, {@code CommentEntity#isDeleted}·{@code isHiddenAt} 과 같다(한쪽을 고치면 같이 고친다).
     */
    public boolean contentAlive(String refType, long refId, Instant now) {
        Timestamp at = Timestamp.from(now);
        if ("POST".equals(refType)) {
            return !jdbc.queryForList("select 1 from posts where id = ? and status = 'PUBLISHED' and deleted_at is null "
                    + "and (delete_scheduled_at is null or delete_scheduled_at > ?) and (hidden_at is null or (hidden_until is not null and hidden_until <= ?))",
                    Integer.class, refId, at, at).isEmpty();
        }
        return !jdbc.queryForList("select 1 from comments where id = ? and deleted_at is null and (hidden_at is null or (hidden_until is not null and hidden_until <= ?))",
                Integer.class, refId, at).isEmpty();
    }

    /** 그 글이 지금 우수 글로 선정돼 있나({@code post_features} 의 살아 있는 줄) — 우수 글 보류를 확정할 때 다시 본다 */
    public boolean featureActive(long postId) {
        return !jdbc.queryForList("select 1 from post_features where post_id = ? and unfeatured_at is null", Integer.class, postId).isEmpty();
    }

    // ── 정보 등급 「같은 출처 링크 1회」(V238 point_source_claims) ─────────────────────────────

    /** 핫딜 글의 같은 딜 키({@code post_deals.product_url_key}) — 정보 등급 「같은 출처 링크 1회」의 열쇠. 글을 저장한 같은 트랜잭션에서 부른다(세이브포인트가 먼저 내보낸 행을 읽는다) */
    public List<String> postDealKeys(long postId) {
        return jdbc.queryForList("select product_url_key from post_deals where post_id = ? and product_url_key is not null", String.class, postId);
    }

    /** 글 출처 목록 JSON({@code posts.sources}, V232) — 없으면 null */
    public String postSourcesJson(long postId) {
        return jdbc.queryForList("select sources::text from posts where id = ?", String.class, postId).stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * 출처 열쇠마다 트랜잭션 잠금을 잡는다(정렬한 차례 — 교착 없음). 같은 링크로 동시에 쓴 두 글이 둘 다 「처음」으로 보지 않게 한다.
     * Native 인 이유: {@code pg_advisory_xact_lock}(repository.md 가 인정하는 DB 전용 기능) — 아직 없는 열쇠 행은 행 잠금으로 잡을 수 없다.
     */
    public void lockSourceKeys(Collection<String> keys) {
        for (String key : keys.stream().sorted().toList()) {
            jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", (java.sql.ResultSet rs) -> { }, "point-source:" + key);
        }
    }

    /** 이 열쇠 중 하나라도 이미 글 적립을 받았나(사이트 전체) */
    public boolean sourceClaimed(Collection<String> keys) {
        if (keys.isEmpty()) {
            return false;
        }
        String in = keys.stream().map(key -> "?").collect(Collectors.joining(", "));
        return !jdbc.queryForList("select 1 from point_source_claims where source_key in (" + in + ") limit 1", Integer.class, keys.toArray()).isEmpty();
    }

    /** 글 적립을 받은 출처 열쇠를 남긴다(이미 있으면 그대로) */
    public void claimSources(Collection<String> keys, long postId, long userId, Instant now) {
        for (String key : keys) {
            jdbc.update("insert into point_source_claims (source_key, post_id, user_id, created_at) values (?, ?, ?, ?) on conflict (source_key) do nothing", key, postId,
                    userId, Timestamp.from(now));
        }
    }

    /** 인정되는 추천인가 — {@code l.user_id} 를 바깥에서 받는다 */
    private static final String RECOGNIZED = "select 1 from users u join profiles p on p.user_id = u.id join grades g on g.grade_key = p.grade_key "
            + "where u.id = l.user_id and u.status = 'ACTIVE' and u.deleted_at is null and g.level > (select min(level) from grades)";

    private static Entry entryOf(ResultSet rs, int i) throws SQLException {
        return new Entry(rs.getLong(1), rs.getLong(2), PointKind.valueOf(rs.getString(3)), rs.getString(4), rs.getInt(5), rs.getLong(6), rs.getLong(7));
    }

    private static Lot lotOf(ResultSet rs, int i) throws SQLException {
        Timestamp expires = rs.getTimestamp(6);
        return new Lot(rs.getLong(1), rs.getLong(2), PointLotKind.valueOf(rs.getString(3)), rs.getLong(4), rs.getLong(5), expires == null ? null : expires.toInstant(),
                rs.getTimestamp(7).toInstant());
    }
}
