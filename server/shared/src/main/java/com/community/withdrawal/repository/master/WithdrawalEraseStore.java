package com.community.withdrawal.repository.master;

import com.community.auth.entity.QAuthTokenEntity;
import com.community.block.entity.QBlockedWordEntity;
import com.community.block.entity.QUserBlockEntity;
import com.community.block.entity.QUserMemoEntity;
import com.community.board.entity.QBoardStaffEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.debate.entity.QDebatePinRequestEntity;
import com.community.dm.entity.QDmBoxEntity;
import com.community.dm.entity.QDmUserStateEntity;
import com.community.grade.entity.QMemberGradeChangeEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupPublishRequestStatus;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.QGroupEntity;
import com.community.group.entity.QGroupEventResponseEntity;
import com.community.group.entity.QGroupMemberEntity;
import com.community.group.entity.QGroupPublishRequestEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuySupplyChoiceEntity;
import com.community.groupbuy.entity.QGroupBuyWatchEntity;
import com.community.membernotification.entity.QMemberNotificationEntity;
import com.community.membernotification.entity.QMemberNotificationPreferenceEntity;
import com.community.nicknameimage.entity.QNicknameImageRequestEntity;
import com.community.pointshop.entity.QUserEquippedEntity;
import com.community.post.entity.QPostEntity;
import com.community.role.entity.QUserRoleEntity;
import com.community.user.entity.QMemberDisplayPreferenceEntity;
import com.community.user.entity.QMemberViewSettingEntity;
import com.community.user.entity.QProfileNicknameHistoryEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.QUserIdentityEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 완료(익명화) 트랜잭션이 표마다 하는 정리(docs/plans/member-withdrawal/00-analysis.md 4절 「완료 때」) — 회원 행은 지우지 않으므로 {@code ON DELETE CASCADE} 가 작동하지 않아
 * 여기서 직접 지운다. 부르는 곳은 {@code WithdrawalCompletionService}·{@code WithdrawalRequestWriter} 뿐이다. 남기는 것(글·댓글 본문, 반응, 포인트 원장, 거래 기록, 신고·제재,
 * 상대 쪽지함의 대화)은 건드리지 않는다. <b>문장 차례가 정리 차례다</b> — 바꾸지 않는다.
 *
 * <p>엔티티가 있는 표는 QueryDSL 대량 UPDATE·DELETE 다(부르는 트랜잭션 안 — 아직 내보내지 않은 엔티티 변경은 문장 전에 내보내진다). Native SQL 로 남긴 것과 이유:
 * 엔티티가 없는 표({@code avatar_upload_requests}·{@code member_addresses}·{@code mail_outbox}·{@code spring_session}),
 * 엔티티가 매핑하지 않는 칸을 고치는 문장({@code profiles.profile_image}·{@code nickname_image}, {@code user_items.reserved_at}).
 */
@Repository
public class WithdrawalEraseStore extends RoboyamRepositoryQDSL {

    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;
    private static final QGroupMemberEntity MEMBER = QGroupMemberEntity.groupMemberEntity;
    private static final QDebatePinRequestEntity PIN_REQUEST = QDebatePinRequestEntity.debatePinRequestEntity;
    private static final QGroupPublishRequestEntity PUBLISH_REQUEST = QGroupPublishRequestEntity.groupPublishRequestEntity;
    private static final QUserBlockEntity BLOCK = QUserBlockEntity.userBlockEntity;
    private static final QUserMemoEntity MEMO = QUserMemoEntity.userMemoEntity;
    private static final QGroupBuyParticipationEntity ORDER = QGroupBuyParticipationEntity.groupBuyParticipationEntity;

    private final JdbcTemplate jdbc;

    public WithdrawalEraseStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 신청 — 탈퇴 진행 중으로(정상·정지 회원만). 바뀐 줄 수 */
    public int markWithdrawing(long userId, Instant now) {
        return rows(query.update(USER).set(USER.status, UserStatus.WITHDRAWING).set(USER.updatedAt, now)
                .where(USER.id.eq(userId), USER.deletedAt.isNull(), USER.status.in(UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.BANNED)).execute());
    }

