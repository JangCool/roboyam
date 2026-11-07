package com.community.avatarupload.repository.master;

import com.community.avatarupload.entity.AvatarUploadRequestEntity;
import com.community.avatarupload.entity.AvatarUploadStatus;
import com.community.avatarupload.entity.QAvatarUploadRequestEntity;
import com.community.grade.entity.QGradeEntity;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.nicknameimage.entity.QNicknameImageRequestEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 아바타 올리기 신청({@code avatar_upload_requests}, V157) 읽기·쓰기 — 회원 화면(api)·운영자 심사(admin)·작성자 표시가 같이 쓴다. <b>부르는 쪽 트랜잭션에 참여한다</b>
 * (변경권 예약·사용·반환 {@code UserItemStore}(JDBC)·프로필 아바타 바꾸기와 한 트랜잭션 — JPA 트랜잭션 관리자가 JDBC 에도 같은 연결을 준다).
 *
 * <p>상태는 정해진 상태에서만 바꾼다(조건부 대량 UPDATE — 바꾼 줄 수로 판정한다). 그래서 읽기는 모두 프로젝션이다(엔티티를 1차 캐시에 올려 두면 대량 UPDATE 뒤 낡은 값이 보인다).
 * 새 신청은 넣자마자 내보낸다(flush) — 같은 트랜잭션의 JDBC 저장소가 그 줄을 보고, 문장 차례(대기 하나 — 앞 대기 취소 → 새 대기)가 예전과 같게.
 */
@Repository
public class AvatarUploadStore extends RoboyamRepositoryQDSL {

    /** 신청 한 줄 */
    public record Row(long id, long userId, AvatarUploadStatus status, String imageKey, String stillKey, String contentType, int bytes, int width, int height,
                      int frames, String rejectReason, String revokeReason, Long reviewedBy, Instant reviewedAt, Long revokedBy, Instant revokedAt, Instant endedAt,
                      Instant createdAt) {

        /** 움직이는 GIF(장면이 둘 이상) — 첫 장면 정지 그림이 함께 있다 */
        public boolean animated() {
            return frames > 1;
        }

        /** 파일이 아직 있다 */
        public boolean hasFiles() {
            return imageKey != null;
        }
    }

    /** 관리자 목록 한 줄 — 신청 + 회원 지금 닉네임·등급·고른 프로필 이미지 + 심사한 운영자 이름 + 움직이는 닉네임 이미지를 지금 쓰는가 */
    public record AdminRow(Row request, String nickname, String gradeKey, String gradeLabel, String profileImage, String reviewerName, boolean nicknameMotion) {
    }

    private static final QAvatarUploadRequestEntity R = QAvatarUploadRequestEntity.avatarUploadRequestEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QGradeEntity GRADE = QGradeEntity.gradeEntity;
    private static final QOperatorEntity REVIEWER = QOperatorEntity.operatorEntity;
    private static final QNicknameImageRequestEntity NICKNAME = QNicknameImageRequestEntity.nicknameImageRequestEntity;

    private static final ConstructorExpression<Row> ROW = Projections.constructor(Row.class, R.id, R.userId, R.status, R.imageKey, R.stillKey, R.contentType, R.bytes,
            R.width, R.height, R.frames, R.rejectReason, R.revokeReason, R.reviewedBy, R.reviewedAt, R.revokedBy, R.revokedAt, R.endedAt, R.createdAt);

    /** 관리자 목록 한 줄 — 움직이는 닉네임 이미지(승인 + 정지 그림 있음)를 지금 쓰는가는 부분 질의 */
    private static ConstructorExpression<AdminRow> adminRowExpression() {
        BooleanExpression nicknameMotion = JPAExpressions.selectOne().from(NICKNAME)
                .where(NICKNAME.userId.eq(R.userId), NICKNAME.status.eq(NicknameImageStatus.APPROVED), NICKNAME.stillKey.isNotNull()).exists();
        return Projections.constructor(AdminRow.class, ROW, PROFILE.nickname, PROFILE.gradeKey, GRADE.label, PROFILE.profileImage, REVIEWER.operatorName,
                new CaseBuilder().when(nicknameMotion).then(true).otherwise(false));
    }

