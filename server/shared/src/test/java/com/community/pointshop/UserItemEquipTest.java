package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.pointshop.dto.response.MyEquippedView;
import com.community.pointshop.dto.response.MyItemView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.exception.PointShopBlockedException;
import com.community.pointshop.exception.UserItemExpiredException;
import com.community.pointshop.exception.UserItemNotEquippableException;
import com.community.pointshop.exception.UserItemNotFoundException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 내 아이템·장착(계약 「B」 user_items·user_equipped) — 자리마다 하나·소모형·아바타·자리 다름 거절·기간 끝 거절·남의 것 404·장착하면 처음 사용 시각·목록의 환불 판정. 실제 PostgreSQL.
 */
class UserItemEquipTest extends PointShopTestBase {

    @Test
    void 자리마다_하나만_장착되고_다른_줄을_장착하면_바꾼다() {
        long red = kit.color(100, "#FF0000");
        long blue = kit.color(100, "#0000FF");
        long member = kit.newMember();
        kit.grant(member, 200);
        long a = kit.buyItem(member, red);
        long b = kit.buyItem(member, blue);

        kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, a));
        MyEquippedView view = kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, b));

        assertThat(view.nicknameColor().userItemId()).isEqualTo(Long.toString(b));
        assertThat(view.nicknameColor().color()).isEqualTo("#0000FF");
        assertThat(view.profileBorder()).isNull();
        assertThat(kit.count("SELECT count(*) FROM user_equipped WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(kit.userItemRow(a).get("first_used_at")).as("한 번 장착했던 줄도 사용으로 남는다").isNotNull();
        assertThat(kit.userItemRow(b).get("first_used_at")).isNotNull();

        assertThat(kit.inTx(() -> kit.userItems.unequip(member, EquipSlot.NICKNAME_COLOR)).nicknameColor()).isNull();
        assertThat(kit.userItemRow(b).get("first_used_at")).as("풀어도 지우지 않는다").isNotNull();
    }

    @Test
    void 정지_중이면_장착도_해제도_막고_자리와_처음_사용_시각은_그대로다() {
        long green = kit.color(100, "#00AA00");
        long rose = kit.border(100, "ROSE", null);
        long member = kit.newMember();
        kit.grant(member, 500);
        long a = kit.buyItem(member, green);
        long b = kit.buyItem(member, rose);
        kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, a));
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member);

        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.PROFILE_BORDER, b))).isInstanceOf(PointShopBlockedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_RESTRICTED");
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.unequip(member, EquipSlot.NICKNAME_COLOR))).isInstanceOf(PointShopBlockedException.class);
        kit.jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", member);
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.PROFILE_BORDER, b))).as("영구 정지도").isInstanceOf(PointShopBlockedException.class);

        assertThat(kit.count("SELECT count(*) FROM user_equipped WHERE user_id = ?", member)).as("색은 그대로 장착·테두리는 장착 안 됨").isEqualTo(1);
        assertThat(kit.userItemRow(b).get("first_used_at")).as("막힌 장착은 사용이 아니다 — 풀린 뒤 환불할 수 있다").isNull();
        assertThat(kit.userItems.myItems(member).items()).as("목록은 읽는다").hasSize(2);
    }

    @Test
    void 장착할_수_없는_것_변경권_아바타_자리_다름_회수됨_남의_것_기간_끝() {
        long member = kit.newMember();
        long other = kit.newMember();
        kit.grant(member, 5000);
        long ticket = kit.buyItem(member, kit.seedTicket());
        long avatar = kit.buyItem(member, kit.avatarItem("job-banker-m"));
        long color = kit.buyItem(member, kit.color(100, "#ABCDEF"));
        long refunded = kit.buyItem(member, kit.border(100, "ROSE", null));
        kit.refundSelf(member, refunded);
        long period = kit.buyItem(member, kit.periodColor(100, 1));

        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, ticket))).isInstanceOf(UserItemNotEquippableException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.PROFILE_BORDER, avatar))).isInstanceOf(UserItemNotEquippableException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.PROFILE_BORDER, color))).isInstanceOf(UserItemNotEquippableException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.PROFILE_BORDER, refunded))).isInstanceOf(UserItemNotEquippableException.class);
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(other, EquipSlot.NICKNAME_COLOR, color))).isInstanceOf(UserItemNotFoundException.class);
        kit.clock.set(kit.clock.instant().plus(Duration.ofDays(1)));
        assertThatThrownBy(() -> kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, period))).isInstanceOf(UserItemExpiredException.class);
        assertThat(kit.count("SELECT count(*) FROM user_equipped WHERE user_id = ?", member)).isZero();
        assertThat(kit.userItemRow(ticket).get("first_used_at")).isNull();
    }

    @Test
    void 내_아이템은_최근에_산_것이_위_환불_판정과_기한을_싣고_회수된_줄은_기한이_없다() {
        long member = kit.newMember();
        kit.grant(member, 1000);
        Instant bought = kit.clock.instant();
        long first = kit.buyItem(member, kit.color(100, "#101010"));
        long second = kit.buyItem(member, kit.border(200, "SILVER", null));
        kit.refundSelf(member, first);

        var list = kit.userItems.myItems(member);

        assertThat(list.refundDays()).isEqualTo(7);
        assertThat(list.items()).extracting(MyItemView::id).containsExactly(Long.toString(second), Long.toString(first));
        MyItemView open = list.items().get(0);
        assertThat(open.refundable()).isTrue();
        assertThat(open.refundUntil()).isEqualTo(bought.plus(Duration.ofDays(7)));
        assertThat(open.pricePaid()).isEqualTo(200);
        assertThat(open.borderStyle()).isEqualTo("SILVER");
        MyItemView revoked = list.items().get(1);
        assertThat(revoked.status().name()).isEqualTo("REVOKED");
        assertThat(revoked.refundable()).isFalse();
        assertThat(revoked.refundUntil()).isNull();
    }
}
