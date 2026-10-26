package com.community.group.dto.request;

/**
 * 공개 범위·공개 요청 요청 본문(community api-groups.md 11-4·11-5 — 5단계). 검사 순서가 계약이라 어노테이션 없이 받고 서비스가 칸별로 본다({@code fields.<칸>}).
 */
public final class GroupVisibilityRequests {

    private GroupVisibilityRequests() {
    }

    /**
     * {@code PUT /api/groups/{id}/visibility} — 모임장의 공개 범위 변경.
     *
     * @param visibility   PUBLIC · PRIVATE
     * @param acknowledged 비공개→공개 때 true 여야 한다(「기존 글이 모두 공개된다」 확인)
     */
    public record Change(String visibility, Boolean acknowledged) {
    }

    /**
     * {@code POST /api/groups/{id}/posts/{postId}/publish-request} — 작성자의 공개 요청.
     *
     * @param note         처리자에게 남기는 한마디(없어도 된다 — 관리자 설정 textMax 이하)
     * @param acknowledged true 여야 한다(「닉네임과 프로필이 함께 공개된다」 확인)
     */
    public record Publish(String note, Boolean acknowledged) {
    }

    /** {@code POST …/publish-request/approve} — @param acknowledged true 여야 한다(「다른 멤버의 개인정보가 없는지 확인했다」) */
    public record Approve(Boolean acknowledged) {
    }

    /** {@code POST …/publish-request/reject} — @param reason 거절 사유(없어도 된다 — textMax 이하, 작성자에게 그대로 간다) */
    public record Reject(String reason) {
    }
}
