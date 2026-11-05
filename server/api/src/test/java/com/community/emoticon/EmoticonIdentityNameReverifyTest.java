package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.exception.IdentityAlreadyVerifiedException;
import com.community.identity.exception.IdentityPersonMismatchException;
import com.community.identity.service.IdentityVerificationService;
import com.community.point.service.PointPolicy;
import com.community.user.entity.Role;
import com.community.zconfiguration.crypto.DataCipher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 이름 채우기 재인증(emoticon-contract S3 — 사용자 결정 2026-11-05 「추천대로」) — 본인인증은 했지만 이름 암호문이 없는 회원(V141 전 인증)만 한 번 다시 인증할 수 있다.
 * 같은 사람(DI·CI 같음)이면 이름만 채우고(생년월일·인증 시각·DI/CI 그대로, 가입 포인트 다시 없음) 정산 정보 등록이 통과하고, 다른 사람이면 막힌다. 이름이 있으면 지금처럼 막힌다.
 * 정산 정보의 {@code 409 EMOTICON_IDENTITY_NAME_MISSING} 은 최상위 {@code reverify: true} 로 화면이 재인증으로 보낼 수 있다.
 */
class EmoticonIdentityNameReverifyTest extends CommerceApiTestBase {

    private static final String BODY = "{\"type\":\"INDIVIDUAL\",\"businessNumber\":null,\"bankCode\":\"KB\",\"accountNumber\":\"123-4567-890123\",\"holderName\":\"김작가\"}";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private IdentityVerificationService identity;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private PointPolicy policy;

    private MockMvc mvc;
    private Integer signupBefore;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        signupBefore = jdbc.queryForObject("SELECT signup_points FROM point_settings WHERE id = 1", Integer.class);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE point_settings SET signup_points = ? WHERE id = 1", signupBefore);
        policy.invalidate();
    }

    private void signupPoints(int points) {
        jdbc.update("UPDATE point_settings SET signup_points = ? WHERE id = 1", points);
        policy.invalidate();
    }

    private long creator() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key) VALUES (?, ?, 'REGULAR')", id, "n" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    private void verify(long userId, String person, String birth, String name) {
        identity.confirm(userId, identity.start(userId).verificationId(), new FakeIdentityInput(birth, person, name));
    }

    /** V141 전에 인증한 회원처럼 — 이름 암호문을 지운다 */
    private void dropName(long userId) {
        jdbc.update("UPDATE identity_verifications SET name_enc = NULL WHERE user_id = ?", userId);
    }

    private Map<String, Object> verification(long userId) {
        return jdbc.queryForMap("SELECT di_hash, ci_hash, birth_date, verified_at, provider_tx_id, name_enc FROM identity_verifications WHERE user_id = ?", userId);
    }

    private List<Integer> signupLines(long userId) {
        return jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'SIGNUP'", Integer.class, userId);
    }

    private org.springframework.test.web.servlet.ResultActions savePayout(long userId) throws Exception {
        AuthUser user = new AuthUser(userId, Role.USER);
        return mvc.perform(put("/api/me/emoticon-studio/payout-account").principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities()))
                .header("Accept-Language", "ko").contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    @Test
    void 이름_없는_회원은_같은_사람으로_한_번_다시_인증해_이름만_채우고_정산_정보_등록이_통과한다() throws Exception {
        signupPoints(100);
        long member = creator();
        String person = newPerson();
        verify(member, person, "1990-05-14", "김작가");
        dropName(member);
        assertThat(signupLines(member)).containsExactly(100);
        Map<String, Object> before = verification(member);
        savePayout(member).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_IDENTITY_NAME_MISSING"))
                .andExpect(jsonPath("$.error.reverify").value(true));
        // 재인증 — 나이가 달라 보이는 입력이어도 생년월일·인증 시각·해시·업체 건 번호는 그대로, 이름만
        verify(member, person, "1991-01-01", "김작가");
        Map<String, Object> after = verification(member);
        assertThat(after.get("name_enc")).isNotNull();
        assertThat(cipher.decrypt(IdentityVerificationService.NAME, (String) after.get("name_enc"))).isEqualTo("김작가");
        for (String column : List.of("di_hash", "ci_hash", "birth_date", "verified_at", "provider_tx_id")) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(signupLines(member)).as("가입 포인트를 다시 주지 않는다").containsExactly(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verification_attempts WHERE user_id = ? AND status = 'VERIFIED'", Integer.class, member)).isEqualTo(2);
        savePayout(member).andExpect(status().isOk()).andExpect(jsonPath("$.data.registered").value(true)).andExpect(jsonPath("$.data.holderMasked").value("김*가"));
        // 한 번 채우면 다시 할 수 없다
        assertThatThrownBy(() -> identity.start(member)).isInstanceOf(IdentityAlreadyVerifiedException.class);
    }

    @Test
    void 처음_인증_때_가입_포인트가_없었으면_재인증_때도_주지_않는다() {
        signupPoints(0);
        long member = creator();
        String person = newPerson();
        verify(member, person, "1990-05-14", null);
        dropName(member);
        signupPoints(100);
        verify(member, person, "1990-05-14", null);
        assertThat(signupLines(member)).isEmpty();
        assertThat(identity.nameMissing(member)).isFalse();
    }

    @Test
    void 다른_사람이_재인증하면_막고_이름은_비어_있고_시도는_실패로_남는다() {
        long member = creator();
        verify(member, newPerson(), "1990-05-14", "김작가");
        dropName(member);
        Map<String, Object> before = verification(member);
        assertThatThrownBy(() -> verify(member, newPerson(), "1990-05-14", "박남"))
                .isInstanceOfSatisfying(IdentityPersonMismatchException.class, e -> assertThat(e.getErrorCode()).isEqualTo("IDENTITY_PERSON_MISMATCH"));
        assertThat(verification(member)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT failure_code FROM identity_verification_attempts WHERE user_id = ? AND status = 'FAILED'", String.class, member))
                .isEqualTo("IDENTITY_PERSON_MISMATCH");
        assertThat(identity.nameMissing(member)).isTrue();
    }

    @Test
    void 이름이_있는_회원의_재인증은_지금처럼_막힌다() {
        long member = creator();
        String person = newPerson();
        verify(member, person, "1990-05-14", "김작가");
        assertThat(identity.nameMissing(member)).isFalse();
        assertThatThrownBy(() -> identity.start(member)).isInstanceOf(IdentityAlreadyVerifiedException.class);
        // 이름이 없을 때 받아 둔 시도 번호라도 이름이 채워진 뒤에는 확정되지 않는다
        dropName(member);
        String attempt = identity.start(member).verificationId();
        verify(member, person, "1990-05-14", "김작가");
        assertThatThrownBy(() -> identity.confirm(member, attempt, new FakeIdentityInput("1990-05-14", person, "김작가")))
                .isInstanceOf(IdentityAlreadyVerifiedException.class);
    }
}
