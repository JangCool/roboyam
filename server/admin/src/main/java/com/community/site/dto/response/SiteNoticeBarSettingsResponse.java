package com.community.site.dto.response;

import java.util.List;

/**
 * {@code GET·PUT /api/admin/site/notice-bar} — 저장값 다섯 칸 + 공지 게시판 + 지금 띠에 보일 줄의 미리보기.
 *
 * @param customText  없으면 null
 * @param customLink  없으면 null
 * @param noticeBoard 공지 게시판(slug {@code notice}) — 없거나 꺼졌거나 소모임 게시판이면 null(이때 {@code BOARD} 출처는 띠에 아무것도 안 보인다)
 * @param preview     지금 저장값 기준으로 공개 띠에 보일 줄의 제목들(공개 API 와 같은 판정) — 꺼져 있어도 출처 기준으로 채운다
 */
public record SiteNoticeBarSettingsResponse(boolean enabled, String source, int boardCount, String customText, String customLink,
                                            SiteNoticeBoardView noticeBoard, List<String> preview) {
}
