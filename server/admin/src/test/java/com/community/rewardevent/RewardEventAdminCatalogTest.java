package com.community.rewardevent;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.service.AuditLabels;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
import com.community.point.entity.PointReason;
import com.community.rewardevent.controller.RewardEventAdminController;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 보상 이벤트의 카탈로그 줄(docs/features/event-admin-contract.md 「권한」·「화면」·「알림·감사」) — 권한 세 개(등급·포인트 유형 · 조회/처리/민감, 로보티콘 다음) · 관리자 메뉴 줄(admin-web
 * {@code util-admin-menu.ts} 와 같은 키·주소·권한·아이콘) · 모든 API 입구의 권한 선언 · 회원 알림 두 종류 · 감사 대상 종류 · 새 문구가 두 언어에 있다.
 */
class RewardEventAdminCatalogTest {

    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = RewardEventAdminCatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void 권한_세_개는_등급_포인트_유형_조회_처리_민감이고_로보티콘_권한_바로_다음() {
        List<Permission> all = List.of(Permission.REWARD_EVENT_READ, Permission.REWARD_EVENT_MANAGE, Permission.REWARD_EVENT_GRANT);
        assertThat(all).extracting(Permission::code).containsExactly("reward-event.read", "reward-event.manage", "reward-event.grant");
        assertThat(all).extracting(Permission::unit).containsExactly(PermissionUnit.READ, PermissionUnit.MANAGE, PermissionUnit.SENSITIVE);
        for (Permission p : all) {
            assertThat(p.type()).isEqualTo(PermissionType.GRADE_POINT);
            assertThat(p.featureReady()).isTrue();
            assertThat(p.ordinal()).isEqualTo(Permission.EMOTICON_PAYOUT_PII.ordinal() + 1 + all.indexOf(p));
        }
        assertThat(Permission.ofCode("point.adjust.large")).contains(Permission.POINT_ADJUST_LARGE);
    }

