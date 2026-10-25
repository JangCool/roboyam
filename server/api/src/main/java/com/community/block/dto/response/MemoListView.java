package com.community.block.dto.response;

import java.util.List;

/** {@code GET /api/me/memos} — user-web {@code MemoList}. 처음 남긴 것이 최근인 순서, {@code total} 은 탈퇴한 회원을 뺀 수 */
public record MemoListView(long total, List<MemoView> items, boolean hasNext, String nextCursor) {
}
