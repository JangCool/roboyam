package com.community.adminauth.service;

import com.community.adminauth.config.AdminPasswordResetProperties;
import com.community.adminauth.dto.PasswordResetCodeIssued;
import com.community.mail.service.MailDispatcher;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 비밀번호 재설정 승인번호 메일을 <b>업무 트랜잭션이 커밋된 뒤, 요청과 다른 스레드에서</b> 보낸다.
 *
 * <p>다른 운영자 메일({@code OperatorMailSender})은 요청 스레드에서 보내지만 이 메일은 <b>로그인 전 공개 API</b> 의 것이다. 메일은 가입된 계정에만 가므로
 * 요청 스레드에서 보내면 응답 시간(메일 서버 접속·전송)이 계정 존재를 알린다 — 그래서 작은 스레드풀에 맡기고 요청은 바로 끝낸다. 대기열이 가득 차면 그 메일은 버리고 경고만 남긴다
 * (사용자는 쿨다운 뒤 다시 받는다 — 요청 스레드로 되돌려 보내면 시간 차이가 생기므로 일부러 버린다).
 *
 * <p>제목·본문은 알림 문구 엔진(필수 안내 메일 {@link RequiredMail#OPERATOR_RESET_CODE})이 그린다. 메일 언어는 요청 스레드에서 알림에 담아 온 값이다.
 * 메일 서버가 없거나 발송에 실패하면 로그만 남긴다(수신자·승인번호·예외 문구는 남기지 않는다 — 예외는 클래스 이름만).
 */
@Component
public class PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSender.class);

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 100;

    private final MailDispatcher mail;
    private final AdminPasswordResetProperties props;
    private final NotificationMessageRenderer renderer;
    private final ThreadPoolExecutor executor;

    public PasswordResetMailSender(MailDispatcher mail, AdminPasswordResetProperties props, NotificationMessageRenderer renderer) {
        this.mail = mail;
        this.props = props;
        this.renderer = renderer;
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "admin-reset-mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCodeIssued(PasswordResetCodeIssued event) {
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            log.warn("비밀번호 재설정 메일 대기열이 가득 차 이 메일은 보내지 않았다");
        }
    }

    private void send(PasswordResetCodeIssued event) {
        try {
            RenderedEmail content = renderer.renderEmail(MessageRenderRequest.requiredMail(RequiredMail.OPERATOR_RESET_CODE)
                    .locale(event.locale())
                    .variable("code", event.code())
                    .variable("validMinutes", props.codeValidMinutes())
                    .build());
            if (mail.sendTagged(event.email(), content.subject(), content.untaggedSubject(), content.html())) {
                log.info("운영자 비밀번호 재설정 승인번호 메일을 보냈다 문구={}", content.messageId());
            }
        } catch (RuntimeException e) {
            // 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.error("운영자 비밀번호 재설정 메일 발송에 실패했다 원인={}", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
