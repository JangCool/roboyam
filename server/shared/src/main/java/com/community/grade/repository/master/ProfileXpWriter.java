package com.community.grade.repository.master;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 경험치·레벨 쓰기({@code profiles.xp}·{@code profiles.level}, V196) — {@code XpService} 만 부른다.
 *
 * <p>Native SQL(JDBC)인 이유: 포인트 원장 줄을 쓰는 흐름({@code PointService} → {@code PointLedgerWriter}, JDBC)과 같은 자리에서 — 글·댓글쓰기 트랜잭션의 세이브포인트
 * ({@code IsolatedSideWork}) 안에서 — 해야 해 영속성 컨텍스트를 거치지 않는다(같은 트랜잭션이 프로필 엔티티를 들고 있어도 그 엔티티는 이 두 칸을 쓰지 않는다 —
 * {@code ProfileEntity} 의 읽기 전용 매핑). 증감은 「더한 뒤 0 아래로 내려가지 않게」 한 문장이라 같은 회원의 동시 증감도 잃지 않는다
 * (원장 쓰기가 이미 그 회원의 잔액 행을 잠근 아래에서 부른다). 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class ProfileXpWriter {

    /** 바꾼 뒤의 경험치와 바꾸기 전의 레벨 */
    public record Changed(long xp, int previousLevel) {
    }

    private final JdbcTemplate jdbc;

    public ProfileXpWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 레벨 곡선 값({@code grade_settings.xp_curve_base}) — 행이 없으면 비어 있다 */
    public Optional<Integer> curveBase() {
        return jdbc.queryForList("select xp_curve_base from grade_settings where id = 1", Integer.class).stream().findFirst();
    }

    /** 경험치를 {@code delta}(부호 포함)만큼 바꾼다 — 0 아래로는 내려가지 않는다. 프로필 행이 없으면 비어 있다 */
    public Optional<Changed> add(long userId, long delta) {
        return jdbc.query("update profiles set xp = greatest(0, xp + ?) where user_id = ? returning xp, level",
                (rs, i) -> new Changed(rs.getLong(1), rs.getInt(2)), delta, userId).stream().findFirst();
    }

    /**
     * 모든 회원의 경험치를 원장에서 다시 세어 채우고 레벨을 다시 매긴다(V196 의 채우기와 같은 셈 — 데모 팩처럼 원장을 통째로 넣은 뒤). 바뀐 줄 수.
     * 레벨은 DB 함수 {@code grade_xp_level}(Java {@code XpCurve#levelOf} 와 같은 식)로 센다 — 한 문장이라 회원 수와 상관없이 왕복이 한 번이다.
     *
     * @param reasons 경험치에 드는 원장 사유({@code XpService#GAIN} ∪ {@code LOSS})
     */
    public int recomputeAllFromLedger(java.util.Collection<String> reasons) {
        return jdbc.update("""
                update profiles p
                   set xp = s.xp, level = grade_xp_level(s.xp, coalesce((select xp_curve_base from grade_settings where id = 1), 10))
                  from (select pr.user_id, coalesce(greatest(0, sum(l.delta)), 0)::bigint as xp
                          from profiles pr left join point_ledger l on l.user_id = pr.user_id and l.reason = any(?::text[])
                         group by pr.user_id) s
                 where p.user_id = s.user_id
                   and (p.xp <> s.xp or p.level <> grade_xp_level(s.xp, coalesce((select xp_curve_base from grade_settings where id = 1), 10)))
                """, (Object) reasons.toArray(String[]::new));
    }

    /** 레벨을 바꾼다 */
    public void changeLevel(long userId, int level) {
        jdbc.update("update profiles set level = ? where user_id = ?", level, userId);
    }
}
