package com.community.auth.service;

import com.community.terms.service.TermsService;
import org.springframework.stereotype.Component;

/**
 * 가입 때 동의 받은 약관 버전({@code users.terms_version}, 16자 이하)을 정하는 <b>유일한 곳</b>이다 — 이메일 가입({@link SignupService})과
 * 소셜 가입 마무리({@code SocialAccountWriter})가 같은 메서드를 쓴다.
 *
 * <p>값은 가입하는 순간의 지금 약관 기준 {@code T{이용약관 버전}.P{개인정보처리방침 버전}}({@link TermsService#currentAgreementVersion()},
 * 게시본이 없는 종류는 0 — {@code docs/features/terms-contract.md}). 설치 마법사의 루트 계정은 이것을 쓰지 않는다({@code 'install'}).
 */
@Component
public class TermsVersionProvider {

    private final TermsService terms;

    public TermsVersionProvider(TermsService terms) {
        this.terms = terms;
    }

    /** 지금 가입하는 회원이 동의하는 약관 버전 */
    public String currentTermsVersion() {
        return terms.currentAgreementVersion();
    }
}
