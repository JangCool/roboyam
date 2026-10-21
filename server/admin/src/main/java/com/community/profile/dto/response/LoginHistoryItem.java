package com.community.profile.dto.response;

import java.time.Instant;

/**
 * 내 로그인 이력 한 줄.
 *
 * @param id     감사 기록 ID(문자열)
 * @param at     시도한 시각
 * @param ip     접속한 IP. 모르면 null
 * @param agent  접속 환경 요약({@code Chrome / Windows / Desktop} 모양, 알아내지 못한 UA 는 정제한 원문 앞 80자) — User-Agent 를 남기기 전의 기록이면 null(화면은 「알 수 없음」으로 보인다)
 * @param result {@code OK} · {@code FAIL}
 * @param reason 실패 사유 코드 — 실패면 늘 {@code INVALID_CREDENTIALS}(계정 상태·비밀번호 틀림을 가르지 않는다). 성공이면 null
 */
public record LoginHistoryItem(String id, Instant at, String ip, String agent, String result, String reason) {
}
