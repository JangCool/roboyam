package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.batch.core.BatchCatalog;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizDomain;
import com.community.bizapplication.service.BizApplicationNotices;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
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
 * 사업자 신청 건이 서버 카탈로그에 더한 줄 — 권한(ad.manage·deal-order.refund·ad.read·hotdeal.read·hotdeal.manage 기능 켬, ad.restore 추가), 메뉴(업무별 — 사업자 · 광고 · 핫딜,
 * 04-ia-review S7), 감사 행위 13개의 보관 분류와 대상 종류, 운영자 알림(도메인별 심사 대기·환불 심사 대기 넷 + 사업자 인증 링크), 회원 알림 종류, 결제 주문 종류, Java 배치 셋,
 * 새 문구가 두 언어에 다 있다.
 */
class BizApplicationAdminCatalogTest {

    @Test
    void 권한_도메인별_조회_처리는_기능이_생겼고_ad_restore_는_ad_takedown_바로_다음의_민감_조치() {
        for (Permission p : List.of(Permission.AD_MANAGE, Permission.DEAL_ORDER_REFUND, Permission.BUSINESS_READ, Permission.AD_READ, Permission.HOTDEAL_READ,
                Permission.HOTDEAL_MANAGE)) {
            assertThat(p.featureReady()).as(p.code()).isTrue();
        }
        assertThat(Permission.ofCode("ad.restore")).contains(Permission.AD_RESTORE);
        assertThat(Permission.AD_RESTORE.type()).isEqualTo(PermissionType.AD_PROMO);
        assertThat(Permission.AD_RESTORE.unit()).isEqualTo(PermissionUnit.SENSITIVE);
        assertThat(Permission.AD_RESTORE.featureReady()).isTrue(); // /ads 백엔드에서 기능이 생겼다(긴급 내림·연락처 평문도)
        assertThat(Permission.AD_TAKEDOWN.featureReady()).isTrue();
        assertThat(Permission.ofCode("ad.contact.reveal")).contains(Permission.AD_CONTACT_REVEAL);
        assertThat(Permission.AD_CONTACT_REVEAL.unit()).isEqualTo(PermissionUnit.SENSITIVE);
        assertThat(Permission.AD_CONTACT_REVEAL.type()).isEqualTo(PermissionType.AD_PROMO);
        assertThat(Permission.AD_CONTACT_REVEAL.ordinal()).isEqualTo(Permission.AD_RESTORE.ordinal() + 1);
        assertThat(Permission.AD_RESTORE.ordinal()).isEqualTo(Permission.AD_TAKEDOWN.ordinal() + 1);
    }

