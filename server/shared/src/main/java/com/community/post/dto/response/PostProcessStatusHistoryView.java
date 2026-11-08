package com.community.post.dto.response;

import java.util.List;

/** 처리 상태 이력 — 최신순, 50줄까지. 상태를 쓰지 않는 게시판이면 빈 목록 */
public record PostProcessStatusHistoryView(List<PostProcessStatusHistoryItemView> items) {
}
