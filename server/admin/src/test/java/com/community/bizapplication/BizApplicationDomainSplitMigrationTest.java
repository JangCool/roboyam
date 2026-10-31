package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.service.BizApplicationMemberService;
import com.community.commerce.Commerce04AdminTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * 도메인 나누기 마이그레이션(04-ia-review S5·S7) — 이미 설치된 곳의 데이터를 옮긴다. 실제 관리자 앱 문맥(기동 때 카탈로그 동기화가 돈다) + PostgreSQL 에서 SQL 을 손으로 다시 돌린다.
 * <ul>
 *   <li>shared V114: 옛 이벤트 {@code BIZ_APPLICATION_REVIEW_PENDING}·{@code BIZ_APPLICATION_REFUND_REQUESTED} 의 운영자 설정·개인 끄기를 새 두 이름에 옮기고 옛 줄을 지운다.
 *       이미 받은 알림은 건의 종류로 새 이름·문구 키·링크로, 사업자 인증 알림 링크는 {@code /business-reviews} 로.</li>
 *   <li>admin V4: 저장한 관리자 메뉴의 옛 화면 키 {@code bizApplications} → {@code adReviews}.</li>
 * </ul>
 */
class BizApplicationDomainSplitMigrationTest extends Commerce04AdminTestBase {

    private static final List<String> NEW_CODES = List.of("AD_REVIEW_PENDING", "HOTDEAL_REVIEW_PENDING", "AD_REFUND_REQUESTED", "HOTDEAL_REFUND_REQUESTED");

    @Autowired
    private BizApplicationMemberService members;

