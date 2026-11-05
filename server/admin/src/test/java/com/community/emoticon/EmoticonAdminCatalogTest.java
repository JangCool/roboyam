package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
import com.community.point.entity.PointReason;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 로보티콘 S1 의 카탈로그 줄(emoticon-contract — 권한 · 관리자 메뉴 · 운영자 이벤트 · 회원 알림 · 감사 · 원장 사유) — 서버와 admin-web(권한 카탈로그 · 메뉴 정의)에서 같은 코드·유형·차례이고,
 * 새 문구가 두 언어에 있으며, 모든 API 입구가 계약의 권한을 선언한다.
 */
class EmoticonAdminCatalogTest {

    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");
    private static final Pattern PERMISSION_LINE = Pattern.compile(
            "\\{ code: '([^']+)', unit: '([A-Z]+)', type: '([A-Za-z]+)', nameKey: '[^']+', descKey: '[^']+', featureReady: (true|false) }");
    private static final List<String> SCREENS = List.of("emoticonReviews", "emoticonPacks", "emoticonSales", "emoticonSettlements", "emoticonSettings");

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = EmoticonAdminCatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void 권한_여섯_개는_등급_포인트_유형_조회_처리_설정_민감_셋이고_포인트_상점_권한_바로_다음() {
        assertThat(Permission.ofCode("emoticon.read")).contains(Permission.EMOTICON_READ);
        assertThat(Permission.ofCode("emoticon.payout.pii")).contains(Permission.EMOTICON_PAYOUT_PII);
        assertThat(Permission.EMOTICON_READ.unit()).isEqualTo(PermissionUnit.READ);
        assertThat(Permission.EMOTICON_REVIEW.unit()).isEqualTo(PermissionUnit.MANAGE);
        assertThat(Permission.EMOTICON_SETTINGS.unit()).isEqualTo(PermissionUnit.CONFIG);
        List<Permission> all = List.of(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW, Permission.EMOTICON_SETTINGS, Permission.EMOTICON_SALES,
                Permission.EMOTICON_SETTLEMENT, Permission.EMOTICON_PAYOUT_PII);
        for (Permission p : all) {
            assertThat(p.type()).isEqualTo(PermissionType.GRADE_POINT);
            // S1 세 줄은 화면이 연결돼 켜졌고, 판매 내역(S3)·정산·정산 계좌(S4)는 그 화면 서버 API 가 생겨 켰다 — 여섯 줄 모두 켜짐
            assertThat(p.featureReady()).as(p.code()).isTrue();
            assertThat(p.ordinal()).as(p.code()).isEqualTo(Permission.POINTSHOP_REFUND.ordinal() + 1 + all.indexOf(p));
        }
        for (Permission p : List.of(Permission.EMOTICON_SALES, Permission.EMOTICON_SETTLEMENT, Permission.EMOTICON_PAYOUT_PII)) {
            assertThat(p.unit()).as(p.code()).isEqualTo(PermissionUnit.SENSITIVE);
        }
    }

