package com.community.osslicense.dto.response;

/**
 * 공개 화면에 보일 직접 추가 항목 한 줄(숨기지 않은 것) — user-web {@code OssManualEntry} 와 같은 칸. 빈 선택 칸은 null.
 * 버전은 싣지 않는다(사용자 2026-11-11 — 알려진 취약점을 찾는 단서가 되지 않게, 관리자 API 에만 있다).
 *
 * @param category USER · ADMIN · SERVER · FONT_ICON · OTHER
 */
public record OssNoticePublicEntryView(String id, String name, String license, String copyright, String licenseText, String sourceUrl,
                                       String category) {
}
