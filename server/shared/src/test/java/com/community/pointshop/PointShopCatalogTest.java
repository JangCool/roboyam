package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.pointshop.dto.request.PointShopAvatarScope;
import com.community.pointshop.dto.request.PointShopAvatarTarget;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.dto.response.PointShopAdminItemView;
import com.community.pointshop.dto.response.PointShopAvatarBulkResultView;
import com.community.pointshop.dto.response.PointShopAvatarCategorySummaryView;
import com.community.pointshop.dto.response.PointShopAvatarSummaryView;
import com.community.pointshop.dto.response.PointShopItemListView;
import com.community.pointshop.dto.response.PointShopItemView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.exception.PointShopItemNotFoundException;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * 포인트 상점 품목(계약 「B」·「1-2 화면 목업」 관리자) — 만들기·고치기 칸 검사(종류별 칸 조합·종류·코드 불변), 아바타 품목 제한·묶음 가격·묶음 판매, 회원 목록, 색을 고치면 장식 표시가 바뀜,
 * 설정, 감사 분류. 실제 PostgreSQL.
 */
class PointShopCatalogTest extends PointShopTestBase {

    private static Map<String, ?> fields(Supplier<?> work) {
        try {
            work.get();
        } catch (ValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("400 VALIDATION 이 아니다");
    }

    private static String text(Object value) {
        return value == null ? null : value instanceof Enum<?> e ? e.name() : value.toString();
    }

    private static PointShopItemSaveRequest with(PointShopItemSaveRequest r, String field, Object value) {
        return new PointShopItemSaveRequest(field.equals("code") ? (String) value : r.code(), field.equals("kind") ? text(value) : r.kind(),
                field.equals("usageType") ? text(value) : r.usageType(), field.equals("name") ? (String) value : r.name(), r.nameEn(),
                field.equals("description") ? (String) value : r.description(), r.descriptionEn(), field.equals("color") ? (String) value : r.color(),
                field.equals("borderStyle") ? (String) value : r.borderStyle(), field.equals("avatarId") ? (String) value : r.avatarId(),
                field.equals("pricePoint") ? (Integer) value : r.pricePoint(), field.equals("durationDays") ? (Integer) value : r.durationDays(),
                field.equals("stock") ? (Integer) value : r.stock(), field.equals("saleStartAt") ? (Instant) value : r.saleStartAt(),
                field.equals("saleEndAt") ? (Instant) value : r.saleEndAt(), r.active(), field.equals("sortOrder") ? (Integer) value : r.sortOrder());
    }

    @Test
    void 만들기는_종류별_칸_조합과_범위를_칸마다_검사한다() {
        PointShopItemSaveRequest color = PointShopTestKit.request(kit.code("v"), PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#123456", null, null,
                null, null, null, true);
        assertThat(fields(() -> kit.create(with(color, "color", "red")))).containsOnlyKeys("color");
        assertThat(fields(() -> kit.create(with(color, "borderStyle", "GOLD")))).as("색 품목에 테두리 칸").containsOnlyKeys("borderStyle");
        assertThat(fields(() -> kit.create(with(color, "usageType", PointShopUsageType.CONSUMABLE)))).containsOnlyKeys("usageType");
        assertThat(fields(() -> kit.create(with(color, "pricePoint", 0)))).containsOnlyKeys("pricePoint");
        assertThat(fields(() -> kit.create(with(color, "pricePoint", 100_001)))).containsOnlyKeys("pricePoint");
        assertThat(fields(() -> kit.create(with(color, "durationDays", 30)))).as("영구에 기간").containsOnlyKeys("durationDays");
        assertThat(fields(() -> kit.create(with(with(color, "usageType", PointShopUsageType.PERIOD), "durationDays", 366)))).containsOnlyKeys("durationDays");
        assertThat(fields(() -> kit.create(with(color, "stock", -1)))).containsOnlyKeys("stock");
        Instant now = kit.clock.instant();
        assertThat(fields(() -> kit.create(with(with(color, "saleStartAt", now), "saleEndAt", now)))).containsOnlyKeys("saleEndAt");
        assertThat(fields(() -> kit.create(with(color, "code", "AB")))).containsOnlyKeys("code");
        assertThat(fields(() -> kit.create(with(color, "code", "avatar-mine")))).containsOnlyKeys("code");
        assertThat(fields(() -> kit.create(with(color, "name", " ")))).containsOnlyKeys("name");
        assertThat(fields(() -> kit.create(with(color, "sortOrder", -1)))).containsOnlyKeys("sortOrder");
        assertThat(fields(() -> kit.create(with(with(color, "kind", "HAT"), "pricePoint", 0)))).as("모르는 종류도 다른 칸과 함께").containsOnlyKeys("kind", "pricePoint");
        assertThat(fields(() -> kit.create(with(color, "usageType", "FOREVER")))).containsOnlyKeys("usageType");

        PointShopItemSaveRequest border = PointShopTestKit.request(kit.code("v"), PointShopItemKind.PROFILE_BORDER, PointShopUsageType.PERMANENT, 100, null, "PINK", null,
                null, null, null, true);
        assertThat(fields(() -> kit.create(border))).as("팔레트 7개 밖").containsOnlyKeys("borderStyle");
        PointShopItemSaveRequest ticket = PointShopTestKit.request(kit.code("v"), PointShopItemKind.NICKNAME_CHANGE_TICKET, PointShopUsageType.PERMANENT, 100, null, null,
                null, null, null, null, true);
        assertThat(fields(() -> kit.create(ticket))).as("변경권은 소모형만").containsOnlyKeys("usageType");
        PointShopItemSaveRequest avatar = new PointShopItemSaveRequest(kit.code("v"), "PROFILE_AVATAR", "PERMANENT", "새 아바타", null,
                "설명", null, null, null, "job-doctor-m", 100, null, null, null, null, true, 0);
        assertThat(fields(() -> kit.create(avatar))).as("아바타는 만들 수 없다").containsKeys("kind");

        String taken = kit.code("dup");
        kit.create(with(color, "code", taken));
        assertThat(fields(() -> kit.create(with(color, "code", taken)))).containsOnlyKeys("code");

        Map<String, ?> many = fields(() -> kit.create(new PointShopItemSaveRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null)));
        assertThat(many.keySet()).as("칸 차례 = 요청 선언 차례").containsExactly("code", "kind", "usageType", "name", "description", "pricePoint", "active", "sortOrder");
    }

