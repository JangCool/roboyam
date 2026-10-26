package com.community.point.dto.response;

/**
 * 본인인증 보너스(가입 포인트, 커머스 1-3) — 본인인증을 마치면 계정마다 평생 한 번.
 *
 * @param points   줄 포인트(정책 {@code signupPoints}, 0 = 주지 않음)
 * @param received 이미 받았나
 */
public record SignupPointRuleView(int points, boolean received) {
}
