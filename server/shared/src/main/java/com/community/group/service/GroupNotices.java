package com.community.group.service;

import com.community.group.entity.GroupEntity;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import org.springframework.stereotype.Component;

/**
 * 소모임 회원 알림을 한 줄로 — 받는 사람·덧붙임·메일 버튼 경로(알림함 링크와 같은 곳)를 여기서 정한다(community api-groups.md 7절).
 * 업무 트랜잭션 안에서 업무 변경 뒤 마지막에 부른다(발행은 세이브포인트 — 실패해도 업무를 깨지 않는다, 감싸지 않는다).
 */
@Component
public class GroupNotices {

    private final MemberNotificationPublisher publisher;

    public GroupNotices(MemberNotificationPublisher publisher) {
        this.publisher = publisher;
    }

    /** 개설 승인 → 신청자(모임장) */
    public void approved(GroupEntity g) {
        publisher.group(MemberNotificationType.GROUP_APPROVED, g.getLeaderId(), g.getId(), g.getName(), null, null, groupPath(g));
    }

    /** 개설 반려 → 신청자(사유) */
    public void rejected(GroupEntity g, String reason) {
        publisher.group(MemberNotificationType.GROUP_REJECTED, g.getLeaderId(), g.getId(), g.getName(), reason, null, "/groups/me");
    }

    /** 가입 신청 → 처리할 사람(모임장 · 가입 신청을 맡은 매니저) */
    public void joinRequested(GroupEntity g, long recipientId, long applicantId) {
        publisher.group(MemberNotificationType.GROUP_JOIN_REQUEST, recipientId, g.getId(), g.getName(), null, applicantId,
                "/groups/" + g.getId() + "/manage/applications");
    }

    /** 가입 승인 → 신청자 */
    public void joinApproved(GroupEntity g, long userId, long deciderId) {
        publisher.group(MemberNotificationType.GROUP_JOIN_APPROVED, userId, g.getId(), g.getName(), null, deciderId, groupPath(g));
    }

    /** 가입 거절 → 신청자(처리자가 사유를 적었을 때만 사유) */
    public void joinRejected(GroupEntity g, long userId, long deciderId, String reason) {
        publisher.group(MemberNotificationType.GROUP_JOIN_REJECTED, userId, g.getId(), g.getName(), reason, deciderId, "/groups/me");
    }

    /** 강퇴 → 내보낸 회원 */
    public void kicked(GroupEntity g, long userId, long actorId) {
        publisher.group(MemberNotificationType.GROUP_KICKED, userId, g.getId(), g.getName(), null, actorId, "/groups");
    }

    /** 해산 → 멤버 한 명(모임장 제외 — 부르는 쪽이 멤버마다) */
    public void dissolved(GroupEntity g, long userId, long leaderId) {
        publisher.group(MemberNotificationType.GROUP_DISSOLVED, userId, g.getId(), g.getName(), null, leaderId, "/groups");
    }

    /** 모임장 넘기기 → 새 모임장 */
    public void leaderTransferred(GroupEntity g, long newLeaderId, long previousLeaderId) {
        publisher.group(MemberNotificationType.GROUP_LEADER_TRANSFERRED, newLeaderId, g.getId(), g.getName(), null, previousLeaderId, groupPath(g));
    }

    /** 운영 조치 비활성 → 모임장(사유) */
    public void deactivated(GroupEntity g, String reason) {
        publisher.group(MemberNotificationType.GROUP_DEACTIVATED, g.getLeaderId(), g.getId(), g.getName(), reason, null, groupPath(g));
    }

    /** 다시 열기 → 모임장(운영 메모는 싣지 않는다) */
    public void reactivated(GroupEntity g) {
        publisher.group(MemberNotificationType.GROUP_REACTIVATED, g.getLeaderId(), g.getId(), g.getName(), null, null, groupPath(g));
    }

    private static String groupPath(GroupEntity g) {
        return "/groups/" + g.getId();
    }
}
