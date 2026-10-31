package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.batch.core.BatchCatalog;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.menu.service.AdminScreenRegistry;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.permission.entity.Permission;
import com.community.payment.dto.PaymentOrderType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * 사업자 신청 건이 서버 카탈로그에 더한 줄 — 권한(ad.manage·deal-order.refund 기능 켬), 메뉴 「사업자」의 신청 심사, 감사 행위 13개의 보관 분류와 대상 종류, 운영자 알림 두 종류,
 * 회원 알림 종류, 결제 주문 종류, Java 배치 셋, 새 문구가 두 언어에 다 있다.
 */
class BizApplicationAdminCatalogTest {

    @Test
    void 권한_ad_manage_와_deal_order_refund_는_기능이_생겼다() {
        assertThat(Permission.AD_MANAGE.featureReady()).isTrue();
        assertThat(Permission.DEAL_ORDER_REFUND.featureReady()).isTrue();
        assertThat(Permission.BUSINESS_READ.featureReady()).isTrue();
    }

    @Test
    void 메뉴_사업자_묶음에_신청_심사_화면이_있고_조회는_business_read() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("bizApplications").orElseThrow();
        assertThat(screen.path()).isEqualTo("/biz-applications");
        assertThat(screen.permission()).isEqualTo(Permission.BUSINESS_READ);
    }

    @Test
    void 감사_행위의_보관_분류_돈은_5년_계약서_열람은_개인정보_반려_전환_기한은_일반() {
        for (String action : List.of(AuditActions.BIZ_APP_APPROVE, AuditActions.BIZ_APP_PRICE_ADJUST, AuditActions.BIZ_APP_CONTRACT_UPLOAD, AuditActions.BIZ_DEPOSIT_CONFIRM,
                AuditActions.BIZ_APP_CONFIRM, AuditActions.BIZ_APP_CANCEL, AuditActions.BIZ_APP_REFUND_APPROVE, AuditActions.BIZ_APP_REFUND_DENY,
                AuditActions.BIZ_APP_REFUND_TRANSFER)) {
            assertThat(AuditRetention.classify(action)).as(action).isEqualTo(AuditRetentionClass.MONEY);
        }
        assertThat(AuditRetention.classify(AuditActions.BIZ_APP_CONTRACT_VIEW)).isEqualTo(AuditRetentionClass.PERSONAL_DATA);
        for (String action : List.of(AuditActions.BIZ_APP_REJECT, AuditActions.BIZ_APP_MANUAL_SWITCH, AuditActions.BIZ_APP_DEPOSIT_DEADLINE)) {
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.GENERAL);
        }
        assertThat(AuditLabels.TARGET_TYPES).contains("BIZ_APPLICATION");
    }

    @Test
    void 운영자_알림_심사_대기는_광고_처리_권한_환불_심사는_환불_권한_링크는_신청_건() {
        assertThat(AdminNotificationEvent.BIZ_APPLICATION_REVIEW_PENDING.recipientPermissions()).containsExactly(Permission.AD_MANAGE);
        assertThat(AdminNotificationEvent.BIZ_APPLICATION_REVIEW_PENDING.linkPath()).isEqualTo("/biz-applications?id={id}");
        assertThat(AdminNotificationEvent.BIZ_APPLICATION_REFUND_REQUESTED.recipientPermissions()).containsExactly(Permission.DEAL_ORDER_REFUND);
        assertThat(AdminNotificationEvent.BIZ_APPLICATION_REFUND_REQUESTED.featureReady()).isTrue();
    }

    @Test
    void 회원_알림_결제_주문_배치_종류() {
        assertThat(MemberNotificationType.valueOf("BIZ_APPLICATION")).isNotNull();
        assertThat(MemberEvent.BIZ_APPLICATION.type()).isEqualTo(MemberNotificationType.BIZ_APPLICATION);
        assertThat(PaymentOrderType.valueOf("BIZ_APPLICATION")).isNotNull();
        for (BatchCatalog job : List.of(BatchCatalog.BIZ_PAYMENT_EXPIRE, BatchCatalog.BIZ_PAYMENT_DUE_NOTICE, BatchCatalog.BIZ_SCHEDULE_NOTICE)) {
            assertThat(job.isJava()).as(job.key()).isTrue();
            assertThat(job.defaultEnabled()).isTrue();
        }
        assertThat(BatchCatalog.BIZ_PAYMENT_EXPIRE.defaultCron()).isEqualTo("0 */10 * * * *");
    }

    @Test
    void 새_문구는_두_언어에_모두_있다() throws IOException {
        Properties ko = bundle("messages/messages_ko.properties");
        Properties en = bundle("messages/messages_en.properties");
        List<String> keys = new ArrayList<>();
        for (String code : List.of("BIZ_APPLICATION_NOT_FOUND", "BIZ_APPLICATION_STATE_CHANGED", "BIZ_SLOT_FULL", "BIZ_DEPOSIT_INCOMPLETE", "BIZ_PRODUCT_CLOSED",
                "BIZ_CONTRACT_NOT_FOUND")) {
            keys.add("error." + code);
        }
        for (String action : AuditActions.all()) {
            if (action.startsWith("BIZ_")) {
                keys.add("audit.action." + action);
            }
        }
        keys.add("audit.targetType.BIZ_APPLICATION");
        for (BatchCatalog job : List.of(BatchCatalog.BIZ_PAYMENT_EXPIRE, BatchCatalog.BIZ_PAYMENT_DUE_NOTICE, BatchCatalog.BIZ_SCHEDULE_NOTICE)) {
            keys.add(job.nameKey());
            keys.add(job.descriptionKey());
        }
        keys.addAll(List.of("batch.job.biz-schedule-notice.step.start", "batch.job.biz-schedule-notice.step.end", "batch.target.BIZ_APPLICATION",
                "batch.result.bizPaymentExpire", "batch.result.bizPaymentExpire.none", "batch.result.bizPaymentDueNotice", "batch.result.bizPaymentDueNotice.none",
                "batch.result.bizScheduleNotice", "batch.result.bizScheduleNotice.none", "member.notification.BIZ_APPLICATION.title",
                "member.notification.BIZ_APPLICATION.body", "member.event.BIZ_APPLICATION.name", "member.event.BIZ_APPLICATION.description",
                "member.event.BIZ_APPLICATION.recipients", "bizapp.admin.policyVersion"));
        for (com.community.bizapplication.service.BizApplicationNotices.Kind kind : com.community.bizapplication.service.BizApplicationNotices.Kind.values()) {
            keys.add("member.notification.BIZ_APPLICATION.kind." + kind.name());
        }
        for (AdminNotificationEvent event : List.of(AdminNotificationEvent.BIZ_APPLICATION_REVIEW_PENDING, AdminNotificationEvent.BIZ_APPLICATION_REFUND_REQUESTED)) {
            keys.addAll(List.of(event.nameKey(), event.descriptionKey(), event.recipientsKey(), event.titleKey(), "notification.event." + event.name() + ".body"));
        }
        for (String key : keys) {
            assertThat(ko.getProperty(key)).as("ko " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        }
        // bizapp.* 칸 문구는 두 언어의 키 집합이 같다
        List<String> koKeys = ko.stringPropertyNames().stream().filter(k -> k.startsWith("bizapp.")).sorted().toList();
        List<String> enKeys = en.stringPropertyNames().stream().filter(k -> k.startsWith("bizapp.")).sorted().toList();
        assertThat(koKeys).isEqualTo(enKeys).isNotEmpty();
    }

    private static Properties bundle(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = BizApplicationAdminCatalogTest.class.getClassLoader().getResourceAsStream(path)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
