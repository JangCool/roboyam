package com.community.auth.service;

import com.community.mail.service.MailDispatcher;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 회원 계정 흐름의 필수 안내 메일(비밀번호 재설정 링크·소셜 전용 안내·이메일 변경 링크·이메일 변경 알림)을 <b>요청 스레드가 아닌 작은 스레드풀</b>에서 보낸다.
 *
 * <ul>
 *   <li><b>비밀번호 찾기</b>는 로그인 전 공개 API 라 계정 유무에 따라 하는 일(메일 서버 접속·전송, 토큰 만들기)이 다르면 응답 시간이 계정 존재를 알린다 —
 *       그래서 요청은 일을 이 풀에 넘기고 바로 202 로 끝난다(관리자 앱 {@code PasswordResetMailSender} 와 같은 판단). 계정 조회·토큰 발급도 작업 쪽이 한다.</li>
 *   <li>이메일 변경 메일 두 통도 같은 풀로 보낸다 — 응답이 메일 서버(접속 5초·읽기 10초 제한)를 기다리지 않는다.</li>
 *   <li>대기열이 가득 차면 그 일은 버리고 경고만 남긴다(사용자는 60초 뒤 다시 요청한다 — 요청 스레드로 되돌리면 시간 차이가 생기므로 일부러 버린다).</li>
 * </ul>
 * 제목·본문은 알림 문구 엔진({@link NotificationMessageRenderer}, {@link RequiredMail})이 요청 때 정한 언어로 그린다. 메일 서버가 없으면 {@link MailDispatcher} 가 보내지 않고
 * 로그만 남긴다. 수신자 주소·토큰·예외 문구는 로그에 남기지 않는다(예외는 클래스 이름만).
 */
@Component
public class AccountMailSender {

    private static final Logger log = LoggerFactory.getLogger(AccountMailSender.class);

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 200;

    private final MailDispatcher mail;
    private final NotificationMessageRenderer renderer;
    private final ThreadPoolExecutor executor;

    public AccountMailSender(MailDispatcher mail, NotificationMessageRenderer renderer) {
        this.mail = mail;
        this.renderer = renderer;
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "member-account-mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 일을 풀에 넘긴다. 받지 못했으면(대기열 가득) false — 부르는 쪽은 응답을 바꾸지 않는다.
     *
     * @param what 로그에 남길 일의 이름(값 없음)
     */
    public boolean submit(String what, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("회원 계정 메일 작업이 실패했다 작업={} 원인={}", what, e.getClass().getSimpleName());
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("회원 계정 메일 대기열이 가득 차 이 일은 하지 않았다 작업={}", what);
            return false;
        }
    }

    /** 메일 한 통을 풀에서 보낸다 */
    public void sendLater(RequiredMail kind, String to, Locale locale, Map<String, ?> variables, long userId) {
        submit(kind.code(), () -> sendNow(kind, to, locale, variables, userId));
    }

    /**
     * 메일 한 통을 지금 스레드에서 보낸다 — 풀의 작업 안에서만 부른다. 실패는 로그만 남긴다.
     *
     * @param variables 문구 변수(링크·닉네임 등). {@code siteName}·{@code siteUrl} 은 엔진이 채운다
     */
    public void sendNow(RequiredMail kind, String to, Locale locale, Map<String, ?> variables, long userId) {
        try {
            RenderedEmail content = renderer.renderEmail(MessageRenderRequest.requiredMail(kind).locale(locale).variables(variables).build());
            if (mail.sendTagged(to, content.subject(), content.untaggedSubject(), content.html())) {
                log.info("회원 계정 메일을 보냈다 종류={} userId={} 문구={}", kind.code(), userId, content.messageId());
            }
        } catch (RuntimeException e) {
            // 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.error("회원 계정 메일 발송에 실패했다 종류={} userId={} 원인={}", kind.code(), userId, e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
