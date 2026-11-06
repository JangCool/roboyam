package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.dto.response.AvatarCatalogView;
import com.community.pointshop.dto.response.AvatarCategoryView;
import com.community.pointshop.dto.response.AvatarItemView;
import com.community.pointshop.dto.response.MyItemView;
import com.community.pointshop.dto.response.SelectedAvatarView;
import com.community.pointshop.exception.PointShopRefundNotAllowedException;
import com.community.pointshop.service.AvatarCatalog;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 움직이는 아바타(commerce-motion-avatar-contract 규칙 1~5) — 카탈로그 {@code animated}·{@code stillUrl}, 정지 유료 아바타와 같은 사기·고르기·환불 규칙(규칙 4), 작성자 표시의 정지 그림,
 * 하나만 규칙의 마지막 정지 아바타(고를 때 기억 · 움직이는 닉네임 이미지가 이기면 되돌리기 · 지금 고를 수 없으면 기본). 실제 PostgreSQL.
 */
class MotionAvatarTest extends PointShopTestBase {

    private static final String MOTION = "motion-robot-heart";

    private long motionItem() {
        return kit.avatarItem(MOTION);
    }

    @Test
    void 카탈로그의_움직이는_아바타는_움직임_정지_그림_3000P_를_싣고_카테고리는_animated() {
        AvatarCatalogView catalog = kit.profileAvatars.catalog(null);
        AvatarItemView motion = catalog.items().stream().filter(a -> a.id().equals(MOTION)).findFirst().orElseThrow();
        assertThat(motion.animated()).isTrue();
        assertThat(motion.stillUrl()).isEqualTo("/avatars/motion/" + MOTION + "-still.svg");
        assertThat(motion.url()).isEqualTo("/avatars/motion/" + MOTION + ".svg");
        assertThat(motion.price()).isEqualTo(3000);
        assertThat(motion.itemId()).isEqualTo(Long.toString(motionItem()));
        assertThat(motion.free()).isFalse();
        AvatarItemView still = catalog.items().stream().filter(a -> a.id().equals("job-doctor-m")).findFirst().orElseThrow();
        assertThat(still.animated()).isFalse();
        assertThat(still.stillUrl()).isNull();
        AvatarCategoryView category = catalog.categories().stream().filter(c -> c.id().equals("motion")).findFirst().orElseThrow();
        assertThat(category.animated()).isTrue();
        assertThat(category.count()).isEqualTo(8);
        assertThat(catalog.categories()).filteredOn(AvatarCategoryView::animated).hasSize(1);
        assertThat(kit.catalog.memberItem(kit.newMember(), motionItem()).avatarUrl()).as("상점 품목 줄도 움직이는 그림 주소").isEqualTo("/avatars/motion/" + MOTION + ".svg");
    }

    @Test
    void 움직이는_아바타는_정지_유료_아바타처럼_사서_고르고_고르면_환불할_수_없다_작성자_표시에_정지_그림() {
        long member = kit.newMember();
        kit.grant(member, 5000);
        assertThatThrownBy(() -> kit.inTx(() -> kit.profileAvatars.select(member, MOTION))).as("사기 전").isInstanceOf(com.community.pointshop.exception.AvatarNotOwnedException.class);
        long held = kit.buyItem(member, motionItem());
        assertThat(kit.total(member)).isEqualTo(2000);
        MyItemView before = kit.userItems.myItems(member).items().stream().filter(v -> v.id().equals(Long.toString(held))).findFirst().orElseThrow();
        assertThat(before.animated()).isTrue();
        assertThat(before.avatarStillUrl()).isEqualTo("/avatars/motion/" + MOTION + "-still.svg");
        assertThat(before.avatarUrl()).isEqualTo("/avatars/motion/" + MOTION + ".svg");
        assertThat(before.refundable()).isTrue();

        SelectedAvatarView selected = kit.inTx(() -> kit.profileAvatars.select(member, MOTION));
        assertThat(selected.animated()).isTrue();
        assertThat(selected.avatarStillUrl()).isEqualTo("/avatars/motion/" + MOTION + "-still.svg");
        MyItemView after = kit.userItems.myItems(member).items().stream().filter(v -> v.id().equals(Long.toString(held))).findFirst().orElseThrow();
        assertThat(after.inUse()).isTrue();
        assertThat(after.refundable()).isFalse();
        assertThatThrownBy(() -> kit.refundSelf(member, held)).isInstanceOf(PointShopRefundNotAllowedException.class);

        AuthorLook look = kit.decorations.look(member);
        assertThat(look.avatarUrl()).isEqualTo("/avatars/motion/" + MOTION + ".svg");
        assertThat(look.avatarStillUrl()).isEqualTo("/avatars/motion/" + MOTION + "-still.svg");
        long plain = kit.newMember();
        Map<Long, AuthorLook> looks = kit.decorations.resolve(java.util.List.of(member, plain));
        assertThat(looks.get(plain).avatarStillUrl()).as("정지 아바타는 정지 그림이 없다").isNull();
        assertThat(looks.get(plain).avatarUrl()).isEqualTo("/avatars/roboyam/basic-default.svg");
    }

