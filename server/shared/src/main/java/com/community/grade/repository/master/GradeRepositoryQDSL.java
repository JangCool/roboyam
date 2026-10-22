package com.community.grade.repository.master;

import com.community.grade.dto.PromotionScope;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.MemberGradeChangeEntity;
import com.community.grade.entity.QGradeEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 등급 목록·등급별 회원 수·회원 이동·승급 대상 집계와 일괄 승급. 정렬·집계·갱신은 DB 에서 한다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>승급 대상은 <b>정상(ACTIVE) 회원</b>이다 — 기간·영구 정지 회원은 자동 승급이 멈추고(제재로 중지), 탈퇴 회원은 대상이 아니다.
 * 회원 수는 탈퇴 회원을 뺀 수다.
 */
@Repository
public class GradeRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QGradeEntity GRADE = QGradeEntity.gradeEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QUserEntity USER = QUserEntity.userEntity;

    private final EntityManager entityManager;

    public GradeRepositoryQDSL(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 모든 등급 — 서열 순서 */
    public List<GradeEntity> findAllOrdered() {
        return query.selectFrom(GRADE).orderBy(GRADE.level.asc()).fetch();
    }

    /** 등급마다 소속 회원 수(탈퇴 회원 제외). 회원이 없는 등급은 들어 있지 않다 */
    public Map<String, Long> memberCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : query.select(PROFILE.gradeKey, PROFILE.count()).from(PROFILE).join(USER).on(USER.id.eq(PROFILE.userId))
                .where(USER.status.ne(UserStatus.WITHDRAWN)).groupBy(PROFILE.gradeKey).fetch()) {
            counts.put(row.get(PROFILE.gradeKey), row.get(PROFILE.count()));
        }
        return counts;
    }

    /** 그 등급의 회원 수(탈퇴 회원 제외) */
    public long memberCountOf(String gradeKey) {
        Long count = query.select(PROFILE.count()).from(PROFILE).join(USER).on(USER.id.eq(PROFILE.userId))
                .where(PROFILE.gradeKey.eq(gradeKey), USER.status.ne(UserStatus.WITHDRAWN)).fetchOne();
        return count == null ? 0 : count;
    }

    /**
     * 한 등급의 회원을 모두 다른 등급으로 옮긴다(등급을 지울 때 바로 아래 등급으로). 탈퇴 회원의 프로필도 함께 옮긴다(외래키). 옮긴 줄 수.
     * 옮긴 회원마다(탈퇴 회원 제외) 같은 문장에서 등급 변경 이력({@code kind=ADMIN, reason_code=GRADE_REMOVED}, 저장한 운영자)을 남긴다.
     *
     * <p>Native SQL 인 이유: 「바꾼 행에서 회원 ID 를 받아 이력에 넣는」 데이터 변경 CTE({@code WITH moved AS (UPDATE … RETURNING) INSERT … SELECT})는 JPQL·QueryDSL 에 대응 문법이 없다.
     * 한 문장이라 등급 이동과 이력이 함께 성공하거나 함께 실패하고, 수만 명을 옮겨도 왕복이 한 번이다.
     */
    public long moveMembers(String fromKey, String toKey, long operatorId, Instant now) {
        Object moved = entityManager.createNativeQuery("""
                        WITH moved AS (
                            UPDATE profiles SET grade_key = :toKey, updated_at = :now WHERE grade_key = :fromKey RETURNING user_id
                        ), history AS (
                            INSERT INTO member_grade_changes (user_id, from_grade_key, to_grade_key, kind, reason_code, actor_operator_id, created_at)
                            SELECT m.user_id, :fromKey, :toKey, 'ADMIN', :reasonCode, :operatorId, :now
                              FROM moved m JOIN users u ON u.id = m.user_id
                             WHERE u.status <> 'WITHDRAWN'
                            RETURNING 1
                        )
                        SELECT count(*) FROM moved
                        """)
                .setParameter("fromKey", fromKey).setParameter("toKey", toKey).setParameter("now", now)
                .setParameter("reasonCode", MemberGradeChangeEntity.REASON_GRADE_REMOVED).setParameter("operatorId", operatorId)
                .getSingleResult();
        return ((Number) moved).longValue();
    }

    /** 이 범위에서 올라갈 회원 수 */
    public long countPromotable(PromotionScope scope) {
        if (scope.fromKeys().isEmpty()) {
            return 0;
        }
        Long count = query.select(PROFILE.count()).from(PROFILE).where(promotable(scope)).fetchOne();
        return count == null ? 0 : count;
    }

    /**
     * 이 범위의 회원을 그 등급으로 올린다. 올린 줄 수. 올린 회원마다 같은 문장에서 등급 변경 이력({@code kind=AUTO})을 남긴다 — 승급과 이력이 함께 커밋된다.
     *
     * <p>Native SQL 인 이유: 바꾼 행의 (회원, 이전 등급)을 받아 이력에 넣는 데이터 변경 CTE 는 JPQL·QueryDSL 에 없다. 대상 조건은 {@link #promotable} 와 <b>같은 뜻</b>이다 —
     * 한쪽을 고치면 다른 쪽(과 {@code R__batch_15_grade_promotion.sql})도 고친다. 고른 뒤 다른 곳이 등급을 바꾼 회원은 {@code grade_key = 고른 때의 값} 확인으로 건너뛴다.
     */
    public long promote(PromotionScope scope, String toKey, Instant now) {
        if (scope.fromKeys().isEmpty()) {
            return 0;
        }
        Query statement = entityManager.createNativeQuery("""
                WITH picked AS (
                    SELECT p.user_id, p.grade_key AS from_key
                      FROM profiles p
                     WHERE p.grade_key IN (:fromKeys)
                       AND (:includeManual OR NOT p.grade_manual)
                       AND EXISTS (SELECT 1 FROM users u
                                    WHERE u.id = p.user_id AND u.status = 'ACTIVE' AND u.deleted_at IS NULL AND u.created_at <= :joinedBefore%s)
                ), moved AS (
                    UPDATE profiles p SET grade_key = :toKey, updated_at = :now
                      FROM picked
                     WHERE p.user_id = picked.user_id AND p.grade_key = picked.from_key
                 RETURNING p.user_id, picked.from_key
                ), history AS (
                    INSERT INTO member_grade_changes (user_id, from_grade_key, to_grade_key, kind, created_at)
                    SELECT user_id, from_key, :toKey, 'AUTO', :now FROM moved
                    RETURNING 1
                )
                SELECT count(*) FROM moved
                """.formatted(scope.notJoinedBefore() == null ? "" : " AND u.created_at > :notJoinedBefore"))
                .setParameter("fromKeys", scope.fromKeys()).setParameter("includeManual", scope.includeManual())
                .setParameter("joinedBefore", scope.joinedBefore()).setParameter("toKey", toKey).setParameter("now", now);
        if (scope.notJoinedBefore() != null) {
            statement.setParameter("notJoinedBefore", scope.notJoinedBefore());
        }
        return ((Number) statement.getSingleResult()).longValue();
    }

    /**
     * 승급 대상 조건 — ACTIVE·미탈퇴 회원, 가입 시각 기준 일수, 수동 상향 회원 포함 여부.
     * <b>같은 규칙이 {@link #promote}(native SQL)와 DB 배치(SQL) {@code R__batch_15_grade_promotion.sql} 에도 있다</b> — 여기를 고치면 그쪽도 같이 고친다(그 반대도).
     */
    private static BooleanBuilder promotable(PromotionScope scope) {
        BooleanBuilder eligibleUsers = new BooleanBuilder();
        eligibleUsers.and(USER.status.eq(UserStatus.ACTIVE)).and(USER.deletedAt.isNull()).and(USER.createdAt.loe(scope.joinedBefore()));
        if (scope.notJoinedBefore() != null) {
            eligibleUsers.and(USER.createdAt.gt(scope.notJoinedBefore()));
        }
        BooleanBuilder where = new BooleanBuilder();
        where.and(PROFILE.gradeKey.in(scope.fromKeys()));
        if (!scope.includeManual()) {
            where.and(PROFILE.gradeManual.isFalse());
        }
        where.and(PROFILE.userId.in(JPAExpressions.select(USER.id).from(USER).where(eligibleUsers)));
        return where;
    }
}
