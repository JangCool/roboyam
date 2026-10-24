package com.community.block.dto.response;

import java.util.List;

/** {@code GET /api/me/blocks/users} — user-web {@code BlockedUserList}. {@code total} 은 탈퇴한 회원을 뺀 수 */
public record BlockedUserListView(long total, List<BlockedUserView> items, boolean hasNext, String nextCursor) {
}
