package com.community.member.repository.master;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 관리자 회원 상세의 개인 설정 건수·쪽지 수신·알림 선택 읽기(읽기 전용). <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>Native SQL 인 이유: 쪽지 상태({@code dm_user_states}) · 차단·메모(V71) · 알림함(V50)은 회원 화면(api)의 표라 관리자 쪽에 엔티티·저장소가 없고,
 * 건수 여섯 개를 왕복 한 번에 읽는다. 모두 회원 ID 로 시작하는 색인({@code ix_user_blocks_user}·{@code ix_blocked_words_user}·{@code ix_user_memos_user}·
 * {@code ix_member_notifications_user}·{@code ix_member_notifications_unread})을 탄다.
 * 차단·메모 수는 회원 화면({@code BlockRepositoryQDSL})과 같게 탈퇴한 상대를 뺀다.
 */
@Repository
public class MemberSettingsQueries {

    private final EntityManager entityManager;

    public MemberSettingsQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
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
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT coalesce((SELECT accept_dm FROM dm_user_states WHERE user_id = :userId), true),
                               (SELECT count(*) FROM user_blocks b JOIN users u ON u.id = b.target_id
                                 WHERE b.user_id = :userId AND u.status <> 'WITHDRAWN' AND u.deleted_at IS NULL),
                               (SELECT count(*) FROM blocked_words WHERE user_id = :userId),
                               (SELECT count(*) FROM user_memos m JOIN users u ON u.id = m.target_id
                                 WHERE m.user_id = :userId AND u.status <> 'WITHDRAWN' AND u.deleted_at IS NULL),
                               (SELECT count(*) FROM member_notifications WHERE user_id = :userId),
                               (SELECT count(*) FROM member_notifications WHERE user_id = :userId AND read_at IS NULL)
                        """)
                .setParameter("userId", userId)
                .getSingleResult();
        return new Counts(!Boolean.FALSE.equals(row[0]), num(row[1]), num(row[2]), num(row[3]), num(row[4]), num(row[5]));
    }

    /** 회원이 고른 알림 받기 — 이벤트 코드 → 선택. 줄이 없는 이벤트는 빠진다(둘 다 켬) */
    public Map<String, Choice> choicesOf(long userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT event_code, in_app, email FROM member_notification_preferences WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getResultList();
        Map<String, Choice> out = new HashMap<>();
        for (Object[] row : rows) {
            out.put((String) row[0], new Choice(Boolean.TRUE.equals(row[1]), Boolean.TRUE.equals(row[2])));
        }
        return out;
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
