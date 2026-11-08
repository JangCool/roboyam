package com.community.post.dto.response;

import com.community.hotdeal.dto.response.DealStoreChipView;
import com.community.notice.dto.response.GlobalNoticeView;
import java.util.List;

/**
 * {@code GET /api/boards/{slug}/posts}(user-web {@code PostListResponse}).
 *
 * @param globalPinned 전체 공지(V174) — 공지 게시판 고정글 중 이 게시판 맨 위에도 건 것. {@code pinned} 와 같은 조건(첫 쪽 · 말머리/검색어/상태 거르기 없음)일 때만,
 *                     최대 3개, 공지 게시판 자신에는 없다. 화면은 {@code pinned} 위에 「공지」 줄로 그린다(docs/features/notice-admin-contract.md 「전체 공지」)
 * @param pinned 고정 공지 — 첫 쪽이고 말머리·검색어가 없을 때만, 최대 유효 {@code pinnedMax} 개
 * @param page   1부터
 * @param stores 핫딜 게시판만 — 쇼핑몰 칩(상위 8몰, 말머리·종료 숨기기 적용 뒤·쇼핑몰 거르기 전, 공개 글만). 그 밖 게시판 null
 */
public record PostListResponse(List<GlobalNoticeView> globalPinned, List<PostRowView> pinned, List<PostRowView> items, int page, int totalPages, long totalCount, List<DealStoreChipView> stores) {
}