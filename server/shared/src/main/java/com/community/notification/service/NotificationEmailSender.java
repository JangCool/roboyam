package com.community.notification.service;

import com.community.mail.service.MailDispatcher;
import com.community.notification.dto.NotificationEmailRequested;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.entity.OperatorPreferenceEntity;
import com.community.operator.repository.master.OperatorPreferenceRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.message.RequestLocale;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 이메일을 <b>업무 트랜잭션이 커밋된 뒤, 요청과 다른 스레드에서</b> 보낸다 — 롤백되면 메일도 없고, 받을 사람이 많거나 메일 서버가 느려도 요청이 늦어지지 않는다
 * (접속 5초·읽기 10초 제한은 {@code MailSenderFactory}). 작은 스레드풀과 대기열이 가득 차면 그 메일은 버리고 경고만 남긴다(알림은 이미 서랍에 있다).
 *
 * <ul>
 *   <li>메일 서버가 설정돼 있지 않으면 보내지 않고 로그만 남긴다({@code MailDispatcher}). 한 통이 실패해도 다른 사람에게는 계속 보낸다.</li>
 *   <li>받는 사람마다 <b>자기 표시 언어</b>로 푼다 — 내 표시 형식을 쓰는 운영자는 그 언어({@code operator_preferences.display_language}), 아니면 사이트 표시 기본 언어, 그것도 없으면 {@code ko}.</li>
 *   <li>이메일 주소·메일 내용·예외 문구를 로그에 남기지 않는다(예외는 클래스 이름만).</li>
 * </ul>
 */
@Component
public class NotificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailSender.class);

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 200;

    private final MailDispatcher mail;
    private final OperatorRepository operators;
    private final OperatorPreferenceRepository preferences;
    private final SiteSettingRepository siteSettings;
    private final Messages messages;
    private final String adminUrl;
    private final ThreadPoolExecutor executor;

    public NotificationEmailSender(MailDispatcher mail, OperatorRepository operators, OperatorPreferenceRepository preferences, SiteSettingRepository siteSettings,
                                   Messages messages, @Value("${roboyam.admin-url:}") String adminUrl) {
        this.mail = mail;
        this.operators = operators;
        this.preferences = preferences;
        this.siteSettings = siteSettings;
        this.messages = messages;
        this.adminUrl = normalizeAdminUrl(adminUrl);
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "admin-notification-mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(NotificationEmailRequested event) {
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            log.warn("알림 메일 대기열이 가득 차 이 알림의 메일은 보내지 않았다 event={}", event.eventCode());
        }
    }

    private void send(NotificationEmailRequested event) {
        try {
            SiteSettingEntity site = siteSettings.findById(SiteSettingEntity.ROW_ID).orElse(null);
            String siteName = site == null ? null : site.getSiteName();
            String siteLanguage = site == null ? null : site.getDisplayLanguage();
            Map<Long, OperatorPreferenceEntity> prefs = new HashMap<>();
            preferences.findAllById(event.operatorIds()).forEach(pref -> prefs.put(pref.getOperatorId(), pref));

            int sent = 0;
            for (OperatorEntity operator : operators.findAllById(event.operatorIds())) {
                Locale locale = localeOf(prefs.get(operator.getId()), siteLanguage);
                String title = NotificationArgs.text(event.titleKey(), event.argsJson(), messages, locale);
                String body = NotificationArgs.text(event.bodyKey(), event.argsJson(), messages, locale);
                NotificationMailTemplate.Mail content = NotificationMailTemplate.build(messages, locale, siteName, title == null ? "" : title, body, adminUrl,
                        event.linkPath());
                try {
                    if (!mail.sendTagged(operator.getEmail(), content.subject(), content.untaggedSubject(), content.html())) {
                        return; // 메일 서버가 설정돼 있지 않다 — 나머지도 같다(이유는 MailDispatcher 가 로그로 남겼다)
                    }
                    sent++;
                } catch (RuntimeException e) {
                    // 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
                    log.error("알림 메일 발송에 실패했다 event={} 원인={}", event.eventCode(), e.getClass().getSimpleName());
                }
            }
            log.info("알림 메일을 보냈다 event={} {}통", event.eventCode(), sent);
        } catch (RuntimeException e) {
            log.error("알림 메일을 준비하지 못했다 event={} 원인={}", event.eventCode(), e.getClass().getSimpleName());
        }
    }

    /** 내 표시 형식을 쓰는 운영자는 그 언어, 아니면 사이트 표시 기본 언어, 그것도 없으면 ko */
    private static Locale localeOf(OperatorPreferenceEntity preference, String siteLanguage) {
        String language = preference != null && preference.isDisplayUse() && preference.getDisplayLanguage() != null ? preference.getDisplayLanguage() : siteLanguage;
        return RequestLocale.normalize(language == null ? null : Locale.forLanguageTag(language));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** 앞뒤 공백을 자른다. {@code http(s)://} 로 시작하지 않으면(비었거나 잘못된 값) 없는 것으로 본다. 끝의 {@code /} 는 뗀다 */
    private static String normalizeAdminUrl(String value) {
        String url = value == null ? "" : value.strip();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
