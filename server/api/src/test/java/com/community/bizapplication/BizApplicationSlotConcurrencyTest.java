package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizSlotService;
import com.community.zconfiguration.exception.CommonException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 자리 한도 동시성(설계 3절 · commerce.md 18절 Testcontainers) — 마지막 자리를 두 운영자가 동시에 승인하면 상품별 어드바이저리 잠금 아래 다시 판정해 <b>하나만</b> 이기고
 * 다른 하나는 409 {@code BIZ_SLOT_FULL}(심사 대기 그대로). 결제 대기 건이 자리를 차지한 채로 한도를 넘지 않는다.
 */
class BizApplicationSlotConcurrencyTest extends BizApplicationApiTestBase {

    @Autowired
    private BizSlotService slots;

    @Test
    void 마지막_자리를_두_승인이_동시에_잡으면_하나만_이긴다() throws Exception {
        for (int round = 0; round < 3; round++) {
            BizPeriods.Period p = freshPeriod(3);
            // 넓은 배너 한도 2 — 한 자리를 먼저 채운다
            long first = submitBanner(approvedBusiness(), "WIDE", 0, 3, "ONLINE");
            approve(first, "ONLINE", p);
            long a = submitBanner(approvedBusiness(), "WIDE", 0, 3, "ONLINE");
            long b = submitBanner(approvedBusiness(), "WIDE", 0, 3, "MANUAL");
            long supply = quoteSupply(a, p);
            String deadline = clock.instant().plus(Duration.ofHours(48)).toString();
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> results = new ArrayList<>();
            for (long[] pair : new long[][] {{a, 0}, {b, 1}}) {
                results.add(pool.submit(() -> {
                    go.await();
                    try {
                        review.approve(operator, pair[0], pair[1] == 0 ? "ONLINE" : "MANUAL", supply, null, deadline, p.start().toString(), p.end().toString());
                        return "OK";
                    } catch (CommonException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            go.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(60, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertThat(outcomes).as("round " + round).containsExactlyInAnyOrder("OK", "BIZ_SLOT_FULL");
            assertThat(slots.room(BizProduct.WIDE_BANNER, p.start(), p.end(), null).remaining()).isZero();
            long submitted = List.of(a, b).stream().filter(id -> app(id).status() == BizApplicationStatus.SUBMITTED).count();
            assertThat(submitted).isEqualTo(1);
        }
    }
}
