package com.community.sociallogin.dto;

import com.community.social.entity.SocialProvider;
import com.community.user.entity.MemberGender;

/**
 * 공급자에게서 받은 회원 정보 중 <b>쓰는 것만</b>. 공급자 액세스 토큰·응답 본문은 담지 않는다.
 *
 * @param subject       공급자 회원 번호(1~128자)
 * @param email         이메일(소문자, 형식이 맞을 때만). 없으면 null
 * @param emailVerified 공급자가 그 이메일을 확인했다고 했는가(네이버는 알려 주지 않아 늘 false)
 * @param nickname      닉네임 후보(카카오 프로필 닉네임·네이버 별명·구글 이름 중 닉네임 규칙에 맞는 것). 없으면 null
 * @param gender        공급자가 준 성별({@code MALE}·{@code FEMALE}). 못 받았거나 모르면 null — 개인정보라 {@code toString} 에 싣지 않는다
 */
public record SocialProfile(SocialProvider provider, String subject, String email, boolean emailVerified, String nickname,
                            MemberGender gender) {

    @Override
    public String toString() {
        return "SocialProfile[provider=" + provider + ", email=" + (email == null ? "null" : "(감춤)") + ", emailVerified=" + emailVerified + "]";
    }
}