    @Test
    void 정지_아바타를_고르면_마지막_정지_아바타가_되고_움직이는_것을_고르면_그대로이며_되돌리면_그것으로() {
        long member = kit.newMember();
        kit.grant(member, 5000);
        kit.buyItem(member, motionItem());
        assertThat(kit.lastStill(member)).isNull();
        kit.inTx(() -> kit.profileAvatars.select(member, "hobby-gamer"));
        assertThat(kit.lastStill(member)).isEqualTo("hobby-gamer");
        kit.inTx(() -> kit.profileAvatars.select(member, MOTION));
        assertThat(kit.lastStill(member)).as("움직이는 것은 기억하지 않는다").isEqualTo("hobby-gamer");
        assertThat(kit.profileImage(member)).isEqualTo("avatar:" + MOTION);

        assertThat(kit.inTx(() -> kit.profileAvatars.yieldToNicknameImage(member))).isTrue();
        assertThat(kit.profileImage(member)).isEqualTo("avatar:hobby-gamer");
        assertThat(kit.inTx(() -> kit.profileAvatars.yieldToNicknameImage(member))).as("정지 아바타를 쓰는 중이면 그대로").isFalse();
        assertThat(kit.profileImage(member)).isEqualTo("avatar:hobby-gamer");

        long fresh = kit.newMember();
        kit.grant(fresh, 5000);
        kit.buyItem(fresh, motionItem());
        kit.inTx(() -> kit.profileAvatars.select(fresh, MOTION));
        kit.inTx(() -> kit.profileAvatars.yieldToNicknameImage(fresh));
        assertThat(kit.profileImage(fresh)).as("정지 아바타를 쓴 적이 없으면 basic-default").isEqualTo("avatar:basic-default");
    }

    @Test
    void 마지막_정지_아바타를_지금_고를_수_없으면_기본으로_돌아간다() {
        long member = kit.newMember();
        kit.grant(member, 6000);
        String paid = kit.jdbc.queryForObject("SELECT avatar_id FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' AND avatar_id LIKE 'animal-%' ORDER BY sort_order LIMIT 1",
                String.class);
        long paidHeld = kit.buyItem(member, kit.avatarItem(paid));
        kit.buyItem(member, motionItem());
        kit.inTx(() -> kit.profileAvatars.select(member, paid));
        assertThat(kit.lastStill(member)).isEqualTo(paid);
        kit.inTx(() -> kit.profileAvatars.select(member, MOTION));
        kit.inTx(() -> kit.refunds.refundByOperator(kit.purchaseOf(paidHeld), "예외 환불 시험입니다", kit.operator, null));
        kit.inTx(() -> kit.profileAvatars.yieldToNicknameImage(member));
        assertThat(kit.profileImage(member)).as("예외 환불로 없어진 유료 아바타로는 돌아가지 않는다").isEqualTo("avatar:" + AvatarCatalog.DEFAULT_ID);
    }
}
