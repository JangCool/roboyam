package com.community.notificationtemplate.dto.request;

import java.util.Map;

/**
 * {@code POST·PUT /api/admin/notification-templates/mail[/{id}]} — 미리보기의 저장 전 값({@code MailPreviewRequest.template})도 이 모양.
 *
 * @param audience    {@code MEMBER|OPERATOR|ANY} — 쓰는 문구의 받는 사람과 맞지 않으면 400 {@code fields.audience}
 * @param headerColor 팔레트 색 이름 — null 이면 띠 없음
 * @param footer      언어별 꼬리말
 * @param frames      언어별 틀
 */
public record MailTemplateSaveRequest(String name, String audience, Boolean showLogo, Boolean showSiteName, String headerColor, Map<String, String> footer,
                                      Map<String, String> frames) {
}
