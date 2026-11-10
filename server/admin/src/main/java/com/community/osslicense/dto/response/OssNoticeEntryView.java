package com.community.osslicense.dto.response;

import java.time.Instant;

/**
 * 직접 추가 항목 한 줄(목록·만들기·고치기·숨기기 응답) — admin-web {@code OssNoticeEntry} 와 같은 칸. 빈 선택 칸은 null.
 *
 * @param category USER · ADMIN · SERVER · FONT_ICON · OTHER
 */
public record OssNoticeEntryView(String id, String name, String version, String license, String copyright, String licenseText, String sourceUrl, String category,
                                 boolean hidden, Instant updatedAt) {

    @Override
    public String toString() {
        return "OssNoticeEntryView[id=" + id + ", name=" + name + "]";
    }
}
