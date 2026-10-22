package com.community.mail.dto;

import java.util.List;

/**
 * 메일 서버 설정 이력의 바뀐 칸 하나 — {@code mail_setting_history.changes} 배열의 원소 모양이다(소셜 로그인 설정 이력 {@code SocialSettingChange} 와 같은 모양).
 * 값은 모두 글자다(포트도). <b>비밀번호는 값 없이 표시 코드만</b> — 처음 넣음 {@code null → SET}, 바꿈 {@code SET → CHANGED}, 지움 {@code SET → CLEARED}.
 *
 * @param field  칸 이름: {@code host} · {@code port} · {@code security} · {@code username} · {@code password} · {@code fromAddress} ·
 *               {@code senderName} · {@code subjectPrefix}
 * @param before 이전 값(없었으면 null)
 * @param after  새 값(지웠으면 null)
 */
public record MailSettingChange(String field, String before, String after) {

    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String SECURITY = "security";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String FROM_ADDRESS = "fromAddress";
    public static final String SENDER_NAME = "senderName";
    public static final String SUBJECT_PREFIX = "subjectPrefix";

    /** 칸의 차례 — 이력·감사·필드 오류가 같은 차례를 쓴다 */
    public static final List<String> FIELDS = List.of(HOST, PORT, SECURITY, USERNAME, PASSWORD, FROM_ADDRESS, SENDER_NAME, SUBJECT_PREFIX);

    /** 비밀번호를 처음 넣음 */
    public static final String PASSWORD_SET = "SET";
    /** 비밀번호를 바꿈 */
    public static final String PASSWORD_CHANGED = "CHANGED";
    /** 비밀번호를 지움(사용자 이름을 비움) */
    public static final String PASSWORD_CLEARED = "CLEARED";

    /** 비밀번호 칸 — {@code SET} 이면 이전 값이 없고, {@code CHANGED}·{@code CLEARED} 면 이전 값은 {@code SET} 이다 */
    public static MailSettingChange password(String mark) {
        return new MailSettingChange(PASSWORD, PASSWORD_SET.equals(mark) ? null : PASSWORD_SET, mark);
    }
}
