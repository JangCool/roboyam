package com.community.team.repository.master;

import com.community.notification.repository.master.NotificationRecipientRepositoryQDSL;
import com.community.operator.entity.QOperatorEntity;
import com.community.team.dto.TeamMemberRow;
import com.community.team.entity.QTeamEntity;
import com.community.team.entity.QTeamMemberEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 팀 조회 — 이름 중복, 팀원(운영자 정보 포함), 팀에 넣을 수 있는 활성 운영자. <b>트랜잭션은 부르는 서비스가 연다.</b> */
@Repository
public class TeamRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QTeamEntity TEAM = QTeamEntity.teamEntity;
    private static final QTeamMemberEntity MEMBER = QTeamMemberEntity.teamMemberEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    public TeamRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 같은 이름(대소문자 무시)의 다른 팀이 있는가 — {@code exceptTeamId} 는 자기 자신을 뺄 때(없으면 null) */
    public boolean existsByName(String teamName, Long exceptTeamId) {
        BooleanExpression same = TEAM.teamName.lower().eq(Expressions.stringTemplate("lower({0})", teamName));
        if (exceptTeamId != null) {
            same = same.and(TEAM.id.ne(exceptTeamId));
        }
        return query.selectOne().from(TEAM).where(same).fetchFirst() != null;
    }

    /** 모든 팀의 팀원 — 팀 번호별, 이름순. 운영자 정보와 지금 활성인지를 함께 */
    public Map<Long, List<TeamMemberRow>> membersByTeam(Instant now) {
        var active = new CaseBuilder().when(NotificationRecipientRepositoryQDSL.isActive(now)).then(true).otherwise(false);
        List<Tuple> rows = query.select(MEMBER.teamId, OPERATOR.id, OPERATOR.operatorName, OPERATOR.email, active).from(MEMBER).join(OPERATOR).on(OPERATOR.id.eq(MEMBER.operatorId))
                .orderBy(OPERATOR.operatorName.asc(), OPERATOR.id.asc()).fetch();
        Map<Long, List<TeamMemberRow>> byTeam = new LinkedHashMap<>();
        for (Tuple row : rows) {
            byTeam.computeIfAbsent(row.get(MEMBER.teamId), key -> new ArrayList<>())
                    .add(new TeamMemberRow(row.get(MEMBER.teamId), row.get(OPERATOR.id), row.get(OPERATOR.operatorName), row.get(OPERATOR.email), Boolean.TRUE.equals(row.get(active))));
        }
        return byTeam;
    }

    /** 팀에 넣을 수 있는 운영자(지금 활성인 운영자) — 이름순 */
    public List<TeamMemberRow> activeOperators(Instant now) {
        List<Tuple> rows = query.select(OPERATOR.id, OPERATOR.operatorName, OPERATOR.email).from(OPERATOR).where(NotificationRecipientRepositoryQDSL.isActive(now))
                .orderBy(OPERATOR.operatorName.asc(), OPERATOR.id.asc()).fetch();
        List<TeamMemberRow> out = new ArrayList<>();
        for (Tuple row : rows) {
            out.add(new TeamMemberRow(0, row.get(OPERATOR.id), row.get(OPERATOR.operatorName), row.get(OPERATOR.email), true));
        }
        return out;
    }
}
