package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

/**
 * V86 의 제약·트리거(계약 「B」 표) — 종류별 칸 조합·색·테두리 키·가격·기간·재고·판매 기간·설정 범위 CHECK, 영구 보유 부분 유일 색인, 품목 정체 고정·아바타 삭제 금지,
 * 구매 사본 고정·환불은 한 번, 보유 줄의 처음 사용 시각 유지·상태 전이, 장착 자리 하나. 실제 PostgreSQL.
 */
class PointShopSchemaTest extends PointShopTestBase {

    private static final String INSERT = "INSERT INTO point_shop_items (id, code, kind, usage_type, name, description, color, border_style, avatar_id, price_point, "
            + "duration_days, stock, sale_start_at, sale_end_at) VALUES (?, ?, ?, ?, '시험', '시험', ?, ?, ?, ?, ?, ?, ?, ?)";

    private void item(String kind, String usage, String color, String border, String avatar, int price, Integer duration, Integer stock, Instant start, Instant end) {
        String code = avatar != null ? "avatar-" + avatar : kit.code("s");
        kit.jdbc.update(INSERT, kit.ids.nextId(), code, kind, usage, color, border, avatar, price, duration, stock, start == null ? null : Timestamp.from(start),
                end == null ? null : Timestamp.from(end));
    }

