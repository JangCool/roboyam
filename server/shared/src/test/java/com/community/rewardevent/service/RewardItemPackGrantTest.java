package com.community.rewardevent.service;

import static com.community.rewardevent.service.RewardEventTestKit.item;
import static com.community.rewardevent.service.RewardEventTestKit.packLine;
import static com.community.rewardevent.service.RewardEventTestKit.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.pointshop.dto.response.MyItemView;
import com.community.pointshop.exception.PointShopRefundNotAllowedException;
import com.community.pointshop.service.PointShopRules;
import com.community.zconfiguration.exception.ValidationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 보상 이벤트 — 포인트 상점 아이템·로보티콘 팩 줄(event-admin-contract 「보상 줄」 SHOP_ITEM·EMOTICON_PACK, commerce.md 7-1·7-3): 지급 출처 · 영구는 이미 가졌으면 건너뜀 ·
 * 소모형 수량 · 기간제 기간(앞 줄 끝에서) · 지급 아이템은 셀프·예외 환불 대상이 아니다 · 쓴 아이템은 회수 못 함 · 아바타·작가 유료 팩 거부 · 작가 받기 보상 포인트 없음 ·
 * 이미 가진 팩 건너뜀 · 팩 회수는 이벤트 줄만.
 */
class RewardItemPackGrantTest extends RewardEventTestBase {

    private long grantEvent(com.community.rewardevent.dto.RewardTierDto tier, long... members) {
        long event = kit.closedSelection(kit.selection(tier), members);
        kit.grants.grant(kit.op(), event, "k-" + event);
        return event;
    }

    private List<Map<String, Object>> userItems(long userId, long itemId) {
        return kit.jdbc.queryForList("SELECT * FROM user_items WHERE user_id = ? AND item_id = ? ORDER BY grant_seq, id", userId, itemId);
    }

    @Test
    void 지급한_아이템은_구매_없이_줄_결과를_출처로_가진다_영구는_한_줄() {
        long m = kit.member();
        long color = kit.permanentColor();
        long event = grantEvent(tier("1등", 5, item(color, null)), m);
        List<Map<String, Object>> rows = userItems(m, color);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("purchase_id", null).containsEntry("grant_result_id", kit.results(event, m).get(0).get("id"))
                .containsEntry("status", "OWNED").containsEntry("usage_type", "PERMANENT");
        assertThat(kit.results(event, m).get(0)).containsEntry("status", "SUCCESS").containsEntry("items", 1);
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", Integer.class, m)).isZero();
    }

    @Test
    void 이미_가진_영구_아이템은_건너뛰고_모든_줄이_건너뜀이면_받는_사람도_건너뜀() {
        long m = kit.member();
        long color = kit.permanentColor();
        grantEvent(tier("1등", 5, item(color, null)), m);
        long second = grantEvent(tier("1등", 5, item(color, null)), m);
        assertThat(kit.results(second, m).get(0)).containsEntry("status", "SKIPPED").containsEntry("reason", "ALREADY_OWNED");
        assertThat(kit.recipientStatus(second, m)).isEqualTo("SKIPPED");
        assertThat(userItems(m, color)).hasSize(1); // 영구 아이템 부분 유일 색인 그대로
        assertThat(kit.queries.detail(second).stats().skippedLines()).isEqualTo(1);
    }

    @Test
    void 소모형은_수량만큼_기간제는_지급_순간부터_같은_품목_기간이_남았으면_앞_줄_끝에서() {
        long m = kit.member();
        long ticket = kit.ticket();
        long period = kit.periodColor(30);
        Instant now = kit.clock.instant();
        grantEvent(tier("1등", 5, item(ticket, 3), item(period, null)), m);
        List<Map<String, Object>> tickets = userItems(m, ticket);
        assertThat(tickets).hasSize(3).extracting(r -> r.get("grant_seq")).containsExactly(1, 2, 3);
        Map<String, Object> first = userItems(m, period).get(0);
        Instant start = ((Timestamp) first.get("starts_at")).toInstant();
        Instant end = ((Timestamp) first.get("expires_at")).toInstant();
        assertThat(Duration.between(now, start).abs()).isLessThan(Duration.ofSeconds(5));
        assertThat(Duration.between(start, end)).isEqualTo(Duration.ofDays(30));

        grantEvent(tier("1등", 5, item(period, null)), m);
        List<Map<String, Object>> periods = userItems(m, period);
        assertThat(periods).hasSize(2);
        assertThat(((Timestamp) periods.get(1).get("starts_at")).toInstant()).isEqualTo(end);
        assertThat(((Timestamp) periods.get(1).get("expires_at")).toInstant()).isEqualTo(end.plus(Duration.ofDays(30)));
    }

