package com.community.analytics.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 방문 기록 삭제 요청 표({@code analytics_forget_requests}, analytics V4) 넣기 — 분석 전용 연결({@code analyticsJdbc}). 지우기는 DB 크론({@code R__analytics_13_forget_requests.sql})이 한다.
 *
 * <p>같은 방문자 해시의 <b>대기 중</b> 요청이 있으면 새 줄을 만들지 않고 그 요청을 돌려준다 — 대기 줄의 해시에 유일 인덱스({@code ux_analytics_forget_requests_pending})가 있어
 * 서버 여러 대가 동시에 받아도 한 줄이다({@code ON CONFLICT … DO NOTHING} 뒤 다시 읽기).
 *
 * <p><b>전체 상한</b>: 대기 줄이 {@link #MAX_PENDING} 개에 닿으면 새 요청을 받지 않는다(null) — IP 빈도 제한은 서버 메모리라 서버 수만큼 느슨해지므로, 서버 여러 대가 함께 지키는 상한을 DB 에 둔다
 * (IP 는 저장하지 않는다). 대기 수는 대기 줄 부분 인덱스로 세고, 넣기와 한 문장이라 따로 읽지 않는다. 동시에 들어온 요청 몇 개만큼 넘을 수 있다(느슨한 상한).
 * 크론이 5분마다 500개씩 처리하므로 1,000개면 10분 안에 비워진다.
 */
@Repository
public class VisitForgetStore {

    /** 대기 요청 전체 상한 — 크론 두 번(10분) 분량 */
    public static final int MAX_PENDING = 1000;

    /** 접수된 요청(새로 넣었든 이미 대기 중이었든) */
    public record Received(long id, Instant receivedAt) {
    }

    private static final String INSERT = "INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status) "
            + "SELECT ?, ?, ?, 'PENDING' WHERE (SELECT count(*) FROM analytics_forget_requests WHERE status = 'PENDING') < ? "
            + "ON CONFLICT (visitor_hash) WHERE status = 'PENDING' DO NOTHING RETURNING id, received_at";
    private static final String PENDING = "SELECT id, received_at FROM analytics_forget_requests WHERE visitor_hash = ? AND status = 'PENDING'";

    private final JdbcTemplate jdbc;

    public VisitForgetStore(AnalyticsDatabase db) {
        this.jdbc = db.jdbc();
    }

    /**
     * 요청 하나를 접수한다(자동 커밋 문장 — 부르는 쪽 트랜잭션 없음). 넣기가 건너뛰어졌으면 대기 중인 줄을 다시 읽는다 — 같은 해시가 대기 중이면 상한과 상관없이 그 요청을 돌려준다.
     * 대기 줄도 없으면: 상한에 닿았으면 null, 아니면(그 사이 크론이 처리를 끝냄) 다시 넣는다.
     */
    public Received submit(long id, String visitorHash, Instant at) {
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Received> inserted = jdbc.query(INSERT, (rs, i) -> new Received(rs.getLong(1), rs.getTimestamp(2).toInstant()), id, visitorHash, Timestamp.from(at), MAX_PENDING);
            if (!inserted.isEmpty()) {
                return inserted.getFirst();
            }
            List<Received> pending = jdbc.query(PENDING, (rs, i) -> new Received(rs.getLong(1), rs.getTimestamp(2).toInstant()), visitorHash);
            if (!pending.isEmpty()) {
                return pending.getFirst();
            }
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE status = 'PENDING'", Integer.class);
            if (waiting != null && waiting >= MAX_PENDING) {
                return null;
            }
        }
        throw new IllegalStateException("방문 기록 삭제 요청을 접수하지 못했다(대기 줄이 계속 바뀐다)");
    }
}
