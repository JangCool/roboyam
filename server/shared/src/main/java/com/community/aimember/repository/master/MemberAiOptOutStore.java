package com.community.aimember.repository.master;

import com.community.aimember.entity.QMemberAiOptOutEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원의 AI 제외 설정({@code member_ai_opt_outs}, V264) — 줄이 있으면 켬. 읽기·끄기는 QueryDSL, 켜기는 두 번 눌러도 한 줄만 남게 {@code ON CONFLICT DO NOTHING}
 * (PostgreSQL 전용 — 쪽지 받기 설정 {@code DmQueries#setAccept} 와 같은 방식).
 */
@Repository
public class MemberAiOptOutStore extends IrobitRepositoryQDSL {

    private static final QMemberAiOptOutEntity O = QMemberAiOptOutEntity.memberAiOptOutEntity;

    private final JdbcTemplate jdbc;

    public MemberAiOptOutStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public boolean optedOut(long userId) {
        return query.selectOne().from(O).where(O.userId.eq(userId)).fetchFirst() != null;
    }

    /** 이 회원들 중 켠 회원 */
    public Set<Long> optedOut(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(O.userId).from(O).where(O.userId.in(userIds)).fetch());
    }

    /** 켜기(이미 켰으면 그대로) */
    public void optOut(long userId, Instant now) {
        jdbc.update("INSERT INTO member_ai_opt_outs (user_id, created_at) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING", userId, Timestamp.from(now));
    }

    /** 끄기(줄이 없으면 그대로) */
    public void optIn(long userId) {
        query.delete(O).where(O.userId.eq(userId)).execute();
    }
}
