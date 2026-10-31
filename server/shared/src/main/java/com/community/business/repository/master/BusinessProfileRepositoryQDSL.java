package com.community.business.repository.master;

import com.community.business.entity.BusinessStatus;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** 사업자 신원 여러 명 한 번에 읽기 — 작성자 사업자 뱃지(공개 사이트) */
@Repository
public class BusinessProfileRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QBusinessProfileEntity BUSINESS = QBusinessProfileEntity.businessProfileEntity;

    public BusinessProfileRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 그중 사업자 신원이 {@code APPROVED} 인 회원 ID — 한 문장. 비어 있으면 읽지 않는다 */
    public Set<Long> approvedUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(BUSINESS.userId).from(BUSINESS).where(BUSINESS.userId.in(userIds), BUSINESS.status.eq(BusinessStatus.APPROVED)).fetch());
    }
}
