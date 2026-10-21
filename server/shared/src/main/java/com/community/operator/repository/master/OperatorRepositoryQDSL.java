package com.community.operator.repository.master;

import com.community.operator.dto.OperatorCounts;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.entity.OperatorTab;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 운영자 목록(탭·키워드)·탭별 인원. 정렬·조건은 DB 에서 한다. <b>트랜잭션은 부르는 서비스가 연다.</b> */
@Repository
public class OperatorRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    public OperatorRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 탭의 운영자를 최근 등록순으로. 키워드가 있으면 이름·이메일 부분 일치(대소문자 무시) */
    public List<OperatorEntity> search(OperatorTab tab, String keyword, Instant now) {
        BooleanExpression where = inTab(tab, now);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.strip();
            where = where.and(OPERATOR.email.containsIgnoreCase(k).or(OPERATOR.operatorName.containsIgnoreCase(k)));
        }
        return query.selectFrom(OPERATOR).where(where).orderBy(OPERATOR.createdAt.desc(), OPERATOR.id.desc()).fetch();
    }

    /**
     * 그 이름(대소문자 무시)을 쓰는 <b>다른</b> 운영자가 있는가 — {@code exceptId} 는 자기 자신(새로 만드는 쪽은 없는 ID 0). 유일 인덱스 {@code ux_operators_name_lower}
     * ({@code lower(operator_name)})와 같은 함수로 견주도록 DB 의 {@code lower} 를 쓴다.
     */
    public boolean existsOtherWithName(String operatorName, long exceptId) {
        return query.selectOne().from(OPERATOR)
                .where(OPERATOR.operatorName.lower().eq(Expressions.stringTemplate("lower({0})", operatorName)), OPERATOR.id.ne(exceptId))
                .fetchFirst() != null;
    }

    public OperatorCounts counts(Instant now) {
        return new OperatorCounts(count(OperatorTab.ACTIVE, now), count(OperatorTab.PENDING, now), count(OperatorTab.BLOCKED, now));
    }

    private long count(OperatorTab tab, Instant now) {
        Long count = query.select(OPERATOR.count()).from(OPERATOR).where(inTab(tab, now)).fetchOne();
        return count == null ? 0 : count;
    }

    /**
     * 탭 조건. 승인 대기 = 미승인, 막힘 = 승인됐는데 잠김·만료·사용 중지·기간 밖, 사용 = 승인됐고 막히지 않음.
     * 기간 조건은 {@code isNotNull().and(...)} 로 써서 비어 있는 기간이 NULL 이 아니라 false 가 되게 한다(NULL 이면 {@code NOT} 이 행을 빠뜨린다).
     */
    private static BooleanExpression inTab(OperatorTab tab, Instant now) {
        BooleanExpression periodOver = OPERATOR.accountStartAt.isNotNull().and(OPERATOR.accountStartAt.gt(now))
                .or(OPERATOR.accountEndAt.isNotNull().and(OPERATOR.accountEndAt.lt(now)));
        BooleanExpression blocked = OPERATOR.approved.isTrue()
                .and(OPERATOR.locked.isTrue().or(OPERATOR.expired.isTrue()).or(OPERATOR.enabled.isFalse()).or(periodOver));
        return switch (tab) {
            case PENDING -> OPERATOR.approved.isFalse();
            case BLOCKED -> blocked;
            case ACTIVE -> OPERATOR.approved.isTrue().and(blocked.not());
        };
    }
}
