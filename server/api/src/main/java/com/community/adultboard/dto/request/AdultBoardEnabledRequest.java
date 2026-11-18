package com.community.adultboard.dto.request;

/**
 * {@code PUT /api/me/adult-board} — 켜기 {@code { enabled: true, password, pin }} · 끄기 {@code { enabled: false }}(확인 없음). 검사는 서비스가 한다
 * (비밀번호가 없는 회원은 {@code password} 를 보내지 않는다).
 */
public record AdultBoardEnabledRequest(Boolean enabled, String password, String pin) {

    @Override
    public String toString() {
        return "AdultBoardEnabledRequest[enabled=" + enabled + ", password=(감춤), pin=(감춤)]";
    }
}