    @Test
    void 만들기와_고치기는_감사를_남기고_고치기는_바뀐_칸만_종류와_코드는_못_바꾼다() {
        PointShopAdminItemView created = kit.create(PointShopTestKit.request(kit.code("a"), PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#111111",
                null, null, null, null, null, true));
        long id = Long.parseLong(created.id());
        assertThat(created.createdBy()).isNotNull();
        assertThat(created.soldCount()).isZero();
        AuditEvent create = kit.audits.stream().filter(e -> e.action().equals(AuditActions.POINT_SHOP_ITEM_CREATE)).findFirst().orElseThrow();
        assertThat(create.targetId()).isEqualTo(created.id());

        PointShopItemSaveRequest same = new PointShopItemSaveRequest(created.code(), created.kind().name(), created.usageType().name(), created.name(), null, created.description(), null,
                created.color(), null, null, 100, null, null, null, null, true, 0);
        assertThat(fields(() -> kit.inTx(() -> kit.catalog.update(id, with(same, "code", "other-code"), kit.operator, null)))).containsOnlyKeys("code");
        assertThat(fields(() -> kit.inTx(() -> kit.catalog.update(id, with(same, "kind", PointShopItemKind.PROFILE_BORDER), kit.operator, null)))).containsKeys("kind");

        kit.audits.clear();
        kit.inTx(() -> kit.catalog.update(id, same, kit.operator, null));
        assertThat(kit.audits).as("바뀐 칸이 없으면 감사도 없다").isEmpty();

        PointShopAdminItemView updated = kit.inTx(() -> kit.catalog.update(id, with(same, "pricePoint", 250), kit.operator, "1.2.3.4"));
        assertThat(updated.pricePoint()).isEqualTo(250);
        AuditEvent update = kit.audits.get(0);
        assertThat(update.action()).isEqualTo(AuditActions.POINT_SHOP_ITEM_UPDATE);
        assertThat(update.detail()).containsEntry("before", Map.of("pricePoint", 100)).containsEntry("after", Map.of("pricePoint", 250));
        for (String action : List.of(AuditActions.POINT_SHOP_ITEM_CREATE, AuditActions.POINT_SHOP_ITEM_UPDATE, AuditActions.POINT_SHOP_AVATAR_PRICE,
                AuditActions.POINT_SHOP_AVATAR_SALE, AuditActions.POINT_SHOP_SETTINGS_UPDATE)) {
            assertThat(AuditRetention.classify(action)).as(action + " 일반 운영").isEqualTo(AuditRetentionClass.GENERAL);
        }
    }

