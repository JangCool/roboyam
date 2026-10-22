package com.community.mail.dto.response;

/**
 * 시험 발송 결과 — 실패도 오류가 아니라 200 {@code succeeded=false} 다(하모니카3 와 같은 모양).
 *
 * @param succeeded 메일 서버가 메일을 받아 갔는가
 * @param reason    실패 원인 코드(성공이면 null): {@code NOT_CONFIGURED} · {@code CRYPTO_KEY_MISSING} · {@code CONNECT_FAILED} · {@code AUTH_FAILED} ·
 *                  {@code TLS_FAILED} · {@code TIMEOUT} · {@code REJECTED} · {@code FAILED}
 * @param detail    원인 코드를 요청 언어로 푼 짧은 문구(성공이면 null). SMTP 응답 원문·자격 증명·서버 주소를 싣지 않는다
 */
public record MailTestResultView(boolean succeeded, String reason, String detail) {
}
