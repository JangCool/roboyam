package com.community.block.dto.response;

import java.util.List;

/** {@code GET /api/me/blocks/words} — user-web {@code BlockedWordList}. 최근에 더한 것이 위 */
public record BlockedWordListView(int limit, int minLength, int maxLength, List<BlockedWordView> items) {
}
