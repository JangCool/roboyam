package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.pointshop.dto.NicknameTicketInfo;
import com.community.pointshop.exception.NicknameTicketRequiredException;
import com.community.zconfiguration.exception.CommonException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/**
 * 닉네임 변경권(member-mypage-contract 「후속 작업」, 계약 결정 1·2) — 한 장이 동시 두 요청에 두 번 쓰이지 않음(SKIP LOCKED), 장이 둘이면 둘 다 쓰임, 먼저 산 것부터, 없으면 409 + 장수·값,
 * 값은 포인트 상점 변경권 품목 가격(V86 이 옛 정책 값을 옮겼다). 실제 PostgreSQL.
 */
class NicknameTicketTest extends PointShopTestBase {

    @Test
    void 변경권_한_장은_동시_두_요청에_두_번_쓰이지_않는다() throws Exception {
        long member = kit.newMember();
        kit.grant(member, 5000);
        long only = kit.buyItem(member, kit.seedTicket());

        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            jobs.add(() -> {
                try {
                    return "USED " + kit.inTx(() -> kit.tickets.useOne(member));
                } catch (NicknameTicketRequiredException e) {
                    return e.getErrorCode();
                }
            });
        }
        assertThat(PointShopTestKit.runTogether(jobs)).containsExactlyInAnyOrder("USED " + only, "NICKNAME_TICKET_REQUIRED");
        assertThat(kit.userItemRow(only)).containsEntry("status", "USED");
        assertThat(kit.userItemRow(only).get("used_at")).isNotNull();
        assertThat(kit.userItemRow(only).get("first_used_at")).isNotNull();
        assertThat(kit.tickets.count(member)).isZero();
    }

    @Test
    void 장이_둘이면_동시_두_요청이_서로_다른_장을_쓴다() throws Exception {
        long member = kit.newMember();
        kit.grant(member, 5000);
        long ticket = kit.seedTicket();
        long first = kit.buyItem(member, ticket);
        long second = kit.buyItem(member, ticket);

        List<Callable<Long>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            jobs.add(() -> kit.inTx(() -> kit.tickets.useOne(member)));
        }
        assertThat(PointShopTestKit.runTogether(jobs)).containsExactlyInAnyOrder(first, second);
        assertThat(kit.tickets.count(member)).isZero();
    }

    @Test
    void 없으면_409_에_장수와_한_장_값을_싣고_트랜잭션이_되돌아가면_변경권도_그대로다() {
        long member = kit.newMember();
        assertThatThrownBy(() -> kit.inTx(() -> kit.tickets.useOne(member))).isInstanceOf(NicknameTicketRequiredException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("ticketCount", 0).containsEntry("ticketPrice", kit.tickets.price()));

        kit.grant(member, 5000);
        long ticket = kit.buyItem(member, kit.seedTicket());
        assertThatThrownBy(() -> kit.inTx(() -> {
            kit.tickets.useOne(member);
            throw new IllegalStateException("닉네임 저장이 실패했다(중복 등)");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(kit.userItemRow(ticket)).containsEntry("status", "OWNED");
        assertThat(kit.tickets.count(member)).isEqualTo(1);
    }

    @Test
    void 값은_시드_변경권_품목_가격이고_장수와_사러_갈_품목을_준다() {
        long member = kit.newMember();
        kit.grant(member, 5000);
        long seed = kit.seedTicket();
        kit.buyItem(member, seed);
        kit.buyItem(member, seed);

        NicknameTicketInfo info = kit.tickets.info(member);

        assertThat(info.count()).isEqualTo(2);
        assertThat(info.itemId()).isEqualTo(Long.toString(seed));
        assertThat(info.price()).isEqualTo(kit.jdbc.queryForObject("SELECT price_point FROM point_shop_items WHERE id = ?", Integer.class, seed));
        assertThat(kit.jdbc.queryForObject("SELECT usage_type FROM point_shop_items WHERE id = ?", String.class, seed)).isEqualTo("CONSUMABLE");
    }
}