    @Test
    void 품목_CHECK_종류별_칸_조합_색_테두리_가격_기간_재고_판매_기간() {
        item("NICKNAME_COLOR", "PERMANENT", "#12AB9f", null, null, 1, null, 0, null, null);
        item("PROFILE_BORDER", "PERIOD", null, "RAINBOW", null, 100_000, 365, null, null, null);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", null, null, null, 100, null, null, null, null)).as("색 품목에 색이 없다")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#12345", null, null, 100, null, null, null, null)).as("#RRGGBB 아님")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#123456", "GOLD", null, 100, null, null, null, null)).as("색 품목에 테두리")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("PROFILE_BORDER", "PERMANENT", null, "PINK", null, 100, null, null, null, null)).as("팔레트 7개 밖")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_CHANGE_TICKET", "PERMANENT", null, null, null, 100, null, null, null, null)).as("변경권은 소모형만")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("PROFILE_AVATAR", "PERIOD", null, null, "zz-test-a", 100, 10, null, null, null)).as("아바타는 영구만")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("PROFILE_AVATAR", "PERMANENT", null, null, null, 100, null, null, null, null)).as("아바타 ID 필수")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("PROFILE_AVATAR", "PERMANENT", null, null, "zz-test-b", 100, null, 5, null, null)).as("아바타는 재고 없음")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("PROFILE_AVATAR", "PERMANENT", null, null, "job-doctor-m", 100, null, null, null, null)).as("아바타 ID 유일(코드도)")
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#123456", null, null, 0, null, null, null, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#123456", null, null, 100_001, null, null, null, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERIOD", "#123456", null, null, 100, null, null, null, null)).as("기간제는 기간 필수")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERIOD", "#123456", null, null, 100, 366, null, null, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#123456", null, null, 100, 30, null, null, null)).as("영구에 기간")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#123456", null, null, 100, null, -1, null, null)).isInstanceOf(DataAccessException.class);
        Instant now = Instant.now();
        assertThatThrownBy(() -> item("NICKNAME_COLOR", "PERMANENT", "#123456", null, null, 100, null, null, now, now)).as("판매 끝 > 시작")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_settings SET refund_days = 31 WHERE id = 1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_settings SET refund_days = 0 WHERE id = 1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_shop_settings (id) VALUES (2)")).as("설정은 한 행").isInstanceOf(DataAccessException.class);
    }

    @Test
    void 품목의_코드_종류_아바타_ID_는_바꿀_수_없고_재고는_0_아래로_내려가지_않는다() {
        long item = kit.border(100, "GOLD", 1);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_items SET code = 'changed-code' WHERE id = ?", item)).hasMessageContaining("fixed");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_items SET kind = 'NICKNAME_COLOR', color = '#000000', border_style = NULL WHERE id = ?", item))
                .hasMessageContaining("fixed");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_items SET avatar_id = 'job-chef-f' WHERE id = ?", kit.avatarItem("job-chef-m")))
                .hasMessageContaining("fixed");
        assertThat(kit.itemStore.takeStock(item)).isTrue();
        assertThat(kit.itemStore.takeStock(item)).as("0 이면 0 줄").isFalse();
        assertThat(kit.stock(item)).isZero();
    }

    @Test
    void 영구_아이템은_회원_품목마다_회수되지_않은_줄_하나만_부분_유일_색인() {
        long item = kit.color(100, "#AAAAAA");
        long member = kit.newMember();
        kit.grant(member, 100);
        long owned = kit.buyItem(member, item);
        long purchase = kit.purchaseOf(owned);
        long otherPurchase = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO point_shop_purchases (id, user_id, item_id, item_code, item_name, item_kind, usage_type, price_point, refund_notice_agreed_at, "
                + "use_ledger_id, created_at) SELECT ?, user_id, item_id, item_code, item_name, item_kind, usage_type, price_point, now(), ?, now() FROM point_shop_purchases "
                + "WHERE id = ?", otherPurchase, kit.ids.nextId(), purchase);

        String insert = "INSERT INTO user_items (id, user_id, item_id, purchase_id, usage_type, status, starts_at, created_at) VALUES (?, ?, ?, ?, 'PERMANENT', 'OWNED', now(), now())";
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, item, otherPurchase)).isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_user_items_permanent");
        kit.jdbc.update("UPDATE user_items SET status = 'REVOKED', revoked_at = now() WHERE id = ?", owned);
        kit.jdbc.update(insert, kit.ids.nextId(), member, item, otherPurchase);
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ? AND item_id = ?", member, item)).isEqualTo(2);
    }

    @Test
    void 구매_사본은_고칠_수_없고_환불은_한_번_뿐이다() {
        long item = kit.color(100, "#BBBBBB");
        long member = kit.newMember();
        kit.grant(member, 100);
        long userItem = kit.buyItem(member, item);
        long purchase = kit.purchaseOf(userItem);

        for (String set : new String[] {"price_point = 1", "item_name = '바꿈'", "item_code = 'x-x-x'", "user_id = user_id + 1", "use_ledger_id = 1",
                "refund_notice_agreed_at = now() - interval '1 day'"}) {
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_purchases SET " + set + " WHERE id = ?", purchase)).as(set).hasMessageContaining("snapshot is fixed");
        }
        kit.refundSelf(member, userItem);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_shop_purchases SET status = 'COMPLETED', refunded_at = NULL, refund_ledger_id = NULL WHERE id = ?", purchase))
                .hasMessageContaining("refund is final");
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_shop_purchases (id, user_id, item_id, item_code, item_name, item_kind, usage_type, price_point, "
                + "status, refund_notice_agreed_at, use_ledger_id, created_at) VALUES (?, 1, ?, 'x', 'x', 'NICKNAME_COLOR', 'PERMANENT', 100, 'REFUNDED', now(), ?, now())",
                kit.ids.nextId(), item, kit.ids.nextId())).as("환불 상태면 환불 칸이 있어야").isInstanceOf(DataAccessException.class);
    }

    @Test
    void 보유_줄의_처음_사용_시각은_지울_수_없고_상태는_앞으로만_간다() {
        long member = kit.newMember();
        kit.grant(member, 5000);
        long ticket = kit.buyItem(member, kit.seedTicket());
        kit.inTx(() -> kit.tickets.useOne(member));

        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET first_used_at = NULL WHERE id = ?", ticket)).hasMessageContaining("first_used_at is kept");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET status = 'OWNED', used_at = NULL WHERE id = ?", ticket)).hasMessageContaining("cannot go from");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET expires_at = now() + interval '1 day' WHERE id = ?", ticket)).hasMessageContaining("identity is fixed");
        long color = kit.buyItem(member, kit.color(100, "#CCCCCC"));
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET status = 'USED', used_at = now(), first_used_at = now() WHERE id = ?", color)).as("소모형만 USED")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void 장착은_회원_자리마다_하나이고_보유_줄_하나는_한_자리에만() {
        long member = kit.newMember();
        kit.grant(member, 200);
        long a = kit.buyItem(member, kit.color(100, "#DDDDDD"));
        long itemA = kit.jdbc.queryForObject("SELECT item_id FROM user_items WHERE id = ?", Long.class, a);
        kit.jdbc.update("INSERT INTO user_equipped (user_id, slot, user_item_id, item_id, equipped_at) VALUES (?, 'NICKNAME_COLOR', ?, ?, now())", member, a, itemA);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO user_equipped (user_id, slot, user_item_id, item_id, equipped_at) VALUES (?, 'PROFILE_BORDER', ?, ?, now())",
                member, a, itemA)).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO user_equipped (user_id, slot, user_item_id, item_id, equipped_at) VALUES (?, 'AVATAR', ?, ?, now())",
                member, a, itemA)).isInstanceOf(DataAccessException.class);
    }
}
