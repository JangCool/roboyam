package com.community.member.dto.response;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.IdentitySummary;
import java.time.Instant;

/**
 * 회원 상세의 「본인 인증」 구획(계약 「0-4」 {@code identity: { verified, verifiedAt, adult, provider }}) — 생년월일·DI·CI 해시는 싣지 않는다.
 * 탈퇴 회원은 이 칸 자체가 null 이다({@code MemberService}).
 *
 * @param verified   본인인증을 마쳤는가
 * @param verifiedAt 인증 시각(미인증이면 null)
 * @param adult      지금(서비스 날짜) 만 19세 이상인가(미인증이면 null)
 * @param provider   인증 구현 {@code PORTONE} · {@code FAKE}(미인증이면 null)
 */
public record MemberIdentityView(boolean verified, Instant verifiedAt, Boolean adult, IdentityProviderKind provider) {

    /** 인증 기록이 없는 회원 */
    public static MemberIdentityView unverified() {
        return new MemberIdentityView(false, null, null, null);
    }

    public static MemberIdentityView of(IdentitySummary summary) {
        return new MemberIdentityView(summary.verified(), summary.verifiedAt(), summary.adult(), summary.provider());
    }
}
