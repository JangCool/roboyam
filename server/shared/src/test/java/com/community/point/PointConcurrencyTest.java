package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.point.exception.PointInsufficientException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 동시 적립·사용(스레드마다 연결·트랜잭션 따로) — 잔액 행 잠금 하나로 줄을 서므로 잔액이 음수가 되지 않고 불변식(원장 합 = 잔액 = 남은 묶음 합, 줄의 묶음 반영 합 = delta)이 유지된다
 * (commerce.md 5-4·5-5, 18절).
 */
class PointConcurrencyTest extends PointTestBase {

    private static <T> List<T> runTogether(List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return job.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 동시에_여럿이_쓰면_잔액만큼만_성공하고_음수가_되지_않는다() throws Exception {
        long member = kit.newMember();
        kit.adjust(member, 60);
        kit.chargePaid(member, 40);
        AtomicInteger refused = new AtomicInteger();

        List<Callable<Boolean>> jobs = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            jobs.add(() -> {
                try {
                    kit.use(member, 10);
                    return true;
                } catch (PointInsufficientException e) {
                    refused.incrementAndGet();
                    return false;
                }
            });
        }
        List<Boolean> results = runTogether(jobs);

        assertThat(results.stream().filter(ok -> ok).count()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(6);
        assertThat(kit.balance(member)).containsExactly(0, 0, 0, 0);
        kit.assertInvariants(member);
    }

    @Test
    void 적립_지급_사용_회수가_섞여도_불변식이_유지된다() throws Exception {
        long member = kit.newMember();
        kit.adjust(member, 200);
        long[] posts = new long[8];
        for (int i = 0; i < posts.length; i++) {
            posts[i] = kit.ids.nextId();
        }
        AtomicInteger used = new AtomicInteger();

        List<Callable<Long>> jobs = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            int n = i;
            jobs.add(() -> switch (n % 4) {
                case 0 -> kit.earnPost(member, posts[n / 4 % posts.length]); // 하루 5번까지만 — 넘으면 0
                case 1 -> kit.adjust(member, 7).applied();
                case 2 -> {
                    try {
                        kit.use(member, 13);
                        used.incrementAndGet();
                        yield -13L;
                    } catch (PointInsufficientException e) {
                        yield 0L;
                    }
                }
                default -> -kit.revokePost(member, posts[n / 4 % posts.length]);
            });
        }
        runTogether(jobs);

        long[] balance = kit.balance(member);
        assertThat(balance[0]).isGreaterThanOrEqualTo(0);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POST'", member)).as("하루 상한도 지킨다").isLessThanOrEqualTo(5);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'USE'", member)).isEqualTo(used.get());
        kit.assertInvariants(member);
    }
}