    @Test
    void admin_web_권한_카탈로그도_광고_조치_셋을_같은_단위_유형_기능_켬으로_둔다() throws IOException {
        List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of("..", "..", "apps", "admin-web", "app", "utils", "util-permission-catalog.ts"),
                StandardCharsets.UTF_8);
        for (String code : List.of("ad.takedown", "ad.restore", "ad.contact.reveal")) {
            String line = lines.stream().filter(l -> l.contains("code: '" + code + "'")).findFirst().orElse(null);
            assertThat(line).as(code).isNotNull().contains("unit: 'SENSITIVE'").contains("type: 'adPromo'").contains("featureReady: true");
        }
    }

    @Test
    void 종류의_도메인과_도메인의_권한() {
        assertThat(BizApplicationKind.AD_BANNER.domain()).isEqualTo(BizDomain.AD);
        assertThat(BizApplicationKind.AD_TEXT_LINE.domain()).isEqualTo(BizDomain.AD);
        assertThat(BizApplicationKind.HOTDEAL_POST.domain()).isEqualTo(BizDomain.HOTDEAL);
        assertThat(BizApplicationKind.HOTDEAL_PLACEMENT.domain()).isEqualTo(BizDomain.HOTDEAL);
        assertThat(BizDomain.AD.kinds()).containsExactly(BizApplicationKind.AD_BANNER, BizApplicationKind.AD_TEXT_LINE);
        assertThat(BizDomain.HOTDEAL.kinds()).containsExactly(BizApplicationKind.HOTDEAL_POST, BizApplicationKind.HOTDEAL_PLACEMENT);
        assertThat(BizDomain.AD.readPermission()).isEqualTo(Permission.AD_READ);
        assertThat(BizDomain.AD.managePermission()).isEqualTo(Permission.AD_MANAGE);
        assertThat(BizDomain.HOTDEAL.readPermission()).isEqualTo(Permission.HOTDEAL_READ);
        assertThat(BizDomain.HOTDEAL.managePermission()).isEqualTo(Permission.HOTDEAL_MANAGE);
    }

    @Test
    void 메뉴는_업무별_사업자_광고_핫딜_묶음이고_통합_신청_심사는_없다() {
        assertThat(AdminScreenRegistry.screen("bizApplications")).isEmpty();
        record Expect(String key, String path, Permission permission) {
        }
        for (Expect e : List.of(new Expect("businessReviews", "/business-reviews", Permission.BUSINESS_READ),
                new Expect("businesses", "/businesses", Permission.BUSINESS_READ), new Expect("adReviews", "/ad-reviews", Permission.AD_READ),
                new Expect("ads", "/ads", Permission.AD_READ), new Expect("hotdealReviews", "/hotdeal-reviews", Permission.HOTDEAL_READ))) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(e.key()).orElseThrow();
            assertThat(screen.path()).as(e.key()).isEqualTo(e.path());
            assertThat(screen.permission()).as(e.key()).isEqualTo(e.permission());
            assertThat(screen.labelKey()).as(e.key()).isEqualTo("menu." + e.key());
        }
        // 묶음 차례: 소모임 → 사업자(인증 심사 · 사업자 목록) → 광고(광고 심사 · 광고 일정표) → 핫딜(핫딜 심사), 묶음 아이콘은 04-ia-review 1-2 표
        List<AdminMenuNode> normal = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL);
        List<String> labels = normal.stream().map(AdminMenuNode::labelKey).toList();
        int group = labels.indexOf("menu.group");
        assertThat(group).isNotNegative();
        assertThat(labels.subList(group, group + 4)).containsExactly("menu.group", "menu.business", "menu.ad", "menu.hotdeal");
        assertThat(normal.get(group + 1).icon()).isEqualTo("pi pi-briefcase");
        assertThat(normal.get(group + 2).icon()).isEqualTo("pi pi-megaphone");
        assertThat(normal.get(group + 3).icon()).isEqualTo("pi pi-ticket");
        assertThat(normal.get(group + 1).children().stream().map(AdminMenuNode::targetKey).toList()).containsExactly("businessReviews", "businesses");
        assertThat(normal.get(group + 2).children().stream().map(AdminMenuNode::targetKey).toList()).containsExactly("adReviews", "ads");
        assertThat(normal.get(group + 3).children().stream().map(AdminMenuNode::targetKey).toList()).containsExactly("hotdealReviews");
        assertThat(AdminScreenRegistry.isGroupLabelKey("menu.ad")).isTrue();
        assertThat(AdminScreenRegistry.isGroupLabelKey("menu.hotdeal")).isTrue();
    }

    @Test
    void 감사_행위의_보관_분류_돈은_5년_계약서_열람은_개인정보_반려_전환_기한은_일반() {
        for (String action : List.of(AuditActions.BIZ_APP_APPROVE, AuditActions.BIZ_APP_PRICE_ADJUST, AuditActions.BIZ_APP_CONTRACT_UPLOAD, AuditActions.BIZ_DEPOSIT_CONFIRM,
                AuditActions.BIZ_APP_CONFIRM, AuditActions.BIZ_APP_CANCEL, AuditActions.BIZ_APP_REFUND_APPROVE, AuditActions.BIZ_APP_REFUND_DENY,
                AuditActions.BIZ_APP_REFUND_TRANSFER)) {
            assertThat(AuditRetention.classify(action)).as(action).isEqualTo(AuditRetentionClass.MONEY);
        }
        assertThat(AuditRetention.classify(AuditActions.BIZ_APP_CONTRACT_VIEW)).isEqualTo(AuditRetentionClass.PERSONAL_DATA);
        // 광고 일정표 — 운영자 등록·되돌리기는 돈(5년), 연락처 평문은 개인정보, 광고주 만들기·긴급 내림은 일반
        assertThat(AuditRetention.classify(AuditActions.BIZ_APP_OPERATOR_CREATE)).isEqualTo(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.classify(AuditActions.AD_RESTORE)).isEqualTo(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.classify(AuditActions.AD_CONTACT_REVEAL)).isEqualTo(AuditRetentionClass.PERSONAL_DATA);
        assertThat(AuditRetention.declared(AuditActions.AD_ADVERTISER_CREATE)).contains(AuditRetentionClass.GENERAL);
        assertThat(AuditRetention.declared(AuditActions.AD_TAKEDOWN)).contains(AuditRetentionClass.GENERAL);
        assertThat(AuditLabels.TARGET_TYPES).contains("AD_ADVERTISER");
        for (String action : List.of(AuditActions.BIZ_APP_REJECT, AuditActions.BIZ_APP_MANUAL_SWITCH, AuditActions.BIZ_APP_DEPOSIT_DEADLINE)) {
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.GENERAL);
        }
        assertThat(AuditLabels.TARGET_TYPES).contains("BIZ_APPLICATION");
    }

    @Test
    void 운영자_알림은_도메인별_심사_대기는_그_도메인_처리_권한_환불은_환불_권한_링크는_그_도메인_심사_화면() {
        assertThat(AdminNotificationEvent.ofCode("BIZ_APPLICATION_REVIEW_PENDING")).isEmpty();
        assertThat(AdminNotificationEvent.ofCode("BIZ_APPLICATION_REFUND_REQUESTED")).isEmpty();
        record Expect(AdminNotificationEvent event, Permission holder, String link) {
        }
        for (Expect e : List.of(new Expect(AdminNotificationEvent.AD_REVIEW_PENDING, Permission.AD_MANAGE, "/ad-reviews?id={id}"),
                new Expect(AdminNotificationEvent.HOTDEAL_REVIEW_PENDING, Permission.HOTDEAL_MANAGE, "/hotdeal-reviews?id={id}"),
                new Expect(AdminNotificationEvent.AD_REFUND_REQUESTED, Permission.DEAL_ORDER_REFUND, "/ad-reviews?id={id}"),
                new Expect(AdminNotificationEvent.HOTDEAL_REFUND_REQUESTED, Permission.DEAL_ORDER_REFUND, "/hotdeal-reviews?id={id}"))) {
            assertThat(e.event().recipientPermissions()).as(e.event().name()).containsExactly(e.holder());
            assertThat(e.event().requiredPermission()).as(e.event().name()).isEqualTo(e.holder());
            assertThat(e.event().linkPath()).as(e.event().name()).isEqualTo(e.link());
            assertThat(e.event().featureReady()).as(e.event().name()).isTrue();
            assertThat(e.event().collapseSeconds()).as(e.event().name()).isEqualTo(300);
        }
        assertThat(BizApplicationNotices.reviewEvent(BizDomain.AD)).isEqualTo(AdminNotificationEvent.AD_REVIEW_PENDING);
        assertThat(BizApplicationNotices.reviewEvent(BizDomain.HOTDEAL)).isEqualTo(AdminNotificationEvent.HOTDEAL_REVIEW_PENDING);
        assertThat(BizApplicationNotices.refundEvent(BizDomain.AD)).isEqualTo(AdminNotificationEvent.AD_REFUND_REQUESTED);
        assertThat(BizApplicationNotices.refundEvent(BizDomain.HOTDEAL)).isEqualTo(AdminNotificationEvent.HOTDEAL_REFUND_REQUESTED);
        // 사업자 인증 알림은 「인증 심사」 화면으로
        assertThat(AdminNotificationEvent.BUSINESS_APPLICATION_PENDING.linkPath()).isEqualTo("/business-reviews?id={profileId}");
        assertThat(AdminNotificationEvent.BUSINESS_RE_REVIEW_PENDING.linkPath()).isEqualTo("/business-reviews?id={profileId}");
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
                "BIZ_CONTRACT_NOT_FOUND", "BIZ_SELF_APPROVAL_DENIED", "AD_ADVERTISER_NOT_FOUND")) {
            keys.add("error." + code);
        }
        for (String action : AuditActions.all()) {
            if (action.startsWith("BIZ_") || action.startsWith("AD_")) {
                keys.add("audit.action." + action);
            }
        }
        keys.add("audit.targetType.BIZ_APPLICATION");
        keys.addAll(List.of("audit.targetType.AD_ADVERTISER", "ad.advertiser.nameTaken", "ad.advertiser.businessLinked", "ad.advertiser.notFound", "ad.advertiser.phone",
                "ad.advertiser.email", "ad.reason.invalid", "ad.weight.range", "ad.takedown.mail.subject", "ad.takedown.mail.body", "ad.takedown.mail.reason"));
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
        for (AdminNotificationEvent event : List.of(AdminNotificationEvent.AD_REVIEW_PENDING, AdminNotificationEvent.HOTDEAL_REVIEW_PENDING,
                AdminNotificationEvent.AD_REFUND_REQUESTED, AdminNotificationEvent.HOTDEAL_REFUND_REQUESTED)) {
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
