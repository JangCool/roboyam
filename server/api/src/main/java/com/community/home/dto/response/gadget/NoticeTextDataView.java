package com.community.home.dto.response.gadget;

/**
 * {@code NOTICE_TEXT} 가젯의 data — user-web {@code NoticeTextData}. 운영자가 적은 평문(HTML 아님).
 *
 * @param format {@code PLAIN} · {@code BULLETS}
 */
public record NoticeTextDataView(String text, String format) {
}
