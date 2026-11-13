package com.community.post.dto.response;

import com.community.debate.dto.response.DebateSummaryView;
import com.community.hotdeal.dto.response.DealWarningView;
import com.community.hotdeal.dto.response.PostDealModerationView;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.poll.dto.response.PollView;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/posts/{id}}·{@code PUT /api/posts/{id}} 응답(user-web {@code PostDetail}).
 *
 * @param contentFormat        본문 형식 TEXT · HTML
 * @param content              본문 — TEXT 는 평문, HTML 은 서버가 정화하고 이미지 주소·크기를 채운 HTML(화면은 이것만 그대로 그린다)
 * @param updatedAt            작성자가 고쳤으면 그 시각, 아니면 null
 * @param liked                내가 이미 추천했다(비회원 false)
 * @param mine                 로그인한 내 회원 글
 * @param deleteScheduledAt    예약 삭제된 글이면 지워질 시각
 * @param prev                 이전 글(더 오래된 글), 없으면 null
 * @param next                 다음 글(더 새 글), 없으면 null
 * @param externalLinkNofollow 본문 외부 링크에 nofollow 를 단다(관리자 보안 설정)
 * @param blocked              보는 사람이 작성 회원을 차단했으면 {@code USER}, 제목에 차단 단어가 들었으면 {@code WORD}, 아니면 null(내 글·비로그인 늘 null) —
 *                             게시판 글 목록과 같은 판정({@code ViewerBlocks}). 본문은 그대로 주고 가리는 것은 화면 몫. {@code PUT} 응답은 늘 null
 * @param highlighted          지금 하이라이트로 노출 중인 글인가(노출 켬 + 기간 안) — 화면이 제목 옆에 「하이라이트」 표시를 단다. 「찬성·반대 의견 받기」와 무관
 * @param debate               하이라이트 토론 요약(늘 있다 — 하이라이트가 아니면 {@code enabled false}) — docs/features/popular-highlight-contract.md 「서버 구현 결과」
 * @param poll                 글에 붙은 투표, 없으면 null — docs/features/post-poll-contract.md
 * @param group                소모임 글이면 소모임 칸({@code id}·{@code name}·{@code visibility}·{@code dissolved}·{@code canPublish}·{@code canUnpublish} — 소모임 2단계), 일반 글 null
 * @param deal                 핫딜 게시판의 글이면 딜(docs/features/hotdeal-contract.md), 아니면 null
 * @param dealModeration       보는 사람이 그 핫딜 게시판의 매니저·스텝(정지 아님)이고 딜이 있으면 매니저·스텝 조치(role·canEnd·canReopen), 아니면 null
 * @param warnings             고치기({@code PUT}) 응답에만 — 저장은 됐지만 알릴 것(예 {@code DEAL_LINK_BLOCKED}). 글 보기는 null
 * @param staffProtected       매니저·스텝의 글 숨기기·삭제가 막힌 글 — 공지 말머리·고정 글, 보는 사람이 그 게시판 매니저·스텝이면 같은 게시판 매니저·스텝의 글도
 *                             (docs/features/board-staff-action-contract.md 정책 4, 화면은 ⋯ 메뉴에서 뺀다)
 * @param sanctionProtected    보는 사람이 그 게시판 매니저·스텝이고 작성자가 같은 게시판 매니저·스텝이다 — 「작성자 제재」가 막힌다(board-sanction-contract, 화면은 ⋯ 메뉴에서 뺀다).
 *                             그 밖의 보는 사람은 늘 false
 * @param secret               비밀글인가(V166 — 읽을 수 없는 사람은 이 응답 대신 403 SECRET_POST)
 * @param processStatus        처리 상태(V166) — 게시판이 상태를 쓰지 않으면 null, 쓰면 글 값(없으면 RECEIVED)
 * @param processStatusAt      처리 상태를 마지막으로 바꾼 시각(처음 접수·상태를 쓰지 않는 게시판은 null)
 * @param processStatusNote    마지막 상태 변경의 메모(V168 — 없거나 상태를 쓰지 않는 게시판은 null)
 * @param viewer               보는 사람이 할 수 있는 일({@code canChangeStatus} — V168)
 * @param thumbnail            대표 썸네일 — 글쓴이 본인(회원 글, 소모임 글 아님)에게만, 그 밖 null(V206, list-thumbnail-contract 「2단계」)
 */
public record PostDetailView(String id, PostBoardRefView board, PostPrefixRefView prefix, String title, String contentFormat, String content, PostAuthorView author,
                             Instant createdAt, Instant updatedAt, int viewCount, int likeCount, boolean liked, int commentCount, boolean adult, boolean mine,
                             PostProtectionView protection, Instant deleteScheduledAt, PostNeighborView prev, PostNeighborView next,
                             boolean externalLinkNofollow, String blocked, boolean highlighted, DebateSummaryView debate, PollView poll,
                             PostGroupView group, PostDealView deal, PostDealModerationView dealModeration, List<DealWarningView> warnings,
                             boolean staffProtected, boolean sanctionProtected, boolean secret, String processStatus, Instant processStatusAt,
                             String processStatusNote, PostViewerView viewer, PostThumbnailView thumbnail) {
}