    private void run(String path) throws IOException {
        jdbc.execute(new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8));
    }

    private long approvedBusiness() {
        long user = newMember();
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, 'APPROVED', false, '이관상사', 'x', ?, '1234', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())", profile, user,
                String.format("%064x", profile));
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, ?, 'APPROVED', now())", ids.nextId(), profile, service);
        }
        return user;
    }

    private long notification(long operatorId, String code, String link, String dedupe) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO admin_notifications (id, operator_id, event_code, severity, title_key, body_key, args, link_path, dedupe_key, created_at) "
                + "VALUES (?, ?, ?, 'INFO', ?, ?, NULL, ?, ?, now())", id, operatorId, code, "notification.event." + code + ".title", "notification.event." + code + ".body",
                link, dedupe);
        return id;
    }

    private Map<String, Object> notificationRow(long id) {
        return jdbc.queryForMap("SELECT event_code, title_key, body_key, link_path FROM admin_notifications WHERE id = ?", id);
    }

    private Map<String, Object> settings(String code) {
        return jdbc.queryForMap("SELECT enabled, email_enabled, browser_enabled, default_recipients FROM notification_events WHERE code = ?", code);
    }

    @Test
    void V114_는_옛_알림_설정_끄기_받은_알림을_도메인별_새_이름으로_옮기고_옛_줄을_지운다() throws IOException {
        List<Map<String, Object>> before = new ArrayList<>();
        NEW_CODES.forEach(code -> before.add(settings(code)));
        long operator = operatorWith();
        List<Long> rows = new ArrayList<>();
        try {
            long user = approvedBusiness();
            LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
            long ad = members.submitAd(user, new BizAdApplyRequest("TEXT_LINE", start.toString(), start.plusDays(2).toString(), null, "https://a.example.com", null,
                    "이관 시험 한 줄", "MANUAL", "v1", "v1"));
            long deal = members.submitDeal(user, new BizDealApplyRequest("[이관상사] 텀블러", new BizDealInfo("https://smartstore.naver.com/roboyam/products/7",
                    "네이버 스마트스토어", 32_000L, 15_900L, 0L, null, "LIMITED", "행사", "NONE"), null, null, "MANUAL", "v1", "v1"));

            // 옛 이름 — 운영자가 바꾼 값(심사 대기는 끄고 이메일·브라우저 켜고 팀에만, 환불은 이메일 켬)과 개인 끄기, 받은 알림
            jdbc.update("INSERT INTO notification_events (code, enabled, email_enabled, browser_enabled, default_recipients, updated_at) VALUES "
                    + "('BIZ_APPLICATION_REVIEW_PENDING', false, true, true, false, now()), ('BIZ_APPLICATION_REFUND_REQUESTED', true, true, false, true, now())");
            jdbc.update("INSERT INTO operator_notification_mutes (operator_id, event_code) VALUES (?, 'BIZ_APPLICATION_REVIEW_PENDING')", operator);
            long adRow = notification(operator, "BIZ_APPLICATION_REVIEW_PENDING", "/biz-applications?id=" + ad, Long.toString(ad));
            long dealRow = notification(operator, "BIZ_APPLICATION_REFUND_REQUESTED", "/biz-applications?id=" + deal, Long.toString(deal));
            long businessRow = notification(operator, "BUSINESS_APPLICATION_PENDING", "/businesses?id=777", "777");
            rows.addAll(List.of(adRow, dealRow, businessRow));

            run("db/migration/shared/V114__biz_notification_events_by_domain.sql");

            for (String code : List.of("AD_REVIEW_PENDING", "HOTDEAL_REVIEW_PENDING")) {
                assertThat(settings(code)).as(code).containsEntry("enabled", false).containsEntry("email_enabled", true).containsEntry("browser_enabled", true)
                        .containsEntry("default_recipients", false);
            }
            for (String code : List.of("AD_REFUND_REQUESTED", "HOTDEAL_REFUND_REQUESTED")) {
                assertThat(settings(code)).as(code).containsEntry("enabled", true).containsEntry("email_enabled", true).containsEntry("browser_enabled", false)
                        .containsEntry("default_recipients", true);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_events WHERE code LIKE 'BIZ_APPLICATION_%'", Integer.class)).isZero();
            assertThat(jdbc.queryForList("SELECT event_code FROM operator_notification_mutes WHERE operator_id = ? ORDER BY event_code", String.class, operator))
                    .containsExactly("AD_REVIEW_PENDING", "HOTDEAL_REVIEW_PENDING");

            assertThat(notificationRow(adRow)).containsEntry("event_code", "AD_REVIEW_PENDING").containsEntry("link_path", "/ad-reviews?id=" + ad)
                    .containsEntry("title_key", "notification.event.AD_REVIEW_PENDING.title").containsEntry("body_key", "notification.event.AD_REVIEW_PENDING.body");
            assertThat(notificationRow(dealRow)).containsEntry("event_code", "HOTDEAL_REFUND_REQUESTED").containsEntry("link_path", "/hotdeal-reviews?id=" + deal)
                    .containsEntry("title_key", "notification.event.HOTDEAL_REFUND_REQUESTED.title");
            assertThat(notificationRow(businessRow)).containsEntry("event_code", "BUSINESS_APPLICATION_PENDING").containsEntry("link_path", "/business-reviews?id=777");

            run("db/migration/shared/V114__biz_notification_events_by_domain.sql"); // 다시 돌아도 바뀌는 것이 없다(옛 줄이 없다)
            assertThat(settings("AD_REVIEW_PENDING")).containsEntry("enabled", false);
            assertThat(notificationRow(businessRow)).containsEntry("link_path", "/business-reviews?id=777");
        } finally {
            for (int i = 0; i < NEW_CODES.size(); i++) {
                Map<String, Object> b = before.get(i);
                jdbc.update("UPDATE notification_events SET enabled = ?, email_enabled = ?, browser_enabled = ?, default_recipients = ? WHERE code = ?", b.get("enabled"),
                        b.get("email_enabled"), b.get("browser_enabled"), b.get("default_recipients"), NEW_CODES.get(i));
            }
            jdbc.update("DELETE FROM notification_events WHERE code LIKE 'BIZ_APPLICATION_%'");
            jdbc.update("DELETE FROM operator_notification_mutes WHERE operator_id = ?", operator);
            for (long id : rows) {
                jdbc.update("DELETE FROM admin_notifications WHERE id = ?", id);
            }
        }
    }

    @Test
    void admin_V4_는_저장한_메뉴의_옛_화면_키를_광고_심사로_바꾼다() throws IOException {
        long section = ids.nextId();
        long screen = ids.nextId();
        try {
            jdbc.update("INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, label_key, icon, visible) VALUES (?, 'normal', NULL, 'SECTION', 900, "
                    + "'menu.business', 'pi pi-briefcase', true)", section);
            jdbc.update("INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, icon, visible, target_type, target_key) VALUES (?, 'normal', ?, 'SCREEN', 0, "
                    + "'pi pi-inbox', false, 'ADMIN_SCREEN', 'bizApplications')", screen, section);

            run("db/migration/admin/V4__admin_menu_biz_split.sql");

            assertThat(jdbc.queryForMap("SELECT target_key, visible, icon FROM admin_menu_nodes WHERE id = ?", screen)).containsEntry("target_key", "adReviews")
                    .containsEntry("visible", false).containsEntry("icon", "pi pi-inbox");
        } finally {
            jdbc.update("DELETE FROM admin_menu_nodes WHERE id = ?", section); // 하위는 CASCADE
        }
    }
}
