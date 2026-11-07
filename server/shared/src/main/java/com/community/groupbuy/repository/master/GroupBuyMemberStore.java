package com.community.groupbuy.repository.master;

import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 공구가 보는 회원 쪽 값 — 참여 자격(상태·이메일 인증)과 회원별 어드바이저리 잠금. 회원 엔티티는 다른 업무의 것이라 고치지 않고 한 줄 모양으로 읽기만 한다
 * (포인트 상점 {@code PointShopMemberStore} 와 같은 모양).
 *
 * <p>Native SQL 로 남긴 것과 이유: (공구, 회원) 어드바이저리 잠금 {@code pg_advisory_xact_lock}(결정 37 잠금 순서의 맨 앞).
 */
@Repository
public class GroupBuyMemberStore extends GroupBuyStoreSupport {

    private static final QUserEntity U = QUserEntity.userEntity;

    /** 자격 판정 재료 — {@code status} 는 users.status, {@code gone} 은 탈퇴·삭제 */
    public record MemberState(String status, boolean emailVerified, boolean gone) {

        /** 정지·영구 정지·탈퇴 진행 중(새 참여·문의·리뷰를 막는다 — member-withdrawal-contract) */
        public boolean suspended() {
            return "SUSPENDED".equals(status) || "BANNED".equals(status) || "WITHDRAWING".equals(status);
        }
    }

    public GroupBuyMemberStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public Optional<MemberState> state(long userId) {
        Tuple t = query.select(U.status, U.emailVerifiedAt, U.deletedAt).from(U).where(U.id.eq(userId)).fetchFirst();
        if (t == null) {
            return Optional.empty();
        }
        UserStatus status = t.get(U.status);
        return Optional.of(new MemberState(status == null ? null : status.name(), t.get(U.emailVerifiedAt) != null,
                status == UserStatus.WITHDRAWN || t.get(U.deletedAt) != null));
    }

    /**
     * (공구, 회원) 어드바이저리 잠금 — 트랜잭션 범위, <b>모든 행 잠금보다 먼저</b>(결정 37: A → G → O → X). 1인 한도·중복 주문 검사를 줄 세운다(10-4).
     * 키 = {@code hashtext('gb:<공구>:<회원>')}. Native SQL 인 이유: DB 함수 {@code pg_advisory_xact_lock}.
     */
    public void lockMemberInGroupBuy(long groupBuyId, long userId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "gb:" + groupBuyId + ":" + userId);
    }
}
