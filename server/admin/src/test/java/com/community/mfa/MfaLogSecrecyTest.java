package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.mfa.controller.MfaTrustCookie;
import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaVerifyResult;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.request.MfaCodeRequest;
import com.community.mfa.dto.request.MfaPasswordRequest;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.dto.response.MfaEnrollmentConfirmResponse;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.mfa.dto.response.MfaRecoveryCodesResponse;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 비밀키·OTP 코드·복구 코드·신뢰 기기 쿠키 값·비밀키 암호문·비밀번호가 <b>서버 로그에 없다</b>(commerce-foundation-contract 「0-3」 — 「비밀키·코드·복구 코드·쿠키 값은
 * 감사·로그·오류에 없다」). 실제 필터 체인으로 등록·확정·로그인 2단계(실패·복구 코드·OTP+다시 묻지 않기)·복구 코드 새로 만들기·다시 등록을 돌리며 표준 출력(로그백 콘솔,
 * local 프로필이라 com.community 는 DEBUG)을 잡아 확인한다. 요청·응답 DTO 의 {@code toString} 감춤도 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class MfaLogSecrecyTest extends MfaMvcTestBase {

    private final List<String> secretValues = new ArrayList<>();
    private final List<String> otpCodes = new ArrayList<>();

    private String data(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data." + path);
    }

    private List<String> codes(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.recoveryCodes");
    }

    private MvcResult postJson(String path, String body, Cookie... cookies) throws Exception {
        return mvc.perform(withCsrf(post(path), cookies).content(body)).andReturn();
    }

    private String otp(String code) {
        otpCodes.add(code);
        return code;
    }

    private void rememberRecovery(List<String> codes) {
        secretValues.addAll(codes);
        codes.forEach(code -> secretValues.add(code.replace("-", "")));
    }

    @Test
    void 등록부터_로그인_2단계까지_돌려도_로그에_비밀값이_없다(CapturedOutput output) throws Exception {
        mfaOff();
        long operator = newOperator();
        secretValues.add(PASSWORD);

        // 자발 등록(로그인 세션)
        Cookie session = sessionCookie(login(operator, false));
        MvcResult started = postJson("/api/admin/mfa/enrollment", "{}", session);
        assertThat(started.getResponse().getStatus()).isEqualTo(200);
        String secret = data(started, "secret");
        secretValues.add(secret);
        MvcResult confirmed = postJson("/api/admin/mfa/enrollment/confirm", "{\"code\":\"" + otp(codeNow(secret)) + "\"}", session);
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        List<String> recovery = codes(confirmed);
        rememberRecovery(recovery);

        // 로그인 2단계 — 틀린 코드, 복구 코드
        mfaOn();
        Cookie pending = sessionCookie(login(operator, false));
        assertThat(postJson("/api/admin/auth/mfa/verify", "{\"code\":\"" + otp(wrongCode(secret)) + "\"}", pending).getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson("/api/admin/auth/mfa/verify", "{\"recoveryCode\":\"" + recovery.get(0).toLowerCase() + "\"}", pending).getResponse().getStatus())
                .isEqualTo(200);

        // 로그인 2단계 — OTP + 이 기기에서 다시 묻지 않기
        Cookie pending2 = sessionCookie(login(operator, false));
        MvcResult remembered = postJson("/api/admin/auth/mfa/verify", "{\"code\":\"" + otp(nextCode(secret)) + "\",\"rememberDevice\":true}", pending2);
        assertThat(remembered.getResponse().getStatus()).isEqualTo(200);
        Cookie trust = remembered.getResponse().getCookie(MfaTrustCookie.NAME);
        secretValues.add(trust.getValue());
        // 신뢰 기기로 바로 로그인
        assertThat(login(operator, false, trust).getResponse().getContentAsString()).contains("\"authenticated\":true");

        // 복구 코드 새로 만들기 · 다시 등록
        MvcResult regenerated = postJson("/api/admin/mfa/recovery-codes", "{\"code\":\"" + otp(nextCode(secret)) + "\"}", session);
        assertThat(regenerated.getResponse().getStatus()).isEqualTo(200);
        rememberRecovery(codes(regenerated));
        MvcResult reenroll = postJson("/api/admin/mfa/reenroll", "{\"password\":\"" + PASSWORD + "\"}", session);
        assertThat(reenroll.getResponse().getStatus()).isEqualTo(200);
        String secret2 = data(reenroll, "secret");
        secretValues.add(secret2);
        MvcResult reconfirmed = postJson("/api/admin/mfa/enrollment/confirm", "{\"code\":\"" + otp(nextCode(secret2)) + "\"}", session);
        assertThat(reconfirmed.getResponse().getStatus()).isEqualTo(200);
        rememberRecovery(codes(reconfirmed));
        secretValues.add(jdbc.queryForObject("SELECT secret_enc FROM operator_mfa WHERE operator_id = ?", String.class, operator));

        String logs = output.getAll();
        assertThat(logs).as("로그를 실제로 잡고 있다").contains("OTP 등록을 확정했다", "신뢰 기기를 모두 끊었다");
        for (String value : secretValues) {
            assertThat(logs).as("로그에 비밀값이 없다").doesNotContain(value);
        }
        for (String code : otpCodes) {
            assertThat(Pattern.compile("(?<![0-9])" + code + "(?![0-9])").matcher(logs).find()).as("로그에 OTP 코드 %s 가 없다", code).isFalse();
        }
    }

    @Test
    void 요청_응답_DTO_의_toString_은_비밀값을_싣지_않는다() {
        String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        String recovery = "ABCDE-FGHJK";
        String token = "trust-token-value-0123456789abcdefghijklmnop";

        List<String> rendered = List.of(
                new MfaVerifyRequest("123456", recovery, true).toString(),
                new MfaCodeRequest("654321").toString(),
                new MfaPasswordRequest(PASSWORD).toString(),
                new MfaEnrollmentView(secret, "otpauth://totp/x:y?secret=" + secret, "x", "y").toString(),
                new MfaEnrollmentConfirmResponse(List.of(recovery), new AdminMeResponse(false, null, null, null, null, null)).toString(),
                new MfaRecoveryCodesResponse(List.of(recovery)).toString(),
                new MfaVerifyResult(MfaLoginMethod.TOTP, null, token, 30).toString(),
                new OperatorMfaRow(1L, "v1:secret-cipher", null, 1L, null, "v1:pending-cipher", null, false).toString());

        assertThat(rendered).allSatisfy(text -> assertThat(text).doesNotContain(secret, recovery, token, "123456", "654321", PASSWORD, "v1:secret-cipher",
                "v1:pending-cipher"));
    }
}
