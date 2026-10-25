package com.community.identity.provider;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.IdentityStartInfo;
import com.community.identity.dto.VerifiedPerson;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.exception.IdentityNotConfiguredException;
import com.community.identity.exception.IdentityProviderFailedException;
import com.community.portone.dto.PortOneIdentityCredentials;
import com.community.portone.service.PortOneSettingsService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 포트원 V2 통합 본인인증 — 화면이 포트원 SDK 로 인증을 마치면 서버가 그 인증 번호로 결과를 다시 조회해 믿는다(화면이 보낸 값은 믿지 않는다).
 *
 * <ul>
 *   <li>요청: {@code GET https://api.portone.io/identity-verifications/{identityVerificationId}?storeId=<상점 ID>} + {@code Authorization: PortOne <API 비밀키>}
 *       (포트원 V2 REST API 「본인인증 단건 조회」). 인증 번호는 서버가 먼저 발급한 값({@code identity_verification_attempts.id})이다.</li>
 *   <li>응답: {@code status} 가 {@code VERIFIED} 일 때만 받아들인다({@code READY} = 아직 끝나지 않음, {@code FAILED} = 실패). 결과의 {@code id} 가 조회한 인증 번호와 같아야 하고,
 *       {@code verifiedCustomer} 의 {@code ci}·{@code di}(둘 중 하나는 있어야 한다)·{@code birthDate}({@code YYYY-MM-DD})를 쓴다. 이름·전화번호·성별·통신사는 읽지 않는다.</li>
 *   <li>실패(4xx·5xx·연결 실패·모양이 틀림)는 모두 502 {@code IDENTITY_PROVIDER_FAILED}. 로그에는 HTTP 상태와 포트원 오류 {@code type} 만 — 응답 본문·CI·DI·생년월일·비밀키는 남기지 않는다.</li>
 *   <li>설정(상점 ID·API 비밀키·채널 키)은 요청마다 DB({@link PortOneSettingsService})에서 읽는다 — 관리자 화면에서 바꾸면 바로 반영된다. 비었으면 503 {@code IDENTITY_NOT_CONFIGURED}.</li>
 * </ul>
 */
public class PortOneIdentityVerifier implements IdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(PortOneIdentityVerifier.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 포트원 V2 API 주소 — 코드에 고정(운영자가 바꾸는 값이 아니다) */
    public static final String BASE_URL = "https://api.portone.io";

    private final Supplier<Optional<PortOneIdentityCredentials>> settings;
    private final RestClient http;

    /**
     * @param settings 포트원 본인인증 설정 값(요청마다 부른다 — 보통 {@link PortOneSettingsService#identityCredentials})
     * @param http     요청 도구(제한 시간·리다이렉트 안 따라감은 만드는 쪽이 정한다)
     */
    public PortOneIdentityVerifier(Supplier<Optional<PortOneIdentityCredentials>> settings, RestClient.Builder http) {
        this.settings = settings;
        this.http = http.baseUrl(BASE_URL).build();
    }

    @Override
    public IdentityProviderKind kind() {
        return IdentityProviderKind.PORTONE;
    }

    @Override
    public IdentityStartInfo startInfo() {
        PortOneIdentityCredentials credentials = credentials();
        return new IdentityStartInfo(credentials.storeId(), credentials.channelKey());
    }

    @Override
    public VerifiedPerson verify(String verificationId, FakeIdentityInput ignored) {
        PortOneIdentityCredentials credentials = credentials();
        String body;
        try {
            body = http.get()
                    .uri(uri -> uri.path("/identity-verifications/{id}").queryParam("storeId", credentials.storeId()).build(verificationId))
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + credentials.apiSecret())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn("포트원 본인인증 조회 실패 status={} type={}", response.getStatusCode().value(), errorType(text));
                            return null;
                        }
                        return text;
                    });
        } catch (RestClientException e) {
            log.warn("포트원 본인인증 조회 실패 — 연결 원인={}", e.getClass().getSimpleName());
            throw new IdentityProviderFailedException();
        }
        if (body == null) {
            throw new IdentityProviderFailedException();
        }
        return parse(verificationId, body);
    }

    /** 응답 본문 → 사람. 받아들일 수 없으면 502 */
    static VerifiedPerson parse(String verificationId, String body) {
        Map<?, ?> root;
        try {
            root = MAPPER.readValue(body, Map.class);
        } catch (JacksonException e) {
            log.warn("포트원 본인인증 응답을 읽지 못했다");
            throw new IdentityProviderFailedException();
        }
        if (root == null) {
            log.warn("포트원 본인인증 응답이 비었다");
            throw new IdentityProviderFailedException();
        }
        Object status = root.get("status");
        if (!"VERIFIED".equals(status)) {
            log.info("포트원 본인인증이 끝나지 않았다 status={}", status instanceof String s ? s : "?");
            throw new IdentityProviderFailedException();
        }
        if (!verificationId.equals(root.get("id"))) {
            log.warn("포트원 본인인증 응답의 인증 번호가 조회한 번호와 다르다");
            throw new IdentityProviderFailedException();
        }
        if (!(root.get("verifiedCustomer") instanceof Map<?, ?> customer)) {
            log.warn("포트원 본인인증 응답에 verifiedCustomer 가 없다");
            throw new IdentityProviderFailedException();
        }
        String ci = text(customer.get("ci"));
        String di = text(customer.get("di"));
        if (ci == null && di == null) {
            log.warn("포트원 본인인증 응답에 CI·DI 가 모두 없다 — 본인인증 PG 설정을 확인(다날·KCP)");
            throw new IdentityProviderFailedException();
        }
        LocalDate birthDate;
        try {
            String raw = text(customer.get("birthDate"));
            if (raw == null) {
                throw new DateTimeParseException("없음", "", 0);
            }
            birthDate = LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("포트원 본인인증 응답의 생년월일을 읽지 못했다");
            throw new IdentityProviderFailedException();
        }
        return new VerifiedPerson(ci, di, birthDate, verificationId);
    }

    private PortOneIdentityCredentials credentials() {
        return settings.get().orElseThrow(IdentityNotConfiguredException::new);
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    /** 포트원 오류 본문의 {@code type}(예: {@code IDENTITY_VERIFICATION_NOT_FOUND} · {@code UNAUTHORIZED}) — 로그용, 없으면 "?" */
    private static String errorType(String body) {
        try {
            Object type = MAPPER.readValue(body, Map.class).get("type");
            return type instanceof String s && s.matches("[A-Z_]{1,80}") ? s : "?";
        } catch (JacksonException | IllegalArgumentException e) {
            return "?";
        }
    }
}
