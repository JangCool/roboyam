package com.community.post.dto.response;

import java.time.Instant;

/**
 * 게시글 관리 목록·상세의 글 한 줄 — docs/features/post-admin-contract.md {@code AdminPostItem}.
 *
 * @param prefix            말머리(없으면 null)
 * @param state             PUBLIC · HIDDEN · DELETED(지움 · 예약 삭제 시각 지남) — 지움이 숨김을 이긴다
 * @param hiddenReason      숨김 까닭 REPORTED · ILLEGAL · RIGHTS · STAFF — 숨김 중이 아니면 null(지운 글도 숨긴 채 지웠으면 값이 있다)
 * @param hiddenAt          숨긴 시각(숨김 중이 아니면 null)
 * @param hiddenUntil       임시조치가 끝나는 시각(권리침해 숨김만, 그 밖 null)
 * @param deletedAt         지운 시각(예약 삭제가 지났는데 아직 확정 전이면 예약 시각)
 * @param deleteScheduledAt 댓글 많은 글의 예약 삭제 시각(없으면 null)
 * @param purged            원문을 파기했는가(복구 불가)
 * @param pinned            고정 글인가
 * @param notice            말머리가 공통 「공지」(NOTICE)인가
 * @param debate            찬반 토론이 열려 있는가
 * @param poll              투표가 붙어 있는가(목록 칩)
 * @param openReports       이 글에 대한 처리 대기 신고 수(글 신고만)
 * @param opinionCount      의견 수 — 입장 있는 원댓글(지운 것 제외·숨긴 것 포함). 토론이 닫혀도 센다(닫힌 토론도 의견 관리를 열 수 있게)
 * @param pendingPinRequests 대기 중인 의견 고정 요청 수
 * @param group             소모임 글이면 소모임 표시, 일반 글 null(소모임 2단계)
 * @param restricted        비공개 소모임 글 — {@code title} 이 null 이고 상세 본문도 싣지 않는다(내용은 신고 처리의 신고 당시 내용으로만 본다). 이동은 409 POST_STATE_INVALID
 */
public record AdminPostItemView(String id, AdminPostBoardRefView board, AdminPostPrefixRefView prefix, String title, AdminPostAuthorView author, String state,
                                String hiddenReason, Instant hiddenAt, Instant hiddenUntil, Instant deletedAt, Instant deleteScheduledAt, boolean purged,
                                boolean pinned, boolean notice, boolean adult, boolean debate, boolean poll, int likeCount, int commentCount, int viewCount,
                                int openReports, Instant createdAt, int opinionCount, int pendingPinRequests, AdminPostGroupView group, boolean restricted) {
}
