package com.community.withdrawal.service;

import com.community.mail.entity.MailSettingEntity;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.mail.service.MailDispatcher;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 탈퇴 메일(member-withdrawal-contract 「메일」) — 완료 안내는 <b>익명화 전에 읽은 주소</b>로 보낸다(메일 대기열은 보낼 때 {@code users.email} 을 읽어 NULL 이면 취소하므로 쓸 수 없다 —
 * 분석 문서 5-4). 트랜잭션이 커밋된 뒤 작은 스레드풀에서 보내고(요청·배치 스레드가 메일 서버를 기다리지 않는다), 실패는 로그만 남긴다(주소 없음).
 * 진행 상태 메일(신청 뒤 10일)도 같은 길로 보낸다 — 그때 주소는 아직 있지만 길을 하나로 둔다.
 */
@Component
public class WithdrawalMailSender {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalMailSender.class);
    private static final int THREADS = 1;
    private static final int QUEUE_CAPACITY = 500;

    private final MailDispatcher mail;
    private final NotificationMessageRenderer renderer;
    private final MailSettingRepository mailSettings;
    private final WithdrawalProperties properties;
    private final Messages messages;
    private final ThreadPoolExecutor executor;

    public WithdrawalMailSender(MailDispatcher mail, NotificationMessageRenderer renderer, MailSettingRepository mailSettings, WithdrawalProperties properties, Messages messages) {
        this.mail = mail;
        this.renderer = renderer;
        this.mailSettings = mailSettings;
        this.properties = properties;
        this.messages = messages;
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "member-withdrawal-mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 고객센터 메일 — 설정값, 비어 있으면 메일 서버의 보내는 주소, 그것도 없으면 빈 글자(사이트 설정에는 연락 메일 칸이 없다) */
    public String supportEmail() {
        if (!properties.supportEmail().isEmpty()) {
            return properties.supportEmail();
        }
        return mailSettings.findById(MailSettingEntity.ROW_ID).map(MailSettingEntity::getFromAddress).filter(from -> from != null && !from.isBlank()).orElse("");
    }

    /**
     * 완료 안내 — 지금 트랜잭션이 커밋된 뒤 보낸다(롤백되면 보내지 않는다).
     *
     * @param hasPaymentHistory 결제 이력이 있어 연락용 이메일을 분리 보관했는가 — 그때만 보관 안내 문장({@code contactNotice})을 넣는다
     */
    public void completedAfterCommit(long userId, String email, String language, String nickname, Instant rejoinUntil, boolean hasPaymentHistory) {
        if (email == null || email.isBlank()) {
            return;
        }
        Locale locale = locale(language);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nickname", nickname == null ? "" : nickname);
        variables.put("retentionYears", Integer.toString(properties.contactRetentionYears()));
        variables.put("rejoinDate", ServiceTime.dateOf(rejoinUntil).toString());
        variables.put("supportEmail", supportEmail());
        variables.put("contactNotice", hasPaymentHistory ? messages.get(new MessageRef("mail.withdrawalCompleted.contact", Integer.toString(properties.contactRetentionYears())), locale)
                : "");
        afterCommit(() -> sendNow(RequiredMail.MEMBER_WITHDRAWAL_COMPLETED, email, locale, variables, userId));
    }

    /** 진행 상태 — 남은 일·예상 완료일. 지금 트랜잭션이 커밋된 뒤 보낸다 */
    public void progressAfterCommit(long userId, String email, String language, String nickname, List<WithdrawalPending> pending, Instant expectedCompleteAt) {
        if (email == null || email.isBlank()) {
            return;
        }
        Locale locale = locale(language);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nickname", nickname == null ? "" : nickname);
        variables.put("pendingSummary", pending.stream().map(p -> messages.get(new MessageRef("withdrawal.pending." + p.code().name(), p.label()), locale))
                .collect(Collectors.joining(", ")));
        variables.put("expectedDate", expectedCompleteAt == null ? messages.get(new MessageRef("withdrawal.pending.unknownDate"), locale)
                : ServiceTime.dateOf(expectedCompleteAt).toString());
        variables.put("supportEmail", supportEmail());
        afterCommit(() -> sendNow(RequiredMail.MEMBER_WITHDRAWAL_PROGRESS, email, locale, variables, userId));
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(task);
                }
            });
        } else {
            submit(task);
        }
    }

    private void submit(Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("탈퇴 메일 작업이 실패했다 원인={}", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("탈퇴 메일 대기열이 가득 차 이 통은 보내지 않았다");
        }
    }

    /** 메일 한 통을 지금 스레드에서 보낸다 — 풀의 작업 안에서만 부른다(시험은 직접 불러도 된다). 보냈으면 true */
    boolean sendNow(RequiredMail kind, String to, Locale locale, Map<String, Object> variables, long userId) {
        try {
            RenderedEmail content = renderer.renderEmail(MessageRenderRequest.requiredMail(kind).locale(locale).variables(variables).build());
            boolean sent = mail.sendTagged(to, content.subject(), content.untaggedSubject(), content.html());
            if (sent) {
                log.info("탈퇴 메일을 보냈다 종류={} userId={} 문구={}", kind.code(), userId, content.messageId());
            }
            return sent;
        } catch (RuntimeException e) {
            log.error("탈퇴 메일 발송에 실패했다 종류={} userId={} 원인={}", kind.code(), userId, e.getClass().getSimpleName());
            return false;
        }
    }

    private Locale locale(String language) {
        String own = MessageLocales.of(language);
        if (own != null) {
            return MessageLocales.locale(own);
        }
        String site = MessageLocales.of(renderer.site().language());
        return MessageLocales.locale(site != null ? site : MessageLocales.KO);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
