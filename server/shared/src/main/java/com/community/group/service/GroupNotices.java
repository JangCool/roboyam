package com.community.group.service;

import com.community.group.entity.GroupEntity;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import java.util.List;
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

    // ── 매니저(3단계 — api-groups.md 7절 「매니저 알림 5종」) ──────────────────

    /** 매니저 임명 → 임명된 사람(맡은 일) */
    public void managerAppointed(GroupEntity g, long userId, long leaderId, List<String> permissions) {
        publisher.groupManager(MemberNotificationType.GROUP_MANAGER_APPOINTED, userId, g.getId(), g.getName(), leaderId, permissions, null, null, managePath(g));
    }

    /** 맡은 일 바뀜 → 그 매니저(바뀐 뒤 지금 맡은 일 전체) */
    public void managerPermissionsChanged(GroupEntity g, long userId, long leaderId, List<String> permissions) {
        publisher.groupManager(MemberNotificationType.GROUP_MANAGER_PERMISSIONS_CHANGED, userId, g.getId(), g.getName(), leaderId, permissions, null, null,
                managePath(g));
    }

    /** 해임 → 해임된 사람 */
    public void managerRemoved(GroupEntity g, long userId, long leaderId) {
        publisher.groupManager(MemberNotificationType.GROUP_MANAGER_REMOVED, userId, g.getId(), g.getName(), leaderId, null, null, null, groupPath(g));
    }

    /** 사퇴 → 모임장(일으킨 사람 = 물러난 매니저) */
    public void managerResigned(GroupEntity g, long managerId) {
        publisher.groupManager(MemberNotificationType.GROUP_MANAGER_RESIGNED, g.getLeaderId(), g.getId(), g.getName(), managerId, null, null, null, membersPath(g));
    }

    /** 매니저가 강퇴 → 모임장(일으킨 사람 = 강퇴한 매니저, 내보낸 멤버) */
    public void kickedByManager(GroupEntity g, long managerId, long targetId, String targetName) {
        publisher.groupManager(MemberNotificationType.GROUP_KICKED_BY_MANAGER, g.getLeaderId(), g.getId(), g.getName(), managerId, null, targetId, targetName,
                membersPath(g));
    }

    // ── 일정(4단계 — api-groups.md 9절 「알림 GROUP_EVENT」) ──────────────────

    /**
     * 일정 등록·수정·취소 → 멤버 한 명(일을 한 사람 제외 — 부르는 쪽이 멤버마다). 회원이 끌 수 있는 알림이다.
     *
     * @param action CREATED · UPDATED · CANCELED
     * @param reason 취소 사유(적었을 때만, 그 밖 null)
     */
    public void event(GroupEntity g, long userId, long actorId, long eventId, String eventTitle, String action, String reason) {
        publisher.groupEvent(userId, g.getId(), g.getName(), eventId, eventTitle, action, reason, actorId);
    }

    // ── 공개 범위·공개 요청(5단계 — api-groups.md 11-8 「알림 7종」) ──────────────

    /** 공개 요청 → 처리할 사람 한 명(모임장 · 「글 공개 처리·되돌리기」 매니저 — 부르는 쪽이 사람마다, 요청한 사람 본인 제외) */
    public void publishRequested(GroupEntity g, long recipientId, long postId, String title, long requesterId) {
        publisher.groupPublish(MemberNotificationType.GROUP_PUBLISH_REQUESTED, recipientId, g.getId(), g.getName(), postId, title, requesterId, null, null,
                "/groups/" + g.getId() + "/manage/publish-requests");
    }

    /**
     * 글 공개 처리 결과 → 글쓴이(처리자가 글쓴이 본인이면 안 간다).
     *
     * @param type      GROUP_PUBLISH_APPROVED · GROUP_PUBLISH_REJECTED · GROUP_POST_PUBLISHED · GROUP_POST_MEMBERS_ONLY · GROUP_POST_RELEASED · GROUP_POST_UNPUBLISHED
     * @param actorRole 처리자 역할 LEADER · MANAGER
     * @param reason    거절 사유(거절만, 적었을 때만)
     */
    public void postDecision(MemberNotificationType type, GroupEntity g, long authorId, long postId, String title, long actorId, String actorRole, String reason) {
        if (type == MemberNotificationType.GROUP_POST_UNPUBLISHED) {
            publisher.groupPostUnpublished(authorId, g.getId(), g.getName(), postId, title, actorId, actorRole);
            return;
        }
        publisher.groupPublish(type, authorId, g.getId(), g.getName(), postId, title, actorId, actorRole, reason, null);
    }

    /** 공개 범위 변경 → 멤버 한 명(바꾼 모임장 제외 — 부르는 쪽이 멤버마다) */
    public void visibilityChanged(GroupEntity g, long userId, long leaderId) {
        publisher.groupVisibilityChanged(userId, g.getId(), g.getName(), leaderId, g.getVisibility().name());
    }

    /** 공개 범위 기능을 켤 때의 안내 → 「글 공개 처리·되돌리기」를 맡은 지금 매니저(맡은 일 전체를 싣는다, 일으킨 사람 = 그 소모임 모임장) */
    public void rolloutManagerNotice(long managerId, long groupId, String groupName, long leaderId, List<String> permissions) {
        publisher.groupManager(MemberNotificationType.GROUP_MANAGER_PERMISSIONS_CHANGED, managerId, groupId, groupName, leaderId, permissions, null, null,
                "/groups/" + groupId + "/manage");
    }

    private static String managePath(GroupEntity g) {
        return "/groups/" + g.getId() + "/manage";
    }

    private static String membersPath(GroupEntity g) {
        return "/groups/" + g.getId() + "/manage/members";
    }

    private static String groupPath(GroupEntity g) {
        return "/groups/" + g.getId();
    }
}
