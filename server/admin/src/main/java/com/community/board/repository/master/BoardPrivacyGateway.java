package com.community.board.repository.master;

import com.community.board.dto.response.BoardPrivacyTargetView;
import java.sql.Timestamp;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인정보 보유기간의 대상 수·대상 보기·한 덩어리 처리 — 판정은 DB 함수 한 곳({@code R__batch_18_board_privacy_retention.sql} 의
 * {@code board_privacy_count} · {@code board_privacy_targets} · {@code board_privacy_process})에만 있고, DB 배치도 같은 함수를 부른다(이중 구현 없음).
 *
 * <p>native SQL 인 이유: DB 함수 호출이라 JPQL/QueryDSL 에 없다. 처리({@link #process})는 트랜잭션 밖에서 문장마다 커밋한다 — 덩어리 하나가 짧은 트랜잭션이 되게.
 */
@Repository
public class BoardPrivacyGateway {

    private final JdbcTemplate jdbc;

    public BoardPrivacyGateway(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 축의 대상 수(저장된 설정 기준) */
    public long count(String axis) {
        Long count = jdbc.queryForObject("SELECT board_privacy_count(?)", Long.class, axis);
        return count == null ? 0 : count;
    }

    /** 축의 대상 앞의 몇 줄(오래된 것부터) */
    public List<BoardPrivacyTargetView> targets(String axis, int limit) {
        return jdbc.query("SELECT target_id, title, board_name, created_at FROM board_privacy_targets(?, ?)",
                (rs, i) -> {
                    Timestamp at = rs.getTimestamp(4);
                    return new BoardPrivacyTargetView(rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2),
                            rs.getString(3) == null ? "" : rs.getString(3), at == null ? null : at.toInstant());
                }, axis, limit);
    }

    /** 축 하나를 덩어리 하나만큼 처리한다. 돌려주는 값 = 바꾼 행 수, 배치가 도는 중이면 -1 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public long process(String axis, int limit) {
        Long processed = jdbc.queryForObject("SELECT board_privacy_process(?, ?)", Long.class, axis, limit);
        return processed == null ? 0 : processed;
    }
}
