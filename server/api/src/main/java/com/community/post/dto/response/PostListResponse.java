package com.community.post.dto.response;

import com.community.hotdeal.dto.response.DealStoreChipView;
import java.util.List;

/**
 * {@code GET /api/boards/{slug}/posts}(user-web {@code PostListResponse}).
 *
 * @param pinned 고정 공지 — 첫 쪽이고 말머리·검색어가 없을 때만, 최대 유효 {@code pinnedMax} 개
 * @param page   1부터
 * @param stores 핫딜 게시판만 — 쇼핑몰 칩(상위 8몰, 말머리·종료 숨기기 적용 뒤·쇼핑몰 거르기 전, 공개 글만). 그 밖 게시판 null
 */
public record PostListResponse(List<PostRowView> pinned, List<PostRowView> items, int page, int totalPages, long totalCount, List<DealStoreChipView> stores) {
}