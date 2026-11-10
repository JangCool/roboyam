package com.community.osslicense.dto;

/**
 * 직접 추가 항목의 칸(검사·정리를 마친 값 — {@code com.community.osslicense.service.OssNotices}). 빈 선택 칸은 null.
 *
 * @param category USER · ADMIN · SERVER · FONT_ICON · OTHER
 */
public record OssNoticeEntryFields(String name, String version, String license, String copyright, String licenseText, String sourceUrl, String category,
                                   boolean hidden) {

    /** 전문은 길어 로그에 싣지 않는다(글자 수만) */
    @Override
    public String toString() {
        return "OssNoticeEntryFields[name=" + name + ", version=" + version + ", license=" + license + ", category=" + category + ", hidden=" + hidden
                + ", licenseText=" + (licenseText == null ? 0 : licenseText.length()) + " chars]";
    }
}
