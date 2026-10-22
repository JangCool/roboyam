package com.community.captcha;

/**
 * 자동입력 방지(캡차) 결과 확인 — 비회원 글·댓글 쓰기에서 부른다. 구현은 {@link CaptchaConfig} 가 고른다:
 * 비밀키({@code roboyam.captcha.turnstile-secret}, 환경 변수 {@code TURNSTILE_SECRET_KEY})가 있으면 Cloudflare Turnstile, 없으면 「검증 안 함」(기동 로그에 경고).
 * 외부 요청이라 <b>트랜잭션 밖에서</b> 부른다.
 */
public interface CaptchaVerifier {

    /**
     * @param token    화면이 받은 결과 토큰(비어 있지 않다 — 빈 값은 부르는 쪽이 칸 오류로 막는다)
     * @param remoteIp 요청한 사람의 IP(선택 — 공급자에 함께 보낸다)
     * @return 사람이 맞으면 true
     */
    boolean verify(String token, String remoteIp);
}
