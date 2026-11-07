package com.community.point.repository.master;

import com.community.point.entity.QPointAttendanceEntity;
import com.community.point.entity.QPointBalanceEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 출석 줄({@code point_attendances}, V89) 넣기·읽기와 출석 자격 판정에 필요한 회원 상태 — {@code AttendanceService} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 * 읽기는 QueryDSL 프로젝션이다.
 *
 * <p>넣기가 Native SQL 인 이유: 한 문장 {@code insert … on conflict (user_id, day) do nothing} 이어야 같은 날 두 번(동시에도) 눌러도 한 줄만 생기고 한 번만 성공한다
 * (「찾아보고 없으면 넣는다」로 나누면 동시 출석이 유일 위반으로 트랜잭션을 깬다). 출석 포인트 지급({@code PointService#attendance})은 이 뒤 세이브포인트에서 따로 한다 — 이 저장소는
 * 세이브포인트 안에서 불리지 않는다.
 */
@Repository
public class PointAttendanceStore extends RoboyamRepositoryQDSL {

    /** 출석 자격에 필요한 회원 상태 */
    public record MemberState(String status, boolean emailVerified, boolean gone) {

        /** 정지·영구 정지·탈퇴 진행 중(새 출석을 막는다 — member-withdrawal-contract) */
        public boolean suspended() {
            return "SUSPENDED".equals(status) || "BANNED".equals(status) || "WITHDRAWING".equals(status);
        }
    }

    private static final QPointAttendanceEntity A = QPointAttendanceEntity.pointAttendanceEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QPointBalanceEntity B = QPointBalanceEntity.pointBalanceEntity;

    private final EntityManager entityManager;

    public PointAttendanceStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public Optional<MemberState> member(long userId) {
        Tuple t = query.select(U.status, U.emailVerifiedAt, U.deletedAt).from(U).where(U.id.eq(userId)).fetchOne();
        if (t == null) {
            return Optional.empty();
        }
        UserStatus status = t.get(U.status);
        return Optional.of(new MemberState(status == null ? null : status.name(), t.get(U.emailVerifiedAt) != null,
                status == UserStatus.WITHDRAWN || t.get(U.deletedAt) != null));
    }

    /** 그날 출석 줄을 넣는다 — 이미 있으면(동시에 들어온 것 포함) false */
    public boolean insert(long id, long userId, LocalDate day, int streak, Instant now) {
        return entityManager.createNativeQuery("insert into point_attendances (id, user_id, day, streak, created_at) values (?1, ?2, ?3, ?4, ?5) "
                        + "on conflict (user_id, day) do nothing")
                .setParameter(1, id).setParameter(2, userId).setParameter(3, day).setParameter(4, streak)
                .setParameter(5, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).executeUpdate() > 0;
    }

    /** 그날의 연속 일수(그날 출석하지 않았으면 빈 값) */
    public Optional<Integer> streakOn(long userId, LocalDate day) {
        return Optional.ofNullable(query.select(A.streak).from(A).where(A.userId.eq(userId), A.attendanceDay.eq(day)).fetchOne());
    }

    /** {@code from} 이상 {@code toExclusive} 미만의 출석한 날(오름차순) */
    public List<LocalDate> days(long userId, LocalDate from, LocalDate toExclusive) {
        return query.select(A.attendanceDay).from(A).where(A.userId.eq(userId), A.attendanceDay.goe(from), A.attendanceDay.lt(toExclusive))
                .orderBy(A.attendanceDay.asc()).fetch();
    }

    /** 지금 포인트 합계(잔액 행이 없으면 0) */
    public long balance(long userId) {
        Long balance = query.select(B.balance).from(B).where(B.userId.eq(userId)).fetchOne();
        return balance == null ? 0 : balance;
    }
}