    @Test
    void 지급_아이템은_셀프_환불_409_GRANTED_내_아이템은_출처_이벤트_보상_환불_불가_예외_환불할_구매가_없다() {
        long m = kit.member();
        long color = kit.permanentColor();
        grantEvent(tier("1등", 5, item(color, null)), m);
        long userItem = (Long) userItems(m, color).get(0).get("id");

        assertThatThrownBy(() -> kit.refunds.refundSelf(m, userItem)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((PointShopRefundNotAllowedException) e).getExtras()).containsEntry("blockedBy", PointShopRules.BLOCKED_GRANTED));
        MyItemView mine = kit.inTx(() -> kit.userItems.myItems(m)).items().stream().filter(v -> v.id().equals(Long.toString(userItem))).findFirst().orElseThrow();
        assertThat(mine.source()).isEqualTo(MyItemView.SOURCE_REWARD_EVENT);
        assertThat(mine.refundable()).isFalse();
        assertThat(mine.purchaseId()).isNull();
        assertThat(mine.refundUntil()).isNull();
        assertThat(mine.pricePaid()).isZero();
        // 예외 환불은 구매 번호로만 — 이 회원에게는 구매가 없다(관리자 구매 내역에도 나오지 않는다)
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", Integer.class, m)).isZero();
        assertThat(userItems(m, color).get(0)).containsEntry("status", "OWNED");
    }

    @Test
    void 쓴_아이템은_회수하지_못하고_ITEM_USED_안_쓴_줄만_회수한다() {
        long m = kit.member();
        long ticket = kit.ticket();
        long color = kit.permanentColor();
        long event = grantEvent(tier("1등", 5, item(ticket, 2), item(color, null)), m);
        List<Map<String, Object>> tickets = userItems(m, ticket);
        kit.jdbc.update("UPDATE user_items SET status = 'USED', used_at = now(), first_used_at = now() WHERE id = ?", tickets.get(0).get("id"));
        String rid = Long.toString(kit.jdbc.queryForObject("SELECT id FROM reward_event_recipients WHERE event_id = ? AND user_id = ?", Long.class, event, m));

        kit.grants.revoke(kit.op(), event, List.of(rid), "잘못 지급했어요");

        List<Map<String, Object>> results = kit.results(event, m);
        assertThat(results.get(0)).containsEntry("reason", "ITEM_USED").containsEntry("status", "SUCCESS");
        assertThat(results.get(1)).containsEntry("status", "REVOKED");
        assertThat(userItems(m, ticket)).extracting(r -> r.get("status")).containsExactly("USED", "REVOKED");
        assertThat(userItems(m, color).get(0)).containsEntry("status", "REVOKED");
        assertThat(kit.recipientStatus(event, m)).isEqualTo("REVOKED");
    }

    @Test
    void 아바타_품목과_작가_유료_팩은_보상으로_고를_수_없다_400() {
        long avatar = kit.avatarItem();
        assertThatThrownBy(() -> kit.create(kit.selection(tier("1등", 5, item(avatar, null))))).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("tiers[0].rewards[0].itemId"));
        long creator = kit.member();
        long paid = kit.pack(creator, 1_500); // 기본 가격 단계(1,500·2,500·3,500) 중 하나
        assertThatThrownBy(() -> kit.create(kit.selection(tier("1등", 5, packLine(paid))))).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields().get("tiers[0].rewards[0].packId").key()).isEqualTo("rewardEvent.line.packId.creatorPaid"));
        assertThat(kit.queries.catalog().packs()).filteredOn(p -> p.id().equals(Long.toString(paid))).singleElement()
                .satisfies(p -> assertThat(p.selectable()).isFalse());
    }

    @Test
    void 작가_무료_팩을_주면_이벤트_받기_줄이_생기고_작가_받기_보상_포인트는_없다_받은_수는_한_번() {
        long creator = kit.member();
        long m = kit.member();
        long pack = kit.pack(creator, 0);
        long event = grantEvent(tier("1등", 5, packLine(pack)), m);
        Map<String, Object> acq = kit.jdbc.queryForMap("SELECT kind, reward_result_id, removed_at FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", m, pack);
        assertThat(acq).containsEntry("kind", "EVENT").containsEntry("reward_result_id", kit.results(event, m).get(0).get("id")).containsEntry("removed_at", null);
        assertThat(kit.ledger(creator, "EMOTICON_CREATOR_REWARD")).isEmpty();
        assertThat(kit.jdbc.queryForObject("SELECT acquire_count FROM emoticon_packs WHERE id = ?", Integer.class, pack)).isEqualTo(1);
        assertThat(kit.inTx(() -> kit.acquisitionStore.ownedAmong(m, List.of(pack)))).containsExactly(pack);
        // 내 로보티콘(`GET /api/me/emoticons` 의 `reward`)이 「이벤트 보상」으로 보이게 — 가진 팩 조회가 출처를 넘긴다(작가 자기 팩·무료 받기는 false)
        EmoticonAcquisitionStore.OwnedPack owned = kit.inTx(() -> kit.acquisitionStore.owned(m)).get(pack);
        assertThat(owned.reward()).isTrue();
        assertThat(owned.purchaseId()).isNull();
        EmoticonAcquisitionStore.OwnedPack creatorOwn = kit.inTx(() -> kit.acquisitionStore.owned(creator)).get(pack);
        assertThat(creatorOwn == null || !creatorOwn.reward()).isTrue();
    }

    @Test
    void 이미_가진_팩과_작가_자기_팩은_건너뛴다() {
        long creator = kit.member();
        long m = kit.member();
        long pack = kit.pack(null, 0);
        kit.inTx(() -> {
            kit.acquisitionStore.insert(kit.ids.nextId(), m, pack, kit.clock.instant());
            return null;
        });
        long own = kit.pack(creator, 0);
        long event = grantEvent(tier("1등", 5, packLine(pack), packLine(own)), m, creator);
        assertThat(kit.results(event, m).get(0)).containsEntry("status", "SKIPPED").containsEntry("reason", "PACK_ALREADY_OWNED");
        assertThat(kit.results(event, creator).get(1)).containsEntry("status", "SKIPPED").containsEntry("reason", "PACK_ALREADY_OWNED");
        assertThat(kit.jdbc.queryForObject("SELECT kind FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", String.class, m, pack)).isEqualTo("FREE");
    }

    @Test
    void 팩_회수는_이벤트로_받은_줄만_빼고_뺀_줄은_다시_받으면_무료_받기로_돌아간다() {
        long m = kit.member();
        long pack = kit.pack(null, 0);
        long event = grantEvent(tier("1등", 5, packLine(pack)), m);
        String rid = Long.toString(kit.jdbc.queryForObject("SELECT id FROM reward_event_recipients WHERE event_id = ? AND user_id = ?", Long.class, event, m));
        kit.grants.revoke(kit.op(), event, List.of(rid), "잘못 지급했어요");
        assertThat(kit.jdbc.queryForObject("SELECT removed_at IS NOT NULL FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Boolean.class, m, pack)).isTrue();
        assertThat(kit.results(event, m).get(0)).containsEntry("status", "REVOKED");
        long row = kit.jdbc.queryForObject("SELECT id FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Long.class, m, pack);
        kit.inTx(() -> {
            kit.acquisitionStore.restore(row, kit.clock.instant());
            return null;
        });
        assertThat(kit.jdbc.queryForMap("SELECT kind, reward_result_id, removed_at FROM emoticon_acquisitions WHERE id = ?", row)).containsEntry("kind", "FREE")
                .containsEntry("reward_result_id", null).containsEntry("removed_at", null);
    }

    @Test
    void 판매_중단된_팩은_지급_때_ITEM_UNAVAILABLE() {
        long m = kit.member();
        long pack = kit.pack(null, 0);
        long event = kit.closedSelection(kit.selection(tier("1등", 5, packLine(pack))), m);
        kit.jdbc.update("UPDATE emoticon_packs SET status = 'SUSPENDED', suspend_reason = '시험 판매 중단', suspended_at = now(), suspend_kind = 'OPERATOR' WHERE id = ?", pack);
        kit.grants.grant(kit.op(), event, "k-" + event);
        assertThat(kit.results(event, m).get(0)).containsEntry("status", "FAILED").containsEntry("reason", "ITEM_UNAVAILABLE");
    }
}