    /** 취소 — 탈퇴 진행 중에서 되돌린다({@code restoreStatus} = 신청 전 상태). 바뀐 줄 수 */
    public int restoreStatus(long userId, String restoreStatus, Instant now) {
        return rows(query.update(USER).set(USER.status, UserStatus.valueOf(restoreStatus)).set(USER.updatedAt, now)
                .where(USER.id.eq(userId), USER.status.eq(UserStatus.WITHDRAWING)).execute());
    }

    /** 완료 — 계정 익명화: 이메일·비밀번호·마지막 로그인 NULL, WITHDRAWN, deleted_at. 약관 동의·만 14세 확인 시각은 남긴다(동의 증빙, 식별 불가) */
    public int anonymizeUser(long userId, Instant now) {
        return rows(query.update(USER).setNull(USER.email).setNull(USER.passwordHash).setNull(USER.lastLoginAt).set(USER.status, UserStatus.WITHDRAWN)
                .set(USER.deletedAt, now).set(USER.updatedAt, now)
                .where(USER.id.eq(userId), USER.deletedAt.isNull()).execute());
    }

    /**
     * 프로필 익명화 — 닉네임·소개·프로필 이미지·닉네임 이미지 NULL, 등급은 그대로.
     *
     * <p>Native SQL 인 이유: {@code profile_image}·{@code nickname_image} 는 프로필 엔티티가 매핑하지 않는 칸이다(다른 저장소가 고친다).
     */
    public void anonymizeProfile(long userId, Instant now) {
        jdbc.update("UPDATE profiles SET nickname = NULL, bio = NULL, profile_image = NULL, nickname_image = NULL, updated_at = ? WHERE user_id = ?", Timestamp.from(now), userId);
    }

    /** 설정·이력·관계·역할·토큰·주소록(배송지 관리 — 탈퇴하면 즉시 삭제) — 회원 번호가 키인 줄을 지운다(표 차례 그대로 — 엔티티가 없는 표는 SQL) */
    public void deleteOwnRows(long userId) {
        deleteByUser(QProfileNicknameHistoryEntity.profileNicknameHistoryEntity, QProfileNicknameHistoryEntity.profileNicknameHistoryEntity.userId, userId);
        deleteByUser(QMemberGradeChangeEntity.memberGradeChangeEntity, QMemberGradeChangeEntity.memberGradeChangeEntity.userId, userId);
        deleteByUser(QAuthTokenEntity.authTokenEntity, QAuthTokenEntity.authTokenEntity.userId, userId);
        deleteByUser(QUserIdentityEntity.userIdentityEntity, QUserIdentityEntity.userIdentityEntity.userId, userId);
        deleteByUser(QMemberDisplayPreferenceEntity.memberDisplayPreferenceEntity, QMemberDisplayPreferenceEntity.memberDisplayPreferenceEntity.userId, userId);
        deleteByUser(QMemberViewSettingEntity.memberViewSettingEntity, QMemberViewSettingEntity.memberViewSettingEntity.userId, userId);
        deleteByUser(QMemberNotificationPreferenceEntity.memberNotificationPreferenceEntity, QMemberNotificationPreferenceEntity.memberNotificationPreferenceEntity.userId, userId);
        deleteByUser(QDmUserStateEntity.dmUserStateEntity, QDmUserStateEntity.dmUserStateEntity.userId, userId);
        deleteByUser(QBlockedWordEntity.blockedWordEntity, QBlockedWordEntity.blockedWordEntity.userId, userId);
        deleteByUser(QUserRoleEntity.userRoleEntity, QUserRoleEntity.userRoleEntity.userId, userId);
        deleteByUser(QBoardStaffEntity.boardStaffEntity, QBoardStaffEntity.boardStaffEntity.userId, userId);
        deleteByUser(QMemberNotificationEntity.memberNotificationEntity, QMemberNotificationEntity.memberNotificationEntity.userId, userId);
        deleteByUser(QGroupBuyWatchEntity.groupBuyWatchEntity, QGroupBuyWatchEntity.groupBuyWatchEntity.userId, userId);
        deleteByUser(QGroupBuySupplyChoiceEntity.groupBuySupplyChoiceEntity, QGroupBuySupplyChoiceEntity.groupBuySupplyChoiceEntity.userId, userId);
        deleteByUser(QUserEquippedEntity.userEquippedEntity, QUserEquippedEntity.userEquippedEntity.userId, userId);
        deleteByUser(QNicknameImageRequestEntity.nicknameImageRequestEntity, QNicknameImageRequestEntity.nicknameImageRequestEntity.userId, userId);
        deleteByUserNative("avatar_upload_requests", userId);
        deleteByUser(QGroupEventResponseEntity.groupEventResponseEntity, QGroupEventResponseEntity.groupEventResponseEntity.userId, userId);
        deleteByUser(QDmBoxEntity.dmBoxEntity, QDmBoxEntity.dmBoxEntity.userId, userId);
        deleteByUserNative("member_addresses", userId);
        // 차단·메모는 양방향(남이 나에 대해 적은 메모도 나에 관한 정보다)
        query.delete(BLOCK).where(BLOCK.userId.eq(userId).or(BLOCK.targetId.eq(userId))).execute();
        query.delete(MEMO).where(MEMO.userId.eq(userId).or(MEMO.targetId.eq(userId))).execute();
    }

