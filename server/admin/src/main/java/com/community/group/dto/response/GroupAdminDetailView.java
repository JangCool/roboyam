package com.community.group.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/admin/groups/{id}} — admin-web {@code GroupDetail}(읽기 전용 정보 + 비활성 기록).
 *
 * @param chat            채팅 링크가 있는지만(주소는 싣지 않는다)
 * @param creator         개설을 신청한 회원
 * @param reviewer        개설을 승인한 운영자(자유 개설이면 null)
 * @param leaderLoad      모임장이 가진 소모임 수(신청 중·열림·비활성 합)와 지금 1인 상한
 * @param postCount       삭제하지 않은 소모임 글 수(숨김·나가서 가린 글 포함)
 * @param publicPostCount 비멤버에게 보이는 글 수(공개 소모임의 글 · 개별 공개된 글 — 멤버 전용으로 돌린 글·숨김·가림은 빠진다, 5단계)
 * @param reportCaseCount 이 소모임을 대상으로 접수된 신고 사건 수(로보얌 신고 대상에 소모임이 아직 없다 — 늘 0)
 * @param deactivation    마지막으로 비활성이 된 때의 기록(한 번도 없으면 null)
 * @param managers        지금 매니저 — 임명이 이른 순(같으면 회원 ID), 없으면 빈 배열(읽기 전용). 공개 사이트의 매니저 수와 같은 줄들이다 — 탈퇴한 회원은 {@code user.withdrawn: true}(회원 탈퇴의 자격 소멸은 6단계)
 * @param managerLimit    이 소모임에 정해 둔 매니저 인원 상한(저장된 값 — 유효 상한은 min(이 값, managerLimitMax))
 * @param managerLimitMax 매니저 규칙의 최대 인원
 */
public record GroupAdminDetailView(String id, String name, String description, GroupCategoryRefView category, GroupBoardRefView board, String status,
                                   String joinType, boolean recruiting, String recruitState, int capacity, int memberCount, boolean offline, boolean stockNotice,
                                   Chat chat, GroupPersonView leader, GroupPersonView creator, GroupPersonView reviewer, Instant requestedAt, Instant openedAt,
                                   Instant lastActivityAt, LeaderLoad leaderLoad, long postCount, long publicPostCount, long reportCaseCount,
                                   Deactivation deactivation, List<Manager> managers, int managerLimit, int managerLimitMax, String visibility,
                                   Instant visibilityChangedAt, GroupPersonView visibilityChangedBy) {

    public record Chat(boolean kakao, boolean discord) {
    }

    public record LeaderLoad(long total, long active, long inactive, int max) {
    }

    /**
     * @param by         처리한 운영자(기록이 없으면 null)
     * @param reason     모임장에게 전달한 사유
     * @param reportCase 신고 처리로 비활성이 됐을 때 그 사건(로보얌에는 아직 없다 — 늘 null)
     * @param memo       다시 열 때의 메모(운영자만 본다)
     */
    public record Deactivation(Instant at, GroupPersonView by, String reason, ReportCase reportCase, Instant reactivatedAt, GroupPersonView reactivatedBy,
                               String memo) {
    }

    public record ReportCase(String id, long no) {
    }

    /** @param permissions 맡은 일 키(고정 순서) */
    public record Manager(GroupPersonView user, Instant appointedAt, List<String> permissions) {
    }
}
