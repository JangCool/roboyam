package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.pointshop.dto.response.AvatarCatalogView;
import com.community.pointshop.dto.response.AvatarItemView;
import com.community.pointshop.dto.response.SelectedAvatarView;
import com.community.pointshop.exception.AvatarNotFoundException;
import com.community.pointshop.exception.AvatarNotOwnedException;
import com.community.pointshop.exception.PointShopBlockedException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import org.junit.jupiter.api.Test;

/**
 * 프로필 아바타 고르기·목록(결정 14, 계약 「B」 고른 아바타) — 무료는 누구나·유료는 가진 것만·고르면 {@code first_used_at}·자격·형식, 목록의 보유·고른 것·환불 가능·판매 여부. 실제 PostgreSQL.
 */
class ProfileAvatarTest extends PointShopTestBase {

    private static AvatarItemView item(AvatarCatalogView view, String id) {
        return view.items().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void 무료_아바타는_누구나_고르고_같은_것을_다시_골라도_된다() {
        long member = kit.newMember();

        SelectedAvatarView selected = kit.inTx(() -> kit.profileAvatars.select(member, "hobby-gamer"));
        kit.inTx(() -> kit.profileAvatars.select(member, "hobby-gamer"));

        assertThat(selected.avatarUrl()).isEqualTo("/avatars/roboyam/hobby-gamer.svg");
        assertThat(kit.jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).isEqualTo("avatar:hobby-gamer");
        assertThat(kit.profileAvatars.avatarUrl(member)).isEqualTo("/avatars/roboyam/hobby-gamer.svg");
        AvatarCatalogView view = kit.profileAvatars.catalog(member);
        assertThat(view.selectedId()).isEqualTo("hobby-gamer");
        assertThat(item(view, "hobby-gamer").selected()).isTrue();
        assertThat(item(view, "basic-default").owned()).isTrue();
    }

    @Test
    void 유료_아바타는_가진_것만_고르고_고르면_처음_사용_시각을_남긴다() {
        long member = kit.newMember();
        kit.grant(member, 1000);
        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(member, "job-chef-m"))).isInstanceOf(AvatarNotOwnedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("AVATAR_NOT_OWNED");

        long userItem = kit.buyItem(member, kit.avatarItem("job-chef-m"));
        assertThat(kit.jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).as("사도 프로필은 바뀌지 않는다").isNull();
        AvatarCatalogView before = kit.profileAvatars.catalog(member);
        assertThat(item(before, "job-chef-m").owned()).isTrue();
        assertThat(item(before, "job-chef-m").refundable()).as("안 썼고 기한 안").isTrue();
        assertThat(before.balance()).isZero();

        kit.inTx(() -> kit.profileAvatars.select(member, "job-chef-m"));

        assertThat(kit.userItemRow(userItem).get("first_used_at")).isNotNull();
        AvatarCatalogView after = kit.profileAvatars.catalog(member);
        assertThat(item(after, "job-chef-m").refundable()).isFalse();
        assertThat(item(after, "job-chef-m").selected()).isTrue();
        var mine = kit.userItems.myItems(member).items().get(0);
        assertThat(mine.inUse()).isTrue();
        assertThat(mine.equipped()).as("아바타는 장착 칸을 쓰지 않는다").isFalse();
        assertThat(mine.avatarUrl()).isEqualTo("/avatars/roboyam/job-chef-m.svg");
    }

    @Test
    void 판매를_멈춘_유료_아바타도_가진_사람은_고를_수_있다() {
        long member = kit.newMember();
        kit.grant(member, 1000);
        long item = kit.avatarItem("job-chef-f");
        kit.buyItem(member, item);
        kit.jdbc.update("UPDATE point_shop_items SET active = false WHERE id = ?", item);
        try {
            assertThat(item(kit.profileAvatars.catalog(member), "job-chef-f").forSale()).isFalse();
            kit.inTx(() -> kit.profileAvatars.select(member, "job-chef-f"));
            assertThat(kit.profileAvatars.avatarUrl(member)).isEqualTo("/avatars/roboyam/job-chef-f.svg");
        } finally {
            kit.jdbc.update("UPDATE point_shop_items SET active = true WHERE id = ?", item);
        }
    }

    @Test
    void 형식_없는_아바타_자격() {
        long member = kit.newMember();
        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(member, "  "))).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("avatarId"));
        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(member, "robot-01"))).isInstanceOf(AvatarNotFoundException.class);

        long unverified = kit.newMember();
        kit.jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", unverified);
        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(unverified, "basic-boy"))).isInstanceOf(PointShopBlockedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED");
        long banned = kit.newMember();
        kit.jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", banned);
        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(banned, "basic-boy"))).isInstanceOf(PointShopBlockedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("ACCOUNT_RESTRICTED");
    }

    @Test
    void 비로그인_목록은_보유와_고른_것이_비어_있고_328줄_카탈로그_차례() {
        AvatarCatalogView view = kit.profileAvatars.catalog(null);

        assertThat(view.items()).hasSize(328);
        assertThat(view.items().get(0).id()).isEqualTo("basic-default");
        assertThat(view.selectedId()).isNull();
        assertThat(view.balance()).isZero();
        assertThat(view.items()).noneMatch(AvatarItemView::owned).noneMatch(AvatarItemView::selected);
        AvatarItemView paid = item(view, "job-doctor-m");
        assertThat(paid.free()).isFalse();
        assertThat(paid.price()).isEqualTo(1000);
        assertThat(paid.itemId()).isEqualTo(Long.toString(kit.avatarItem("job-doctor-m")));
        assertThat(item(view, "basic-default").price()).isNull();
        assertThat(view.categories()).hasSize(14);
        assertThat(view.refundDays()).isEqualTo(7);
    }
}
