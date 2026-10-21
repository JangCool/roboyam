package com.community.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * roboyam.mail.* — 메일 서버 연결 시간 제한. 설치 마법사의 연결 시험이 쓴다.
 *
 * @param connectTimeoutMillis SMTP 서버에 접속하는 데 기다리는 시간(5초)
 * @param ioTimeoutMillis      접속한 뒤 응답을 읽고 보내는 데 기다리는 시간(10초)
 */
@ConfigurationProperties(prefix = "roboyam.mail")
public record MailSendProperties(int connectTimeoutMillis, int ioTimeoutMillis) {
}
