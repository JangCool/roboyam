package com.community.post.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 소모임 글의 소모임 칸(community {@code GroupRef}·{@code GroupPostInfo} — api-groups.md 8절·11-7). 일반 글이면 응답의 {@code group} 이 null 이다.
 * 글 줄(소모임 글 탭)은 {@code id}·{@code name}·{@code visibility}·{@code dissolved} 만, 글 상세는 아래 칸까지 싣는다. <b>값이 null 인 칸은 응답에서 빠진다</b>(줄에서는 상세 칸이 빠진다 —
 * 화면은 빠진 칸을 null 로 읽는다).
 *
 * @param visibility              PRIVATE(비멤버에게 안 보임) · PUBLIC(비멤버에게 보임 — 공개 소모임의 글 · 개별 공개된 글)
 * @param dissolved               해산한 소모임의 공개 글(소모임 연결은 남는다 — 화면은 링크 없이 「해산한 소모임」)
 * @param canPublish              (상세만) 공개 범위 기능이 켜져 있으면 모임장·「글 공개 처리·되돌리기」 매니저의 직접 공개(비공개 소모임 · 비공개 글 · 쓸 수 있음),
 *                                꺼져 있으면 옛 뜻(작성자이고 지금도 멤버이며 쓸 수 있고 아직 비공개)
 * @param canUnpublish            (상세만) 비공개 소모임의 개별 공개 글을 되돌릴 수 있다(작성자 · 모임장 · 맡은 매니저 — 공개 소모임의 글은 false, 그쪽은 {@code canMakeMembersOnly})
 * @param groupVisibility         (상세만) 소모임의 공개 범위 PUBLIC · PRIVATE · null(공개 범위 기능이 꺼져 있고 비공개 소모임 — 빠진다)
 * @param publicBy                (상세만) 비멤버에게 보이는 까닭 GROUP(공개 소모임) · APPROVED(공개 요청 승인) · DIRECT(직접 공개) · null(안 보임 — 빠진다)
 * @param membersOnly             (상세만) 공개 소모임에서 멤버 전용으로 돌린 글
 * @param publishRequest          (상세만) 공개 요청 상태 — 작성자·처리자에게만, 요청이 대기·거절일 때만(아니면 빠진다)
 * @param canRequestPublish       (상세만) 작성자 · 비공개 소모임 · 아직 비공개 · 쓸 수 있음 · 요청 조건 통과(기능 켜짐)
 * @param canDecidePublishRequest (상세만) 처리자이고 요청이 대기 중
 * @param canMakeMembersOnly      (상세만) 처리자 · 공개 소모임 · 지금 비멤버에게 보이는 글 · 쓸 수 있음
 * @param canReleaseMembersOnly   (상세만) 처리자 · 멤버 전용으로 돌린 글 · 쓸 수 있음
 * @param membersOnlyCommentCount (상세만) 비멤버·비회원에게만 — 공개 시각 이전에 달려 가려진 댓글 수(멤버는 빠진다)
 * @param publishTextMax          (상세만) 공개 요청 한마디·거절 사유의 글자 수 한도(관리자 설정 textMax 10~100)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostGroupView(String id, String name, String visibility, boolean dissolved, Boolean canPublish, Boolean canUnpublish, String groupVisibility,
                            String publicBy, Boolean membersOnly, PublishRequest publishRequest, Boolean canRequestPublish, Boolean canDecidePublishRequest,
                            Boolean canMakeMembersOnly, Boolean canReleaseMembersOnly, Integer membersOnlyCommentCount, Integer publishTextMax) {

    public static final String PUBLIC = "PUBLIC";
    public static final String PRIVATE = "PRIVATE";

    /** 글 줄용(상세 칸 없음) */
    public static PostGroupView ref(String id, String name, boolean publicPost, boolean dissolved) {
        return new PostGroupView(id, name, publicPost ? PUBLIC : PRIVATE, dissolved, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 글 상세의 공개 요청 상태(community {@code PublishRequestInfo}).
     *
     * @param status       PENDING · REJECTED
     * @param rejectReason 거절 사유(거절됐고 적었을 때)
     * @param decidedAt    거절 시각(거절일 때)
     * @param canRetryAt   거절 뒤 다시 요청할 수 있는 시각(거절됐고 한도가 남았을 때 — 이미 지났을 수 있다), 아니면 null
     * @param rejectCount  이 글이 거절된 횟수
     */
    public record PublishRequest(String status, Instant requestedAt, String note, String rejectReason, Instant decidedAt, Instant canRetryAt, int rejectCount) {
    }
}
