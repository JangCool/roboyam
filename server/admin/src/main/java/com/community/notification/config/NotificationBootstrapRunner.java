package com.community.notification.config;

import com.community.membernotification.entity.MemberEvent;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationCatalogSynchronizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 ① 알림 이벤트 정의와 메시지 번들이 맞는지 확인하고 ② 이벤트 카탈로그를 {@code notification_events} 표와 맞춘다({@link NotificationCatalogSynchronizer}).
 *
 * <p><b>번들 확인</b>: 모든 이벤트가 두 언어(ko·en) 번들에 {@code notification.event.<코드>.name|description|recipients|title|body} 다섯 키를 가져야 한다. 빠지면 사용자에게는 일반 오류 문구가 보이므로
 * 어떤 키가 어느 언어에서 빠졌는지 ERROR 로 모두 남기고, {@code local}·{@code dev} 프로파일에서는 <b>기동을 멈춘다</b>(개발 중에 바로 알아채게 — 운영은 같은 빌드이므로 거의 일어나지 않지만 그래도 멈추지는 않고 ERROR 만 남긴다).
 * 이름·제목은 비어 있으면 안 된다(본문은 비어도 된다 — 본문 없는 알림).
 */
@Component
public class NotificationBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationBootstrapRunner.class);

    private final NotificationCatalogSynchronizer synchronizer;
    private final Environment environment;
    private final String basename;

    public NotificationBootstrapRunner(NotificationCatalogSynchronizer synchronizer, Environment environment,
                                       @Value("${spring.messages.basename:messages/messages}") String basename) {
        this.synchronizer = synchronizer;
        this.environment = environment;
        this.basename = basename;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = missingBundleKeys();
        if (!missing.isEmpty()) {
            log.error("알림 이벤트의 문구가 메시지 번들에 없다 — 번들(messages_ko·en.properties)에 추가해 주세요: {}", missing);
            if (environment.acceptsProfiles(Profiles.of("local", "dev"))) {
                throw new IllegalStateException("알림 이벤트 문구가 번들에 없어 기동을 멈춘다: " + missing);
            }
        }
        synchronizer.sync();
    }

    /** 빠진(또는 비어 있어선 안 되는데 빈) 키 — {@code 언어:키} 형태 */
    List<String> missingBundleKeys() {
        List<String> missing = new ArrayList<>();
        for (String language : List.of("ko", "en")) {
            Properties bundle;
            try {
                bundle = PropertiesLoaderUtils.loadProperties(new EncodedResource(new ClassPathResource(basename + "_" + language + ".properties"), StandardCharsets.UTF_8));
            } catch (IOException e) {
                missing.add(language + ":(번들을 읽을 수 없다)");
                continue;
            }
            for (AdminNotificationEvent event : AdminNotificationEvent.values()) {
                for (String key : event.bundleKeys()) {
                    String value = bundle.getProperty(key);
                    boolean mustHaveText = !key.equals(event.bodyKey());
                    if (value == null || (mustHaveText && value.isBlank())) {
                        missing.add(language + ":" + key);
                    }
                }
            }
            // 회원 이벤트(관리 화면의 이름·설명·받는 사람) — 셋 다 비어 있으면 안 된다
            for (MemberEvent event : MemberEvent.values()) {
                for (String key : event.bundleKeys()) {
                    String value = bundle.getProperty(key);
                    if (value == null || value.isBlank()) {
                        missing.add(language + ":" + key);
                    }
                }
            }
        }
        return missing;
    }
}
