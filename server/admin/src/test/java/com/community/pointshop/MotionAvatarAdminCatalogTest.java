package com.community.pointshop;

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
 * 아바타 올리기·변경권의 카탈로그 줄(commerce-motion-avatar-contract 「알림·감사·메뉴」) — 관리자 메뉴(닉네임 이미지 심사 바로 다음, admin-web 사본과 같은 줄) · 운영자 알림
 * {@code AVATAR_UPLOAD_PENDING}(닉네임 이미지 심사 권한자) · 회원 알림 {@code AVATAR_UPLOAD_REVIEWED}(끌 수 없음) · 감사 행위(일반 운영)·대상 종류 · 새 문구 두 언어.
 */
class MotionAvatarAdminCatalogTest {

    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MotionAvatarAdminCatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void 아바타_올리기_심사_메뉴는_회원_묶음의_닉네임_이미지_심사_바로_다음이고_admin_web_사본과_같다() throws IOException {
        List<AdminMenuNode> normal = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL);
        AdminMenuNode member = normal.stream().filter(n -> "menu.member".equals(n.labelKey())).findFirst().orElseThrow();
        List<String> keys = member.children().stream().map(AdminMenuNode::targetKey).toList();
        assertThat(keys.indexOf("avatarUploads")).isEqualTo(keys.indexOf("nicknameImages") + 1);
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("avatarUploads").orElseThrow();
        assertThat(screen.path()).isEqualTo("/members/avatar-uploads");
        assertThat(screen.permission()).isEqualTo(Permission.NICKNAME_IMAGE_READ);
        assertThat(screen.superOnly()).isFalse();
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        String expected = "{ key: 'avatarUploads', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                + screen.permission().code() + "' }";
        assertThat(source).contains(expected);
        assertThat(source.indexOf(expected)).isGreaterThan(source.indexOf("key: 'nicknameImages'"));
    }

    @Test
    void 운영자_알림_회원_알림_감사_행위() {
        AdminNotificationEvent pending = AdminNotificationEvent.AVATAR_UPLOAD_PENDING;
        assertThat(pending.requiredPermission()).isEqualTo(Permission.NICKNAME_IMAGE_MANAGE);
        assertThat(pending.linkPath()).isEqualTo("/members/avatar-uploads");
        assertThat(pending.featureReady()).isTrue();
        assertThat(MemberNotificationType.of("AVATAR_UPLOAD_REVIEWED")).isPresent();
        assertThat(MemberEvent.AVATAR_UPLOAD_REVIEWED.type()).isEqualTo(MemberNotificationType.AVATAR_UPLOAD_REVIEWED);
        assertThat(MemberEvent.AVATAR_UPLOAD_REVIEWED.locked()).as("운영자 처리 알림 — 회원이 끌 수 없다").isTrue();
        assertThat(MemberEvent.AVATAR_UPLOAD_REVIEWED.featureReady()).isTrue();
        for (String action : List.of(AuditActions.AVATAR_UPLOAD_APPROVE, AuditActions.AVATAR_UPLOAD_REJECT, AuditActions.AVATAR_UPLOAD_REVOKE)) {
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.GENERAL);
        }
        assertThat(AuditLabels.TARGET_TYPES).contains("AVATAR_UPLOAD");
    }

    @Test
    void 새_문구는_두_언어에_있다() throws IOException {
        Properties ko = bundle("ko");
        Properties en = bundle("en");
        List<String> keys = new ArrayList<>(MemberEvent.AVATAR_UPLOAD_REVIEWED.bundleKeys());
        keys.addAll(AdminNotificationEvent.AVATAR_UPLOAD_PENDING.bundleKeys());
        for (String result : List.of("APPROVED", "REJECTED", "REVOKED")) {
            keys.add("member.notification.AVATAR_UPLOAD_REVIEWED.title." + result);
            keys.add("member.notification.AVATAR_UPLOAD_REVIEWED.result." + result);
        }
        keys.addAll(List.of("member.notification.AVATAR_UPLOAD_REVIEWED.title", "notificationTemplate.var.avatarUploadResult.desc", "notificationTemplate.var.avatarUploadReason.desc",
                "error.NICKNAME_IMAGE_TICKET_REQUIRED", "error.AVATAR_UPLOAD_TICKET_REQUIRED", "error.POINT_SHOP_REFUND_NOT_ALLOWED.RESERVED", "error.AVATAR_UPLOAD_NOT_FOUND",
                "error.AVATAR_UPLOAD_REQUEST_NOT_FOUND", "error.AVATAR_UPLOAD_NOT_PENDING", "error.AVATAR_UPLOAD_NOT_ACTIVE", "avatarUpload.error.EMAIL_NOT_VERIFIED",
                "avatarUpload.error.ACCOUNT_RESTRICTED", "avatarUpload.admin.status.invalid", "avatarUpload.admin.page.range", "avatarUpload.admin.reason.range",
                "audit.targetType.AVATAR_UPLOAD"));
        for (String file : List.of("required", "size", "type", "corrupt", "pixels", "animatedWebp", "frames", "tooSmall", "ratio")) {
            keys.add("avatarUpload.file." + file);
        }
        for (String action : List.of(AuditActions.AVATAR_UPLOAD_APPROVE, AuditActions.AVATAR_UPLOAD_REJECT, AuditActions.AVATAR_UPLOAD_REVOKE)) {
            keys.add("audit.action." + action);
        }
        for (String key : keys) {
            assertThat(ko.getProperty(key)).as("ko " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        }
    }
}