    private void deleteByUser(EntityPathBase<?> table, NumberPath<Long> userIdPath, long userId) {
        query.delete(table).where(userIdPath.eq(userId)).execute();
    }

    /** Native SQL 인 이유: 엔티티가 없는 표다 */
    private void deleteByUserNative(String table, long userId) {
        jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
    }

    /** 대기 중인 토론 고정 요청·소모임 공개 요청을 닫는다 */
    public void cancelPendingRequests(long userId) {
        query.delete(PIN_REQUEST).where(PIN_REQUEST.requesterId.eq(userId), PIN_REQUEST.status.eq("PENDING")).execute();
        query.update(PUBLISH_REQUEST).set(PUBLISH_REQUEST.status, GroupPublishRequestStatus.CANCELED).set(PUBLISH_REQUEST.cancelReason, "AUTHOR_LEFT")
                .where(PUBLISH_REQUEST.requesterId.eq(userId), PUBLISH_REQUEST.status.eq(GroupPublishRequestStatus.PENDING)).execute();
    }

    /**
     * 대기 메일 — 탈퇴 완료 메일은 대기열이 아니라 바로 보내므로 남은 PENDING 은 모두 취소한다.
     *
     * <p>Native SQL 인 이유: {@code mail_outbox} 는 엔티티가 없는 표다.
     */
    public int cancelPendingMail(long userId, Instant now) {
        return jdbc.update("UPDATE mail_outbox SET status = 'CANCELED', variables = NULL, finished_at = ?, last_error = 'WITHDRAWN' WHERE user_id = ? AND status = 'PENDING'",
                Timestamp.from(now), userId);
    }

    /** 글·댓글의 접속 IP — 작성 {@code days} 일이 지난 것은 지금 지운다(안 지난 것은 배치 R__batch_36 이 90일째에 지운다) */
    public int clearOldAuthorIps(long userId, int days, Instant now) {
        Instant cutoff = now.minusSeconds(days * 86_400L);
        long posts = query.update(POST).setNull(POST.authorIp)
                .where(POST.authorId.eq(userId), POST.authorIp.isNotNull(), POST.createdAt.loe(cutoff)).execute();
        long comments = query.update(COMMENT).setNull(COMMENT.authorIp)
                .where(COMMENT.authorId.eq(userId), COMMENT.authorIp.isNotNull(), COMMENT.createdAt.loe(cutoff)).execute();
        return rows(posts + comments);
    }

