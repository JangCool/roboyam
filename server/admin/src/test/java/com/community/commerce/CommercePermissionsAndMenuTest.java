package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 0-5 권한 10개(docs/domain/commerce.md 13-2, commerce-foundation-contract 결정 12)와 커머스 설정 메뉴 — 서버 {@code Permission}, 서버가 뜰 때 맞추는 {@code permissions} 표,
 * admin-web 권한 카탈로그({@code util-permission-catalog.ts})가 같은 유형·단위·「기능 준비 중」인지. 실제 관리자 앱 문맥 + PostgreSQL.
 */
class CommercePermissionsAndMenuTest extends CommerceAdminTestBase {

    /** @param ready 기능이 생겼나(featureReady) — point.adjust.large 는 커머스 1-1, pointshop.*(0-5 의 shop.* 를 이름만 바꿈)은 1-2, commerce.payment.read·commerce.reconcile.manage 는 2a-3 에서 생겼다 */
    record Expected(String code, PermissionType type, PermissionUnit unit, String frontType, boolean ready) {
    }

    private static final List<Expected> NEW = List.of(
            new Expected("commerce.groupbuy.cancel", PermissionType.GB_ORDER, PermissionUnit.SENSITIVE, "gbOrder", false),
            new Expected("commerce.refund.large", PermissionType.GB_ORDER, PermissionUnit.SENSITIVE, "gbOrder", false),
            new Expected("commerce.payment.read", PermissionType.GB_SETTLE, PermissionUnit.READ, "gbSettle", true),
            new Expected("commerce.reconcile.manage", PermissionType.GB_SETTLE, PermissionUnit.SENSITIVE, "gbSettle", true),
            new Expected("pointshop.read", PermissionType.GRADE_POINT, PermissionUnit.READ, "gradePoint", true),
            new Expected("pointshop.manage", PermissionType.GRADE_POINT, PermissionUnit.MANAGE, "gradePoint", true),
            new Expected("pointshop.refund", PermissionType.GRADE_POINT, PermissionUnit.SENSITIVE, "gradePoint", true),
            new Expected("point.adjust.large", PermissionType.GRADE_POINT, PermissionUnit.SENSITIVE, "gradePoint", true),
            new Expected("point.refund.large", PermissionType.GRADE_POINT, PermissionUnit.SENSITIVE, "gradePoint", false),
            new Expected("point.refund.approve", PermissionType.GRADE_POINT, PermissionUnit.SENSITIVE, "gradePoint", false));

    @Test
    void 권한_10개는_결정_12_의_유형과_단위이고_고액_포인트_조정과_포인트_상점은_기능이_생겼다() {
        assertThat(NEW).hasSize(10);
        for (Expected expected : NEW) {
            Permission permission = Permission.ofCode(expected.code()).orElseThrow(() -> new AssertionError("Permission 에 없다: " + expected.code()));
            assertThat(permission.type()).as(expected.code()).isEqualTo(expected.type());
            assertThat(permission.unit()).as(expected.code()).isEqualTo(expected.unit());
            assertThat(permission.featureReady()).as(expected.code()).isEqualTo(expected.ready());
        }
        assertThat(Permission.ofCode("member.withdrawn.contact")).as("0-4 몫 — 0-5 가 더하지 않는다(0-4 가 합치면 있다)").satisfiesAnyOf(
                found -> assertThat(found).isEmpty(), found -> assertThat(found.get().type()).isEqualTo(PermissionType.MEMBER_VIEW));
    }

    @Test
    void 서버가_뜰_때_permissions_표에도_같은_값으로_들어간다() {
        for (Expected expected : NEW) {
            Map<String, Object> row = jdbc.queryForMap("SELECT axis, type_id, category, feature_ready FROM permissions WHERE code = ?", expected.code());
            assertThat(row.get("axis")).isEqualTo("OPERATOR");
            assertThat(row.get("type_id")).as(expected.code()).isEqualTo(expected.frontType());
            assertThat(row.get("category")).as(expected.code()).isEqualTo(expected.unit().name());
            assertThat(row.get("feature_ready")).as(expected.code()).isEqualTo(expected.ready());
        }
    }

    @Test
    void admin_web_권한_카탈로그에_같은_코드_유형_단위로_기능_준비_중이다() throws Exception {
        Path catalog = Path.of("..", "..", "apps", "admin-web", "app", "utils", "util-permission-catalog.ts");
        List<String> lines = Files.readAllLines(catalog, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (Expected expected : NEW) {
            String line = lines.stream().filter(l -> l.contains("code: '" + expected.code() + "'")).findFirst().orElse(null);
            if (line == null) {
                missing.add(expected.code());
                continue;
            }
            assertThat(line).as(expected.code()).contains("unit: '" + expected.unit().name() + "'").contains("type: '" + expected.frontType() + "'")
                    .contains("featureReady: " + expected.ready());
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void 커머스_설정_메뉴는_commerce_settings_권한이고_알림_이벤트_묶음과_운영자와_권한_묶음_사이() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("commerceSettings").orElseThrow();
        assertThat(screen.path()).isEqualTo("/settings/commerce");
        assertThat(screen.permission()).isEqualTo(Permission.COMMERCE_SETTINGS);
        assertThat(screen.labelKey()).isEqualTo("menu.commerceSettings");
        assertThat(screen.superOnly()).isFalse();
        assertThat(AdminScreenRegistry.isGroupLabelKey("menu.commerce")).isTrue();

        List<String> keys = new ArrayList<>();
        AdminScreenRegistry.defaults().get(AdminScreenRegistry.SETTINGS).forEach(node -> collect(node, keys));
        assertThat(keys).containsSubsequence("memberEvents", "commerceSettings", "operators");
    }

    private static void collect(com.community.menu.dto.AdminMenuNode node, List<String> into) {
        if (node.targetKey() != null) {
            into.add(node.targetKey());
        }
        node.children().forEach(child -> collect(child, into));
    }
}
