package com.community.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
import com.community.withdrawal.controller.MemberWithdrawalAdminController;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 회원 탈퇴 관리(member-withdrawal-contract 「관리자」)의 카탈로그 줄 — 권한 {@code member.withdraw} · 메뉴 {@code memberWithdrawals} · 감사 {@code MEMBER_WITHDRAW_FORCE} 가 서버와 admin-web
 * (권한 카탈로그 · 메뉴 정의)에서 같은 코드·유형·차례이고, 새 문구가 두 언어에 있으며, 모든 API 입구가 계약의 권한을 선언한다.
 */
class MemberWithdrawalAdminCatalogTest {

    private static final Path ADMIN_WEB = Path.of("..", "..", "apps", "admin-web", "app");

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MemberWithdrawalAdminCatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void 권한_member_withdraw_는_회원_조회_유형의_민감_조치이고_기능이_있으며_연락처_열람_바로_다음이다() {
        Permission permission = Permission.ofCode("member.withdraw").orElseThrow();
        assertThat(permission).isEqualTo(Permission.MEMBER_WITHDRAW);
        assertThat(permission.type()).isEqualTo(PermissionType.MEMBER_VIEW);
        assertThat(permission.unit()).isEqualTo(PermissionUnit.SENSITIVE);
        assertThat(permission.featureReady()).isTrue();
        List<String> codes = Permission.allCodes();
        assertThat(codes.indexOf("member.withdraw")).isEqualTo(codes.indexOf("member.withdrawn.contact") + 1);
    }

    @Test
    void admin_web_권한_카탈로그에_같은_코드_유형_단위_차례로_있다() throws Exception {
        List<String> lines = Files.readAllLines(ADMIN_WEB.resolve(Path.of("utils", "util-permission-catalog.ts")), StandardCharsets.UTF_8);
        int contact = indexOf(lines, "code: 'member.withdrawn.contact'");
        int withdraw = indexOf(lines, "code: 'member.withdraw'");
        assertThat(contact).isNotNegative();
        assertThat(withdraw).isGreaterThan(contact);
        assertThat(lines.get(withdraw)).contains("unit: 'SENSITIVE'").contains("type: 'memberView'").contains("featureReady: true");
        // 둘 사이에 다른 권한 줄이 없다(순서가 같다)
        assertThat(lines.subList(contact + 1, withdraw).stream().filter(l -> l.contains("code: '")).toList()).isEmpty();
    }

    @Test
    void 메뉴_탈퇴_관리는_회원_묶음에서_회원_관리_바로_다음이고_admin_web_메뉴_정의와_같다() throws Exception {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("memberWithdrawals").orElseThrow();
        assertThat(screen.path()).isEqualTo("/members/withdrawals");
        assertThat(screen.labelKey()).isEqualTo("menu.memberWithdrawals");
        assertThat(screen.icon()).isEqualTo("pi pi-user-minus");
        assertThat(screen.permission()).isEqualTo(Permission.MEMBER_READ);
        assertThat(screen.superOnly()).isFalse();

        List<String> keys = new ArrayList<>();
        AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL).forEach(node -> collect(node, keys));
        assertThat(keys.indexOf("memberWithdrawals")).isEqualTo(keys.indexOf("members") + 1);
        assertThat(keys).containsSubsequence("members", "memberWithdrawals", "grades", "nicknameImages");

        List<String> lines = Files.readAllLines(ADMIN_WEB.resolve(Path.of("utils", "util-admin-menu.ts")), StandardCharsets.UTF_8);
        int members = indexOf(lines, "key: 'members'");
        int withdrawals = indexOf(lines, "key: 'memberWithdrawals'");
        int grades = indexOf(lines, "key: 'grades'");
        assertThat(members).isNotNegative();
        assertThat(withdrawals).isGreaterThan(members).isLessThan(grades);
        assertThat(lines.get(withdrawals)).contains("to: '/members/withdrawals'").contains("icon: 'pi pi-user-minus'").contains("labelKey: 'menu.memberWithdrawals'")
                .contains("permission: 'member.read'");
        assertThat(lines.subList(members + 1, withdrawals).stream().filter(l -> l.contains("key: '")).toList()).isEmpty();
    }

    @Test
    void 감사_강제_탈퇴는_목록에_있고_돈_관련_5년이며_대리_취소는_회원_취소와_같은_행위_이름이다() {
        assertThat(AuditActions.all()).contains(AuditActions.MEMBER_WITHDRAW_FORCE, AuditActions.MEMBER_WITHDRAW_CANCEL);
        assertThat(AuditRetention.declared(AuditActions.MEMBER_WITHDRAW_FORCE)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.declared(AuditActions.MEMBER_WITHDRAW_CANCEL)).contains(AuditRetentionClass.GENERAL);
    }

    @Test
    void 새_문구는_두_언어에_모두_있다() throws IOException {
        List<String> keys = List.of("audit.action." + AuditActions.MEMBER_WITHDRAW_FORCE, "error.WITHDRAWAL_NOT_FOUND", "error.WITHDRAWAL_NOT_MEMBER_REQUESTED",
                "error.MEMBER_ALREADY_WITHDRAWN", "error.WITHDRAWAL_ALREADY_SCHEDULED", "error.WITHDRAWAL_NOT_SCHEDULED", "withdrawalAdmin.reasonKind.invalid",
                "withdrawalAdmin.requestChannel.invalid", "withdrawalAdmin.memo.length", "withdrawalAdmin.memberConfirmed.required", "withdrawalAdmin.query.invalid",
                "withdrawalAdmin.query.reversed");
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String key : keys) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void 모든_입구가_계약의_권한을_선언한다_보기_member_read_강제_탈퇴_대리_취소_미리보기_member_withdraw() {
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        for (Method method : MemberWithdrawalAdminController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class) && !method.isAnnotationPresent(PostMapping.class)) {
                continue;
            }
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required).as(method.getName() + " 는 권한을 선언해야 한다").isNotNull();
            assertThat(required.anyOf()).as(method.getName()).isEmpty();
            declared.put(method.getName(), Arrays.asList(required.value()));
        }
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(Map.of(
                "list", List.of(Permission.MEMBER_READ), "summary", List.of(Permission.MEMBER_READ), "reasons", List.of(Permission.MEMBER_READ),
                "detail", List.of(Permission.MEMBER_READ), "forMember", List.of(Permission.MEMBER_READ),
                "preview", List.of(Permission.MEMBER_WITHDRAW), "force", List.of(Permission.MEMBER_WITHDRAW), "cancel", List.of(Permission.MEMBER_WITHDRAW)));
    }

    private static int indexOf(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static void collect(AdminMenuNode node, List<String> into) {
        if (node.targetKey() != null) {
            into.add(node.targetKey());
        }
        node.children().forEach(child -> collect(child, into));
    }
}
