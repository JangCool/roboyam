package com.community.mail.outbox.service;

import com.community.mail.outbox.dto.OutboxDelivery;
import java.util.Map;

/**
 * 대기열 메일 한 통을 실제로 그려 보낸다 — 워커({@link MailOutboxWorker})가 트랜잭션 밖에서 부른다. 기본 구현은 {@link RequiredMailOutboxSender}.
 * 보내다 실패하면(메일 서버 연결·인증·전송) 던진다 — 워커가 재시도로 돌린다.
 */
public interface OutboxMailSender {

    /**
     * @param kind      대기열의 {@code mail_kind}(필수 안내 메일 코드)
     * @param variables 대기열에 넣은 문구 변수
     */
    OutboxDelivery send(long userId, String kind, Map<String, Object> variables);
}
