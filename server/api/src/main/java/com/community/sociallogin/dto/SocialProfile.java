package com.community.sociallogin.dto;

import com.community.social.entity.SocialProvider;

/**
 * 공급자에게서 받은 회원 정보 중 <b>쓰는 것만</b>. 공급자 액세스 토큰·응답 본문은 담지 않는다.
 *
 * @param subject       공급자 회원 번호(1~128자)
 * @param email         이메일(소문자, 형식이 맞을 때만). 없으면 null
 * @param emailVerified 공급자가 그 이메일을 확인했다고 했는가(네이버는 알려 주지 않아 늘 false)
 * @param nickname      공급자의 「닉네임」 값(카카오 프로필 닉네임·네이버 별명). 실명은 담지 않는다. 없으면 null
 */
public record SocialProfile(SocialProvider provider, String subject, String email, boolean emailVerified, String nickname) {

    @Override
    public String toString() {
        return "SocialProfile[provider=" + provider + ", email=" + (email == null ? "null" : "(감춤)") + ", emailVerified=" + emailVerified + "]";
    }
}
