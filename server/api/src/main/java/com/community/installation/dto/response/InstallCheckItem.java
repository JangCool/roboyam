package com.community.installation.dto.response;

/**
 * 설치 점검 항목 하나.
 *
 * @param key      {@code DATABASE} · {@code MIGRATIONS} · {@code EXTENSIONS} · {@code CRYPTO_KEY} · {@code DB_LOCALE}
 * @param detail   사람이 읽을 요약 — 접속 문자열·사용자 이름·경로 같은 내부 정보는 담지 않는다
 * @param blocking 실패하면 설치를 막는가(false 면 설치는 할 수 있다 — 암호화 키가 없으면 메일 비밀번호·소셜 키만 저장할 수 없다)
 */
public record InstallCheckItem(String key, boolean ok, String label, String detail, boolean blocking) {
}
