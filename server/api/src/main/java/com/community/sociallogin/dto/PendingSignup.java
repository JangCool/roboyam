package com.community.sociallogin.dto;

import com.community.social.entity.SocialProvider;
import com.community.user.entity.MemberGender;
import java.io.Serializable;
import java.time.Instant;

/**
 * 처음 보는 소셜 계정의 가입 완료 대기 — <b>서버 세션에만</b> 두는 짧은 만료의 임시 정보다. 공급자 토큰은 들고 있지 않다(필요한 정보만 받고 버렸다).
 *
 * @param provider          공급자
 * @param subject           공급자 회원 번호
 * @param email             공급자가 준 이메일(소문자, 형식 검사를 마친 값). 없으면 null
 * @param emailVerified     공급자가 그 이메일을 확인했다고 했는가
 * @param nicknameCandidate 공급자가 「닉네임」으로 준 값 중 닉네임 규칙에 맞는 것(실명은 쓰지 않는다). 없으면 null
 * @param gender            공급자가 준 성별({@code MALE}·{@code FEMALE}). 못 받았으면 null — 있으면 가입 화면에서 묻지 않고 이 값을 쓴다
 * @param next              가입 뒤 돌아갈 같은 사이트 상대 경로. 없으면 null
 * @param expiresAt         이 시각이 지나면 쓸 수 없다
 */
public record PendingSignup(SocialProvider provider, String subject, String email, boolean emailVerified, String nicknameCandidate, MemberGender gender,
                            String next, Instant expiresAt) implements Serializable {

    /**
     * serialVersionUID 는 그대로 둔다 — 레코드는 정규 생성자로 복원하므로 {@code gender} 칸이 없던 옛 세션 객체도 gender=null(못 받음)로 깨지 않고 읽힌다.
     */
    private static final long serialVersionUID = 1L;

    /** 성별 없이 만든다(성별을 못 받은 경우·옛 호출부) */
    public PendingSignup(SocialProvider provider, String subject, String email, boolean emailVerified, String nicknameCandidate, String next,
                         Instant expiresAt) {
        this(provider, subject, email, emailVerified, nicknameCandidate, null, next, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "PendingSignup[provider=" + provider + ", email=" + (email == null ? "null" : "(감춤)") + ", emailVerified=" + emailVerified
                + ", expiresAt=" + expiresAt + "]";
    }
}
