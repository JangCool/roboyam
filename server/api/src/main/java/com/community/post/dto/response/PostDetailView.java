package com.community.post.dto.response;

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
 */
public record PostDetailView(String id, PostBoardRefView board, PostPrefixRefView prefix, String title, String contentFormat, String content, PostAuthorView author,
                             Instant createdAt, Instant updatedAt, int viewCount, int likeCount, boolean liked, int commentCount, boolean adult, boolean mine,
                             PostProtectionView protection, Instant deleteScheduledAt, PostNeighborView prev, PostNeighborView next,
                             boolean externalLinkNofollow) {
}
