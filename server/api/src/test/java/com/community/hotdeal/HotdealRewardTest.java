package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.service.DealEndService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * 종료 요청 보상(서버 단계 고정 규칙 5 — 커머스 수준, commerce.md 18절) — 종료 경로 셋(작성자·운영진·자동, + 품절 저장) 모두 · 의심 구간 요청자에게만 · 하루 지급 횟수(포인트 정책) ·
 * 중복 지급 없음(원장 유일) · 동시 종료 경합 · 포인트 불변식.
 */
class HotdealRewardTest extends HotdealStage2TestBase {

    @Test
    void 작성자_종료는_의심_구간_요청자에게_보상한다() {
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 3);
        long late = member();
        request(post, late, "PRICE_CHANGED");
        states.end(Long.toString(post), author);
        List<Long> all = new ArrayList<>(requesters);
        all.add(late);
        for (long m : all) {
            assertThat(rewards(m)).as("회원 " + m).isEqualTo(1);
            assertThat(balance(m)).isEqualTo(rewardPoints());
        }
        assertThat(rewards(author)).isZero();
        assertThat(jdbc.queryForList("SELECT ref_id FROM point_ledger WHERE reason = 'DEAL_END_REQUEST' AND user_id = ?", Long.class, late))
                .containsExactly(jdbc.queryForObject("SELECT id FROM deal_end_requests WHERE post_id = ? AND member_id = ?", Long.class, post, late));
        assertInvariants(all);
    }

    @Test
    void 운영진_종료와_자동_종료도_보상한다() {
        long manual = newDeal(member());
        List<Long> a = requestBy(manual, 3);
        assertThat(ends.endByManager(manual, hotdealOperator(), "종료")).isEqualTo(DealEndService.Outcome.CHANGED);
        a.forEach(m -> assertThat(rewards(m)).isEqualTo(1));

        long auto = newDeal(member());
        List<Long> b = requestBy(auto, 3);
        afterFlag(auto, Duration.ofHours(12));
        ends.autoEnd(auto, clock.instant());
        b.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        List<Long> all = new ArrayList<>(a);
        all.addAll(b);
        assertInvariants(all);
    }

    @Test
    void 재고를_품절로_고쳐_끝내도_보상한다() {
        long author = member();
        String link = url("s" + uniq() + ".shop.example.com");
        long post = writeId(author, deal(link));
        List<Long> requesters = requestBy(post, 3);
        update(author, post, withStock(deal(link), "SOLD_OUT"));
        assertThat(dealRow(post)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        assertInvariants(requesters);
    }

    @Test
    void 의심이_아닌_딜의_종료는_보상하지_않는다() {
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 2);
        states.end(Long.toString(post), author);
        requesters.forEach(m -> assertThat(rewards(m)).isZero());
    }

    @Test
    void keep_으로_반박된_회차의_요청자는_보상받지_않는다() {
        long author = member();
        long post = newDeal(author);
        List<Long> disputed = requestBy(post, 3);
        states.keep(Long.toString(post), author);
        clock.set(clock.instant().plusSeconds(1));
        List<Long> nextRound = requestBy(post, 1); // 문턱 전 — 의심 아님
        states.end(Long.toString(post), author);
        disputed.forEach(m -> assertThat(rewards(m)).isZero());
        nextRound.forEach(m -> assertThat(rewards(m)).isZero());

        long other = newDeal(author);
        List<Long> first = requestBy(other, 3);
        states.keep(Long.toString(other), author);
        clock.set(clock.instant().plusSeconds(1));
        List<Long> second = requestBy(other, 3); // 다시 의심 — 이 회차만 보상
        ends.endByManager(other, hotdealOperator(), null);
        first.forEach(m -> assertThat(rewards(m)).isZero());
        second.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
    }

    @Test
    void 하루_지급은_포인트_정책의_횟수까지() {
        setting("end_request_per_author_limit", 0);
        long hunter = member();
        int limit = rewardDailyLimit();
        List<Long> deals = new ArrayList<>();
        for (int i = 0; i <= limit; i++) {
            long post = newDeal(member());
            request(post, hunter, "SOLD_OUT");
            requestBy(post, 2);
            deals.add(post);
        }
        for (long post : deals) {
            ends.endByManager(post, hotdealOperator(), null);
        }
        assertThat(rewards(hunter)).isEqualTo(limit);
        assertThat(balance(hunter)).isEqualTo((long) limit * rewardPoints());
        assertInvariants(List.of(hunter));
    }

    @Test
    void 같은_요청에_두_번_주지_않는다() {
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 3);
        states.end(Long.toString(post), author);
        // 다시 불러도(다른 종료 경로가 늦게 와도) 원장 유일로 한 번
        tx.executeWithoutResult(s -> ends.lock(post).ifPresent(ends::rewardRequesters));
        assertThat(ends.endByManager(post, hotdealOperator(), null)).isEqualTo(DealEndService.Outcome.STATE_CHANGED);
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        assertInvariants(requesters);
    }

    @Test
    void 동시_종료_경합에서_한_길만_끝내고_보상은_한_번() throws Exception {
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 3);
        afterFlag(post, Duration.ofHours(13));
        long operator = hotdealOperator();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    states.end(Long.toString(post), author);
                    return true;
                } catch (com.community.zconfiguration.exception.CommonException e) {
                    return false;
                }
            }));
            results.add(pool.submit(() -> {
                start.await();
                return ends.endByManager(post, operator, null) == DealEndService.Outcome.CHANGED;
            }));
            results.add(pool.submit(() -> {
                start.await();
                return ends.autoEnd(post, clock.instant());
            }));
            start.countDown();
            int winners = 0;
            for (Future<Boolean> f : results) {
                winners += f.get() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        assertInvariants(requesters);
    }

    @Test
    void 보상은_DEAL_END_REQUEST_적립_줄이다() {
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 3);
        states.end(Long.toString(post), author);
        assertThat(jdbc.queryForObject("SELECT kind FROM point_ledger WHERE user_id = ? AND reason = 'DEAL_END_REQUEST'", String.class, requesters.get(0)))
                .isEqualTo("EARN");
    }
}
