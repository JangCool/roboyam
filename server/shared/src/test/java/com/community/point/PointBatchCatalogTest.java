package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchCategory;
import com.community.batch.core.BatchRegistry;
import com.community.batch.entity.BatchSource;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventLockReason;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.point.batch.PointExpireJob;
import com.community.point.batch.PointExpiryNoticeJob;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

/**
 * 커머스 1-1 B 가 더한 카탈로그 줄(배치 3개 · 회원 알림 종류 · 회원 이벤트 · 알림 문구 대상)이 있고 <b>두 언어</b>에 문구가 있다(db-batch.md · notification.md).
 * 시험 DB 를 쓰지 않는다.
 */
class PointBatchCatalogTest {

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = PointBatchCatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static List<String> requiredKeys() {
        List<String> keys = new ArrayList<>();
        for (BatchCatalog batch : List.of(BatchCatalog.POINT_EXPIRE, BatchCatalog.POINT_EXPIRY_NOTICE, BatchCatalog.POINT_INVARIANT_CHECK)) {
            keys.add(batch.nameKey());
            keys.add(batch.descriptionKey());
        }
        for (BatchCatalog batch : List.of(BatchCatalog.POINT_EXPIRE, BatchCatalog.POINT_EXPIRY_NOTICE)) {
            keys.add("batch.job." + batch.key() + ".param.userId");
            keys.add("batch.job." + batch.key() + ".param.userId.help");
        }
        keys.addAll(List.of("batch.result.pointExpire", "batch.result.pointExpire.none", "batch.target." + PointExpireJob.TARGET,
                "batch.result.pointExpiryNotice", "batch.result.pointExpiryNotice.none", "batch.result.pointExpiryNotice.off", "batch.target." + PointExpiryNoticeJob.TARGET,
                "batch.result.pointInvariantCheck", "batch.result.pointInvariantCheck.none",
                "member.notification.POINTS_EXPIRING.title", "member.notification.POINTS_EXPIRING.body"));
        keys.addAll(MemberEvent.POINTS_EXPIRING.bundleKeys());
        for (String variable : List.of("expiringAmount", "expiringDate", "expiringDays")) {
            keys.add("notificationTemplate.var." + variable + ".desc");
            keys.add("notificationTemplate.var." + variable + ".sample");
        }
        return keys;
    }

    @Test
    void 새_문구는_두_언어에_모두_있다() throws IOException {
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String key : requiredKeys()) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void 모든_회원_이벤트의_이름_설명_받는_사람이_두_언어에_있다() throws IOException {
        // 관리자 서버 기동 검사(NotificationBootstrapRunner)와 같은 규칙 — 빠지면 서버가 뜨지 않는다
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (MemberEvent event : MemberEvent.values()) {
                for (String key : event.bundleKeys()) {
                    assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
                }
            }
        }
    }

    @Test
    void 소멸과_알림은_매일_도는_Java_배치_불변식은_DB_배치() {
        assertThat(BatchCatalog.POINT_EXPIRE.key()).isEqualTo("point-expire");
        assertThat(BatchCatalog.POINT_EXPIRE.source()).isEqualTo(BatchSource.JAVA);
        assertThat(BatchCatalog.POINT_EXPIRE.category()).isEqualTo(BatchCategory.MEMBER);
        assertThat(BatchCatalog.POINT_EXPIRE.defaultEnabled()).isTrue();
        CronExpression expire = CronExpression.parse(BatchCatalog.POINT_EXPIRE.defaultCron());
        assertThat(expire.next(java.time.LocalDateTime.of(2026, 10, 26, 0, 0))).as("매일 00:10").isEqualTo(java.time.LocalDateTime.of(2026, 10, 26, 0, 10));
        assertThat(expire.next(java.time.LocalDateTime.of(2026, 10, 26, 0, 10))).isEqualTo(java.time.LocalDateTime.of(2026, 10, 27, 0, 10));

        assertThat(BatchCatalog.POINT_EXPIRY_NOTICE.key()).isEqualTo("point-expiry-notice");
        assertThat(BatchCatalog.POINT_EXPIRY_NOTICE.source()).isEqualTo(BatchSource.JAVA);
        assertThat(BatchCatalog.POINT_EXPIRY_NOTICE.defaultEnabled()).isTrue();
        CronExpression notice = CronExpression.parse(BatchCatalog.POINT_EXPIRY_NOTICE.defaultCron());
        assertThat(notice.next(java.time.LocalDateTime.of(2026, 10, 26, 0, 0)).getHour()).as("낮 시간").isBetween(9, 18);

        assertThat(BatchCatalog.POINT_INVARIANT_CHECK.source()).isEqualTo(BatchSource.POSTGRESQL);
        assertThat(BatchCatalog.POINT_INVARIANT_CHECK.defaultCron()).isNull();
    }

    @Test
    void 배치_구현이_카탈로그와_짝이_맞고_재시작을_지원한다() {
        PointExpireJob expire = new PointExpireJob(null, null, new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(java.time.Clock.class));
        PointExpiryNoticeJob notice = new PointExpiryNoticeJob(null, null, null,
                new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(java.time.Clock.class));
        BatchRegistry registry = BatchRegistry.forJobs(List.of(expire, notice)); // 모양 검사(단계·주 단계·파라미터)
        assertThat(registry.jobOf(BatchCatalog.POINT_EXPIRE)).isSameAs(expire);
        assertThat(registry.jobOf(BatchCatalog.POINT_EXPIRY_NOTICE)).isSameAs(notice);
        assertThat(expire.resumable()).isTrue();
        assertThat(notice.resumable()).isTrue();
        assertThat(expire.params()).extracting(p -> p.name()).containsExactly("userId");
        assertThat(notice.params()).extracting(p -> p.name()).containsExactly("userId");
    }

    @Test
    void 소멸_예정_알림은_시스템_묶음의_계정_알림이고_문구_대상이_있다() {
        assertThat(MemberNotificationType.POINTS_EXPIRING.category()).isEqualTo("system");
        assertThat(MemberEvent.of(MemberNotificationType.POINTS_EXPIRING)).isEqualTo(MemberEvent.POINTS_EXPIRING);
        assertThat(MemberEvent.POINTS_EXPIRING.featureReady()).isTrue();
        assertThat(MemberEvent.POINTS_EXPIRING.lockReason()).isEqualTo(MemberEventLockReason.ACCOUNT);
        assertThat(MessageTargetCatalog.find(MessageTargetType.EVENT, "POINTS_EXPIRING")).get().satisfies(target -> {
            assertThat(target.inApp().titleKey()).isEqualTo("member.notification.POINTS_EXPIRING.title");
            assertThat(target.inApp().bodyKey()).isEqualTo("member.notification.POINTS_EXPIRING.body");
            assertThat(target.inApp().argNames()).containsExactly("amount", "date", "days");
        });
    }
}
