package com.community.mail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 메일 서버 설정 이력의 바뀐 칸 하나 — {@code mail_setting_history.changes} 배열의 원소 모양이다.
 *
 * @param field  칸 이름: {@code host} · {@code port} · {@code security} · {@code username} · {@code password} · {@code fromAddress}
 * @param before 이전 값(글자, 없었으면 null). 비밀번호는 늘 null
 * @param after  새 값(글자, 지웠으면 null). 비밀번호는 늘 null
 * @param change 비밀번호만: {@code SET}(새로 넣음) · {@code CHANGED}(바꿈) · {@code CLEARED}(지움). 다른 칸은 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MailSettingChange(String field, String before, String after, String change) {

    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String SECURITY = "security";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String FROM_ADDRESS = "fromAddress";

    /** 칸의 차례 — 이력·감사·필드 오류가 같은 차례를 쓴다 */
    public static final List<String> FIELDS = List.of(HOST, PORT, SECURITY, USERNAME, PASSWORD, FROM_ADDRESS);

    public static final String PASSWORD_SET = "SET";
    public static final String PASSWORD_CHANGED = "CHANGED";
    public static final String PASSWORD_CLEARED = "CLEARED";

    public static MailSettingChange value(String field, String before, String after) {
        return new MailSettingChange(field, before, after, null);
    }

    public static MailSettingChange password(String change) {
        return new MailSettingChange(PASSWORD, null, null, change);
    }
}
