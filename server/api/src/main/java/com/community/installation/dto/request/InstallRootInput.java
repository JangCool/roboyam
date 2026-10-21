package com.community.installation.dto.request;

/**
 * 루트 계정 입력. {@link #toString()} 은 비밀번호를 감춘다.
 *
 * @param nickname 2~12자(예약어 검사는 하지 않는다 — "운영자" 같은 이름을 쓸 수 있다, 중복은 안 된다)
 * @param password 8~64자, 영문과 숫자 포함
 */
public record InstallRootInput(String email, String nickname, String password) {

    @Override
    public String toString() {
        return "InstallRootInput[email=" + email + ", nickname=" + nickname + ", password=" + (password == null ? "null" : "(감춤)") + "]";
    }
}
