package com.community.point.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 잔액 잠금·원장 쓰기와 그 판정에 필요한 읽기 — {@code PointService} 만 부른다.
 *
 * <p>Native SQL(JDBC)인 이유: (1) 적립·회수는 글·댓글쓰기 트랜잭션의 세이브포인트 안에서 해야 해(실패가 글쓰기를 깨지 않게 — {@code IsolatedSideWork}) 영속성 컨텍스트를 거치지 않는다.
 * (2) 잔액 행은 「없으면 0 으로 만들고 잠근다」({@code INSERT … ON CONFLICT DO NOTHING} + {@code SELECT … FOR UPDATE}) — 같은 회원의 지급·차감이 이 행에서 줄을 서므로 잔액이 음수가 되거나
 * 하루 상한을 넘지 않는다. (3) 추천 보너스의 「인정된 추천」 수는 추천 표(해시 파티션)·회원·등급을 잇는 한 문장이다. 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class PointLedgerWriter {

    private final JdbcTemplate jdbc;

    public PointLedgerWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 잔액 행을 (없으면 0 으로 만들고) 잠근 뒤 지금 잔액을 돌려준다 — 트랜잭션이 끝날 때까지 같은 회원의 다른 지급·차감은 기다린다 */
    public long lockBalance(long userId, Instant now) {
        jdbc.update("insert into point_balances (user_id, balance, updated_at) values (?, 0, ?) on conflict (user_id) do nothing", userId, Timestamp.from(now));
        Long balance = jdbc.queryForObject("select balance from point_balances where user_id = ? for update", Long.class, userId);
        return balance == null ? 0 : balance;
    }

    /** 원장 한 줄 + 잔액 갱신(잔액 행을 잠근 뒤에만 부른다) */
    public void append(long id, long userId, String kind, String reason, int delta, long balanceAfter, String note, String refType, Long refId, Long actorOperatorId,
                       Instant now) {
        Timestamp at = Timestamp.from(now);
        jdbc.update("insert into point_ledger (id, user_id, kind, reason, delta, balance_after, note, ref_type, ref_id, actor_operator_id, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, userId, kind, reason, delta, balanceAfter, note, refType, refId, actorOperatorId, at);
        jdbc.update("update point_balances set balance = ?, updated_at = ? where user_id = ?", balanceAfter, at, userId);
    }

    /** 포인트를 받을 수 있는 회원인가 — 정상(ACTIVE) 회원만(정지·영구 정지·탈퇴는 받지 않는다) */
    public boolean earnable(long userId) {
        return !jdbc.queryForList("select 1 from users where id = ? and status = 'ACTIVE' and deleted_at is null", Integer.class, userId).isEmpty();
    }

    /** 되돌려 줄 수 있는 회원인가 — 탈퇴·삭제되지 않은 회원(정지 회원 포함) */
    public boolean restorable(long userId) {
        return !jdbc.queryForList("select 1 from users where id = ? and status <> 'WITHDRAWN' and deleted_at is null", Integer.class, userId).isEmpty();
    }

    /** 같은 사유·대상 줄이 이미 있는가(멱등) */
    public boolean exists(String reason, String refType, long refId) {
        return !jdbc.queryForList("select 1 from point_ledger where reason = ? and ref_type = ? and ref_id = ? limit 1", Integer.class, reason, refType, refId)
                .isEmpty();
    }

    /** 그 사유·대상으로 지급한 양(없으면 0) */
    public long granted(long userId, String reason, String refType, long refId) {
        Long sum = jdbc.queryForObject("select coalesce(sum(delta), 0) from point_ledger where user_id = ? and reason = ? and ref_type = ? and ref_id = ?",
                Long.class, userId, reason, refType, refId);
        return sum == null ? 0 : sum;
    }

    /** 회원이 {@code sinceId} 뒤에(오늘) 그 사유로 받은 횟수 — 지운 글·댓글도 센다(지급 줄은 지워지지 않는다) */
    public int countSince(long userId, String reason, long sinceId) {
        Integer count = jdbc.queryForObject("select count(*) from point_ledger where user_id = ? and reason = ? and id >= ?", Integer.class, userId, reason, sinceId);
        return count == null ? 0 : count;
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
    public boolean recognizedLiker(long userId) {
        return !jdbc.queryForList("select 1 from (select ?::bigint as user_id) l where exists (" + RECOGNIZED + ")", Integer.class, userId).isEmpty();
    }

    /** 인정되는 추천인가 — {@code l.user_id} 를 바깥에서 받는다 */
    private static final String RECOGNIZED = "select 1 from users u join profiles p on p.user_id = u.id join grades g on g.grade_key = p.grade_key "
            + "where u.id = l.user_id and u.status = 'ACTIVE' and u.deleted_at is null and g.level > (select min(level) from grades)";
}
