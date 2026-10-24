package com.community.auth.dto.response;

/**
 * {@code POST /api/auth/email/verify} 결과.
 *
 * @param verified 늘 true(쓸 수 없는 링크는 오류로 끝난다)
 * @param changed  이메일 변경 링크였고 로그인 이메일이 새 주소로 바뀌었다(인증 링크면 false)
 */
public record EmailVerifyResponse(boolean verified, boolean changed) {
}
