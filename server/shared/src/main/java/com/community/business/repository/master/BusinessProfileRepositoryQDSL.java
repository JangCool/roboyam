package com.community.business.repository.master;

import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.business.entity.QBusinessServiceEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** 사업자 신원 여러 명 한 번에 읽기 — 작성자 사업자 뱃지(공개 사이트) · 관리자 사업자 심사 목록(admin {@code BusinessAdminService}) */
@Repository
public class BusinessProfileRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QBusinessProfileEntity BUSINESS = QBusinessProfileEntity.businessProfileEntity;
    private static final QBusinessServiceEntity SERVICE = QBusinessServiceEntity.businessServiceEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

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

    // ── 관리자 심사 목록 ─────────────────────────────────────────────────────

    /**
     * 관리자 목록 조건(상태 제외) — 비어 있는(null) 조건은 거르지 않는다.
     *
     * @param service   이 서비스를 켠(끔 {@code OFF} 이 아닌) 신원만
     * @param term      상호·신청자 닉네임 부분 일치(대소문자 무시)
     * @param bizNoHash 등록번호 10자리의 블라인드 색인 — 같으면 든다(term 이 있을 때만)
     * @param last4     등록번호 끝 4자리 — 같으면 든다(term 이 있을 때만)
     */
    public record AdminCriteria(BusinessServiceKind service, String term, String bizNoHash, String last4) {
    }

    /** 관리자 목록 한 줄(신원 + 신청자 닉네임 — 프로필이 없으면 null) */
    public record AdminRow(long id, String companyName, String bizNoLast4, BusinessStatus status, boolean reReview, long userId, String nickname, Instant submittedAt) {
    }

    /** 상태별 건수(상태 거르기를 뺀 같은 조건) — 줄이 없는 상태는 빠진다 */
    public Map<BusinessStatus, Long> adminStatusCounts(AdminCriteria c) {
        Map<BusinessStatus, Long> out = new EnumMap<>(BusinessStatus.class);
        for (Tuple t : from(query.select(BUSINESS.status, BUSINESS.count())).where(where(c)).groupBy(BUSINESS.status).fetch()) {
            out.put(t.get(BUSINESS.status), t.get(BUSINESS.count()));
        }
        return out;
    }

    public long adminCount(AdminCriteria c, BusinessStatus status) {
        Long n = from(query.select(BUSINESS.count())).where(where(c), status == null ? null : BUSINESS.status.eq(status)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 한 쪽 — 심사 대기({@code PENDING})는 신청 오래된 것 먼저, 그 밖(다른 상태·전체)은 최근 것 먼저 */
    public List<AdminRow> adminPage(AdminCriteria c, BusinessStatus status, long offset, int limit) {
        JPAQuery<Tuple> q = from(query.select(BUSINESS.id, BUSINESS.companyName, BUSINESS.bizNoLast4, BUSINESS.status, BUSINESS.reReview, BUSINESS.userId,
                PROFILE.nickname, BUSINESS.submittedAt)).where(where(c), status == null ? null : BUSINESS.status.eq(status));
        q = status == BusinessStatus.PENDING ? q.orderBy(BUSINESS.submittedAt.asc(), BUSINESS.id.asc()) : q.orderBy(BUSINESS.submittedAt.desc(), BUSINESS.id.desc());
        return q.offset(offset).limit(limit).fetch().stream()
                .map(t -> new AdminRow(t.get(BUSINESS.id), t.get(BUSINESS.companyName), t.get(BUSINESS.bizNoLast4), t.get(BUSINESS.status),
                        Boolean.TRUE.equals(t.get(BUSINESS.reReview)), t.get(BUSINESS.userId), t.get(PROFILE.nickname), t.get(BUSINESS.submittedAt)))
                .toList();
    }

    /** 회원 닉네임(프로필이 없으면 비어 있다) */
    public Optional<String> nickname(long userId) {
        return Optional.ofNullable(query.select(PROFILE.nickname).from(PROFILE).where(PROFILE.userId.eq(userId)).fetchFirst());
    }

    /** 운영자 표시 이름(운영자가 없으면 비어 있다) */
    public Optional<String> operatorName(long operatorId) {
        return Optional.ofNullable(query.select(OPERATOR.operatorName).from(OPERATOR).where(OPERATOR.id.eq(operatorId)).fetchFirst());
    }

    /** 신원 · 신청자 프로필(닉네임 검색) */
    private static <T> JPAQuery<T> from(JPAQuery<T> select) {
        return select.from(BUSINESS).leftJoin(PROFILE).on(PROFILE.userId.eq(BUSINESS.userId));
    }

    private static BooleanBuilder where(AdminCriteria c) {
        BooleanBuilder w = new BooleanBuilder();
        if (c.service() != null) {
            w.and(JPAExpressions.selectOne().from(SERVICE)
                    .where(SERVICE.profileId.eq(BUSINESS.id), SERVICE.service.eq(c.service()), SERVICE.status.ne(BusinessServiceStatus.OFF)).exists());
        }
        if (c.term() != null) {
            BooleanExpression match = BUSINESS.companyName.containsIgnoreCase(c.term()).or(PROFILE.nickname.containsIgnoreCase(c.term()));
            if (c.bizNoHash() != null) {
                match = match.or(BUSINESS.bizNoHash.eq(c.bizNoHash()));
            } else if (c.last4() != null) {
                match = match.or(BUSINESS.bizNoLast4.eq(c.last4()));
            }
            w.and(match);
        }
        return w;
    }
}
