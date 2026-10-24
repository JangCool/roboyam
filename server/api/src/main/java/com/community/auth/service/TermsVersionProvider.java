package com.community.auth.service;

import org.springframework.stereotype.Component;

/**
 * 가입 때 동의 받은 약관 버전({@code users.terms_version}, 16자 이하)을 정하는 <b>유일한 곳</b>이다 — 이메일 가입({@link SignupService})과
 * 소셜 가입 마무리({@code SocialAccountWriter})가 같은 메서드를 쓴다.
 *
 * <p>약관 기능이 아직 없어 자리표시 값 {@code v1} 을 돌려준다. 약관 기능을 합칠 때 {@link #currentTermsVersion()} 의 몸통만 「지금 게시 중인 약관 버전 조회」로 바꾼다
 * (부르는 쪽은 그대로다).
 */
@Component
public class TermsVersionProvider {

    /** 지금 가입하는 회원이 동의하는 약관 버전 */
    public String currentTermsVersion() {
        return "v1";
    }
}