    private final AvatarUploadRequestRepository requests;
    private final EntityManager entityManager;

    public AvatarUploadStore(JPAQueryFactory query, AvatarUploadRequestRepository requests, EntityManager entityManager) {
        super(query);
        this.requests = requests;
        this.entityManager = entityManager;
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    public Optional<Row> find(long id) {
        return Optional.ofNullable(query.select(ROW).from(R).where(R.id.eq(id)).fetchOne());
    }

    /**
     * 처리하려는 신청을 잠근다(승인·반려·해제·회원 취소가 겹쳐도 한쪽만 이긴다). 잠금은 신청 표만 고르는 id 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} —
     * 이 표를 FK 로 가리키는 표가 없다), 값은 그 뒤 프로젝션으로 읽는다(잠근 뒤의 값).
     */
    public Optional<Row> findForUpdate(long id) {
        if (query.select(R.id).from(R).where(R.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() == null) {
            return Optional.empty();
        }
        return find(id);
    }

    /** 신청한 회원(없는 신청이면 빈 값) */
    public Optional<Long> userIdOf(long id) {
        return Optional.ofNullable(query.select(R.userId).from(R).where(R.id.eq(id)).fetchOne());
    }

    /** 회원의 그 상태 신청(대기·승인은 회원당 하나 — 부분 유일 색인) */
    public Optional<Row> byStatus(long userId, AvatarUploadStatus status) {
        return Optional.ofNullable(query.select(ROW).from(R).where(R.userId.eq(userId), R.status.eq(status)).orderBy(R.id.desc()).limit(1).fetchFirst());
    }

    /** 회원의 그 상태 신청 전부(반려 파일 정리) */
    public List<Row> allByStatus(long userId, AvatarUploadStatus status) {
        return query.select(ROW).from(R).where(R.userId.eq(userId), R.status.eq(status)).fetch();
    }

    /** 회원의 가장 최근 신청 */
    public Optional<Row> latest(long userId) {
        return Optional.ofNullable(query.select(ROW).from(R).where(R.userId.eq(userId)).orderBy(R.id.desc()).limit(1).fetchFirst());
    }

    /** 여러 회원의 승인된 그림(「내가 올린 아바타」) — 작성자 표시가 목록마다 한 번 */
    public Map<Long, Row> currentOf(Collection<Long> userIds) {
        Map<Long, Row> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Row row : query.select(ROW).from(R).where(R.status.eq(AvatarUploadStatus.APPROVED), R.userId.in(userIds)).fetch()) {
            out.put(row.userId(), row);
        }
        return out;
    }

    /** 오늘(한국 시간 0시 뒤) 올린 수 — 하루 한도 */
    public long countSince(long userId, Instant since) {
        Long count = query.select(R.count()).from(R).where(R.userId.eq(userId), R.createdAt.goe(since)).fetchOne();
        return count == null ? 0 : count;
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    public void insertPending(long id, long userId, String imageKey, String stillKey, String contentType, int bytes, int width, int height, int frames, Instant now) {
        AvatarUploadRequestEntity row = AvatarUploadRequestEntity.pending(id, userId, imageKey, stillKey, contentType, bytes, width, height, frames, now);
        requests.persist(row);
        entityManager.flush();
        entityManager.detach(row); // 이후 값은 프로젝션으로 읽는다
    }

    /** 대기 → CANCELED(회원 취소·새 신청이 대체) */
    public boolean cancel(long id, Instant now) {
        return query.update(R).set(R.status, AvatarUploadStatus.CANCELED).set(R.endedAt, now).where(R.id.eq(id), R.status.eq(AvatarUploadStatus.PENDING)).execute() == 1;
    }

    /** 대기 → APPROVED */
    public boolean approve(long id, long operatorId, Instant now) {
        return query.update(R).set(R.status, AvatarUploadStatus.APPROVED).set(R.reviewedBy, operatorId).set(R.reviewedAt, now)
                .where(R.id.eq(id), R.status.eq(AvatarUploadStatus.PENDING)).execute() == 1;
    }

    /** 대기 → REJECTED(사유) */
    public boolean reject(long id, long operatorId, String reason, Instant now) {
        return query.update(R).set(R.status, AvatarUploadStatus.REJECTED).set(R.rejectReason, reason).set(R.reviewedBy, operatorId).set(R.reviewedAt, now)
                .where(R.id.eq(id), R.status.eq(AvatarUploadStatus.PENDING)).execute() == 1;
    }

    /** 승인 → REVOKED(운영자 해제, 사유) */
    public boolean revoke(long id, long operatorId, String reason, Instant now) {
        return query.update(R).set(R.status, AvatarUploadStatus.REVOKED).set(R.revokeReason, reason).set(R.revokedBy, operatorId).set(R.revokedAt, now)
                .where(R.id.eq(id), R.status.eq(AvatarUploadStatus.APPROVED)).execute() == 1;
    }

    /** 승인 → REMOVED(회원이 지움·다른 그림이 승인돼 바뀜) */
    public boolean remove(long id, Instant now) {
        return query.update(R).set(R.status, AvatarUploadStatus.REMOVED).set(R.endedAt, now).where(R.id.eq(id), R.status.eq(AvatarUploadStatus.APPROVED)).execute() == 1;
    }

    /** 파일을 지웠다(지우는 일은 커밋 뒤 부르는 쪽이 한다) — 키를 비운다 */
    public void dropFiles(long id) {
        query.update(R).setNull(R.imageKey).setNull(R.stillKey).where(R.id.eq(id)).execute();
    }

    // ── 관리자 목록 ─────────────────────────────────────────────────────────

    /**
     * 상태별 한 쪽 — 대기는 오래된 순(먼저 온 것부터), 해제는 해제 시각, 나머지는 심사 시각 최근 순. 「승인」 탭({@code APPROVED})은 승인된 뒤 바뀐·지워진 그림(REMOVED)도 보인다.
     *
     * @param keyword 닉네임 부분 일치(대소문자 무시) — null 이면 조건 없음
     */
    public List<AdminRow> adminPage(AvatarUploadStatus status, String keyword, int offset, int size) {
        OrderSpecifier<?>[] order = switch (status) {
            case PENDING -> new OrderSpecifier<?>[] {R.id.asc()};
            case REVOKED -> new OrderSpecifier<?>[] {R.revokedAt.desc(), R.id.desc()};
            default -> new OrderSpecifier<?>[] {R.reviewedAt.desc(), R.id.desc()};
        };
        return adminFrom(query.select(adminRowExpression())).where(adminWhere(status), keyword(keyword)).orderBy(order).offset(offset).limit(size).fetch();
    }

    public long adminCount(AvatarUploadStatus status, String keyword) {
        Long count = adminFrom(query.select(R.count())).where(adminWhere(status), keyword(keyword)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 한 줄(승인·반려·해제 뒤 응답) */
    public Optional<AdminRow> adminRow(long id) {
        return Optional.ofNullable(adminFrom(query.select(adminRowExpression())).where(R.id.eq(id)).fetchOne());
    }

    private static <T> JPAQuery<T> adminFrom(JPAQuery<T> select) {
        return select.from(R).leftJoin(PROFILE).on(PROFILE.userId.eq(R.userId)).leftJoin(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey))
                .leftJoin(REVIEWER).on(REVIEWER.id.eq(R.reviewedBy));
    }

    private static BooleanExpression adminWhere(AvatarUploadStatus status) {
        if (status == AvatarUploadStatus.APPROVED) {
            return R.status.eq(AvatarUploadStatus.APPROVED).or(R.status.eq(AvatarUploadStatus.REMOVED).and(R.reviewedAt.isNotNull()));
        }
        return R.status.eq(status);
    }

    private static BooleanExpression keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return PROFILE.nickname.lower().like("%" + escaped + "%", '\\');
    }
}
