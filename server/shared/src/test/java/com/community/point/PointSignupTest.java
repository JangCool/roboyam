package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 가입 포인트(계약 「1-3」 확정 3·4·5, commerce.md 6-2) — 계정마다 평생 한 번({@code SIGNUP} + {@code USER:{user_id}}, 원장 유일 색인), 0 이면 없음, 동결·정상 회원 아님이면 없음,
 * 하루 총 한도 제외, 미회수 상계. 본인인증과 이은 흐름(재인증·동시 확정·동결 뒤 풀려도 안 줌)은 api {@code SignupPointIdentityTest}.
 */
class PointSignupTest extends PointTestBase {

    private long signup(long member) {
        return kit.inTx(() -> kit.points.signup(member));
    }

    @Test
    void 가입_포인트는_한_번만이고_대상은_USER_회원_ID다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("signup_points", 300), () -> {
            assertThat(signup(member)).isEqualTo(300);
            assertThat(signup(member)).isZero();
        });
        assertThat(kit.jdbc.queryForMap("SELECT kind, delta, ref_type, ref_id FROM point_ledger WHERE user_id = ? AND reason = 'SIGNUP'", member))
                .containsEntry("kind", "EARN").containsEntry("delta", 300).containsEntry("ref_type", "USER").containsEntry("ref_id", member);
        assertThat(kit.inTx(() -> kit.points.signupReceived(member))).isTrue();
        kit.assertInvariants(member);
    }

    @Test
    void 동시에_불러도_한_번만_준다() throws Exception {
        long member = kit.newMember();
        kit.withSettings(Map.of("signup_points", 300), () -> {
            ExecutorService pool = Executors.newFixedThreadPool(6);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<Long>> futures = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return signup(member);
                    }));
                }
                start.countDown();
                long sum = 0;
                for (Future<Long> future : futures) {
                    sum += future.get(60, TimeUnit.SECONDS);
                }
                assertThat(sum).isEqualTo(300);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                pool.shutdownNow();
            }
        });
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'SIGNUP'", member)).isEqualTo(1);
        kit.assertInvariants(member);
    }

    @Test
    void 정책이_0이면_주지_않는다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("signup_points", 0), () -> assertThat(signup(member)).isZero());
        assertThat(kit.inTx(() -> kit.points.signupReceived(member))).isFalse();
    }

    @Test
    void 동결_중이거나_정상_회원이_아니면_주지_않는다() {
        long frozen = kit.newMember();
        kit.freeze(frozen);
        long suspended = kit.newMember();
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        kit.withSettings(Map.of("signup_points", 300), () -> {
            assertThat(signup(frozen)).isZero();
            assertThat(signup(suspended)).isZero();
        });
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id IN (?, ?)", frozen, suspended)).isZero();
    }

    @Test
    void 미회수가_있으면_가입_포인트에서_상계하고_하루_총_한도에는_들지_않는다() {
        long member = kit.newMember();
        long postId = kit.ids.nextId();
        assertThat(kit.earnPost(member, postId)).isEqualTo(20);
        kit.use(member, 20);
        kit.revokePost(member, postId); // 미회수 20
        kit.withSettings(Map.of("signup_points", 300, "daily_total_cap", 20), () -> assertThat(signup(member)).isEqualTo(300));
        assertThat(kit.balance(member)).containsExactly(280, 0, 0, 280);
        long line = kit.entryId(member, "SIGNUP", "USER", member);
        assertThat(kit.entryId(member, "REVOKE_DEBT_OFFSET", "LEDGER", line)).isPositive();
        assertThat(kit.inTx(() -> kit.points.earnedToday(member))).as("가입 포인트는 활동 적립 합에 없다").isEqualTo(20);
        kit.assertInvariants(member);
    }

    @Test
    void 원장_유일_색인이_같은_회원의_두_번째_가입_포인트_줄을_막는다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("signup_points", 300), () -> signup(member));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, "
                + "balance_after, ref_type, ref_id, created_at) VALUES (?, ?, 'EARN', 'SIGNUP', 1, 1, 0, 301, 'USER', ?, now())", kit.ids.nextId(), member, member))
                .hasMessageContaining("ux_point_ledger_ref");
    }
}
