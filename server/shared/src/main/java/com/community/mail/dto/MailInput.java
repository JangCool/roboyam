package com.community.mail.dto;

/**
 * 메일 서버 입력. 설치 마법사의 {@code mail}·{@code mail-check} 요청이 같은 모양이다. 검사는 서비스가 모든 위반을 한 번에 모아 돌려준다.
 *
 * <p>{@link #toString()} 은 비밀번호를 감춘다 — 요청 객체가 로그·오류 메시지에 찍혀도 값이 새지 않게 한다.
 *
 * @param host     1~255자
 * @param port     1~65535
 * @param username 비우면 인증 없이 보낸다
 * @param password 사용자 이름이 있으면 필수
 * @param from     보내는 사람 이메일(형식 검사)
 * @param security {@code NONE} · {@code STARTTLS} · {@code SSL}. 생략하면 {@code NONE}
 */
public record MailInput(String host, Integer port, String username, String password, String from, String security) {

    @Override
    public String toString() {
        return "MailInput[host=" + host + ", port=" + port + ", username=" + username + ", password="
                + (password == null || password.isEmpty() ? "null" : "(감춤)") + ", from=" + from + ", security=" + security + "]";
    }
}
