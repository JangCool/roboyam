package com.community.notification.service;

import com.community.notification.channel.ChannelMessageFormatter;
import com.community.notification.channel.ChannelPostResult;
import com.community.notification.channel.NotificationChannelPoster;
import com.community.notification.dto.NotificationChannelPostRequested;
import com.community.notification.entity.NotificationChannelEntity;
import com.community.notification.repository.master.NotificationChannelRepository;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.message.RequestLocale;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
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
 * 알림을 Slack · Mattermost 도구로 <b>업무 트랜잭션이 커밋된 뒤, 요청과 다른 스레드에서</b> 한 번 올린다(이메일 발송기와 같은 방식) — 롤백되면 게시도 없고,
 * 외부 서비스가 느리거나 죽어도 요청이 늦어지지 않으며 업무·서랍 알림은 정상이다. 작은 스레드풀과 대기열이 가득 차면 그 게시는 버리고 경고만 남긴다.
 *
 * <ul>
 *   <li>이벤트당 도구마다 <b>한 번</b>(받는 사람 수와 상관없다). 글은 사이트 표시 기본 언어(없으면 ko)로 풀고 사용자 입력은 무력화한다({@link ChannelMessageFormatter}).</li>
 *   <li>도구별 실패는 서로 영향을 주지 않는다. 결과는 도구 id·사유 코드만 로그에 남긴다 — 외부 응답 본문·주소·토큰·예외 문구는 남기지 않는다.</li>
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
    private final Messages messages;
    private final String adminUrl;
    private final ThreadPoolExecutor executor;

    public NotificationChannelPostSender(NotificationChannelRepository channels, NotificationChannelPoster poster, SiteSettingRepository siteSettings, Messages messages,
                                         @Value("${roboyam.admin-url:}") String adminUrl) {
        this.channels = channels;
        this.poster = poster;
        this.siteSettings = siteSettings;
        this.messages = messages;
        this.adminUrl = normalizeAdminUrl(adminUrl);
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
            SiteSettingEntity site = siteSettings.findById(SiteSettingEntity.ROW_ID).orElse(null);
            String language = site == null ? null : site.getDisplayLanguage();
            Locale locale = RequestLocale.normalize(language == null ? null : Locale.forLanguageTag(language));
            String title = NotificationArgs.text(event.titleKey(), event.argsJson(), messages, locale);
            String body = NotificationArgs.text(event.bodyKey(), event.argsJson(), messages, locale);
            String link = event.linkPath() == null || adminUrl == null ? null : adminUrl + event.linkPath();
            String linkLabel = messages.get(new MessageRef("notification.channel.post.open"), locale);

            for (NotificationChannelEntity channel : channels.findAllById(event.channelIds())) {
                if (!channel.isEnabled()) {
                    continue;
                }
                String text = ChannelMessageFormatter.format(channel.getChannelType(), event.severity(), title == null ? "" : title, body, link, linkLabel);
                ChannelPostResult result = poster.post(channel, text);
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

    /** 앞뒤 공백을 자른다. {@code http(s)://} 로 시작하지 않으면 없는 것으로 본다. 끝의 {@code /} 는 뗀다 */
    private static String normalizeAdminUrl(String value) {
        String url = value == null ? "" : value.strip();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
