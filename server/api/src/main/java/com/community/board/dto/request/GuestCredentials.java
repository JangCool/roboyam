package com.community.board.dto.request;

/**
 * 비회원 작성 정보(user-web {@code GuestCredentials}) — 이름·비밀번호(서버는 bcrypt 해시만 저장)·자동입력 방지 토큰. 검사는 서비스가 칸 경로({@code guest.name} 등)로 한다.
 */
public record GuestCredentials(String name, String password, String captchaToken) {

    @Override
    public String toString() {
        return "GuestCredentials[name=(감춤), password=(감춤), captchaToken=(감춤)]";
    }
}
