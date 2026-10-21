package com.community.mail.dto;

import com.community.mail.entity.MailSecurity;

/**
 * 검사를 마친 메일 서버 값. {@code username} 이 null 이면 인증 없이 보내고 그때 {@code password} 도 null 이다.
 *
 * <p>{@link #toString()} 은 비밀번호를 감춘다.
 */
public record MailValues(String host, int port, String username, String password, String from, MailSecurity security) {

    @Override
    public String toString() {
        return "MailValues[host=" + host + ", port=" + port + ", username=" + username + ", password="
                + (password == null ? "null" : "(감춤)") + ", from=" + from + ", security=" + security + "]";
    }
}