    @Test
    void admin_web_권한_카탈로그에_같은_코드_유형_단위_차례로_있다() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-permission-catalog.ts"));
        List<String> web = new ArrayList<>();
        Matcher m = PERMISSION_LINE.matcher(source);
        while (m.find()) {
            web.add(m.group(1) + "|" + m.group(2) + "|" + m.group(3));
        }
        int at = web.indexOf("emoticon.read|READ|gradePoint");
        assertThat(at).isEqualTo(web.indexOf("pointshop.refund|SENSITIVE|gradePoint") + 1);
        assertThat(web.subList(at, at + 6)).containsExactly("emoticon.read|READ|gradePoint", "emoticon.review|MANAGE|gradePoint", "emoticon.settings|CONFIG|gradePoint",
                "emoticon.sales|SENSITIVE|gradePoint", "emoticon.settlement|SENSITIVE|gradePoint", "emoticon.payout.pii|SENSITIVE|gradePoint");
    }

    @Test
    void 메뉴_로보티콘_묶음은_포인트_상점_바로_다음_세_화면_emoticon_read_admin_web_정의와_같은_줄_차례() throws IOException {
        List<AdminMenuNode> normal = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL);
        List<String> sections = normal.stream().map(AdminMenuNode::labelKey).toList();
        assertThat(sections.indexOf("menu.emoticon")).isEqualTo(sections.indexOf("menu.pointShop") + 1);
        AdminMenuNode section = normal.get(sections.indexOf("menu.emoticon"));
        assertThat(section.icon()).isEqualTo("pi pi-face-smile");
        assertThat(section.children().stream().map(AdminMenuNode::targetKey).toList()).containsExactlyElementsOf(SCREENS);
        assertThat(AdminScreenRegistry.isGroupLabelKey("menu.emoticon")).isTrue();
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        int last = -1;
        for (String key : SCREENS) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(key).orElseThrow();
            // 판매 내역(S3)은 emoticon.sales, 작가 정산(S4)은 emoticon.settlement — 돈과 구매 회원·작가 정보라 일반 조회와 나눴다
            assertThat(screen.permission()).as(key).isEqualTo("emoticonSales".equals(key) ? Permission.EMOTICON_SALES
                    : "emoticonSettlements".equals(key) ? Permission.EMOTICON_SETTLEMENT : Permission.EMOTICON_READ);
            assertThat(screen.superOnly()).isFalse();
            String expected = "{ key: '" + key + "', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                    + screen.permission().code() + "' }";
            assertThat(source).as(key).contains(expected);
            assertThat(source.indexOf(expected)).as(key + " 차례").isGreaterThan(last);
            last = source.indexOf(expected);
        }
        assertThat(AdminScreenRegistry.screen("emoticonReviews").orElseThrow().path()).isEqualTo("/robotikon/reviews");
        assertThat(source).containsPattern("key: 'emoticon',\\s*icon: 'pi pi-face-smile',\\s*labelKey: 'menu.emoticon',");
    }

    @Test
    void 운영자_이벤트_심사_대기는_심사_권한자에게_그_건_서랍으로_잇는다() {
        AdminNotificationEvent event = AdminNotificationEvent.EMOTICON_REVIEW_PENDING;
        assertThat(event.requiredPermission()).isEqualTo(Permission.EMOTICON_REVIEW);
        assertThat(event.linkPath()).isEqualTo("/robotikon/reviews?id={reviewId}");
        assertThat(event.featureReady()).isTrue();
    }

    @Test
    void 회원_알림_EMOTICON_PACK_은_끌_수_없는_처리_알림() {
        assertThat(MemberNotificationType.of("EMOTICON_PACK")).isPresent();
        assertThat(MemberEvent.EMOTICON_PACK.type()).isEqualTo(MemberNotificationType.EMOTICON_PACK);
        assertThat(MemberEvent.EMOTICON_PACK.featureReady()).isTrue();
        // 후속 결정 2 — 구매·환불 알림은 따로 둔 종류(받는 사람 = 산 회원), 끌 수 없는 거래 알림
        assertThat(MemberNotificationType.of("EMOTICON_PURCHASE")).isPresent();
        assertThat(MemberEvent.EMOTICON_PURCHASE.type()).isEqualTo(MemberNotificationType.EMOTICON_PURCHASE);
        assertThat(MemberEvent.EMOTICON_PURCHASE.featureReady()).isTrue();
        assertThat(MemberEvent.EMOTICON_PURCHASE.locked()).isTrue();
    }

    @Test
    void 감사_행위는_분류가_정해져_있고_대상_종류는_거르기_선택지에_있다() {
        for (String action : List.of(AuditActions.EMOTICON_REVIEW_APPROVE, AuditActions.EMOTICON_REVIEW_REJECT, AuditActions.EMOTICON_PACK_SUSPEND,
                AuditActions.EMOTICON_PACK_RESUME, AuditActions.EMOTICON_PACK_CREATE, AuditActions.EMOTICON_PACK_UPDATE, AuditActions.EMOTICON_PACK_CREATOR_WITHDRAWN)) {
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.GENERAL);
        }
        // S2 — 가격 바꾸기·설정(유료 판매·수수료·가격 단계)은 돈 관련, 정산 계좌 원문 보기는 개인정보 열람
        assertThat(AuditRetention.declared(AuditActions.EMOTICON_PACK_PRICE)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.declared(AuditActions.EMOTICON_SETTINGS_UPDATE)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.declared(AuditActions.EMOTICON_PAYOUT_REVEAL)).contains(AuditRetentionClass.PERSONAL_DATA);
        assertThat(AuditLabels.TARGET_TYPES).contains("EMOTICON_REVIEW", "EMOTICON_PACK", "EMOTICON_SETTINGS", "EMOTICON_PAYOUT_ACCOUNT");
        // S3 — 판매 예외 환불(결제 취소)은 돈 관련, 대상 = 구매 기록
        assertThat(AuditRetention.declared(AuditActions.EMOTICON_SALE_REFUND)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditLabels.TARGET_TYPES).contains("EMOTICON_PURCHASE");
        // S4 — 정산 확정·보류·풀기·지급 기록은 돈 관련, 대상 = 정산 줄
        for (String action : List.of(AuditActions.EMOTICON_SETTLEMENT_CONFIRM, AuditActions.EMOTICON_SETTLEMENT_HOLD, AuditActions.EMOTICON_SETTLEMENT_UNHOLD,
                AuditActions.EMOTICON_SETTLEMENT_PAID)) {
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.MONEY);
        }
        assertThat(AuditLabels.TARGET_TYPES).contains("EMOTICON_SETTLEMENT");
    }

    @Test
    void 새_문구는_두_언어에_있다() throws IOException {
        Properties ko = bundle("ko");
        Properties en = bundle("en");
        List<String> keys = new ArrayList<>(List.of("point.reason." + PointReason.EMOTICON_CREATOR_REWARD.name(), "member.notification.EMOTICON_PACK.title",
                "member.notification.EMOTICON_PACK.body", "member.event.EMOTICON_PACK.name", "member.event.EMOTICON_PACK.description",
                "member.event.EMOTICON_PACK.recipients", "notificationTemplate.var.emoticonPackName.desc", "notificationTemplate.var.emoticonPackDetail.desc",
                "emoticon.image.reason", "emoticon.body.tooMany", "emoticon.blocked.ACCOUNT_RESTRICTED", "emoticon.blocked.EMAIL_NOT_VERIFIED"));
        for (String part : List.of("name", "description", "recipients", "title", "body")) {
            keys.add("notification.event.EMOTICON_REVIEW_PENDING." + part);
        }
        for (String kind : List.of("APPROVED", "REJECTED", "UPDATE_APPROVED", "UPDATE_REJECTED", "SUSPENDED", "RESUMED", "APPROVED_PAID", "PRICE_CHANGED",
                "UPDATE_PRICE_CHANGED")) {
            keys.add("member.notification.EMOTICON_PACK.kind." + kind);
        }
        for (String code : List.of("EMOTICON_PACK_NOT_FOUND", "EMOTICON_PACK_SUSPENDED", "EMOTICON_PACK_PAID", "EMOTICON_PACK_LOCKED", "EMOTICON_PAYOUT_REQUIRED",
                "EMOTICON_PENDING_LIMIT", "EMOTICON_NOT_OWNED", "EMOTICON_REVIEW_NOT_FOUND", "EMOTICON_REVIEW_NOT_PENDING", "EMOTICON_PACK_NOT_ON_SALE",
                "EMOTICON_PACK_NOT_SUSPENDED", "EMOTICON_PACK_NOT_OFFICIAL", "EMOTICON_UPLOAD_LIMIT", "EMOTICON_CONTRACT_REQUIRED", "EMOTICON_IDENTITY_NAME_MISSING",
                "EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND", "EMOTICON_PACK_NOT_ON_SALE.price", "EMOTICON_PACK_CREATOR_WITHDRAWN", "EMOTICON_PACK_CREATOR_WITHDRAWN.price",
                "EMOTICON_PACK_CREATOR_WITHDRAWN.review")) {
            keys.add("error." + code);
        }
        keys.addAll(List.of("emoticon.contract.title", "emoticon.studio.saleWish.paidDisabled", "emoticon.payout.type", "emoticon.payout.businessNumber",
                "emoticon.payout.bankCode", "emoticon.payout.accountNumber", "emoticon.payout.holderName.required", "emoticon.payout.holderName.mismatch",
                "emoticon.review.price.tier", "emoticon.review.price.paidDisabled", "emoticon.review.price.freeWish", "emoticon.review.price.noPayout",
                "emoticon.review.price.noContract", "emoticon.review.price.same", "emoticon.settings.paidSalesEnabled.required", "emoticon.settings.priceTiers.count",
                "emoticon.settings.priceTiers.range", "emoticon.settings.priceTiers.duplicate", "emoticon.settings.priceTiers.inUse",
                "audit.action." + AuditActions.EMOTICON_PACK_PRICE, "audit.action." + AuditActions.EMOTICON_PAYOUT_REVEAL, "audit.targetType.EMOTICON_PAYOUT_ACCOUNT"));
        for (String reason : List.of("TYPE", "COVER_TYPE", "DIMENSION", "TOO_LARGE", "TOO_MANY_FRAMES", "NOT_LOOP")) {
            keys.add("emoticon.image." + reason);
        }
        for (String action : List.of(AuditActions.EMOTICON_REVIEW_APPROVE, AuditActions.EMOTICON_REVIEW_REJECT, AuditActions.EMOTICON_PACK_SUSPEND,
                AuditActions.EMOTICON_PACK_RESUME, AuditActions.EMOTICON_PACK_CREATE, AuditActions.EMOTICON_PACK_UPDATE, AuditActions.EMOTICON_SETTINGS_UPDATE,
                AuditActions.EMOTICON_PACK_CREATOR_WITHDRAWN)) {
            keys.add("audit.action." + action);
        }
        // S3 — 유료 구매 · 환불 · 판매 내역 · 이름 채우기 재인증
        for (String code : List.of("EMOTICON_PACK_FREE", "EMOTICON_PACK_OWNED", "EMOTICON_PACK_OWNED.free", "EMOTICON_PAID_SALES_CLOSED", "EMOTICON_PACK_SUSPENDED.purchase",
                "EMOTICON_PACK_PAID.remove", "EMOTICON_PURCHASE_NOT_FOUND", "EMOTICON_ALREADY_REFUNDED", "EMOTICON_REFUND_NOT_ALLOWED.USED",
                "EMOTICON_REFUND_NOT_ALLOWED.EXPIRED", "EMOTICON_SALE_NOT_FOUND", "EMOTICON_SALE_ALREADY_REFUNDED", "IDENTITY_PERSON_MISMATCH")) {
            keys.add("error." + code);
        }
        keys.addAll(List.of("emoticon.purchaseBlocked.ACCOUNT_RESTRICTED", "emoticon.purchaseBlocked.EMAIL_NOT_VERIFIED", "emoticon.purchase.agreeRefundNotice",
                "emoticon.sales.instant", "audit.action." + AuditActions.EMOTICON_SALE_REFUND, "audit.targetType.EMOTICON_PURCHASE"));
        // S4 — 작가 정산
        for (String code : List.of("EMOTICON_SETTLEMENT_NOT_FOUND", "EMOTICON_SETTLEMENT_NOT_HOLDABLE", "EMOTICON_SETTLEMENT_NOT_HELD", "EMOTICON_SETTLEMENT_NOT_CONFIRMED",
                "EMOTICON_SETTLEMENT_NOTHING_TO_PAY")) {
            keys.add("error." + code);
        }
        keys.addAll(List.of("emoticon.settlement.ids", "emoticon.settlement.transferDate", "emoticon.settlement.transferDate.future", "emoticon.settlement.memo",
                "audit.action." + AuditActions.EMOTICON_SETTLEMENT_CONFIRM, "audit.action." + AuditActions.EMOTICON_SETTLEMENT_HOLD,
                "audit.action." + AuditActions.EMOTICON_SETTLEMENT_UNHOLD, "audit.action." + AuditActions.EMOTICON_SETTLEMENT_PAID, "audit.targetType.EMOTICON_SETTLEMENT"));
        for (String type : List.of("EMOTICON_REVIEW", "EMOTICON_PACK", "EMOTICON_SETTINGS")) {
            keys.add("audit.targetType." + type);
        }
        for (String key : keys) {
            assertThat(ko.getProperty(key)).as("ko " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        }
        // 이름 문구는 사람이 읽는 한 줄(이유 코드는 칸에 — 문구 자리는 코드 그대로)
        assertThat(ko.getProperty("emoticon.image.reason")).isEqualTo("{0}");
    }
}
