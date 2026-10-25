package com.community.terms.repository.master;

import com.community.terms.dto.TermsVersionMeta;
import com.community.terms.entity.QTermsVersionEntity;
import com.community.terms.entity.TermsKind;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TermsVersionRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QTermsVersionEntity VERSION = QTermsVersionEntity.termsVersionEntity;

    public TermsVersionRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 모든 종류의 게시본(본문 없이) — 종류, 버전 큰 것 먼저 */
    public List<TermsVersionMeta> allMeta() {
        return query.select(Projections.constructor(TermsVersionMeta.class, VERSION.kind, VERSION.version, VERSION.effectiveDate, VERSION.publishedAt,
                        VERSION.publishedBy, VERSION.changeNote))
                .from(VERSION)
                .orderBy(VERSION.kind.asc(), VERSION.version.desc())
                .fetch();
    }

    /** 한 종류의 게시본(본문 없이) — 버전 큰 것 먼저 */
    public List<TermsVersionMeta> metaOf(TermsKind kind) {
        return query.select(Projections.constructor(TermsVersionMeta.class, VERSION.kind, VERSION.version, VERSION.effectiveDate, VERSION.publishedAt,
                        VERSION.publishedBy, VERSION.changeNote))
                .from(VERSION)
                .where(VERSION.kind.eq(kind))
                .orderBy(VERSION.version.desc())
                .fetch();
    }
}
