package com.community.identity.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.IdentityStartInfo;
import com.community.identity.dto.VerifiedPerson;
import com.community.identity.exception.IdentityNotConfiguredException;
import com.community.identity.exception.IdentityProviderFailedException;
import com.community.portone.dto.PortOneIdentityCredentials;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 포트원 V2 본인인증 조회(계약 「0-4」 — {@code GET https://api.portone.io/identity-verifications/{id}} + {@code Authorization: PortOne <API 비밀키>}, VERIFIED 의
 * {@code verifiedCustomer} 에서 ci·di·birthDate)를 HTTP 흉내({@link MockRestServiceServer})로 확인한다 — 실제 포트원은 부르지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class PortOneIdentityVerifierTest {

    private static final String ID = "iv-1234567890";
    private static final String STORE = "store-abc";
    private static final String SECRET = "secret-must-not-leak-77";
    private static final String CI = "CI-VALUE-must-not-leak==";
    private static final String DI = "DI-VALUE-must-not-leak==";

    private final AtomicReference<Optional<PortOneIdentityCredentials>> settings = new AtomicReference<>();
    private MockRestServiceServer server;
    private PortOneIdentityVerifier verifier;

    @BeforeEach
    void setUp() {
        settings.set(Optional.of(new PortOneIdentityCredentials(STORE, SECRET, "channel-xyz")));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new PortOneIdentityVerifier(settings::get, builder);
    }

    private static String verified(String id, String ci, String di, String birthDate) {
        return "{\"status\":\"VERIFIED\",\"id\":\"" + id + "\",\"channel\":{\"id\":\"ch\"},\"verifiedCustomer\":{\"name\":\"홍길동\",\"phoneNumber\":\"01012345678\","
                + (ci == null ? "" : "\"ci\":\"" + ci + "\",") + (di == null ? "" : "\"di\":\"" + di + "\",") + "\"birthDate\":\"" + birthDate + "\",\"gender\":\"MALE\"},"
                + "\"requestedAt\":\"2026-10-24T00:00:00Z\",\"verifiedAt\":\"2026-10-24T00:01:00Z\",\"pgTxId\":\"pg-1\"}";
    }

    private void expectLookup(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(requestTo("https://api.portone.io/identity-verifications/" + ID + "?storeId=" + STORE))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "PortOne " + SECRET))
                .andRespond(response);
    }

    @Test
    void 인증_번호로_단건_조회하고_VERIFIED_의_ci_di_생년월일을_쓴다(CapturedOutput output) {
        expectLookup(withSuccess(verified(ID, CI, DI, "1990-05-14"), MediaType.APPLICATION_JSON));

        VerifiedPerson person = verifier.verify(ID, null);

        server.verify();
        assertThat(person.ci()).isEqualTo(CI);
        assertThat(person.di()).isEqualTo(DI);
        assertThat(person.birthDate()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(person.providerTxId()).isEqualTo(ID);
        assertThat(person.toString()).doesNotContain(CI).doesNotContain(DI).doesNotContain("1990");
        assertThat(output.getAll()).doesNotContain(SECRET).doesNotContain(CI).doesNotContain(DI);
    }

    @Test
    void DI_나_CI_하나만_있어도_받는다() {
        expectLookup(withSuccess(verified(ID, null, DI, "2001-01-02"), MediaType.APPLICATION_JSON));
        assertThat(verifier.verify(ID, null).ci()).isNull();
    }

    @Test
    void 끝나지_않았거나_실패한_인증은_502() {
        expectLookup(withSuccess("{\"status\":\"READY\",\"id\":\"" + ID + "\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityProviderFailedException.class);

        server.reset();
        expectLookup(withSuccess("{\"status\":\"FAILED\",\"id\":\"" + ID + "\",\"failure\":{\"reason\":\"x\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityProviderFailedException.class);
    }

    @Test
    void 업체_오류_응답과_연결_실패는_502_이고_로그에_본문_비밀키가_없다(CapturedOutput output) {
        expectLookup(withResourceNotFound().body("{\"type\":\"IDENTITY_VERIFICATION_NOT_FOUND\",\"message\":\"" + CI + "\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityProviderFailedException.class);

        server.reset();
        expectLookup(withStatus(HttpStatus.UNAUTHORIZED).body("{\"type\":\"UNAUTHORIZED\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityProviderFailedException.class);

        server.reset();
        expectLookup(withServerError());
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityProviderFailedException.class);

        server.reset();
        expectLookup(request -> {
            throw new java.io.IOException("connection reset");
        });
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityProviderFailedException.class);

        assertThat(output.getAll()).contains("IDENTITY_VERIFICATION_NOT_FOUND").doesNotContain(SECRET).doesNotContain(CI);
    }

    @Test
    void 모양이_틀린_응답은_502() {
        for (String body : new String[] {
                "not json",
                "null",
                verified("iv-other", CI, DI, "1990-05-14"),                  // 조회한 번호와 다른 결과
                verified(ID, null, null, "1990-05-14"),                      // CI·DI 둘 다 없음
                verified(ID, CI, DI, "1990/05/14"),                          // 생년월일 형식
                "{\"status\":\"VERIFIED\",\"id\":\"" + ID + "\"}"            // verifiedCustomer 없음
        }) {
            server.reset();
            expectLookup(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> verifier.verify(ID, null)).as(body).isInstanceOf(IdentityProviderFailedException.class);
        }
    }

    @Test
    void 설정_칸이_비면_업체를_부르지_않고_503() {
        settings.set(Optional.empty());
        assertThatThrownBy(() -> verifier.verify(ID, null)).isInstanceOf(IdentityNotConfiguredException.class);
        assertThatThrownBy(() -> verifier.startInfo()).isInstanceOf(IdentityNotConfiguredException.class);
        server.verify(); // 요청 없음
    }

    @Test
    void 시작_값은_상점_ID_와_채널_키만이고_구현은_PORTONE() {
        IdentityStartInfo info = verifier.startInfo();
        assertThat(info.storeId()).isEqualTo(STORE);
        assertThat(info.channelKey()).isEqualTo("channel-xyz");
        assertThat(info.toString()).doesNotContain(SECRET);
        assertThat(verifier.kind()).isEqualTo(IdentityProviderKind.PORTONE);
        assertThat(PortOneIdentityVerifier.BASE_URL).isEqualTo("https://api.portone.io");
    }
}
