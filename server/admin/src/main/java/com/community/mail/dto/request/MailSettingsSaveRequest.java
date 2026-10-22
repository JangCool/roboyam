package com.community.mail.dto.request;

/**
 * 메일 서버 설정 저장 — {@code PUT /api/admin/mail-settings}. 검사는 설치 마법사와 같은 규칙을 서비스가 모아서 한다(어노테이션 없음).
 *
 * @param host        1~255자, 공백·제어 문자 불가
 * @param port        1~65535
 * @param security    {@code NONE} · {@code STARTTLS} · {@code SSL}. 비우면 {@code NONE}
 * @param username    비우면 인증 없이 보낸다(저장된 비밀번호도 지운다)
 * @param password    비우면 저장된 비밀번호를 그대로 둔다(단 사용자 이름·서버 주소·보안 방식을 바꾸면 다시 입력해야 한다). 넣으면 암호화해 바꾼다
 * @param fromAddress   보내는 사람 이메일(필수, 형식 검사)
 * @param senderName    보내는 이름(선택). 앞뒤 공백을 자르고 비면 null. 1~50자, 줄바꿈·제어 문자 불가
 * @param subjectPrefix 제목 머리말(선택). 앞뒤 공백을 자르고 비면 null. 1~30자, 줄바꿈·제어 문자 불가
 */
public record MailSettingsSaveRequest(String host, Integer port, String security, String username, String password, String fromAddress,
                                      String senderName, String subjectPrefix) {

    @Override
    public String toString() {
        return "MailSettingsSaveRequest[host=" + host + ", port=" + port + ", security=" + security + ", username=" + username + ", password="
                + (password == null || password.isEmpty() ? "null" : "(감춤)") + ", fromAddress=" + fromAddress + ", senderName=" + senderName + ", subjectPrefix=" + subjectPrefix + "]";
    }
}
