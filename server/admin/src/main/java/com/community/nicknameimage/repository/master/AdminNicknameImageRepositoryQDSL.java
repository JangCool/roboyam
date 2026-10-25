package com.community.nicknameimage.repository.master;

import com.community.grade.entity.QGradeEntity;
import com.community.nicknameimage.entity.NicknameImageRequestEntity;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.nicknameimage.entity.QNicknameImageRequestEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 관리자 닉네임 그림 목록 — 상태별, 닉네임 부분 일치(대소문자 무시), 페이징은 DB 에서. 대기는 오래된 순(먼저 온 것부터 검토), 나머지는 최근 처리 순(승인·반려 = 검토 시각, 해제 = 해제 시각).
 * 신청한 회원의 지금 닉네임·등급 이름과 검토한 운영자 이름을 왼쪽 조인으로 함께 읽는다.
 */
@Repository
public class AdminNicknameImageRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QNicknameImageRequestEntity REQUEST = QNicknameImageRequestEntity.nicknameImageRequestEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QGradeEntity GRADE = QGradeEntity.gradeEntity;
    private static final QOperatorEntity REVIEWER = QOperatorEntity.operatorEntity;

    public AdminNicknameImageRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 목록 한 줄 — 신청 + 회원 닉네임·등급 키·등급 이름 + 검토한 운영자 이름 */
    public record Row(NicknameImageRequestEntity request, String nickname, String gradeKey, String gradeLabel, String reviewerName) {
    }

    public List<Row> page(NicknameImageStatus status, String keyword, int page, int size) {
        List<Tuple> rows = query.select(REQUEST, PROFILE.nickname, PROFILE.gradeKey, GRADE.label, REVIEWER.operatorName)
                .from(REQUEST)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(REQUEST.userId))
                .leftJoin(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey))
                .leftJoin(REVIEWER).on(REVIEWER.id.eq(REQUEST.reviewedBy))
                .where(REQUEST.status.eq(status), keyword(keyword))
                .orderBy(order(status))
                .offset((long) page * size).limit(size)
                .fetch();
        return rows.stream().map(t -> new Row(t.get(REQUEST), t.get(PROFILE.nickname), t.get(PROFILE.gradeKey), t.get(GRADE.label), t.get(REVIEWER.operatorName)))
                .toList();
    }

    public long count(NicknameImageStatus status, String keyword) {
        Long n = query.select(REQUEST.count()).from(REQUEST).leftJoin(PROFILE).on(PROFILE.userId.eq(REQUEST.userId))
                .where(REQUEST.status.eq(status), keyword(keyword)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 한 줄(승인·반려·해제 뒤 응답) */
    public Row row(long id) {
        Tuple t = query.select(REQUEST, PROFILE.nickname, PROFILE.gradeKey, GRADE.label, REVIEWER.operatorName)
                .from(REQUEST)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(REQUEST.userId))
                .leftJoin(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey))
                .leftJoin(REVIEWER).on(REVIEWER.id.eq(REQUEST.reviewedBy))
                .where(REQUEST.id.eq(id)).fetchOne();
        return t == null ? null : new Row(t.get(REQUEST), t.get(PROFILE.nickname), t.get(PROFILE.gradeKey), t.get(GRADE.label), t.get(REVIEWER.operatorName));
    }

    /** 신청한 회원 ID(없는 신청이면 null) */
    public Long userIdOf(long id) {
        return query.select(REQUEST.userId).from(REQUEST).where(REQUEST.id.eq(id)).fetchOne();
    }

    private static BooleanExpression keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return PROFILE.nickname.lower().like("%" + escaped + "%", '\\');
    }

    private static OrderSpecifier<?>[] order(NicknameImageStatus status) {
        return switch (status) {
            case PENDING -> new OrderSpecifier<?>[] {REQUEST.id.asc()};
            case REVOKED -> new OrderSpecifier<?>[] {REQUEST.revokedAt.desc(), REQUEST.id.desc()};
            default -> new OrderSpecifier<?>[] {REQUEST.reviewedAt.desc(), REQUEST.id.desc()};
        };
    }
}
