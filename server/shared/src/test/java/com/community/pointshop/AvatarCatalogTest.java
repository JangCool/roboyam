package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.pointshop.service.AvatarCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 아바타 카탈로그(결정 14, 계약 「1-2 서버 계획」) — 서버 자원 {@code avatars/manifest.json} 328종(무료 22 · 유료 306), 유료 ID 집합 = V86 이 시드한 아바타 품목,
 * 두 앱 {@code public/avatars/roboyam/*.svg} 이름 집합 = 카탈로그.
 */
class AvatarCatalogTest extends PointShopTestBase {

    private static final AvatarCatalog CATALOG = new AvatarCatalog();

    @Test
    void 카탈로그는_328종_무료_22_basic_hobby_유료_306_카테고리_14() {
        assertThat(CATALOG.all()).hasSize(328);
        assertThat(CATALOG.paid()).hasSize(306);
        assertThat(CATALOG.all().stream().filter(AvatarCatalog.Avatar::free).map(AvatarCatalog.Avatar::category).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("basic", "hobby");
        assertThat(CATALOG.categories()).hasSize(14);
        assertThat(CATALOG.categories().get(0).id()).isEqualTo("basic");
        assertThat(CATALOG.categories().stream().mapToInt(AvatarCatalog.Category::count).sum()).isEqualTo(328);
        assertThat(CATALOG.find(AvatarCatalog.DEFAULT_ID)).isPresent();
        assertThat(CATALOG.find("job-doctor-m").orElseThrow().url()).isEqualTo("/avatars/roboyam/job-doctor-m.svg");
    }

    @Test
    void 고른_값이_없거나_카탈로그에_없으면_기본_아바타로_그린다() {
        assertThat(CATALOG.urlOf(null)).isEqualTo("/avatars/roboyam/basic-default.svg");
        assertThat(CATALOG.urlOf("avatar:no-such-avatar")).isEqualTo("/avatars/roboyam/basic-default.svg");
        assertThat(CATALOG.urlOf("profiles/1-x/avatar.png")).as("아바타 값이 아니면").isEqualTo("/avatars/roboyam/basic-default.svg");
        assertThat(CATALOG.urlOf("avatar:hobby-gamer")).isEqualTo("/avatars/roboyam/hobby-gamer.svg");
    }

    @Test
    void 유료_ID_집합은_V86_이_시드한_아바타_품목과_같다_코드_종류_사용_방식_순서() {
        List<Map<String, Object>> rows = kit.jdbc.queryForList("SELECT code, avatar_id, usage_type, stock, duration_days, sale_start_at, sale_end_at, sort_order, name "
                + "FROM point_shop_items WHERE kind = 'PROFILE_AVATAR'");
        assertThat(rows.stream().map(r -> (String) r.get("avatar_id")).collect(Collectors.toSet()))
                .isEqualTo(CATALOG.paid().stream().map(AvatarCatalog.Avatar::id).collect(Collectors.toSet()));
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("avatar_id");
            AvatarCatalog.Avatar avatar = CATALOG.find(id).orElseThrow();
            assertThat(row.get("code")).isEqualTo("avatar-" + id);
            assertThat(row.get("usage_type")).isEqualTo("PERMANENT");
            assertThat(row.get("stock")).isNull();
            assertThat(row.get("duration_days")).isNull();
            assertThat(row.get("sale_start_at")).isNull();
            assertThat(row.get("sale_end_at")).isNull();
            assertThat(row.get("name")).isEqualTo(avatar.name());
            assertThat(row.get("sort_order")).as("카탈로그 순서 — 유료 안 1..306").isEqualTo(CATALOG.paid().indexOf(avatar) + 1);
        }
        assertThat(kit.count("SELECT count(*) FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' AND avatar_id IN ('basic-default', 'hobby-gamer')"))
                .as("무료 아바타는 품목이 없다").isZero();
    }

    @Test
    void 두_앱의_public_avatars_roboyam_svg_이름_집합은_카탈로그_328_과_같다() throws IOException {
        Set<String> catalog = CATALOG.all().stream().map(avatar -> avatar.id() + ".svg").collect(Collectors.toSet());
        for (String app : List.of("user-web", "admin-web")) {
            Path dir = Path.of("..", "..", "apps", app, "public", "avatars", "roboyam");
            try (Stream<Path> files = Files.list(dir)) {
                Set<String> names = files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".svg")).collect(Collectors.toSet());
                assertThat(names).as(app).isEqualTo(catalog);
            }
        }
    }
}
