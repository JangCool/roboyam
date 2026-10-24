package com.community.post.dto.response;

import com.community.debate.dto.response.DebateSummaryView;
import com.community.poll.dto.response.PollView;
import java.time.Instant;

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
 */
public record PostDetailView(String id, PostBoardRefView board, PostPrefixRefView prefix, String title, String contentFormat, String content, PostAuthorView author,
                             Instant createdAt, Instant updatedAt, int viewCount, int likeCount, boolean liked, int commentCount, boolean adult, boolean mine,
                             PostProtectionView protection, Instant deleteScheduledAt, PostNeighborView prev, PostNeighborView next,
                             boolean externalLinkNofollow, String blocked, boolean highlighted, DebateSummaryView debate, PollView poll) {
}
