package com.community.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.exception.IdentityAlreadyVerifiedException;
import com.community.identity.service.IdentityVerificationService;
import com.community.point.service.PointPolicy;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.testsupport.PointInvariants;
import com.community.zconfiguration.exception.CommonException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 가입 포인트 = 본인인증 완료 때 계정마다 평생 한 번(커머스 1-3 확정 3·4, commerce.md 6-2) — 실제 본인인증 확정(가짜 구현) + PostgreSQL. 재인증·동시 확정에도 한 줄, 동결 중 인증이면 없고
 * 풀려도 주지 않음, 정책 0 이면 없음(인증은 성공), 정지 회원은 없음.
 */
class SignupPointIdentityTest extends CommerceApiTestBase {

    private static final String ADULT_BIRTH = "1990-05-14";
    private static final int SIGNUP = 300;

    @Autowired
    private IdentityVerificationService identity;
    @Autowired
    private PointPolicy policy;
    @Autowired
    private MemberRestrictionService restrictions;

    private Integer before;

    @BeforeEach
    void signupPoints() {
        before = jdbc.queryForObject("SELECT signup_points FROM point_settings WHERE id = 1", Integer.class);
        setSignup(SIGNUP);
    }

    @AfterEach
    void restore() {
        setSignup(before);
    }

    private void setSignup(int points) {
        jdbc.update("UPDATE point_settings SET signup_points = ? WHERE id = 1", points);
        policy.invalidate();
    }

    private void verify(long member) {
        identity.confirm(member, identity.start(member).verificationId(), new FakeIdentityInput(ADULT_BIRTH, newPerson()));
    }

    private List<Integer> signupLines(long member) {
        return jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'SIGNUP' AND ref_type = 'USER' AND ref_id = ?", Integer.class, member,
                member);
    }

    @Test
    void 본인인증을_확정하면_가입_포인트를_한_번_주고_다시_인증할_수_없어_두_번째는_없다() {
        long member = newMember();
        verify(member);
        assertThat(signupLines(member)).containsExactly(SIGNUP);

        assertThatThrownBy(() -> verify(member)).isInstanceOf(IdentityAlreadyVerifiedException.class);
        assertThat(signupLines(member)).containsExactly(SIGNUP);
        assertThat(PointInvariants.violations(jdbc, member)).isEmpty();
    }

    @Test
    void 같은_회원이_동시에_확정해도_인증도_가입_포인트도_하나다() throws Exception {
        long member = newMember();
        List<String> attempts = List.of(identity.start(member).verificationId(), identity.start(member).verificationId(), identity.start(member).verificationId());
        String person = newPerson();
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        List<String> results = new ArrayList<>();
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String attempt : attempts) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        identity.confirm(member, attempt, new FakeIdentityInput(ADULT_BIRTH, person));
                        return "OK";
                    } catch (CommonException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(results).containsOnlyOnce("OK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verifications WHERE user_id = ?", Integer.class, member)).isEqualTo(1);
        assertThat(signupLines(member)).containsExactly(SIGNUP);
        assertThat(PointInvariants.violations(jdbc, member)).isEmpty();
    }

    @Test
    void 동결_중에_인증하면_주지_않고_동결이_풀려도_주지_않는다() {
        long member = newMember();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, clock.instant().plus(Duration.ofHours(1)), "가입 포인트 동결 시험", null),
                newOperator(), "127.0.0.1");
        verify(member);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verifications WHERE user_id = ?", Integer.class, member)).as("인증은 성공").isEqualTo(1);
        assertThat(signupLines(member)).isEmpty();

        clock.advance(Duration.ofHours(2)); // 동결이 끝났다
        assertThatThrownBy(() -> verify(member)).isInstanceOf(IdentityAlreadyVerifiedException.class);
        assertThat(signupLines(member)).as("나중에도 주지 않는다").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ?", Integer.class, member)).isZero();
    }

    @Test
    void 정지_회원이_인증하면_주지_않는다() {
        long member = newMember();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member);
        verify(member);
        assertThat(signupLines(member)).isEmpty();
    }

    @Test
    void 정책이_0이면_주지_않고_인증은_성공한다() {
        setSignup(0);
        long member = newMember();
        verify(member);
        assertThat(identity.me(member).verified()).isTrue();
        assertThat(signupLines(member)).isEmpty();
    }
}
