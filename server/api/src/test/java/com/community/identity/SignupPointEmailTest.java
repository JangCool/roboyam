package com.community.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.service.EmailLinkService;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.service.IdentityVerificationService;
import com.community.point.service.PointPolicy;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.PendingSignup;
import com.community.sociallogin.service.SocialAccountWriter;
import com.community.testsupport.PointInvariants;
import com.community.zconfiguration.crypto.Digests;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 가입 포인트 = <b>이메일 인증(가입 완료) 때</b> 계정마다 평생 한 번(사용자 결정 2026-11-17 — 무료 포인트는 아이템 구매에만 쓰여 유료 포인트 역할이 없으므로
 * 본인인증(1인 1회)으로 묶을 까닭이 없다. 전에는 본인인증 확정 때 줬다 — 커머스 1-3 확정 3·4 를 고침). 실제 PostgreSQL.
 * <ul>
 *   <li>이메일 첫 인증({@link EmailLinkService#verify}) → 받음, 다시 눌러도 한 줄.</li>
 *   <li>소셜 가입({@link SocialAccountWriter#createMember}) — 공급자가 확인한 이메일이면 가입 때 받음, 확인 안 된 이메일은 그 뒤 이메일 인증 때, 이메일 없음은 없음.</li>
 *   <li>본인인증은 더 이상 가입 포인트를 주지 않는다.</li>
 *   <li>동결 중 인증이면 없고 풀려도 주지 않음, 정지 회원은 없음, 정책 0 이면 없음(인증은 성공).</li>
 * </ul>
 * 한 번만·동시성·상계·하루 한도 제외 규칙 자체는 shared {@code PointSignupTest}.
 */
class SignupPointEmailTest extends CommerceApiTestBase {

    private static final int SIGNUP = 300;

    @Autowired
    private EmailLinkService emailLinks;
    @Autowired
    private SocialAccountWriter social;
    @Autowired
    private AuthTokenRepository tokens;
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

    /** 이메일 인증 링크를 누른다 */
    private void verifyEmail(long member) {
        String raw = Digests.newToken();
        Instant now = Instant.now();
        tx.executeWithoutResult(s -> tokens.persist(AuthTokenEntity.create(ids.nextId(), member, AuthTokenPurpose.VERIFY_EMAIL, Digests.sha256Hex(raw),
                now.plus(Duration.ofHours(1)), now)));
        emailLinks.verify(raw);
    }

    private List<Integer> signupLines(long member) {
        return jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'SIGNUP' AND ref_type = 'USER' AND ref_id = ?", Integer.class, member,
                member);
    }

    private long socialMember(String email, boolean verified) {
        String nick = "sp" + Long.toString(ids.nextId() % 100_000_000_000L, 36);
        return social.createMember(new PendingSignup(SocialProvider.KAKAO, "sp-" + ids.nextId(), email, verified, "x", null, clock.instant().plus(Duration.ofMinutes(10))),
                nick).id();
    }

    @Test
    void 이메일_첫_인증이면_가입_포인트를_한_번_주고_다시_눌러도_한_줄이다() {
        long member = newMember();
        assertThat(signupLines(member)).as("가입만 하고 인증 전").isEmpty();

        verifyEmail(member);
        assertThat(signupLines(member)).containsExactly(SIGNUP);

        verifyEmail(member); // 이미 인증한 회원이 새 인증 링크를 눌렀다 — 첫 인증이 아니다
        assertThat(signupLines(member)).containsExactly(SIGNUP);
        assertThat(PointInvariants.violations(jdbc, member)).isEmpty();
    }

    @Test
    void 소셜_가입은_확인된_이메일이면_가입_때_주고_확인_안_된_이메일은_이메일_인증_때_준다() {
        long verified = socialMember("sp-v-" + ids.nextId() + "@test.local", true);
        long unverified = socialMember("sp-u-" + ids.nextId() + "@test.local", false);
        long noEmail = socialMember(null, false);

        assertThat(signupLines(verified)).containsExactly(SIGNUP);
        assertThat(signupLines(unverified)).isEmpty();
        assertThat(signupLines(noEmail)).isEmpty();

        verifyEmail(unverified);
        assertThat(signupLines(unverified)).containsExactly(SIGNUP);
    }

    @Test
    void 본인인증은_가입_포인트를_주지_않는다() {
        long member = newMember();
        identity.confirm(member, identity.start(member).verificationId(), new FakeIdentityInput("1990-05-14", newPerson()));
        assertThat(identity.me(member).verified()).isTrue();
        assertThat(signupLines(member)).isEmpty();

        verifyEmail(member);
        assertThat(signupLines(member)).as("이메일 인증 때 한 번").containsExactly(SIGNUP);
    }

    @Test
    void 동결_중에_인증하면_주지_않고_동결이_풀려도_주지_않는다() {
        long member = newMember();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, clock.instant().plus(Duration.ofHours(1)), "가입 포인트 동결 시험", null),
                newOperator(), "127.0.0.1");
        verifyEmail(member);
        assertThat(signupLines(member)).isEmpty();

        clock.advance(Duration.ofHours(2)); // 동결이 끝났다 — 다시 눌러도 첫 인증이 아니라 주지 않는다
        verifyEmail(member);
        assertThat(signupLines(member)).as("나중에도 주지 않는다").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ?", Integer.class, member)).isZero();
    }

    @Test
    void 정지_회원이_인증하면_주지_않는다() {
        long member = newMember();
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member);
        verifyEmail(member);
        assertThat(signupLines(member)).isEmpty();
    }

    @Test
    void 정책이_0이면_주지_않고_인증은_성공한다() {
        setSignup(0);
        long member = newMember();
        verifyEmail(member);
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, member)).isTrue();
        assertThat(signupLines(member)).isEmpty();
    }
}
