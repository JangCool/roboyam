package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.batch.core.BatchCatalog;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationSeverity;
import com.community.permission.entity.Permission;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * 커머스 2a-3 이 서버 카탈로그에 더한 줄 — 메뉴 「결제」 묶음 다섯 줄(포인트 상점 다음, admin-web 사본과 같은 키·주소·권한·아이콘·차례), 감사 행위 분류(돈 관련 넷 · 개인정보 열람 하나 ·
 * 다시 대사는 일반)와 대상 종류 넷, 운영자 알림 두 종류(결제 이상 → 결제 이상 화면, 대사 불일치 WARNING·하루 한 번 → 대사 불일치 화면), 배치 두 줄, 새 문구가 두 언어에 다 있다.
 */
class Commerce2aCatalogTest {

    /** 시험 작업 폴더 = server/admin */
    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    private static final List<String> PAYMENT_SCREENS = List.of("paymentOrders", "paymentWebhooks", "paymentAnomalies", "paymentReconcileDays", "paymentReconciliation");

    @Test
    void 결제_묶음은_보통_모드에서_포인트_상점_다음이고_다섯_화면_모두_결제_조회_권한() {
        List<String> paths = List.of("/payments/orders", "/payments/webhooks", "/payments/anomalies", "/payments/reconcile-days", "/payments/reconciliation");
        List<String> icons = List.of("pi pi-list", "pi pi-inbox", "pi pi-exclamation-triangle", "pi pi-calendar", "pi pi-check-square");
        for (int i = 0; i < PAYMENT_SCREENS.size(); i++) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(PAYMENT_SCREENS.get(i)).orElseThrow();
            assertThat(screen.path()).isEqualTo(paths.get(i));
            assertThat(screen.icon()).isEqualTo(icons.get(i));
            assertThat(screen.labelKey()).isEqualTo("menu." + PAYMENT_SCREENS.get(i));
            assertThat(screen.permission()).isEqualTo(Permission.COMMERCE_PAYMENT_READ);
            assertThat(screen.superOnly()).isFalse();
        }
        assertThat(AdminScreenRegistry.isGroupLabelKey("menu.payment")).isTrue();
        List<AdminMenuNode> normal = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL);
        List<String> groups = normal.stream().map(AdminMenuNode::labelKey).toList();
        assertThat(groups.indexOf("menu.groupBuy")).as("2b-4 — 공구 묶음이 포인트 상점과 결제 사이").isEqualTo(groups.indexOf("menu.pointShop") + 1);
        assertThat(groups.indexOf("menu.payment")).isEqualTo(groups.indexOf("menu.groupBuy") + 1);
        AdminMenuNode payment = normal.stream().filter(node -> "menu.payment".equals(node.labelKey())).findFirst().orElseThrow();
        assertThat(payment.icon()).isEqualTo("pi pi-credit-card");
        assertThat(payment.children()).extracting(AdminMenuNode::targetKey).containsExactlyElementsOf(PAYMENT_SCREENS);
    }

    @Test
    void 결제_메뉴_줄은_admin_web_사본과_같다() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        List<Integer> positions = new ArrayList<>();
        for (String key : PAYMENT_SCREENS) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(key).orElseThrow();
            String expected = "{ key: '" + key + "', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                    + screen.permission().code() + "' }";
            assertThat(source).contains(expected);
            positions.add(source.indexOf(expected));
        }
        assertThat(positions).isSorted();
        assertThat(source).contains("key: 'payment',").contains("labelKey: 'menu.payment',");
    }

    @Test
    void 감사_행위는_처리_넷이_돈_관련_상세_열람이_개인정보_열람_다시_대사는_일반이고_대상_종류_넷이_있다() {
        for (String action : List.of(AuditActions.RECONCILE_ISSUE_RESOLVE, AuditActions.PAYMENT_CANCEL_RETRY, AuditActions.PAYMENT_CANCEL_MANUAL, AuditActions.PAYMENT_RECHECK)) {
            assertThat(AuditRetention.classify(action)).as(action).isEqualTo(AuditRetentionClass.MONEY);
        }
        assertThat(AuditRetention.classify(AuditActions.PAYMENT_ORDER_VIEW)).isEqualTo(AuditRetentionClass.PERSONAL_DATA);
        assertThat(AuditRetention.classify(AuditActions.PAYMENT_RECONCILE_RERUN)).isEqualTo(AuditRetentionClass.GENERAL);
        assertThat(AuditActions.all()).contains(AuditActions.PAYMENT_RECONCILE_RERUN);
        assertThat(AuditLabels.TARGET_TYPES).contains("PAYMENT_ORDER", "PAYMENT_CANCEL", "RECONCILIATION_ISSUE", "PAYMENT_RECONCILE_DAY");
    }

    @Test
    void 운영자_알림은_결제_이상과_대사_불일치_받는_사람은_대사_처리_권한() {
        AdminNotificationEvent anomaly = AdminNotificationEvent.COMMERCE_PAYMENT_ANOMALY;
        assertThat(anomaly.linkPath()).isEqualTo("/payments/anomalies");
        assertThat(anomaly.severity()).isEqualTo(NotificationSeverity.CRITICAL);
        assertThat(anomaly.recipientPermissions()).containsExactly(Permission.COMMERCE_RECONCILE_MANAGE);
        assertThat(anomaly.featureReady()).isTrue();

        AdminNotificationEvent mismatch = AdminNotificationEvent.COMMERCE_RECONCILE_MISMATCH;
        assertThat(mismatch.linkPath()).isEqualTo("/payments/reconciliation");
        assertThat(mismatch.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(mismatch.recipientPermissions()).containsExactly(Permission.COMMERCE_RECONCILE_MANAGE);
        assertThat(mismatch.collapseSeconds()).as("하루 한 번").isEqualTo(86400);
        assertThat(mismatch.featureReady()).isTrue();
    }

    @Test
    void 배치_두_줄은_Java_대사와_DB_웹훅_기록_정리() {
        assertThat(BatchCatalog.ofKey("payment-reconciliation")).isEqualTo(BatchCatalog.PAYMENT_RECONCILIATION);
        assertThat(BatchCatalog.PAYMENT_RECONCILIATION.isJava()).isTrue();
        assertThat(BatchCatalog.ofKey("payment-webhook-log-cleanup")).isEqualTo(BatchCatalog.PAYMENT_WEBHOOK_LOG_CLEANUP);
        assertThat(BatchCatalog.PAYMENT_WEBHOOK_LOG_CLEANUP.isJava()).isFalse();
    }

    @Test
    void 새_문구는_두_언어에_모두_있다() throws IOException {
        Properties ko = bundle("messages/messages_ko.properties");
        Properties en = bundle("messages/messages_en.properties");
        List<String> keys = new ArrayList<>(List.of(
                "audit.action.PAYMENT_RECONCILE_RERUN", "audit.targetType.PAYMENT_ORDER", "audit.targetType.PAYMENT_CANCEL", "audit.targetType.RECONCILIATION_ISSUE",
                "audit.targetType.PAYMENT_RECONCILE_DAY",
                "error.PAYMENT_CANCEL_NOT_FOUND", "error.PAYMENT_CANCEL_NOT_GAVE_UP", "error.RECONCILE_ISSUE_NOT_FOUND", "error.RECONCILE_ISSUE_NOT_OPEN",
                "error.RECONCILE_ALREADY_RUNNING", "payment.reason.length", "payment.reconcile.day.notPast", "portone.field.webhookSecret", "payment.query.invalid",
                "payment.query.size.range", "payment.detail.won",
                "batch.job.payment-reconciliation.name", "batch.job.payment-reconciliation.desc", "batch.job.payment-reconciliation.param.day",
                "batch.target.PAYMENT_RECONCILE_DAY", "batch.result.paymentReconciliation", "batch.result.paymentReconciliation.none",
                "batch.job.payment-webhook-log-cleanup.name", "batch.job.payment-webhook-log-cleanup.desc", "batch.result.paymentWebhookLogCleanup",
                "batch.result.paymentWebhookLogCleanup.none"));
        AdminNotificationEvent mismatch = AdminNotificationEvent.COMMERCE_RECONCILE_MISMATCH;
        keys.addAll(List.of(mismatch.nameKey(), mismatch.descriptionKey(), mismatch.recipientsKey(), mismatch.titleKey(),
                "notification.event.COMMERCE_RECONCILE_MISMATCH.body"));
        for (String key : keys) {
            assertThat(ko.getProperty(key)).as("ko " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        }
        for (String kind : List.of("CREATED", "PAID", "ANOMALY", "EXPIRED", "CANCEL_REQUESTED", "AUTO_REJECT", "AMOUNT_MISMATCH", "LATE_PAYMENT", "CONFIRMED", "UNCHANGED",
                "PG_NOT_PAID")) {
            if (ko.getProperty("payment.value." + kind) != null) {
                assertThat(en.getProperty("payment.value." + kind)).as("en payment.value." + kind).isNotBlank();
            }
        }
    }

    private static Properties bundle(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Commerce2aCatalogTest.class.getClassLoader().getResourceAsStream(path)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
