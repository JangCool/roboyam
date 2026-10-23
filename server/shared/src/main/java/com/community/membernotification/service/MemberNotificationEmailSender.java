package com.community.membernotification.service;

import com.community.mail.service.MailDispatcher;
import com.community.membernotification.dto.MemberEmailRequested;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.repository.master.MemberNotificationMailQueries;
import com.community.membernotification.repository.master.MemberNotificationMailQueries.QuietHours;
import com.community.membernotification.repository.master.MemberNotificationMailQueries.Recipient;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
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
 * 회원 알림 메일을 <b>업무 트랜잭션이 커밋된 뒤, 요청과 다른 스레드에서</b> 보낸다(관리자 알림 메일 {@code NotificationEmailSender} 와 같은 방식) — 롤백되면 메일도 없고, 메일 서버가
 * 느리거나 죽어도 댓글쓰기·신고·포인트 조정은 그대로다. 작은 스레드풀과 대기열이 가득 차면 그 메일은 버리고 경고만 남긴다(앱 알림은 이미 알림함에 있다).
 *
 * <ul>
 *   <li><b>받는 사람</b>: 이메일이 있고 <b>인증을 마친</b> 회원만. 탈퇴·삭제 회원은 받지 않는다. <b>정지 회원</b>({@code SUSPENDED}·{@code BANNED})은 활동 알림(댓글·답글)을 받지 않고,
 *       계정 알림(포인트 회수 · 신고로 숨김 — 회원이 끌 수 없게 잠긴 이벤트)은 받는다.</li>
 *   <li><b>조용한 시간</b>: 게시판 기본 설정 「알림」의 이메일 조용한 시간이 켜져 있고 지금(사이트 표시 시간대)이 그 안이면 <b>보내지 않는다</b> — 미뤄 보낼 메일 대기열이 아직 없다
 *       (앱 알림은 남는다, 계약 가정). 시작 = 끝이면 조용한 시간이 없는 것으로 본다. 자정을 넘는 구간(예: 22:00~08:00)을 쓸 수 있다.</li>
 *   <li><b>언어</b>: 회원 표시 설정의 언어({@code member_display_preferences}), 없으면 사이트 표시 기본 언어.</li>
 *   <li><b>문구</b>: 알림 문구 엔진 — 그 회원 이벤트 이메일의 쓸 문구·규칙·메일 템플릿(없으면 기본 문구). 메일의 {@code title}·{@code body} 는 같은 언어로 그린 앱 알림 문구.
 *       링크는 사이트 공개 주소 + 글 경로(볼 글이 없거나 공개 주소가 없으면 버튼 없이).</li>
 *   <li>이메일 주소·메일 내용·예외 문구를 로그에 남기지 않는다(예외는 클래스 이름만).</li>
 * </ul>
 */
@Component
public class MemberNotificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(MemberNotificationEmailSender.class);

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 500;

    private final MailDispatcher mail;
    private final MemberNotificationMailQueries queries;
    private final MemberNotificationMessages texts;
    private final NotificationMessageRenderer renderer;
    private final Messages messages;
    private final ThreadPoolExecutor executor;

    public MemberNotificationEmailSender(MailDispatcher mail, MemberNotificationMailQueries queries, MemberNotificationMessages texts,
                                         NotificationMessageRenderer renderer, Messages messages) {
        this.mail = mail;
        this.queries = queries;
        this.texts = texts;
        this.renderer = renderer;
        this.messages = messages;
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "member-notification-mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(MemberEmailRequested event) {
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            log.warn("회원 알림 메일 대기열이 가득 차 이 메일은 보내지 않았다 type={}", event.facts().type());
        }
    }

    private void send(MemberEmailRequested event) {
        MemberEvent memberEvent = MemberEvent.of(event.facts().type());
        try {
            Optional<Recipient> found = queries.recipient(event.userId());
            if (found.isEmpty()) {
                return;
            }
            Recipient recipient = found.get();
            if (recipient.email() == null || recipient.email().isBlank() || !recipient.verified() || "WITHDRAWN".equals(recipient.status())) {
                log.debug("이메일 인증을 마치지 않았거나 받을 수 없는 회원이라 회원 알림 메일을 보내지 않는다 userId={} event={}", event.userId(), memberEvent);
                return;
            }
            boolean suspended = "SUSPENDED".equals(recipient.status()) || "BANNED".equals(recipient.status());
            if (suspended && !memberEvent.locked()) {
                log.debug("정지 회원이라 활동 알림 메일을 보내지 않는다 userId={} event={}", event.userId(), memberEvent);
                return;
            }
            ZoneId zone = renderer.site().zone();
            if (inQuietHours(queries.quietHours().orElse(null), Instant.now(), zone)) {
                // 미뤄 보낼 메일 대기열(mail_outbox)이 아직 없다 — 이 메일은 보내지 않는다(앱 알림은 남는다)
                log.info("이메일 조용한 시간이라 회원 알림 메일을 보내지 않았다 userId={} event={}", event.userId(), memberEvent);
                return;
            }

            MemberNotificationMessages.Facts facts = withActorName(event, recipient.language());
            String siteUrl = texts.siteUrl();
            String link = event.postId() == null || siteUrl == null ? null : siteUrl + "/posts/" + event.postId();
            RenderedEmail content = texts.email(facts, recipient.language(), recipient.nickname(), link);
            if (mail.sendTagged(recipient.email(), content.subject(), content.untaggedSubject(), content.html())) {
                log.info("회원 알림 메일을 보냈다 userId={} event={} 문구={}", event.userId(), memberEvent, content.messageId());
            }
        } catch (RuntimeException e) {
            // 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.error("회원 알림 메일 발송에 실패했다 userId={} event={} 원인={}", event.userId(), memberEvent, e.getClass().getSimpleName());
        }
    }

    /** 일으킨 회원의 지금 닉네임(없으면 받는 사람 언어의 「탈퇴한 회원」) · 비회원은 그때 이름 */
    private MemberNotificationMessages.Facts withActorName(MemberEmailRequested event, String language) {
        MemberNotificationMessages.Facts facts = event.facts();
        if (event.actorUserId() == null) {
            return facts;
        }
        String name = queries.nickname(event.actorUserId())
                .orElseGet(() -> messages.get(new MessageRef("member.notification.actor.withdrawn"), texts.localeOf(language)));
        return new MemberNotificationMessages.Facts(facts.type(), name, facts.postTitle(), facts.excerpt(), facts.groupCount(), facts.amount(), facts.pointsKind(),
                facts.note(), facts.temporary(), facts.snapshot());
    }

    /** 지금이 조용한 시간 안인가 — 꺼져 있거나 값을 읽을 수 없거나 시작 = 끝이면 false. 끝 시각은 들지 않는다 */
    static boolean inQuietHours(QuietHours quiet, Instant now, ZoneId zone) {
        if (quiet == null || !quiet.enabled()) {
            return false;
        }
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(quiet.start());
            end = LocalTime.parse(quiet.end());
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
        if (start.equals(end)) {
            return false;
        }
        LocalTime at = now.atZone(zone).toLocalTime();
        return start.isBefore(end) ? !at.isBefore(start) && at.isBefore(end) : !at.isBefore(start) || at.isBefore(end);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
