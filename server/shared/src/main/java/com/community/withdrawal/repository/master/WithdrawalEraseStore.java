package com.community.withdrawal.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 완료(익명화) 트랜잭션이 표마다 하는 정리(docs/plans/member-withdrawal/00-analysis.md 4절 「완료 때」) — 회원 행은 지우지 않으므로 {@code ON DELETE CASCADE} 가 작동하지 않아
 * 여기서 직접 지운다. 부르는 곳은 {@code WithdrawalCompletionService} 뿐이다. 남기는 것(글·댓글 본문, 반응, 포인트 원장, 거래 기록, 신고·제재, 상대 쪽지함의 대화)은 건드리지 않는다.
 */
@Repository
public class WithdrawalEraseStore {

    private final JdbcTemplate jdbc;

    public WithdrawalEraseStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 신청 — 탈퇴 진행 중으로(정상·정지 회원만). 바뀐 줄 수 */
    public int markWithdrawing(long userId, Instant now) {
        return jdbc.update("UPDATE users SET status = 'WITHDRAWING', updated_at = ? WHERE id = ? AND deleted_at IS NULL AND status IN ('ACTIVE', 'SUSPENDED', 'BANNED')",
                Timestamp.from(now), userId);
    }

    /** 취소 — 탈퇴 진행 중에서 되돌린다({@code restoreStatus} = 신청 전 상태). 바뀐 줄 수 */
    public int restoreStatus(long userId, String restoreStatus, Instant now) {
        return jdbc.update("UPDATE users SET status = ?, updated_at = ? WHERE id = ? AND status = 'WITHDRAWING'", restoreStatus, Timestamp.from(now), userId);
    }

    /** 완료 — 계정 익명화: 이메일·비밀번호·마지막 로그인 NULL, WITHDRAWN, deleted_at. 약관 동의·만 14세 확인 시각은 남긴다(동의 증빙, 식별 불가) */
    public int anonymizeUser(long userId, Instant now) {
        return jdbc.update("UPDATE users SET email = NULL, password_hash = NULL, last_login_at = NULL, status = 'WITHDRAWN', deleted_at = ?, updated_at = ? "
                + "WHERE id = ? AND deleted_at IS NULL", Timestamp.from(now), Timestamp.from(now), userId);
    }

    /** 프로필 익명화 — 닉네임·소개·프로필 이미지·닉네임 이미지 NULL, 등급은 그대로 */
    public void anonymizeProfile(long userId, Instant now) {
        jdbc.update("UPDATE profiles SET nickname = NULL, bio = NULL, profile_image = NULL, nickname_image = NULL, updated_at = ? WHERE user_id = ?", Timestamp.from(now), userId);
    }

    /** 설정·이력·관계·역할·토큰·주소록(배송지 관리 — 탈퇴하면 즉시 삭제) — 회원 번호가 키인 줄을 지운다 */
    public void deleteOwnRows(long userId) {
        for (String table : List.of("profile_nickname_history", "member_grade_changes", "auth_tokens", "user_identities", "member_display_preferences", "member_view_settings",
                "member_notification_preferences", "dm_user_states", "blocked_words", "user_roles", "board_staff", "member_notifications", "group_buy_watches",
                "group_buy_supply_choices", "user_equipped", "nickname_image_requests", "group_event_responses", "dm_boxes", "member_addresses")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
        }
        // 차단·메모는 양방향(남이 나에 대해 적은 메모도 나에 관한 정보다)
        jdbc.update("DELETE FROM user_blocks WHERE user_id = ? OR target_id = ?", userId, userId);
        jdbc.update("DELETE FROM user_memos WHERE user_id = ? OR target_id = ?", userId, userId);
    }

    /** 대기 중인 토론 고정 요청·소모임 공개 요청을 닫는다 */
    public void cancelPendingRequests(long userId) {
        jdbc.update("DELETE FROM debate_pin_requests WHERE requester_id = ? AND status = 'PENDING'", userId);
        jdbc.update("UPDATE group_post_publish_requests SET status = 'CANCELED', cancel_reason = 'AUTHOR_LEFT' WHERE requester_id = ? AND status = 'PENDING'", userId);
    }

