package com.community.counsel.repository.master;

import com.community.counsel.entity.QCounselWeeklyPickEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 주간 「가장 도움 된 조언」 수({@code counsel_weekly_picks}, V274) — 쌓기는 고를 때 한 문장 upsert({@code ON CONFLICT DO UPDATE}, PostgreSQL 전용 — 여러 글의 고르기가 같은 줄을
 * 동시에 올려도 한 줄에 더해진다), 읽기는 QueryDSL. 배치 없음(ai-love-counsel-contract 「결정」 순위 집계).
 */
@Repository
public class CounselPickStore extends IrobitRepositoryQDSL {

    private static final QCounselWeeklyPickEntity W = QCounselWeeklyPickEntity.counselWeeklyPickEntity;

    private final JdbcTemplate jdbc;

    public CounselPickStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 주·게시판·상담사(사람 회원 = 0)에 한 표 */
    public void add(long boardId, LocalDate weekStart, long aiMemberIdOrHuman) {
        jdbc.update("INSERT INTO counsel_weekly_picks (board_id, week_start, ai_member_id, picks) VALUES (?, ?, ?, 1) "
                + "ON CONFLICT (board_id, week_start, ai_member_id) DO UPDATE SET picks = counsel_weekly_picks.picks + 1", boardId, Date.valueOf(weekStart), aiMemberIdOrHuman);
    }

    /** 그 주·게시판의 상담사(사람 회원 = 0) → 고른 수 */
    public Map<Long, Integer> week(long boardId, LocalDate weekStart) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Tuple t : query.select(W.aiMemberId, W.picks).from(W).where(W.boardId.eq(boardId), W.weekStart.eq(weekStart)).orderBy(W.aiMemberId.asc()).fetch()) {
            Integer n = t.get(W.picks);
            out.put(t.get(W.aiMemberId), n == null ? 0 : n);
        }
        return out;
    }
}
