package com.community.search.dto.response;

import java.util.List;

/**
 * {@code GET /api/search}(user-web {@code SearchResponse}).
 *
 * @param query      서버가 다듬은 검색어
 * @param tokens     찾은 단어들(중복·5개 초과를 버린 것) — 화면이 일치 부분을 강조한다
 * @param scope      all · title · body · author
 * @param board      게시판 slug — 모든 게시판이면 null
 * @param page       쪽 번호로 물었으면 그 쪽, 커서로 물었으면 null
 * @param totalPages 쪽 번호로 물었으면 최대 10(그 뒤는 커서 더보기), 커서로 물었으면 null. 결과가 없으면 0
 * @param nextCursor 다음 요청의 {@code cursor}(이 응답의 마지막 글 ID) — 더 없으면 null
 */
public record SearchResponse(String query, List<String> tokens, String scope, String board, List<SearchRowView> items, Integer page, Integer totalPages,
                             boolean hasNext, String nextCursor) {
}
