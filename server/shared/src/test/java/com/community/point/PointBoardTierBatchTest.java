package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRunner;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunStatus;
import com.community.point.batch.PointPendingReleaseJob;
import com.community.point.batch.PointPopularRewardJob;
import com.community.point.entity.PointPendingGrantEntity;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointPendingGrantStore;
import com.community.point.repository.master.PointPopularRewardStore;
import com.community.point.service.PointService.HeldRelease;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시판 등급별 포인트의 관리자 배치(point-board-tier-contract) — 보류 확정 {@code point-pending-release}(SKIP LOCKED·한 번만)와 인기글 보상 {@code point-popular-reward}(전날 상위 N ·
 * 글쓴이 상한 · 다시 돌려도 한 번 · 목록 고정). 실제 PostgreSQL 위에서 배치 core 로 돌린다. DB 를 다른 시험과 같이 쓰므로 자기 행만 본다.
 */
class PointBoardTierBatchTest extends PointTestBase {

    private static PointBatchTestSupport batch;
    private static PointPendingGrantStore pendingStore;
    private static PointPendingReleaseJob releaseJob;
    private static PointPopularRewardJob popularJob;

    private long board;

    @BeforeAll
    static void setUpBatch() {
        batch = new PointBatchTestSupport(kit);
        pendingStore = JpaTestContext.create(PointPendingGrantStore.class);
        releaseJob = new PointPendingReleaseJob(pendingStore, kit.points, batch.clocks, batch.transactions);
        popularJob = new PointPopularRewardJob(JpaTestContext.create(PointPopularRewardStore.class), kit.points, kit.policy, batch.clocks, batch.transactions);
    }

