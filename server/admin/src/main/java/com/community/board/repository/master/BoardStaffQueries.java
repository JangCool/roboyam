package com.community.board.repository.master;

import com.community.board.entity.QBoardStaffEntity;
import com.community.grade.entity.QGradeEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 게시판 매니저·스텝 조회 — 담당 목록(닉네임·등급·지정한 운영자), 게시판별 인원, 회원별 담당 수, 후보 회원 검색 */
@Repository
public class BoardStaffQueries extends RoboyamRepositoryQDSL {

    private static final QBoardStaffEntity STAFF = QBoardStaffEntity.boardStaffEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QGradeEntity GRADE = QGradeEntity.gradeEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    /** 담당 한 명 */
    public record StaffRow(long userId, String nickname, String gradeKey, String role, Instant appointedAt, String appointedBy) {
    }

    /** 후보 회원 한 명 — 자격 판정에 쓰는 칸 */
    public record CandidateRow(long userId, String nickname, String email, String gradeKey, int gradeLevel, UserStatus status, boolean emailVerified) {
    }

    /** 지정 자격 판정에 쓰는 회원 정보 — 없거나 탈퇴했으면 null */
    public record MemberFacts(long userId, String nickname, String gradeKey, int gradeLevel, UserStatus status, boolean emailVerified) {
    }

    public BoardStaffQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 이 게시판의 담당(매니저 먼저, 지정 순) */
    public List<StaffRow> staffOf(long boardId) {
        List<Tuple> rows = query.select(STAFF.userId, PROFILE.nickname, PROFILE.gradeKey, STAFF.role, STAFF.appointedAt, OPERATOR.operatorName)
                .from(STAFF)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(STAFF.userId))
                .leftJoin(OPERATOR).on(OPERATOR.id.eq(STAFF.appointedByOperator))
                .where(STAFF.boardId.eq(boardId))
                .orderBy(STAFF.role.asc(), STAFF.appointedAt.asc(), STAFF.userId.asc())
                .fetch();
        return rows.stream().map(t -> new StaffRow(t.get(STAFF.userId), nullToEmpty(t.get(PROFILE.nickname)), nullToEmpty(t.get(PROFILE.gradeKey)),
                t.get(STAFF.role), t.get(STAFF.appointedAt), nullToEmpty(t.get(OPERATOR.operatorName)))).toList();
    }

    /** 게시판별 [매니저 수, 스텝 수] */
    public Map<Long, int[]> countsByBoard() {
        Map<Long, int[]> out = new HashMap<>();
        for (Tuple t : query.select(STAFF.boardId, STAFF.role, STAFF.userId.count()).from(STAFF).groupBy(STAFF.boardId, STAFF.role).fetch()) {
            int[] counts = out.computeIfAbsent(t.get(STAFF.boardId), key -> new int[2]);
            Long count = t.get(STAFF.userId.count());
            counts["MANAGER".equals(t.get(STAFF.role)) ? 0 : 1] = count == null ? 0 : count.intValue();
        }
        return out;
    }

    /** 회원별 담당 게시판 수(매니저·스텝 합산) */
    public Map<Long, Long> boardCountsOf(Collection<Long> userIds) {
        Map<Long, Long> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(STAFF.userId, STAFF.boardId.count()).from(STAFF).where(STAFF.userId.in(userIds)).groupBy(STAFF.userId).fetch()) {
            out.put(t.get(STAFF.userId), t.get(STAFF.boardId.count()));
        }
        return out;
    }

    /**
     * 후보 회원 — 탈퇴하지 않은 회원 중 닉네임에 검색어가 들어가거나 이메일이 검색어와 같은(대소문자 무시) 회원, 또는 회원 ID 가 같은 회원. 닉네임 순 최대 {@code limit} 명.
     * 이메일은 부분 검색하지 않는다(가린 이메일로만 보이는 화면에서 이메일 존재를 더듬어 찾지 못하게).
     */
    public List<CandidateRow> candidates(String keyword, int limit) {
        String needle = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        BooleanExpression match = null;
        if (!needle.isEmpty()) {
            match = PROFILE.nickname.lower().contains(needle).or(USER.email.lower().eq(needle));
            if (needle.chars().allMatch(Character::isDigit) && needle.length() <= 19) {
                match = match.or(USER.id.eq(Long.parseLong(needle)));
            }
        }
        List<Tuple> rows = query.select(USER.id, PROFILE.nickname, USER.email, PROFILE.gradeKey, GRADE.level, USER.status, USER.emailVerifiedAt)
                .from(USER)
                .join(PROFILE).on(PROFILE.userId.eq(USER.id))
                .leftJoin(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey))
                .where(USER.deletedAt.isNull(), USER.status.ne(UserStatus.WITHDRAWN), match)
                .orderBy(PROFILE.nickname.asc(), USER.id.asc())
                .limit(limit)
                .fetch();
        return rows.stream().map(t -> new CandidateRow(t.get(USER.id), nullToEmpty(t.get(PROFILE.nickname)), t.get(USER.email),
                nullToEmpty(t.get(PROFILE.gradeKey)), t.get(GRADE.level) == null ? 1 : t.get(GRADE.level), t.get(USER.status),
                t.get(USER.emailVerifiedAt) != null)).toList();
    }

    /** 회원 하나의 자격 판정 정보 — 없거나 탈퇴했으면 null */
    public MemberFacts member(long userId) {
        Tuple t = query.select(USER.id, PROFILE.nickname, PROFILE.gradeKey, GRADE.level, USER.status, USER.emailVerifiedAt)
                .from(USER)
                .join(PROFILE).on(PROFILE.userId.eq(USER.id))
                .leftJoin(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey))
                .where(USER.id.eq(userId), USER.deletedAt.isNull(), USER.status.ne(UserStatus.WITHDRAWN))
                .fetchOne();
        if (t == null) {
            return null;
        }
        return new MemberFacts(t.get(USER.id), nullToEmpty(t.get(PROFILE.nickname)), nullToEmpty(t.get(PROFILE.gradeKey)),
                t.get(GRADE.level) == null ? 1 : t.get(GRADE.level), t.get(USER.status), t.get(USER.emailVerifiedAt) != null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