    /**
     * 공구 주문의 배송지·연락처·현금영수증 암호문 — 기간과 무관하게 즉시 마스킹(17-3). 주문·금액·상태는 남는다.
     *
     * 지운 시각({@code address_masked_at})은 이미 있으면 그대로 둔다(예전 {@code coalesce}).
     */
    public int maskParticipations(long userId, Instant now) {
        return rows(query.update(ORDER).setNull(ORDER.recipientEnc).setNull(ORDER.phoneEnc).setNull(ORDER.postcode).setNull(ORDER.address1Enc).setNull(ORDER.address2Enc)
                .setNull(ORDER.memoEnc).setNull(ORDER.cashReceiptNoEnc).set(ORDER.addressMaskedAt, ORDER.addressMaskedAt.coalesce(now)).set(ORDER.updatedAt, now)
                .where(ORDER.userId.eq(userId), ORDER.recipientEnc.isNotNull().or(ORDER.phoneEnc.isNotNull()).or(ORDER.address1Enc.isNotNull())
                        .or(ORDER.address2Enc.isNotNull()).or(ORDER.memoEnc.isNotNull()).or(ORDER.cashReceiptNoEnc.isNotNull()).or(ORDER.postcode.isNotNull()))
                .execute());
    }

    /**
     * 포인트샵 아이템 소멸 — 가진·쓴 아이템을 회수(REVOKED). 구매 기록은 남는다. 대기 신청에 예약된 올리기 변경권은 예약을 푼다(V157 — 예약은 가진 장에만).
     *
     * <p>Native SQL 인 이유: {@code reserved_at} 은 아이템 엔티티가 매핑하지 않는 칸이다(올리기 신청 저장소가 고친다).
     */
    public int revokeItems(long userId, Instant now) {
        return jdbc.update("UPDATE user_items SET status = 'REVOKED', revoked_at = ?, reserved_at = NULL WHERE user_id = ? AND status IN ('OWNED', 'USED')", Timestamp.from(now),
                userId);
    }

    /**
     * 소모임 멤버십 — 지금 멤버인 소모임마다 나가기와 같은 처리(그 소모임 안 내 글 가림, 멤버 수 −1) 뒤 줄 삭제. 신청 중(APPLIED)·나간 줄도 지운다.
     * 모임장인 소모임은 신청 때 이미 넘기거나 해산했다(남아 있으면 부르는 쪽이 먼저 처리한다). 소모임 행은 소모임 id 차례로
     * {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE})로 잠가 다른 소모임 쓰기와 줄 선다 — 여기서는 멤버 수를 조건 없이 줄이고 내 멤버십 줄만 지우므로 소모임을 FK 로
     * 가리키며 넣는 다른 흐름(가입·일정)을 기다리지 않아도 결과가 같다.
     *
     * @return 나간 소모임 수
     */
    public int leaveAllGroups(long userId, Instant now) {
        List<Long> active = query.select(MEMBER.groupId).from(MEMBER).where(MEMBER.userId.eq(userId), MEMBER.status.eq(GroupMemberStatus.ACTIVE))
                .orderBy(MEMBER.groupId.asc()).fetch();
        for (Long groupId : active) {
            query.select(GROUP.id).from(GROUP).where(GROUP.id.eq(groupId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst();
            query.update(POST).set(POST.groupLeftAt, now).set(POST.updatedAt, now)
                    .where(POST.groupId.eq(groupId), POST.authorId.eq(userId), POST.groupLeftAt.isNull()).execute();
            query.update(GROUP).set(GROUP.memberCount, Expressions.numberTemplate(Integer.class, "greatest({0} - 1, 0)", GROUP.memberCount))
                    .where(GROUP.id.eq(groupId)).execute();
        }
        query.delete(MEMBER).where(MEMBER.userId.eq(userId)).execute();
        return active.size();
    }

    /** 내가 모임장인 살아 있는(PENDING·ACTIVE·INACTIVE) 소모임 ID */
    public List<Long> ledGroupIds(long userId) {
        return query.select(GROUP.id).from(GROUP).where(GROUP.leaderId.eq(userId), GROUP.status.in(GroupStatus.PENDING, GroupStatus.ACTIVE, GroupStatus.INACTIVE))
                .orderBy(GROUP.id.asc()).fetch();
    }

    /**
     * 공개 사이트 로그인 세션 — 모두 끝낸다(관리자 앱은 다른 표). 끝낸 수.
     *
     * <p>Native SQL 인 이유: {@code spring_session} 은 Spring Session JDBC 가 관리하는 표다(엔티티 없음).
     */
    public int deleteSessions(long userId) {
        return jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", Long.toString(userId));
    }

    private static int rows(long changed) {
        return Math.toIntExact(changed);
    }
}
