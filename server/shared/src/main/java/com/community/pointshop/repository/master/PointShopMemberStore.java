package com.community.pointshop.repository.master;

import com.community.point.entity.QPointBalanceEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 포인트 상점이 보는 회원 쪽 값 — 자격(상태·이메일 인증), 지금 잔액, 고른 아바타({@code profiles.profile_image}·{@code last_still_avatar}). {@code com.community.pointshop.service} 만
 * 부른다. 트랜잭션은 부르는 서비스가 연다(구매·환불·아바타 고르기와 한 트랜잭션). 회원·프로필 엔티티는 다른 업무의 것이라 도메인 메서드를 더하지 않는다 — 프로필의 아바타 칸은 엔티티가
 * 쓰지 않는 칸으로 매핑돼 있고 여기의 QueryDSL UPDATE 만 고친다. 읽기는 한 줄 모양(프로젝션).
 */
@Repository
public class PointShopMemberStore extends RoboyamRepositoryQDSL {

    /** 자격 판정 재료 — {@code status} 는 users.status, {@code gone} 은 탈퇴·삭제 */
    public record MemberState(String status, boolean emailVerified, boolean gone) {

        /** 정지·영구 정지·탈퇴 진행 중(새 구매·아바타 변경을 막는다 — member-withdrawal-contract) */
        public boolean suspended() {
            return "SUSPENDED".equals(status) || "BANNED".equals(status) || "WITHDRAWING".equals(status);
        }
    }

    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QPointBalanceEntity B = QPointBalanceEntity.pointBalanceEntity;

    public PointShopMemberStore(JPAQueryFactory query) {
        super(query);
    }

    public Optional<MemberState> state(long userId) {
        Tuple t = query.select(U.status, U.emailVerifiedAt, U.deletedAt).from(U).where(U.id.eq(userId)).fetchOne();
        if (t == null) {
            return Optional.empty();
        }
        UserStatus status = t.get(U.status);
        return Optional.of(new MemberState(status == null ? null : status.name(), t.get(U.emailVerifiedAt) != null,
                status == UserStatus.WITHDRAWN || t.get(U.deletedAt) != null));
    }

    /** 지금 포인트 합계(무상 + 유상 — 잔액 행이 없으면 0) */
    public long balance(long userId) {
        Long balance = query.select(B.balance).from(B).where(B.userId.eq(userId)).fetchOne();
        return balance == null ? 0 : balance;
    }

    /** 고른 프로필 이미지 값(없거나 프로필이 없으면 null) */
    public String profileImage(long userId) {
        return query.select(PR.profileImage).from(PR).where(PR.userId.eq(userId)).fetchOne();
    }

    /** 여러 회원의 프로필 이미지 값(프로필 행이 없는 회원은 빠진다) */
    public Map<Long, String> profileImages(Collection<Long> userIds) {
        Map<Long, String> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        query.select(PR.userId, PR.profileImage).from(PR).where(PR.userId.in(userIds)).fetch().forEach(t -> out.put(t.get(PR.userId), t.get(PR.profileImage)));
        return out;
    }

    /** 프로필 이미지를 바꾼다 — 프로필 행이 있으면 true */
    public boolean setProfileImage(long userId, String value, Instant now) {
        return query.update(PR).set(PR.profileImage, value).set(PR.updatedAt, now).where(PR.userId.eq(userId)).execute() == 1;
    }

    /** 프로필 아바타 칸 — 고른 값·마지막 정지 아바타(V157) */
    public record AvatarState(String profileImage, String lastStillAvatar) {
    }

    /**
     * 프로필 행을 잠그고 아바타 칸을 읽는다(올리기 신청·심사·하나만 규칙이 프로필 행 → 신청 행 차례로 잠근다). 프로필 행이 없으면 빈 값. 프로필 표만 고르는 한 문장
     * ({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다).
     */
    public Optional<AvatarState> lockAvatar(long userId) {
        Tuple t = query.select(PR.profileImage, PR.lastStillAvatar).from(PR).where(PR.userId.eq(userId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
        return t == null ? Optional.empty() : Optional.of(new AvatarState(t.get(PR.profileImage), t.get(PR.lastStillAvatar)));
    }

    /** 아바타 칸(잠그지 않는다) — 프로필 행이 없으면 빈 값 */
    public Optional<AvatarState> avatarState(long userId) {
        Tuple t = query.select(PR.profileImage, PR.lastStillAvatar).from(PR).where(PR.userId.eq(userId)).fetchOne();
        return t == null ? Optional.empty() : Optional.of(new AvatarState(t.get(PR.profileImage), t.get(PR.lastStillAvatar)));
    }

    /**
     * 프로필 아바타를 바꾼다 — 정지 아바타면 마지막 정지 아바타도 그것으로(commerce-motion-avatar-contract 규칙 3). 프로필 행이 있으면 true
     *
     * @param lastStill 새 마지막 정지 아바타 ID(움직이는 아바타를 골랐으면 null — 앞 값을 그대로 둔다)
     */
    public boolean setAvatar(long userId, String profileImage, String lastStill, Instant now) {
        var update = query.update(PR).set(PR.profileImage, profileImage).set(PR.updatedAt, now);
        if (lastStill != null) {
            update.set(PR.lastStillAvatar, lastStill);
        }
        return update.where(PR.userId.eq(userId)).execute() == 1;
    }

    /** 그 값을 쓰고 있을 때만 비운다(예외 환불한 아바타 → 기본 아바타) — 비웠으면 true */
    public boolean clearProfileImageIf(long userId, String value, Instant now) {
        if (value == null) {
            return false; // profile_image = NULL 은 참이 아니다(JDBC 때와 같다)
        }
        return query.update(PR).setNull(PR.profileImage).set(PR.updatedAt, now).where(PR.userId.eq(userId), PR.profileImage.eq(value)).execute() == 1;
    }
}
