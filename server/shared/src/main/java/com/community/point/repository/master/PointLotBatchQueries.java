package com.community.point.repository.master;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포인트 배치(소멸 {@code point-expire} · 소멸 예정 알림 {@code point-expiry-notice})가 대상을 고르는 읽기와 알림 표시 — {@code com.community.point.batch} 만 부른다.
 * 묶음의 남은 양은 여기서 바꾸지 않는다(소멸은 {@code PointService#expireLot} 한 길). 알림 표시({@code expiry_noticed_at})만 여기서 쓴다 — 남은 양·잔액과 무관한 칸이라 잔액 행 잠금이 필요 없고
 * ({@code point_lots_identity_fixed} 트리거가 허용하는 칸), 조건부 UPDATE 라 같은 묶음을 두 번 표시하지 않는다.
 *
 * <p>둘 다 V85 의 부분 색인 {@code ix_point_lots_expiry (expires_at) WHERE kind = 'FREE' AND remaining_amount > 0} 을 탄다. {@code userId} 가 있으면 그 회원만(배치 파라미터 — 특정 회원만 돌리기).
 * Native SQL(JDBC)인 이유: 키셋 비교 {@code (expires_at, id) > (?, ?)} 와 {@code UPDATE … RETURNING} 은 JPQL/QueryDSL 에 없다. 트랜잭션은 배치 실행기가 연다.
 */
@Repository
public class PointLotBatchQueries {

    /** 소멸 대상 묶음 하나 — 정렬 키 (소멸일, ID) */
    public record DueLot(long id, long userId, Instant expiresAt) {
    }

    /** 방금 알림 표시한 묶음 하나 */
    public record NoticedLot(long id, long remaining, Instant expiresAt) {
    }

    /** 알림 기간 안 무상 묶음의 요약 — 가장 가까운 소멸 시각과 기간 안 남은 양 합 */
    public record ExpiringSummary(Instant firstExpiresAt, long amount) {
    }

    private static final String DUE = "select id, user_id, expires_at from point_lots where kind = 'FREE' and remaining_amount > 0 and expires_at <= ? ";
    private static final String NOTICE_TARGET = "kind = 'FREE' and remaining_amount > 0 and expiry_noticed_at is null and expires_at > ? and expires_at <= ? ";

    /** 쉬는 회원 빼기 — 마지막 알림이 {@code restSince} 이후인 회원(회원당 최소 간격) */
    private static final String NOT_RESTING = "and not exists (select 1 from point_balances b where b.user_id = point_lots.user_id and b.expiry_notice_sent_at >= ?) ";

    private final JdbcTemplate jdbc;

    public PointLotBatchQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 소멸 ────────────────────────────────────────────────────────────────

    /**
     * 소멸일({@code expires_at ≤ now})이 지났는데 남은 양이 있는 무상 묶음 — 「아직 처리 안 한 것 전부」(15-3 규칙 ②). (소멸일, ID) 오름차순, {@code after} 다음부터 {@code size} 개.
     *
     * @param after 마지막으로 끝낸 묶음(없으면 null — 처음부터)
     */
    public List<DueLot> dueLots(Instant now, DueLot after, Long userId, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(DUE);
        args.add(Timestamp.from(now));
        if (userId != null) {
            sql.append("and user_id = ? ");
            args.add(userId);
        }
        if (after != null) {
            sql.append("and (expires_at, id) > (?, ?) ");
            args.add(Timestamp.from(after.expiresAt()));
            args.add(after.id());
        }
        sql.append("order by expires_at, id limit ?");
        args.add(size);
        return jdbc.query(sql.toString(), (rs, i) -> new DueLot(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant()), args.toArray());
    }

    /** 「실패 건만 다시」 — 그 묶음들 중 아직 소멸 대상인 것(이미 소멸했으면 빠진다) */
    public List<DueLot> dueLotsByIds(Instant now, List<Long> ids) {
        return jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(DUE + "and id = any (?) order by expires_at, id");
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setArray(2, connection.createArrayOf("bigint", ids.toArray()));
            return statement;
        }, (rs, i) -> new DueLot(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant()));
    }

    // ── 소멸 예정 알림 ──────────────────────────────────────────────────────

    /**
     * 알림 기간({@code from < expires_at ≤ until}) 안에 아직 알리지 않은 무상 묶음이 있는 회원 — 회원 ID 오름차순, {@code afterUserId} 다음부터 {@code size} 명.
     * {@code restSince} 가 있으면 그 뒤에 이미 알림을 받은 회원은 뺀다(그 회원의 묶음은 표시하지 않고 남겨 다음에 알린다).
     */
    public List<Long> noticeMembers(Instant from, Instant until, Instant restSince, Long afterUserId, Long userId, int size) {
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(until)));
        StringBuilder sql = new StringBuilder("select distinct user_id from point_lots where " + NOTICE_TARGET);
        if (restSince != null) {
            sql.append(NOT_RESTING);
            args.add(Timestamp.from(restSince));
        }
        if (userId != null) {
            sql.append("and user_id = ? ");
            args.add(userId);
        }
        if (afterUserId != null) {
            sql.append("and user_id > ? ");
            args.add(afterUserId);
        }
        sql.append("order by user_id limit ?");
        args.add(size);
        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    /** 「실패 건만 다시」 — 그 회원들 중 아직 알릴 묶음이 있는 회원 */
    public List<Long> noticeMembersByIds(Instant from, Instant until, Instant restSince, List<Long> userIds) {
        return jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("select distinct user_id from point_lots where " + NOTICE_TARGET
                    + (restSince == null ? "" : NOT_RESTING) + "and user_id = any (?) order by user_id");
            int i = 1;
            statement.setTimestamp(i++, Timestamp.from(from));
            statement.setTimestamp(i++, Timestamp.from(until));
            if (restSince != null) {
                statement.setTimestamp(i++, Timestamp.from(restSince));
            }
            statement.setArray(i, connection.createArrayOf("bigint", userIds.toArray()));
            return statement;
        }, (rs, i) -> rs.getLong(1));
    }

    /**
     * 그 회원의 알림 기간 안 아직 알리지 않은 무상 묶음에 알린 시각을 적는다 — <b>조건부</b>({@code expiry_noticed_at IS NULL})라 같은 묶음을 두 번 표시하지 않는다. 방금 표시한 묶음
     */
    public List<NoticedLot> markNoticed(long userId, Instant from, Instant until, Instant noticedAt) {
        return jdbc.query("update point_lots set expiry_noticed_at = ? where user_id = ? and " + NOTICE_TARGET + "returning id, remaining_amount, expires_at",
                (rs, i) -> new NoticedLot(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant()), Timestamp.from(noticedAt), userId, Timestamp.from(from),
                Timestamp.from(until));
    }

    /**
     * 그 회원의 마지막 알림 시각을 적는다 — <b>조건부</b>: {@code restSince} 이후에 이미 알렸으면 적지 않고 false(같은 때 도는 다른 실행과 겹쳐도 한 번만 알린다).
     * 잔액 행의 알림 칸만 바꾼다(잔액·묶음과 무관 — 잔액 행 잠금은 이 문장 동안만).
     */
    public boolean claimNotice(long userId, Instant sentAt, Instant restSince) {
        if (restSince == null) {
            return jdbc.update("update point_balances set expiry_notice_sent_at = ? where user_id = ?", Timestamp.from(sentAt), userId) == 1;
        }
        return jdbc.update("update point_balances set expiry_notice_sent_at = ? where user_id = ? and (expiry_notice_sent_at is null or expiry_notice_sent_at < ?)",
                Timestamp.from(sentAt), userId, Timestamp.from(restSince)) == 1;
    }

    /** 그 회원의 알림 기간 안 무상 묶음 전부(이미 알린 것 포함)의 가장 가까운 소멸 시각과 남은 양 합 — 없으면 빈 값 */
    public Optional<ExpiringSummary> expiringSummary(long userId, Instant from, Instant until) {
        return jdbc.query("select min(expires_at), coalesce(sum(remaining_amount), 0) from point_lots where user_id = ? and kind = 'FREE' and remaining_amount > 0 "
                        + "and expires_at > ? and expires_at <= ?", (rs, i) -> {
                    Timestamp first = rs.getTimestamp(1);
                    return first == null ? null : new ExpiringSummary(first.toInstant(), rs.getLong(2));
                }, userId, Timestamp.from(from), Timestamp.from(until)).stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
