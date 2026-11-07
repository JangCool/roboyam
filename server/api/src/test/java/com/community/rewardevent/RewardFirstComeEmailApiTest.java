package com.community.rewardevent;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.service.EmailLinkService;
import com.community.commerce.CommerceApiTestBase;
import com.community.rewardevent.dto.RewardFirstComeConfig;
import com.community.rewardevent.dto.RewardLineDto;
import com.community.rewardevent.dto.RewardOperator;
import com.community.rewardevent.dto.RewardTierDto;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.service.RewardEventEditService;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.PendingSignup;
import com.community.sociallogin.service.SocialAccountWriter;
import com.community.zconfiguration.crypto.Digests;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 선착순 보상 이벤트의 「이메일 인증(가입 완료)」 기준 사건이 api 의 두 입구에서 실제로 불리는가(event-admin-contract 「상태·방식」 FIRST_COME) — 실제 PostgreSQL.
 * <ul>
 *   <li>이메일 첫 인증({@link EmailLinkService#verify}) → 받음. 이미 인증한 회원이 다시 인증 링크를 눌러도·이메일 변경 링크는 기준 사건이 아니다.</li>
 *   <li>소셜 가입({@link SocialAccountWriter#createMember}) — 공급자가 확인한 이메일이면 받음, 확인하지 않은 이메일·이메일 없음은 받지 않는다.</li>
 * </ul>
 * 이벤트 규칙 자체(인원·1인 1회·동시성·기간)는 shared {@code RewardFirstComeTest} 가 막는다. 여기서는 연결만 본다. 시험마다 만든 이벤트는 끝에 마감한다(같은 시험 DB 를 쓰는 다른 묶음이 받지 않게).
 */
class RewardFirstComeEmailApiTest extends CommerceApiTestBase {

    private static final int POINTS = 120;

    @Autowired
    private RewardEventEditService edits;
    @Autowired
    private EmailLinkService emailLinks;
    @Autowired
    private SocialAccountWriter social;
    @Autowired
    private AuthTokenRepository tokens;

    private RewardOperator op;
    private final List<Long> opened = new ArrayList<>();

    @BeforeEach
    void operator() {
        op = new RewardOperator(newOperator(), "127.0.0.1", true, () -> { });
    }

    @AfterEach
    void closeEvents() {
        opened.forEach(id -> edits.close(op, id));
        opened.clear();
    }

    @Test
    void 이메일_첫_인증이면_받고_다시_눌러도_한_번뿐이다() {
        long event = startedFirstCome("EMAIL_VERIFIED");
        long member = newMember();
        String raw = token(member, AuthTokenPurpose.VERIFY_EMAIL, null);

        emailLinks.verify(raw);

        assertThat(recipientStatus(event, member)).isEqualTo("GRANTED");
        assertThat(eventPoints(member)).isEqualTo(POINTS);
        // 이미 인증한 회원이 새 인증 링크를 눌러도 기준 사건이 아니다(첫 인증만)
        emailLinks.verify(token(member, AuthTokenPurpose.VERIFY_EMAIL, null));
        assertThat(eventPoints(member)).isEqualTo(POINTS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reward_event_recipients WHERE event_id = ?", Long.class, event)).isEqualTo(1);
    }

    @Test
    void 본인인증_기준_이벤트는_이메일_인증으로_받지_않는다() {
        long event = startedFirstCome("IDENTITY_VERIFIED");
        long member = newMember();

        emailLinks.verify(token(member, AuthTokenPurpose.VERIFY_EMAIL, null));

        assertThat(recipientStatus(event, member)).isNull();
        assertThat(eventPoints(member)).isZero();
    }

    @Test
    void 이메일_변경_링크는_기준_사건이_아니다() {
        long event = startedFirstCome("EMAIL_VERIFIED");
        long member = ids.nextId();
        memberFixture.user(member, "rf-" + member + "@test.local", true);
        String newEmail = "rf-new-" + member + "@test.local";

        emailLinks.verify(token(member, AuthTokenPurpose.CHANGE_EMAIL, newEmail));

        assertThat(memberFixture.email(member)).isEqualTo(newEmail);
        assertThat(recipientStatus(event, member)).isNull();
    }

    @Test
    void 소셜_가입은_공급자가_확인한_이메일일_때만_받는다() {
        long event = startedFirstCome("EMAIL_VERIFIED");
        Instant later = clock.instant().plus(Duration.ofMinutes(10));

        long verified = social.createMember(new PendingSignup(SocialProvider.KAKAO, "rf-v-" + ids.nextId(), "rf-v-" + ids.nextId() + "@test.local", true, "x", null, later),
                nick()).id();
        long unverified = social.createMember(new PendingSignup(SocialProvider.KAKAO, "rf-u-" + ids.nextId(), "rf-u-" + ids.nextId() + "@test.local", false, "x", null, later),
                nick()).id();
        long noEmail = social.createMember(new PendingSignup(SocialProvider.KAKAO, "rf-n-" + ids.nextId(), null, false, "x", null, later), nick()).id();

        assertThat(recipientStatus(event, verified)).isEqualTo("GRANTED");
        assertThat(eventPoints(verified)).isEqualTo(POINTS);
        assertThat(recipientStatus(event, unverified)).isNull();
        assertThat(recipientStatus(event, noEmail)).isNull();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 진행 중인 선착순 이벤트(인원 100, 무상 {@value #POINTS}P) — 기간은 실제 시각 기준(인증 서비스가 {@code Instant.now()} 를 쓴다) */
    private long startedFirstCome(String trigger) {
        Instant now = Instant.now();
        RewardLineDto points = new RewardLineDto("new-1", "POINT", POINTS, null, null, null, null, null, null, null, null, null, null);
        RewardEventSaveRequest body = new RewardEventSaveRequest("가입 선착순 " + ids.nextId(), "", now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)), "", "FIRST_COME",
                new RewardFirstComeConfig(trigger, 100), null, null, true, List.of(new RewardTierDto("new-2", "선착순", 100, List.of(points))));
        long id = Long.parseLong(edits.create(op, body).id());
        edits.start(op, id);
        opened.add(id);
        return id;
    }

    /** 인증·변경 토큰을 넣고 원문을 돌려준다 */
    private String token(long userId, AuthTokenPurpose purpose, String newEmail) {
        String raw = Digests.newToken();
        String hash = Digests.sha256Hex(raw);
        Instant now = Instant.now();
        tx.executeWithoutResult(s -> tokens.persist(purpose == AuthTokenPurpose.CHANGE_EMAIL
                ? AuthTokenEntity.createEmailChange(ids.nextId(), userId, newEmail, hash, now.plus(Duration.ofHours(1)), now)
                : AuthTokenEntity.create(ids.nextId(), userId, purpose, hash, now.plus(Duration.ofHours(1)), now)));
        return raw;
    }

    private String recipientStatus(long event, long userId) {
        List<String> rows = jdbc.queryForList("SELECT status FROM reward_event_recipients WHERE event_id = ? AND user_id = ?", String.class, event, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 이 회원이 받은 이벤트 보상 포인트 합(무상) */
    private long eventPoints(long userId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(sum(free_delta), 0) FROM point_ledger WHERE user_id = ? AND reason = 'EVENT_REWARD'", Long.class, userId);
        return sum == null ? 0 : sum;
    }

    private String nick() {
        return "rf" + Long.toString(ids.nextId() % 100_000_000_000L, 36);
    }
}
