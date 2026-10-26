package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;
import java.util.List;

/** 공개 범위·공개 요청 응답(community api-groups.md 11-4·11-5 — 5단계). ID 는 문자열이다 */
public final class GroupVisibilityViews {

    private GroupVisibilityViews() {
    }

    /**
     * {@code GET /api/groups/{id}/visibility-preview?to=} — 확인 시트의 숫자·쿨다운. 지금 값과 같으면 모든 숫자가 0.
     *
     * @param postCount               그 방향으로 바뀌는 글 수(PUBLIC: 멤버 전용 기록이 없는 모든 보이는 글 — 이미 개별 공개된 글 포함 · PRIVATE: 지금 비멤버에게 보이는 글)
     * @param commentCount            그 글들의 지우지 않은 댓글 수
     * @param individuallyPublicCount 그중 지금 개별 공개돼 있는 글 수(PRIVATE 으로 가면 공개 기록이 지워진다)
     * @param membersOnlyCount        멤버 전용으로 돌려 둔 채 남는(PUBLIC — postCount 에 들지 않는다) 또는 잠재워지는(PRIVATE) 글 수
     * @param pendingRequestCount     이 변경으로 닫히는 대기 요청 수(PUBLIC 일 때만)
     * @param canChangeAt             쿨다운이 끝나는 시각 — 지금 바꿀 수 있거나 PRIVATE 이면 null
     */
    public record Preview(String to, int postCount, int commentCount, int individuallyPublicCount, int membersOnlyCount, int pendingRequestCount,
                          Instant canChangeAt) {
    }

    /** {@code PUT /api/groups/{id}/visibility} — @param changedAt 마지막 변경 시각(같은 값이면 이전 변경 시각 — 개설 때 정한 값이면 null) */
    public record ChangeResult(String visibility, Instant changedAt, Affected affected) {
    }

    /** 이번 변경으로 바뀐 글·댓글 수와 멤버 전용으로 남은 글 수(같은 값이면 모두 0) */
    public record Affected(int posts, int comments, int membersOnly) {
    }

    /** {@code POST …/publish-request} — @param status PENDING */
    public record PublishResult(String status, Instant requestedAt) {
    }

    /** {@code POST …/publish-request/reject} — @param rejectCount 이 글이 거절된 횟수(이번 포함) */
    public record RejectResult(String status, int rejectCount) {
    }

    /** 처리 대기 목록의 글 요약 */
    public record RequestPost(String id, String title, Instant createdAt, Instant editedAt) {
    }

    /**
     * 처리 대기 목록의 한 줄.
     *
     * @param author             작성자(쪽지 상대와 같은 모양 — 탈퇴했으면 id null)
     * @param editedAfterRequest 요청 뒤에 글을 고쳤다(승인 전에 다시 보게 하는 칩)
     * @param authorStillMember  작성자가 지금도 멤버다(강퇴됐으면 false — 승인은 막지 않는다)
     * @param rejectCount        이 글이 이전에 거절된 횟수
     */
    public record RequestRow(RequestPost post, DmPartnerView author, String note, Instant requestedAt, boolean editedAfterRequest, boolean authorStillMember,
                             int rejectCount) {
    }

    /**
     * {@code GET /api/groups/{id}/publish-requests} — 오래된 요청이 위.
     *
     * @param nextCursor     다음 쪽 커서(불투명 문자열) — 없으면 null
     * @param total          처리 대기 수(지워졌거나 가려진 글의 요청은 빼고)
     * @param publishTextMax 지금 적용되는 한마디·거절 사유 글자 수 한도(관리자 설정 textMax)
     */
    public record RequestList(List<RequestRow> items, boolean hasNext, String nextCursor, long total, int publishTextMax) {
    }
}