    @Test
    void 색을_고치면_산_사람의_장식_표시도_바뀐다_가격은_사본_그대로() {
        long item = kit.color(300, "#111111");
        long member = kit.newMember();
        kit.grant(member, 300);
        long userItem = kit.buyItem(member, item);
        kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, userItem));
        assertThat(kit.decorations.look(member).decoration().nicknameColor()).isEqualTo("#111111");
        PointShopAdminItemView current = kit.catalog.adminItem(item);

        kit.inTx(() -> kit.catalog.update(item, new PointShopItemSaveRequest(current.code(), current.kind().name(), current.usageType().name(), current.name(), null,
                current.description(), null, "#FF0000", null, null, 999, null, null, null, null, true, 0), kit.operator, null));

        assertThat(kit.decorations.look(member).decoration().nicknameColor()).isEqualTo("#FF0000");
        assertThat(kit.userItems.equipped(member).nicknameColor().color()).isEqualTo("#FF0000");
        assertThat(kit.userItems.myItems(member).items().get(0).pricePaid()).isEqualTo(300);
        assertThat(kit.catalog.adminItem(item).soldCount()).isEqualTo(1);
    }

    @Test
    void 장식은_여러_작성자를_한_번에_풀고_장착이_없으면_null_아바타는_늘_채운다() {
        long colorItem = kit.color(100, "#222222");
        long borderItem = kit.border(100, "GOLD", null);
        long a = kit.newMember();
        long b = kit.newMember();
        long c = kit.newMember();
        kit.grant(a, 200);
        kit.grant(b, 200);
        long ac = kit.buyItem(a, colorItem);
        long ab = kit.buyItem(a, borderItem);
        long bb = kit.buyItem(b, borderItem);
        kit.inTx(() -> kit.userItems.equip(a, EquipSlot.NICKNAME_COLOR, ac));
        kit.inTx(() -> kit.userItems.equip(a, EquipSlot.PROFILE_BORDER, ab));
        kit.inTx(() -> kit.userItems.equip(b, EquipSlot.PROFILE_BORDER, bb));

        var looks = kit.decorations.resolve(List.of(a, b, c));

        assertThat(looks.get(a).decoration().nicknameColor()).isEqualTo("#222222");
        assertThat(looks.get(a).decoration().profileBorder()).isEqualTo("GOLD");
        assertThat(looks.get(b).decoration().nicknameColor()).isNull();
        assertThat(looks.get(b).decoration().profileBorder()).isEqualTo("GOLD");
        assertThat(looks.get(c).decoration()).isNull();
        assertThat(looks.get(c).avatarUrl()).isEqualTo("/avatars/roboyam/basic-default.svg");
    }

    @Test
    void 회원_목록은_판매_끝과_아바타를_빼고_내_보유를_싣는다() {
        long member = kit.newMember();
        kit.grant(member, 1000);
        Instant now = kit.clock.instant();
        long onSale = kit.color(100, "#333333");
        long ended = Long.parseLong(kit.create(PointShopTestKit.request(kit.code("e"), PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#444444", null,
                null, null, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)), true)).id());
        long upcoming = Long.parseLong(kit.create(PointShopTestKit.request(kit.code("u"), PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, 100, "#555555",
                null, null, null, now.plus(Duration.ofDays(1)), null, true)).id());
        kit.buy(member, onSale);

        PointShopItemListView all = kit.catalog.memberList(member, null);
        List<String> ids = all.items().stream().map(PointShopItemView::id).toList();
        assertThat(ids).contains(Long.toString(onSale), Long.toString(upcoming)).doesNotContain(Long.toString(ended));
        assertThat(all.items()).noneMatch(view -> view.kind() == PointShopItemKind.PROFILE_AVATAR);
        assertThat(all.balance()).isEqualTo(900);
        assertThat(all.refundDays()).isEqualTo(7);
        PointShopItemView mine = all.items().stream().filter(view -> view.id().equals(Long.toString(onSale))).findFirst().orElseThrow();
        assertThat(mine.mine().owned()).isTrue();
        assertThat(kit.catalog.memberItem(member, ended).saleState().name()).as("상세는 판매가 끝나도 열린다").isEqualTo("ENDED");
        assertThat(kit.catalog.memberList(member, PointShopItemKind.PROFILE_AVATAR).items()).as("아바타 거르기").hasSize(306);
    }

    @Test
    void 아바타_품목은_이름_영문명_가격_순서_사용_여부만_고치고_DB_가_지우기를_막는다() {
        long item = kit.avatarItem("job-judge-m");
        PointShopAdminItemView before = kit.catalog.adminItem(item);
        assertThat(before.category()).isEqualTo("job");
        PointShopItemSaveRequest edit = new PointShopItemSaveRequest(before.code(), before.kind().name(), before.usageType().name(), "판사(남)", "Judge", before.description(),
                before.descriptionEn(), null, null, before.avatarId(), 1500, null, null, null, null, true, before.sortOrder());
        try {
            assertThat(fields(() -> kit.inTx(() -> kit.catalog.update(item, with(edit, "description", "다른 설명"), kit.operator, null)))).containsOnlyKeys("description");
            assertThat(fields(() -> kit.inTx(() -> kit.catalog.update(item, with(edit, "avatarId", "job-judge-f"), kit.operator, null)))).containsOnlyKeys("avatarId");
            assertThat(fields(() -> kit.inTx(() -> kit.catalog.update(item, with(edit, "stock", 3), kit.operator, null)))).containsOnlyKeys("stock");

            PointShopAdminItemView after = kit.inTx(() -> kit.catalog.update(item, edit, kit.operator, null));
            assertThat(after.name()).isEqualTo("판사(남)");
            assertThat(after.pricePoint()).isEqualTo(1500);
        } finally {
            kit.jdbc.update("UPDATE point_shop_items SET name = ?, name_en = NULL, price_point = 1000 WHERE id = ?", before.name(), item);
        }
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM point_shop_items WHERE id = ?", item)).hasMessageContaining("cannot be deleted");
    }

    @Test
    void 아바타_묶음_가격과_판매는_범위_안의_품목만_바꾸고_무료_카테고리와_아바타가_아닌_품목은_거절한다() {
        long other = kit.color(100, "#666666");
        List<Long> traditional = kit.jdbc.queryForList("SELECT id FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' AND avatar_id LIKE 'traditional-%' ORDER BY id", Long.class);
        assertThat(traditional).hasSize(8);
        try {
            PointShopAvatarBulkResultView price = kit.inTx(() -> kit.catalog.avatarPrice(new PointShopAvatarTarget(PointShopAvatarScope.CATEGORIES, List.of("traditional"),
                    null), 1200, kit.operator, null));
            assertThat(price.matched()).isEqualTo(8);
            assertThat(price.changed()).isEqualTo(8);
            assertThat(kit.inTx(() -> kit.catalog.avatarPrice(new PointShopAvatarTarget(PointShopAvatarScope.CATEGORIES, List.of("traditional"), null), 1200, kit.operator,
                    null)).changed()).as("같은 값이면 0").isZero();
            assertThat(kit.count("SELECT count(*) FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' AND price_point = 1200")).isEqualTo(8);

            PointShopAvatarBulkResultView sale = kit.inTx(() -> kit.catalog.avatarSale(new PointShopAvatarTarget(PointShopAvatarScope.ITEMS, null,
                    List.of(Long.toString(traditional.get(0)), Long.toString(traditional.get(1)))), false, kit.operator, null));
            assertThat(sale.matched()).isEqualTo(2);
            assertThat(sale.changed()).isEqualTo(2);
            assertThat(sale.active()).isFalse();

            PointShopAvatarSummaryView summary = kit.catalog.avatarSummary();
            PointShopAvatarCategorySummaryView trad = summary.categories().stream().filter(c -> c.category().equals("traditional")).findFirst().orElseThrow();
            assertThat(trad.total()).isEqualTo(8);
            assertThat(trad.forSale()).isEqualTo(6);
            assertThat(trad.stopped()).isEqualTo(2);
            assertThat(trad.minPrice()).isEqualTo(1200);
            PointShopAvatarCategorySummaryView basic = summary.categories().get(0);
            assertThat(basic.category()).isEqualTo("basic");
            assertThat(basic.free()).isTrue();
            assertThat(basic.minPrice()).isNull();
            assertThat(summary.totals().total()).isEqualTo(328);
            assertThat(summary.totals().freeCount()).isEqualTo(22);
            assertThat(summary.totals().paidCount()).isEqualTo(306);
            assertThat(summary.totals().stopped()).isEqualTo(2);

            var avatarView = kit.profileAvatars.catalog(null).items().stream().filter(a -> a.itemId() != null && a.itemId().equals(Long.toString(traditional.get(0))))
                    .findFirst().orElseThrow();
            assertThat(avatarView.forSale()).isFalse();
            assertThat(avatarView.price()).isEqualTo(1200);

            assertThat(fields(() -> kit.inTx(() -> kit.catalog.avatarPrice(new PointShopAvatarTarget(PointShopAvatarScope.CATEGORIES, List.of("basic"), null), 100,
                    kit.operator, null)))).containsOnlyKeys("categories");
            assertThat(fields(() -> kit.inTx(() -> kit.catalog.avatarSale(new PointShopAvatarTarget(PointShopAvatarScope.ITEMS, null, List.of(Long.toString(other))),
                    true, kit.operator, null)))).containsOnlyKeys("itemIds");
            assertThatThrownBy(() -> kit.inTx(() -> kit.catalog.avatarSale(new PointShopAvatarTarget(PointShopAvatarScope.ITEMS, null,
                    List.of(Long.toString(traditional.get(2)), "999999999")), false, kit.operator, null))).as("없는 품목이 섞이면 404")
                    .isInstanceOf(PointShopItemNotFoundException.class);
            assertThatThrownBy(() -> kit.inTx(() -> kit.catalog.avatarSale(new PointShopAvatarTarget(PointShopAvatarScope.ITEMS, null, List.of("abc")), false,
                    kit.operator, null))).isInstanceOf(PointShopItemNotFoundException.class);
            assertThat(kit.jdbc.queryForObject("SELECT active FROM point_shop_items WHERE id = ?", Boolean.class, traditional.get(2))).as("아무것도 안 바뀜").isTrue();
            assertThat(fields(() -> kit.inTx(() -> kit.catalog.avatarPrice(new PointShopAvatarTarget(PointShopAvatarScope.ALL, List.of("job"), null), 0, kit.operator,
                    null)))).containsOnlyKeys("categories", "price");
            assertThat(fields(() -> kit.inTx(() -> kit.catalog.avatarPrice(new PointShopAvatarTarget(null, null, null), 100, kit.operator, null)))).containsKeys("scope");
            assertThat(kit.audits.stream().filter(e -> e.action().equals(AuditActions.POINT_SHOP_AVATAR_SALE)).findFirst().orElseThrow().detail())
                    .containsEntry("matched", 2).containsEntry("changed", 2).containsEntry("active", false);
        } finally {
            kit.jdbc.update("UPDATE point_shop_items SET price_point = 1000, active = true WHERE kind = 'PROFILE_AVATAR'");
        }
        PointShopAvatarBulkResultView all = kit.inTx(() -> kit.catalog.avatarSale(new PointShopAvatarTarget(PointShopAvatarScope.ALL, null, null), true, kit.operator, null));
        assertThat(all.matched()).isEqualTo(306);
        assertThat(all.changed()).isZero();
    }

    @Test
    void 설정은_1에서_30일_바꾸면_감사_같은_값이면_그대로() {
        assertThat(kit.catalog.settings().defaultRefundDays()).isEqualTo(7);
        assertThat(fields(() -> kit.inTx(() -> kit.catalog.updateSettings(0, kit.operator, null)))).containsOnlyKeys("refundDays");
        assertThat(fields(() -> kit.inTx(() -> kit.catalog.updateSettings(31, kit.operator, null)))).containsOnlyKeys("refundDays");
        int before = kit.catalog.settings().refundDays();
        try {
            var view = kit.inTx(() -> kit.catalog.updateSettings(14, kit.operator, null));
            assertThat(view.refundDays()).isEqualTo(14);
            assertThat(view.updatedBy()).isNotNull();
            assertThat(kit.audits).hasSize(1);
            assertThat(kit.audits.get(0).detail()).containsEntry("before", Map.of("refundDays", before)).containsEntry("after", Map.of("refundDays", 14));
            kit.inTx(() -> kit.catalog.updateSettings(14, kit.operator, null));
            assertThat(kit.audits).hasSize(1);
        } finally {
            kit.jdbc.update("UPDATE point_shop_settings SET refund_days = ? WHERE id = 1", before);
        }
    }

    @Test
    void 관리자_목록은_종류_거르기와_빼기를_한다() {
        kit.color(100, "#777777");
        var withoutAvatars = kit.catalog.adminItems(null, PointShopItemKind.PROFILE_AVATAR).items();
        assertThat(withoutAvatars).isNotEmpty().noneMatch(view -> view.kind() == PointShopItemKind.PROFILE_AVATAR);
        assertThat(withoutAvatars).anyMatch(view -> view.code().equals("nickname-change"));
        var avatarsOnly = kit.catalog.adminItems(PointShopItemKind.PROFILE_AVATAR, null).items();
        assertThat(avatarsOnly).hasSize(306).allMatch(view -> view.category() != null && view.createdBy() == null);
        assertThatThrownBy(() -> kit.catalog.adminItem(Long.MAX_VALUE)).isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_SHOP_ITEM_NOT_FOUND");
    }
}