    @BeforeEach
    void board() {
        board = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 972)", board, "pb" + Long.toString(board, 36), "배치 시험 " + Long.toString(board, 36));
    }

    private long postRow(long author) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content) VALUES (?, ?, ?, '배치 시험', '본문')", id, board, author);
        return id;
    }

    private long pending(long user, long postId, PointReason reason, int amount, Instant releaseAt) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, status, created_at) "
                + "VALUES (?, ?, ?, 'POST', ?, ?, ?, 'PENDING', now())", id, user, reason.name(), postId, amount, Timestamp.from(releaseAt));
        return id;
    }

    private Map<String, Object> row(long pendingId) {
        return kit.jdbc.queryForMap("SELECT status, cancel_reason FROM point_pending_grants WHERE id = ?", pendingId);
    }

    private RunReport run(BatchRunner runner, com.community.batch.core.BatchJob job, Map<String, String> params) {
        return runner.runRequested(new BatchRunner.RunRequest(job.definition(), BatchRequestKind.NOW, null, BatchParameters.of(params), null, null, null));
    }

    private List<Integer> ledger(long member, String reason) {
        return kit.jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = ? ORDER BY id", Integer.class, member, reason);
    }

    // ── 보류 확정 ───────────────────────────────────────────────────────────────

    @Test
    void 확정_배치는_예정_시각이_지난_보류만_지급하거나_취소하고_두_번_돌아도_한_번만_준다() {
        long author = kit.newMember();
        long alive = postRow(author);
        long gone = postRow(author);
        long later = postRow(author);
        Instant past = kit.clock.instant().minusSeconds(60);
        long paid = pending(author, alive, PointReason.LIKE_BONUS, 50, past);
        long cancelled = pending(author, gone, PointReason.LIKE_BONUS, 50, past);
        long waiting = pending(author, later, PointReason.LIKE_BONUS, 50, kit.clock.instant().plus(Duration.ofHours(3)));
        kit.jdbc.update("UPDATE posts SET deleted_at = now() WHERE id = ?", gone);

        RunReport first = run(batch.runner(releaseJob), releaseJob, Map.of());
        assertThat(first.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(row(paid)).containsEntry("status", "RELEASED");
        assertThat(row(cancelled)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", HeldRelease.TARGET_GONE.name());
        assertThat(row(waiting)).containsEntry("status", "PENDING");
        assertThat(ledger(author, "LIKE_BONUS")).containsExactly(50);

        run(batch.runner(releaseJob), releaseJob, Map.of());
        assertThat(ledger(author, "LIKE_BONUS")).as("다시 돌려도 한 번").containsExactly(50);
        kit.assertInvariants(author);
    }

    @Test
    void 두_서버가_같은_보류를_동시에_집어도_SKIP_LOCKED_로_한_번만_지급한다() throws Exception {
        long author = kit.newMember();
        long postId = postRow(author);
        kit.jdbc.update("INSERT INTO post_features (id, post_id, author_id, featured_by, featured_at) VALUES (?, ?, ?, ?, now())", kit.ids.nextId(), postId, author,
                kit.operator);
        long id = pending(author, postId, PointReason.POST_FEATURED, 500, kit.clock.instant().minusSeconds(1));
        TransactionTemplate tx = new TransactionTemplate(batch.transactions);
        int servers = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(servers);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < servers; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    return tx.execute(status -> {
                        Instant now = kit.clock.instant();
                        Optional<PointPendingGrantEntity> locked = pendingStore.lockDue(id, now);
                        if (locked.isEmpty()) {
                            return false;
                        }
                        PointPendingGrantEntity grant = locked.get();
                        HeldRelease outcome = kit.points.releaseHeld(grant.getUserId(), PointReason.valueOf(grant.getReason()), grant.getRefType(), grant.getRefId(),
                                grant.getAmount());
                        grant.release(now);
                        try {
                            Thread.sleep(200); // 잠금을 쥐고 있는 동안 다른 「서버」가 집으러 온다
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return outcome == HeldRelease.RELEASED;
                    });
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int took = 0;
            for (Future<Boolean> result : results) {
                took += Boolean.TRUE.equals(result.get(30, TimeUnit.SECONDS)) ? 1 : 0;
            }
            assertThat(took).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(ledger(author, "POST_FEATURED")).containsExactly(500);
        assertThat(row(id)).containsEntry("status", "RELEASED");
        kit.assertInvariants(author);
    }

    // ── 인기글 보상 ─────────────────────────────────────────────────────────────

    /** 어제(한국 시간) 정오에 쓴 글 — 추천 수로 순위를 정한다. 글 ID 는 그 시각의 Snowflake(인기글 후보가 ID 범위로 가지치기한다) */
    private long yesterdayPost(Long author, int likes, int seq) {
        Instant noon = LocalDate.now(ServiceTime.ZONE).minusDays(1).atTime(12, 0).atZone(ServiceTime.ZONE).toInstant();
        long id = SnowflakeIdGenerator.floorIdAt(noon) + (kit.ids.nextId() % 1_000_000L) * 64 + seq;
        if (author == null) {
            kit.jdbc.update("INSERT INTO posts (id, board_id, guest_name, author_ip, title, content, like_count, created_at, updated_at) "
                    + "VALUES (?, ?, '손님', '10.9.9.9', '인기 시험', '본문', ?, ?, ?)", id, board, likes, Timestamp.from(noon), Timestamp.from(noon));
        } else {
            kit.jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content, like_count, created_at, updated_at) VALUES (?, ?, ?, '인기 시험', '본문', ?, ?, ?)", id,
                    board, author, likes, Timestamp.from(noon), Timestamp.from(noon));
        }
        return id;
    }

    @Test
    void 인기글_보상은_전날_상위_글쓴이에게_글마다_한_번이고_다시_돌려도_목록이_바뀌지_않는다() {
        String day = LocalDate.now(ServiceTime.ZONE).minusDays(1).toString();
        long a = kit.newMember();
        long b = kit.newMember();
        long ops = kit.newMember();
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), ops);
        int base = 900_000 + (int) (kit.ids.nextId() % 1000); // 다른 시험의 글보다 높게
        long a1 = yesterdayPost(a, base + 90, 1);
        long a2 = yesterdayPost(a, base + 80, 2);
        long a3 = yesterdayPost(a, base + 70, 3); // 한 글쓴이는 2개까지(popular_settings.max_per_author 기본 2)
        long b1 = yesterdayPost(b, base + 60, 4);
        long guest = yesterdayPost(null, base + 50, 5);
        long o1 = yesterdayPost(ops, base + 40, 6);

        kit.withSettings(Map.of("popular_reward_points", 100, "popular_reward_top_n", 50), () -> {
            RunReport first = run(batch.runner(popularJob), popularJob, Map.of("day", day));
            assertThat(first.status()).isEqualTo(BatchRunStatus.OK);
            assertThat(ledger(a, "POPULAR")).containsExactly(100, 100);
            assertThat(ledger(b, "POPULAR")).containsExactly(100);
            assertThat(ledger(ops, "POPULAR")).as("운영팀 계정(스위치 켬)").isEmpty();
            List<Long> picked = kit.jdbc.queryForList("SELECT post_id FROM point_popular_rewards WHERE day = ?::date AND post_id IN (?, ?, ?, ?, ?, ?) ORDER BY rank",
                    Long.class, day, a1, a2, a3, b1, guest, o1);
            assertThat(picked).as("점수 순 · 글쓴이 상한").containsExactly(a1, a2, b1, guest, o1);

            long late = kit.newMember();
            yesterdayPost(late, base + 500, 7); // 뽑은 뒤에 점수가 바뀌어도 목록은 그대로
            run(batch.runner(popularJob), popularJob, Map.of("day", day));
            assertThat(ledger(a, "POPULAR")).containsExactly(100, 100);
            assertThat(ledger(late, "POPULAR")).isEmpty();
        });
        kit.assertInvariants(a, b, ops);
    }

    @Test
    void 인기글_보상_정책이_0이면_꺼짐이다() {
        String day = LocalDate.now(ServiceTime.ZONE).minusDays(2).toString();
        long a = kit.newMember();
        kit.withSettings(Map.of("popular_reward_points", 0), () -> {
            RunReport report = run(batch.runner(popularJob), popularJob, Map.of("day", day));
            assertThat(report.status()).isIn(BatchRunStatus.OK, BatchRunStatus.SKIP);
        });
        assertThat(kit.count("SELECT count(*) FROM point_popular_rewards WHERE day = ?::date", day)).isZero();
        assertThat(ledger(a, "POPULAR")).isEmpty();
    }

    @Test
    void 두_배치의_이름_설명_결과_대상_문구는_두_언어에_있다() throws java.io.IOException {
        for (String language : List.of("ko", "en")) {
            java.util.Properties messages = new java.util.Properties();
            try (java.io.InputStream in = PointBoardTierBatchTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
                messages.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
            for (String key : List.of(releaseJob.definition().nameKey(), releaseJob.definition().descriptionKey(), popularJob.definition().nameKey(),
                    popularJob.definition().descriptionKey(), "batch.result.pointPendingRelease", "batch.result.pointPendingRelease.none",
                    "batch.result.pointPopularReward", "batch.result.pointPopularReward.none", "batch.result.pointPopularReward.off",
                    "batch.target." + PointPendingReleaseJob.TARGET, "batch.target." + PointPopularRewardJob.TARGET, "point.popularReward.day.notPast",
                    "point.reason.POST_FEATURED", "point.reason.POST_FEATURED_REVOKE", "audit.action.POST_FEATURE", "audit.action.POST_UNFEATURE")) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
    }
}
