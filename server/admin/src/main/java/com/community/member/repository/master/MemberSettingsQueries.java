package com.community.member.repository.master;

import com.community.block.entity.QBlockedWordEntity;
import com.community.block.entity.QUserBlockEntity;
import com.community.block.entity.QUserMemoEntity;
import com.community.dm.entity.QDmUserStateEntity;
import com.community.membernotification.entity.QMemberNotificationEntity;
import com.community.membernotification.entity.QMemberNotificationPreferenceEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 관리자 회원 상세의 개인 설정 건수·쪽지 수신·알림 선택 읽기(읽기 전용). <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>차단·금칙어·메모·알림함 건수는 shared 엔티티({@code user_blocks}·{@code blocked_words}·{@code user_memos}·{@code member_notifications})로 센다 — 모두 회원 ID 로
 * 시작하는 색인({@code ix_user_blocks_user}·{@code ix_blocked_words_user}·{@code ix_user_memos_user}·{@code ix_member_notifications_user}·
 * {@code ix_member_notifications_unread})을 탄다. 차단·메모 수는 회원 화면({@code BlockRepositoryQDSL})과 같게 탈퇴한 상대를 뺀다.
 *
 * <p>쪽지 상태({@code dm_user_states})와 알림 선택({@code member_notification_preferences})도 shared 엔티티(QueryDSL)로 읽는다 — 둘 다 회원 ID 가 키(의 앞)다.
 */
@Repository
public class MemberSettingsQueries extends IrobitRepositoryQDSL {

    private static final QUserBlockEntity BLOCK = QUserBlockEntity.userBlockEntity;
    private static final QBlockedWordEntity WORD = QBlockedWordEntity.blockedWordEntity;
    private static final QUserMemoEntity MEMO = QUserMemoEntity.userMemoEntity;
    private static final QMemberNotificationEntity NOTIFICATION = QMemberNotificationEntity.memberNotificationEntity;
    private static final QUserEntity TARGET = new QUserEntity("target");
    private static final QDmUserStateEntity DM_STATE = QDmUserStateEntity.dmUserStateEntity;
    private static final QMemberNotificationPreferenceEntity PREFERENCE = QMemberNotificationPreferenceEntity.memberNotificationPreferenceEntity;

    public MemberSettingsQueries(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 회원의 건수·쪽지 수신
     *
     * @param acceptDm 쪽지 받기 — 상태 행이 없으면 true(V70 의 뜻)
     */
    public record Counts(boolean acceptDm, long blockedUsers, long blockedWords, long memos, long notifications, long unreadNotifications) {
    }

    /** 회원 알림 선택(V67) — 줄이 있는 이벤트만 */
    public record Choice(boolean inApp, boolean email) {
    }

    public Counts countsOf(long userId) {
        long blockedUsers = nz(query.select(BLOCK.count()).from(BLOCK).join(TARGET).on(TARGET.id.eq(BLOCK.targetId))
                .where(BLOCK.userId.eq(userId), TARGET.status.ne(UserStatus.WITHDRAWN), TARGET.deletedAt.isNull()).fetchOne());
        long blockedWords = nz(query.select(WORD.count()).from(WORD).where(WORD.userId.eq(userId)).fetchOne());
        long memos = nz(query.select(MEMO.count()).from(MEMO).join(TARGET).on(TARGET.id.eq(MEMO.targetId))
                .where(MEMO.userId.eq(userId), TARGET.status.ne(UserStatus.WITHDRAWN), TARGET.deletedAt.isNull()).fetchOne());
        long notifications = nz(query.select(NOTIFICATION.count()).from(NOTIFICATION).where(NOTIFICATION.userId.eq(userId)).fetchOne());
        long unread = nz(query.select(NOTIFICATION.count()).from(NOTIFICATION).where(NOTIFICATION.userId.eq(userId), NOTIFICATION.readAt.isNull()).fetchOne());
        return new Counts(acceptDm(userId), blockedUsers, blockedWords, memos, notifications, unread);
    }

    /** 쪽지 받기 — 상태 행이 없으면 true */
    private boolean acceptDm(long userId) {
        Boolean accept = query.select(DM_STATE.acceptDm).from(DM_STATE).where(DM_STATE.userId.eq(userId)).fetchFirst();
        return !Boolean.FALSE.equals(accept);
    }

    /** 회원이 고른 알림 받기 — 이벤트 코드 → 선택. 줄이 없는 이벤트는 빠진다(둘 다 켬) */
    public Map<String, Choice> choicesOf(long userId) {
        Map<String, Choice> out = new HashMap<>();
        for (Tuple row : query.select(PREFERENCE.eventCode, PREFERENCE.inApp, PREFERENCE.email).from(PREFERENCE).where(PREFERENCE.userId.eq(userId)).fetch()) {
            out.put(row.get(PREFERENCE.eventCode), new Choice(Boolean.TRUE.equals(row.get(PREFERENCE.inApp)), Boolean.TRUE.equals(row.get(PREFERENCE.email))));
        }
        return out;
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
