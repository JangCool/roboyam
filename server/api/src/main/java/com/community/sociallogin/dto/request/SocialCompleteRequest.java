package com.community.sociallogin.dto.request;

/**
 * {@code POST /api/auth/social/complete} — 소셜 가입 마무리. 공급자 정보는 서버 세션의 가입 대기에서 오고 요청으로 받지 않는다.
 * 위반은 서비스가 칸별로 한꺼번에 모은다({@code nickname} → {@code agreeTerms} → {@code agreePrivacy} → {@code ageConfirmed} → {@code gender}).
 *
 * @param agreeTerms   이용약관 동의 — true 여야 한다
 * @param agreePrivacy 개인정보 처리방침 동의 — true 여야 한다
 * @param ageConfirmed 만 14세 이상 확인 — true 여야 한다
 * @param gender       성별(선택) {@code MALE}·{@code FEMALE}·{@code NONE} — 없으면 {@code NONE}(밝히지 않음). 틀린 값은 {@code fields.gender}
 */
public record SocialCompleteRequest(String nickname, Boolean agreeTerms, Boolean agreePrivacy, Boolean ageConfirmed, String gender) {

    /** 성별 없이(밝히지 않음) */
    public SocialCompleteRequest(String nickname, Boolean agreeTerms, Boolean agreePrivacy, Boolean ageConfirmed) {
        this(nickname, agreeTerms, agreePrivacy, ageConfirmed, null);
    }
}
