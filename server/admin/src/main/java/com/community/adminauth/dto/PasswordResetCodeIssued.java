package com.community.adminauth.dto;

import java.util.Locale;

/**
 * 비밀번호 재설정 승인번호를 만들었다는 알림 — 트랜잭션이 <b>커밋된 뒤에만</b> 듣는 쪽({@code PasswordResetMailSender})이 메일로 보낸다.
 * 번호 원문은 이 알림에만 있고 DB 에는 해시만 남는다. 메일 언어는 요청의 언어를 요청 스레드에서 미리 담아 둔다. {@link #toString()} 은 값을 싣지 않는다.
 */
public record PasswordResetCodeIssued(String email, String code, Locale locale) {

    @Override
    public String toString() {
        return "PasswordResetCodeIssued[email=(감춤), code=(감춤), locale=" + locale + "]";
    }
}
