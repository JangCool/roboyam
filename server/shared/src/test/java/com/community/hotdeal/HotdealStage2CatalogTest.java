package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventLockReason;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.report.entity.ReportReasons;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * 회원 핫딜 서버 2단계가 더한 카탈로그 줄(회원 알림 5종 · 관리자 알림 · 배치 4개 · 오류 코드 · 신고 사유)이 있고 <b>두 언어</b>에 문구가 있다(notification.md · exception.md · _backend-core 「배치」).
 */
class HotdealStage2CatalogTest {

    private static final List<String> TYPES = List.of("DEAL_FLAGGED", "DEAL_AUTO_ENDED", "DEAL_MANAGER_ENDED", "DEAL_EXPIRED", "DEAL_REOPENED_FIX_ENDS");
    private static final List<BatchCatalog> BATCHES = List.of(BatchCatalog.HOTDEAL_AUTO_END, BatchCatalog.HOTDEAL_EXPIRY_NOTICE, BatchCatalog.HOTDEAL_CLEANUP,
            BatchCatalog.HOTDEAL_SUSPICION);

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = HotdealStage2CatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static List<String> requiredKeys() {
        List<String> keys = new ArrayList<>();
        for (String type : TYPES) {
            keys.addAll(MemberEvent.valueOf(type).bundleKeys());
            keys.add("member.notification." + type + ".title");
            if (!"DEAL_MANAGER_ENDED".equals(type)) {
                keys.add("member.notification." + type + ".body");
            }
        }
        keys.addAll(List.of("notificationTemplate.var.dealEndReason.desc", "notificationTemplate.var.dealEndReason.sample"));
        for (String suffix : List.of("name", "description", "recipients", "title", "body")) {
            keys.add("notification.event.HOTDEAL_KEEP_LIMIT." + suffix);
            keys.add("notification.event.HOTDEAL_SUSPECTED_PROMO." + suffix);
        }
        for (BatchCatalog batch : BATCHES) {
            keys.add(batch.nameKey());
            keys.add(batch.descriptionKey());
        }
        keys.addAll(List.of("batch.result.hotdealAutoEnd", "batch.result.hotdealAutoEnd.none", "batch.result.hotdealAutoEnd.off", "batch.result.hotdealExpiryNotice",
                "batch.result.hotdealExpiryNotice.none", "batch.result.hotdealExpiryNotice.off", "batch.result.hotdealCleanup", "batch.result.hotdealCleanup.none",
                "batch.result.hotdealSuspicion", "batch.result.hotdealSuspicion.none", "batch.job.hotdeal-cleanup.step.og", "batch.job.hotdeal-cleanup.step.revisions",
                "batch.job.hotdeal-suspicion.step.member", "batch.job.hotdeal-suspicion.step.store", "batch.target.POST_DEAL"));
        for (String code : List.of("DEAL_END_REQUEST_OWN", "DEAL_END_REQUEST_NOT_ALLOWED", "DEAL_END_REQUEST_EXISTS", "DEAL_END_REQUEST_DAILY_LIMIT",
                "DEAL_END_REQUEST_AUTHOR_LIMIT", "DEAL_NOT_FLAGGED", "DEAL_KEEP_LIMIT", "DEAL_REOPEN_EXPIRED", "DEAL_REOPEN_USED", "DEAL_NOT_FOUND", "DEAL_ENDED.action")) {
            keys.add("error." + code);
        }
        keys.addAll(List.of("hotdeal.endRequest.reason", "board.report.reason.dealOnly", "homeLayout.gadget.config.hotDealSort", "homeLayout.gadget.config.hotDealBoards",
                "point.reason.DEAL_END_REQUEST"));
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
        assertThat(bundle("ko").getProperty("point.reason.DEAL_END_REQUEST")).isEqualTo("핫딜 종료 알림 보상");
    }

    @Test
    void 회원_알림_5종은_실제_이벤트이고_끌_수_없는_처리_알림() {
        for (String type : TYPES) {
            MemberEvent event = MemberEvent.of(MemberNotificationType.valueOf(type));
            assertThat(event.featureReady()).isTrue();
            assertThat(event.lockReason()).isEqualTo(MemberEventLockReason.ACTION);
        }
    }

    @Test
    void 관리자_알림_링크와_예약_해제() {
        assertThat(AdminNotificationEvent.HOTDEAL_SUSPECTED_PROMO.featureReady()).isTrue();
        assertThat(AdminNotificationEvent.HOTDEAL_SUSPECTED_PROMO.linkPath()).isEqualTo("/hotdeals/suspicions?suspicion={id}");
        assertThat(AdminNotificationEvent.HOTDEAL_KEEP_LIMIT.linkPath()).isEqualTo("/hotdeals?view=ALL&deal={postId}");
        assertThat(AdminNotificationEvent.HOTDEAL_LINK_HOST_CHANGED.linkPath()).isEqualTo("/hotdeals?view=ALL&deal={postId}");
    }

    @Test
    void 배치는_Java_배치이고_핫딜_사유는_핫딜_글에서만() {
        BATCHES.forEach(b -> assertThat(b.isJava()).isTrue());
        assertThat(ReportReasons.forPost(false)).doesNotContain(ReportReasons.DEAL_FALSE_PRICE, ReportReasons.DEAL_HIDDEN_AD);
        assertThat(ReportReasons.forPost(true)).contains(ReportReasons.DEAL_FALSE_PRICE, ReportReasons.DEAL_HIDDEN_AD);
        assertThat(ReportReasons.forTarget("COMMENT")).doesNotContain(ReportReasons.DEAL_HIDDEN_AD);
        assertThat(ReportReasons.NOT_HIDE_COUNTED).containsExactly(ReportReasons.DEAL_FALSE_PRICE);
    }
}
