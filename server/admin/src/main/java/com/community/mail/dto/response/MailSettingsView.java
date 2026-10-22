package com.community.mail.dto.response;

import java.time.Instant;

/**
 * 메일 서버 설정 — 설치 마법사가 넣은 행({@code mail_settings}, 하나뿐)을 그대로 보여 준다. <b>비밀번호는 값이 아니라 「입력됨」({@code passwordSet})만</b> 나간다.
 * 설정이 없으면(설치 때 건너뜀) {@code configured=false} 이고 나머지 값 칸은 null 이다.
 *
 * @param security            {@code NONE} · {@code STARTTLS} · {@code SSL}
 * @param username            비어 있으면(null) 인증 없이 보낸다
 * @param passwordSet         저장된 비밀번호가 있는가
 * @param cryptoKeyConfigured 서버에 암호화 키({@code ROBOYAM_CRYPTO_KEY})가 있는가 — 없으면 비밀번호를 새로 저장하는 요청이 503 {@code CRYPTO_KEY_MISSING} 이고 인증 메일도 나가지 않는다
 * @param updatedAt           마지막 저장 시각(설정이 없으면 null)
 * @param updatedByName       마지막으로 저장한 운영자 이름(지워진 운영자는 대체 문구, 설정이 없으면 null)
 */
public record MailSettingsView(boolean configured, String host, Integer port, String security, String username, boolean passwordSet, String fromAddress,
                               boolean cryptoKeyConfigured, Instant updatedAt, String updatedByName) {

    public static MailSettingsView notConfigured(boolean cryptoKeyConfigured) {
        return new MailSettingsView(false, null, null, null, null, false, null, cryptoKeyConfigured, null, null);
    }
}
