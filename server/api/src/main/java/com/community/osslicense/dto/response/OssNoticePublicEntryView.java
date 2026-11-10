package com.community.osslicense.dto.response;

/**
 * 공개 화면에 보일 직접 추가 항목 한 줄(숨기지 않은 것) — user-web {@code OssNoticeManualEntry} 와 같은 칸. 빈 선택 칸은 null.
 *
 * @param category USER · ADMIN · SERVER · FONT_ICON · OTHER
 */
public record OssNoticePublicEntryView(String id, String name, String version, String license, String copyright, String licenseText, String sourceUrl,
                                       String category) {
}
