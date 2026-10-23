package com.community.notification.service;

import com.community.notification.channel.ChannelPostResult;
import com.community.notification.channel.NotificationChannelPoster;
import com.community.notification.dto.NotificationChannelPostRequested;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationChannelEntity;
import com.community.notification.entity.NotificationChannelType;
import com.community.notification.repository.master.NotificationChannelRepository;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedChat;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.message.RequestLocale;
import jakarta.annotation.PreDestroy;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
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
 * 알림을 Slack · Mattermost 도구로 <b>업무 트랜잭션이 커밋된 뒤, 요청과 다른 스레드에서</b> 한 번 올린다(이메일 발송기와 같은 방식) — 롤백되면 게시도 없고,
 * 외부 서비스가 느리거나 죽어도 요청이 늦어지지 않으며 업무·서랍 알림은 정상이다. 작은 스레드풀과 대기열이 가득 차면 그 게시는 버리고 경고만 남긴다.
 *
 * <ul>
 *   <li>이벤트당 도구마다 <b>한 번</b>(받는 사람 수와 상관없다). 메시지 카드는 알림 문구 엔진이 <b>도구 종류(Slack · Mattermost)별로 한 번</b> 그린다 — 그 이벤트·채널의 쓸 문구·규칙·
 *       Slack/Mattermost 템플릿, 사이트 표시 기본 언어(없으면 ko). 변수 값의 멘션·표기는 엔진이 무력화한다.</li>
 *   <li>올리기는 {@link NotificationChannelPoster#post(NotificationChannelEntity, RenderedChat)} — 도구별 실패는 서로 영향을 주지 않는다. 결과는 도구 id·사유 코드만 로그에 남긴다
 *       (외부 응답 본문·주소·토큰·예외 문구는 남기지 않는다).</li>
 *   <li>발행 뒤에 지워지거나 꺼진 도구는 건너뛴다.</li>
 * </ul>
 */
@Component
public class NotificationChannelPostSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelPostSender.class);

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 200;

    private final NotificationChannelRepository channels;
    private final NotificationChannelPoster poster;
    private final SiteSettingRepository siteSettings;
    private final AdminNotificationMessages texts;
    private final NotificationMessageRenderer renderer;
    private final ThreadPoolExecutor executor;

    public NotificationChannelPostSender(NotificationChannelRepository channels, NotificationChannelPoster poster, SiteSettingRepository siteSettings,
                                         AdminNotificationMessages texts, NotificationMessageRenderer renderer) {
        this.channels = channels;
        this.poster = poster;
        this.siteSettings = siteSettings;
        this.texts = texts;
        this.renderer = renderer;
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "admin-notification-channel");
            thread.setDaemon(true);
            return thread;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(NotificationChannelPostRequested event) {
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            log.warn("공유 채널 게시 대기열이 가득 차 이 알림의 게시는 하지 않았다 event={}", event.eventCode());
        }
    }

    private void send(NotificationChannelPostRequested event) {
        try {
            AdminNotificationEvent notificationEvent = AdminNotificationEvent.ofCode(event.eventCode()).orElse(null);
            if (notificationEvent == null) {
                log.warn("코드에 없는 알림 이벤트라 공유 채널에 올리지 않았다 event={}", event.eventCode());
                return;
            }
            SiteSettingEntity site = siteSettings.findById(SiteSettingEntity.ROW_ID).orElse(null);
            String language = site == null ? null : site.getDisplayLanguage();
            Locale locale = RequestLocale.normalize(language == null ? null : Locale.forLanguageTag(language));
            String adminUrl = renderer.adminUrl();
            String link = event.linkPath() == null || adminUrl == null ? null : adminUrl + event.linkPath();

            Map<MessageChannel, RenderedChat> cards = new EnumMap<>(MessageChannel.class);
            for (NotificationChannelEntity channel : channels.findAllById(event.channelIds())) {
                if (!channel.isEnabled()) {
                    continue;
                }
                MessageChannel tool = channel.getChannelType() == NotificationChannelType.SLACK ? MessageChannel.SLACK : MessageChannel.MATTERMOST;
                RenderedChat card;
                try {
                    card = cards.computeIfAbsent(tool, t -> renderer.renderChat(texts.request(notificationEvent, t, event.argsJson(), locale, event.conditions())
                            .variable(MessageVariable.LINK, link)
                            .severity(event.severity())
                            .occurredAt(event.occurredAt())
                            .build()));
                } catch (RuntimeException e) {
                    log.error("공유 채널 글을 그리지 못했다 event={} channelId={} 원인={}", event.eventCode(), channel.getId(), e.getClass().getSimpleName());
                    continue;
                }
                ChannelPostResult result = poster.post(channel, card);
                if (result.ok()) {
                    log.info("공유 채널에 알림을 올렸다 event={} channelId={}", event.eventCode(), channel.getId());
                } else {
                    log.warn("공유 채널에 알림을 올리지 못했다 event={} channelId={} 사유={}", event.eventCode(), channel.getId(), result.code());
                }
            }
        } catch (RuntimeException e) {
            log.error("공유 채널 게시를 준비하지 못했다 event={} 원인={}", event.eventCode(), e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
