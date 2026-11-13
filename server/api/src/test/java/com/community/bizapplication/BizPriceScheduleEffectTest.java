package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.MyBizApplicationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 요금표 예약 변경이 사업자 쪽에 닿는 규칙(docs/features/ad-price-rules-contract.md 규칙 1·2, 사업자 API {@code scheduledChange}).
 *
 * <ul>
 *   <li>적용 시각부터 정확: 신청 검사(최대 일수·시작일 선택 범위)·견적·한도는 「적용 시각 ≤ 지금인 가장 늦은 예약 값, 없으면 지금 값」 — 배치 없이 기간표에서 읽는다.</li>
 *   <li>견적은 신청을 낸 시각에 고정: 적용 시각 전에 낸 신청은 적용 시각 뒤에 승인해도 그때의 단가(승인 때 기간이 바뀌어도 단가는 그대로, 일수만 다시 센다).</li>
 *   <li>{@code /api/business/ad-products}·{@code deal-products} 의 {@code scheduledChange} 는 상품(노출 자리·핫딜 글 포함)마다 아직 적용 전인 가장 가까운 예약 하나, 없으면 null.</li>
 * </ul>
 * 예약은 시험이 직접 넣고(관리자 API 의 검사는 admin 시험) 끝나면 지운다 — 시험 DB 를 같이 쓰는 다른 시험이 시드 값을 보도록.
 */
class BizPriceScheduleEffectTest extends BizApplicationApiTestBase {

    @Autowired
    private MyBizApplicationService my;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void dropSchedules() {
        for (long id : created) {
            jdbc.update("DELETE FROM biz_price_schedules WHERE id = ?", id);
        }
    }

    /** 예약 한 건(검사 없이 저장소로) — 지금 시계 + {@code after} */
    private long schedule(BizProduct product, Duration after, long price, Integer capacity, int maxDays, int maxLeadDays) {
        long id = ids.nextId();
        Instant at = clock.instant().plus(after);
        tx.executeWithoutResult(s -> priceRules.insertSchedule(id, product, at, price, capacity, maxDays, maxLeadDays, operator.operatorId(), clock.instant()));
        created.add(id);
        return id;
    }

    @Test
    void 적용_시각_전에는_지금_값_뒤에는_예약_값으로_검사하고_견적한다() {
        long user = approvedBusiness();
        // 한 줄 광고(시드 5,000원 · 최대 30일 · 시작일 범위 60일 · 한도 5) — 1시간 뒤 7,000원 · 최대 3일 · 범위 5일 · 한도 4
        schedule(BizProduct.TEXT_LINE, Duration.ofHours(1), 7_000, 4, 3, 5);

        // 적용 전 — 10일·11일 뒤 시작 모두 된다, 견적은 5,000원
        long before = submitTextLine(user, 10, 10, "ONLINE");
        assertThat(app(before).quote().supply()).isEqualTo(5_000L * 10);
        assertThat(priceRules.find(BizProduct.TEXT_LINE).orElseThrow().capacity()).isEqualTo(5);

        // 적용 시각 뒤 — 최대 일수·시작일 범위·단가·한도가 예약 값
        clock.advance(Duration.ofHours(2));
        assertThat(fieldsOf(() -> submitTextLine(user, 0, 10, "ONLINE"))).contains("period");     // 10일 > 최대 3일
        assertThat(fieldsOf(() -> submitTextLine(user, 10, 2, "ONLINE"))).contains("period");     // 시작이 11일 뒤 > 범위 5일
        long after = submitTextLine(user, 1, 3, "ONLINE");
        assertThat(app(after).quote().supply()).isEqualTo(7_000L * 3);
        assertThat(store.items(after)).extracting(ItemRow::unitPrice).containsExactly(7_000L);
        assertThat(priceRules.find(BizProduct.TEXT_LINE).orElseThrow().capacity()).isEqualTo(4);
        assertThat(priceRules.rows().get(BizProduct.TEXT_LINE).unitPrice()).isEqualTo(5_000); // 요금표 줄(설치 기본값)은 그대로 — 지금 값은 기간표에서 읽는다
    }

    @Test
    void 적용_시각_전에_낸_신청은_뒤에_승인해도_낼_때의_단가() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 2, 4, "ONLINE");
        assertThat(app(id).quote().supply()).isEqualTo(5_000L * 4);
        schedule(BizProduct.TEXT_LINE, Duration.ofHours(1), 9_000, 5, 30, 60);
        clock.advance(Duration.ofHours(2));

        BizPeriods.Period p = freshPeriod(6); // 승인 때 기간을 6일로 바꾼다
        assertThat(quoteSupply(id, p)).isEqualTo(5_000L * 6);
        approve(id, "ONLINE", p);
        assertThat(store.items(id)).extracting(ItemRow::unitPrice).containsExactly(5_000L);
        assertThat(app(id).approved().supply()).isEqualTo(5_000L * 6);
        assertThat(app(id).quote().supply()).isEqualTo(5_000L * 6);
    }

    @Test
    void 사업자_상품_목록의_scheduledChange_는_상품마다_가장_가까운_적용_전_예약_하나() {
        schedule(BizProduct.TEXT_LINE, Duration.ofHours(2), 8_000, 5, 20, 40);
        schedule(BizProduct.TEXT_LINE, Duration.ofHours(1), 6_000, 5, 25, 50);
        schedule(BizProduct.HOTDEAL_TOP, Duration.ofDays(1), 25_000, 2, 7, 60);
        schedule(BizProduct.HOTDEAL_POST, Duration.ofDays(2), 35_000, null, 30, 60);
        Instant oneHour = clock.instant().plus(Duration.ofHours(1));

        Map<String, BizMemberView.AdProduct> ads = new java.util.HashMap<>();
        my.adProducts().forEach(p -> ads.put(p.type(), p));
        BizMemberView.ScheduledChange line = ads.get("TEXT_LINE").scheduledChange();
        assertThat(line.effectiveAt()).isEqualTo(oneHour);
        assertThat(line.unitPrice()).isEqualTo(6_000);
        assertThat(line.maxDays()).isEqualTo(25);
        assertThat(line.maxLeadDays()).isEqualTo(50);
        assertThat(ads.get("TEXT_LINE").dailyPrice()).isEqualTo(5_000); // 지금 값은 그대로
        assertThat(ads.get("WIDE").scheduledChange()).isNull();

        BizMemberView.DealProduct deal = my.dealProduct();
        assertThat(deal.scheduledChange().unitPrice()).isEqualTo(35_000);
        assertThat(deal.placements()).filteredOn(pl -> pl.placement().equals("HOME_TOP")).singleElement()
                .satisfies(pl -> assertThat(pl.scheduledChange().unitPrice()).isEqualTo(25_000));
        assertThat(deal.placements()).filteredOn(pl -> pl.placement().equals("CATEGORY_TOP")).singleElement()
                .satisfies(pl -> assertThat(pl.scheduledChange()).isNull());

        // 첫 예약이 적용되면 다음 예약이 안내되고 지금 값은 첫 예약 값
        clock.advance(Duration.ofMinutes(90));
        my.adProducts().forEach(p -> ads.put(p.type(), p));
        assertThat(ads.get("TEXT_LINE").scheduledChange().unitPrice()).isEqualTo(8_000);
        assertThat(ads.get("TEXT_LINE").dailyPrice()).isEqualTo(6_000);
        assertThat(ads.get("TEXT_LINE").maxDays()).isEqualTo(25);
    }
}