    /** 대기 메일 — 탈퇴 완료 메일은 대기열이 아니라 바로 보내므로 남은 PENDING 은 모두 취소한다 */
    public int cancelPendingMail(long userId, Instant now) {
        return jdbc.update("UPDATE mail_outbox SET status = 'CANCELED', variables = NULL, finished_at = ?, last_error = 'WITHDRAWN' WHERE user_id = ? AND status = 'PENDING'",
                Timestamp.from(now), userId);
    }

    /** 글·댓글의 접속 IP — 작성 {@code days} 일이 지난 것은 지금 지운다(안 지난 것은 배치 R__batch_36 이 90일째에 지운다) */
    public int clearOldAuthorIps(long userId, int days, Instant now) {
        Timestamp cutoff = Timestamp.from(now.minusSeconds(days * 86_400L));
        int posts = jdbc.update("UPDATE posts SET author_ip = NULL WHERE author_id = ? AND author_ip IS NOT NULL AND created_at <= ?", userId, cutoff);
        int comments = jdbc.update("UPDATE comments SET author_ip = NULL WHERE author_id = ? AND author_ip IS NOT NULL AND created_at <= ?", userId, cutoff);
        return posts + comments;
    }

    /** 공구 주문의 배송지·연락처·현금영수증 암호문 — 기간과 무관하게 즉시 마스킹(17-3). 주문·금액·상태는 남는다 */
    public int maskParticipations(long userId, Instant now) {
        return jdbc.update("UPDATE group_buy_participations SET recipient_enc = NULL, phone_enc = NULL, postcode = NULL, address1_enc = NULL, address2_enc = NULL, memo_enc = NULL, "
                + "cash_receipt_no_enc = NULL, address_masked_at = coalesce(address_masked_at, ?), updated_at = ? WHERE user_id = ? AND (recipient_enc IS NOT NULL OR phone_enc IS NOT NULL "
                + "OR address1_enc IS NOT NULL OR address2_enc IS NOT NULL OR memo_enc IS NOT NULL OR cash_receipt_no_enc IS NOT NULL OR postcode IS NOT NULL)",
                Timestamp.from(now), Timestamp.from(now), userId);
    }

    /** 포인트샵 아이템 소멸 — 가진·쓴 아이템을 회수(REVOKED). 구매 기록은 남는다 */
    public int revokeItems(long userId, Instant now) {
        return jdbc.update("UPDATE user_items SET status = 'REVOKED', revoked_at = ? WHERE user_id = ? AND status IN ('OWNED', 'USED')", Timestamp.from(now), userId);
    }

    /**
     * 소모임 멤버십 — 지금 멤버인 소모임마다 나가기와 같은 처리(그 소모임 안 내 글 가림, 멤버 수 −1) 뒤 줄 삭제. 신청 중(APPLIED)·나간 줄도 지운다.
     * 모임장인 소모임은 신청 때 이미 넘기거나 해산했다(남아 있으면 부르는 쪽이 먼저 처리한다).
     *
     * @return 나간 소모임 수
     */
    public int leaveAllGroups(long userId, Instant now) {
        List<Long> active = jdbc.queryForList("SELECT group_id FROM group_members WHERE user_id = ? AND status = 'ACTIVE' ORDER BY group_id", Long.class, userId);
        for (Long groupId : active) {
            jdbc.queryForList("SELECT id FROM groups WHERE id = ? FOR UPDATE", Long.class, groupId);
            jdbc.update("UPDATE posts SET group_left_at = ?, updated_at = ? WHERE group_id = ? AND author_id = ? AND group_left_at IS NULL", Timestamp.from(now), Timestamp.from(now),
                    groupId, userId);
            jdbc.update("UPDATE groups SET member_count = greatest(member_count - 1, 0) WHERE id = ?", groupId);
        }
        jdbc.update("DELETE FROM group_members WHERE user_id = ?", userId);
        return active.size();
    }

    /** 내가 모임장인 살아 있는(PENDING·ACTIVE·INACTIVE) 소모임 ID */
    public List<Long> ledGroupIds(long userId) {
        return jdbc.queryForList("SELECT id FROM groups WHERE leader_id = ? AND status IN ('PENDING', 'ACTIVE', 'INACTIVE') ORDER BY id", Long.class, userId);
    }

    /** 공개 사이트 로그인 세션 — 모두 끝낸다(관리자 앱은 다른 표). 끝낸 수 */
    public int deleteSessions(long userId) {
        return jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", Long.toString(userId));
    }
}