    @Test
    void 메뉴_보상_이벤트_묶음은_로보티콘_묶음_바로_다음_한_화면_admin_web_정의와_같은_줄() throws IOException {
        List<AdminMenuNode> normal = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL);
        List<String> sections = normal.stream().map(AdminMenuNode::labelKey).toList();
        assertThat(sections.indexOf("menu.rewardEvent")).isEqualTo(sections.indexOf("menu.emoticon") + 1);
        AdminMenuNode section = normal.get(sections.indexOf("menu.rewardEvent"));
        assertThat(section.icon()).isEqualTo("pi pi-gift");
        assertThat(section.children().stream().map(AdminMenuNode::targetKey).toList()).containsExactly("rewardEvents");
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("rewardEvents").orElseThrow();
        assertThat(screen.path()).isEqualTo("/reward-events");
        assertThat(screen.permission()).isEqualTo(Permission.REWARD_EVENT_READ);
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        assertThat(source).contains("{ key: 'rewardEvents', to: '/reward-events', icon: 'pi pi-gift', labelKey: 'menu.rewardEvents', permission: 'reward-event.read' }");
        assertThat(source).containsPattern("key: 'rewardEvent',\\s*icon: 'pi pi-gift',\\s*labelKey: 'menu.rewardEvent',");
    }

    @Test
    void 모든_API_입구가_계약의_권한을_선언한다() {
        Map<String, Permission> declared = new TreeMap<>();
        for (Method m : RewardEventAdminController.class.getDeclaredMethods()) {
            String path = path(m);
            if (path == null) {
                continue;
            }
            RequirePermission rp = m.getAnnotation(RequirePermission.class);
            assertThat(rp).as(path).isNotNull();
            declared.put(path, rp.value()[0]);
        }
        Map<String, Permission> expected = new TreeMap<>(Map.ofEntries(
                Map.entry("GET ", Permission.REWARD_EVENT_READ), Map.entry("GET /catalog", Permission.REWARD_EVENT_READ),
                Map.entry("GET /member-search", Permission.REWARD_EVENT_READ), Map.entry("POST /preview/comments", Permission.REWARD_EVENT_MANAGE),
                Map.entry("POST /preview/targets", Permission.REWARD_EVENT_MANAGE), Map.entry("GET /{id}", Permission.REWARD_EVENT_READ),
                Map.entry("POST ", Permission.REWARD_EVENT_MANAGE), Map.entry("PUT /{id}", Permission.REWARD_EVENT_MANAGE),
                Map.entry("POST /{id}/start", Permission.REWARD_EVENT_MANAGE), Map.entry("POST /{id}/close", Permission.REWARD_EVENT_MANAGE),
                Map.entry("POST /{id}/cancel", Permission.REWARD_EVENT_MANAGE), Map.entry("POST /{id}/recipients/resolve", Permission.REWARD_EVENT_MANAGE),
                Map.entry("POST /{id}/recipients", Permission.REWARD_EVENT_MANAGE), Map.entry("DELETE /{id}/recipients/{recipientId}", Permission.REWARD_EVENT_MANAGE),
                Map.entry("GET /{id}/recipients", Permission.REWARD_EVENT_READ), Map.entry("POST /{id}/draws", Permission.REWARD_EVENT_MANAGE),
                Map.entry("GET /{id}/draws", Permission.REWARD_EVENT_READ), Map.entry("GET /{id}/grants/preview", Permission.REWARD_EVENT_READ),
                Map.entry("POST /{id}/grants", Permission.REWARD_EVENT_GRANT), Map.entry("POST /{id}/grants/retry", Permission.REWARD_EVENT_GRANT),
                Map.entry("POST /{id}/revocations", Permission.REWARD_EVENT_GRANT),
                Map.entry("POST /{id}/recipients/{recipientId}/manual-delivered", Permission.REWARD_EVENT_GRANT),
                Map.entry("GET /{id}/runs", Permission.REWARD_EVENT_READ), Map.entry("GET /{id}/audit", Permission.REWARD_EVENT_READ)));
        assertThat(declared).isEqualTo(expected);
    }

    private static String path(Method m) {
        String[] v;
        String verb;
        if (m.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            v = m.getAnnotation(GetMapping.class).value();
        } else if (m.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            v = m.getAnnotation(PostMapping.class).value();
        } else if (m.isAnnotationPresent(PutMapping.class)) {
            verb = "PUT";
            v = m.getAnnotation(PutMapping.class).value();
        } else if (m.isAnnotationPresent(DeleteMapping.class)) {
            verb = "DELETE";
            v = m.getAnnotation(DeleteMapping.class).value();
        } else {
            return null;
        }
        return verb + " " + (v.length == 0 ? "" : v[0]);
    }

    @Test
    void 회원_알림_두_종류는_끌_수_없는_처리_알림이고_감사_대상_종류는_거르기_선택지에_있다() {
        for (String type : List.of("REWARD_EVENT_GRANTED", "REWARD_EVENT_REVOKED")) {
            MemberNotificationType t = MemberNotificationType.of(type).orElseThrow();
            MemberEvent e = MemberEvent.of(t);
            assertThat(e.featureReady()).isTrue();
            assertThat(e.locked()).isTrue();
        }
        assertThat(AuditLabels.TARGET_TYPES).contains("REWARD_EVENT");
        assertThat(PointReason.EVENT_REWARD_REVOKE.kindOf(-1)).isEqualTo("REVOKE");
        assertThat(PointReason.EVENT_REWARD.kindOf(1)).isEqualTo("EARN");
    }

    @Test
    void 새_문구는_두_언어에_있다() throws IOException {
        Properties ko = bundle("ko");
        Properties en = bundle("en");
        List<String> keys = new ArrayList<>(List.of("point.reason.EVENT_REWARD", "point.reason.EVENT_REWARD_REVOKE", "audit.targetType.REWARD_EVENT",
                "member.notification.REWARD_EVENT_GRANTED.title", "member.notification.REWARD_EVENT_GRANTED.body", "member.notification.REWARD_EVENT_REVOKED.title",
                "member.event.REWARD_EVENT_GRANTED.name", "member.event.REWARD_EVENT_REVOKED.name", "notificationTemplate.var.rewardEventName.desc",
                "notificationTemplate.var.rewardEventRewards.desc", "notificationTemplate.var.rewardEventRevokeReason.desc"));
        for (String code : List.of("REWARD_EVENT_NOT_FOUND", "REWARD_RECIPIENT_NOT_FOUND", "REWARD_EVENT_STATE", "REWARD_EVENT_PERIOD_PASSED", "REWARD_EVENT_HAS_GRANTS",
                "REWARD_EVENT_LOCKED_FIELD", "REWARD_EVENT_TIER_FULL", "REWARD_DRAW_NOT_ENOUGH", "REWARD_EVENT_NO_COMMENT_SOURCE", "REWARD_RECIPIENT_GRANTED",
                "REWARD_REVOKE_NOTHING", "REWARD_DELIVERY_STATE", "POINT_ADJUST_LARGE_REQUIRED", "POST_NOT_FOUND")) {
            keys.add("error." + code);
        }
        Arrays.stream(AuditActions.class.getDeclaredFields()).map(f -> f.getName()).filter(n -> n.startsWith("REWARD_EVENT_")).forEach(n -> keys.add("audit.action." + n));
        for (String key : keys) {
            assertThat(ko.getProperty(key)).as("ko " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        }
        assertThat(ko.stringPropertyNames().stream().filter(k -> k.startsWith("rewardEvent.")).sorted().toList())
                .isEqualTo(en.stringPropertyNames().stream().filter(k -> k.startsWith("rewardEvent.")).sorted().toList());
    }
}
