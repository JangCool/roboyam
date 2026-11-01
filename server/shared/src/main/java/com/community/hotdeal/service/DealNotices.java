package com.community.hotdeal.service;

import com.community.hotdeal.entity.PostDealEntity;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원 핫딜 알림 발행 지점(서버 단계 고정 규칙 9, 메모 1-7·1-8) — 업무 트랜잭션 안에서 마지막에 한 줄로 부른다(발행기들이 세이브포인트로 격리 — 실패해도 업무를 깨지 않는다).
 * 회원 알림은 그 딜의 작성자에게(탈퇴·비회원이면 발행기가 거른다), 관리자 알림은 {@code hotdeal.manage} 보유자에게. 3단계(관리자 API)의 운영진 종료도 {@link #managerEnded} 를 쓴다.
 */
@Component
public class DealNotices {

    private final MemberNotificationPublisher members;
    private final NotificationPublisher operators;
    private final JdbcTemplate jdbc;

    public DealNotices(MemberNotificationPublisher members, NotificationPublisher operators, DataSource dataSource) {
        this.members = members;
        this.operators = operators;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 종료 요청이 문턱에 닿아 의심이 됐다 */
    public void flagged(PostDealEntity d) {
        toAuthor(MemberNotificationType.DEAL_FLAGGED, d, null);
    }

    /** 의심 유예가 지나 자동 종료됐다 */
    public void autoEnded(PostDealEntity d) {
        toAuthor(MemberNotificationType.DEAL_AUTO_ENDED, d, null);
    }

    /** 운영진이 종료했다 — 사유(작성자에게 가는 글, 없을 수 있다) */
    public void managerEnded(PostDealEntity d, String reason) {
        toAuthor(MemberNotificationType.DEAL_MANAGER_ENDED, d, reason);
    }

    /** 마감 없는 딜이 기간 종료됐다(한 번 — 부르는 쪽이 표시) */
    public void expired(PostDealEntity d) {
        toAuthor(MemberNotificationType.DEAL_EXPIRED, d, null);
    }

    /** 다시 진행 중이 됐는데 기간이 이미 지났다 — 「마감을 고쳐 주세요」 */
    public void reopenedFixEnds(PostDealEntity d) {
        toAuthor(MemberNotificationType.DEAL_REOPENED_FIX_ENDS, d, null);
    }

    /** 「아직 진행 중」 한도를 다 쓴 딜이 다시 의심이 됐다(관리자 알림) */
    public void keepLimit(PostDealEntity d) {
        String postId = Long.toString(d.getPostId());
        operators.publish(AdminNotificationEvent.HOTDEAL_KEEP_LIMIT, NotificationContext.empty().dedupe(postId).link("postId", postId));
    }

    /** 위장 홍보 의심 후보가 새로 열렸다(관리자 알림) */
    public void suspected(long suspicionId) {
        String id = Long.toString(suspicionId);
        operators.publish(AdminNotificationEvent.HOTDEAL_SUSPECTED_PROMO, NotificationContext.empty().dedupe(id).link("id", id));
    }

    private void toAuthor(MemberNotificationType type, PostDealEntity d, String note) {
        if (d.getAuthorId() == null) {
            return;
        }
        members.deal(type, d.getAuthorId(), d.getPostId(), titleOf(d.getPostId()), note);
    }

    /** 알릴 때의 글 제목(글 표는 파티션 표라 연관이 없다 — 없으면 null) */
    private String titleOf(long postId) {
        return jdbc.queryForList("SELECT title FROM posts WHERE id = ?", String.class, postId).stream().findFirst().orElse(null);
    }
}
