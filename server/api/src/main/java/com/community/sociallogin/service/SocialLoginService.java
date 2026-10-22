package com.community.sociallogin.service;

import com.community.auth.dto.AuthUser;
import com.community.auth.service.EmailVerificationService;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.config.SocialEndpoints;
import com.community.sociallogin.config.SocialLoginProperties;
import com.community.sociallogin.dto.AuthorizationFlow;
import com.community.sociallogin.dto.AuthorizationStart;
import com.community.sociallogin.dto.CallbackOutcome;
import com.community.sociallogin.dto.PendingSignup;
import com.community.sociallogin.dto.SocialFailure;
import com.community.sociallogin.dto.SocialMode;
import com.community.sociallogin.dto.SocialProfile;
import com.community.sociallogin.dto.request.SocialCompleteRequest;
import com.community.sociallogin.dto.response.SocialPendingResponse;
import com.community.sociallogin.exception.SocialProviderNotFoundException;
import com.community.sociallogin.service.SocialProviderAdapter.ClientCredentials;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인(OAuth 2.0 인가 코드 흐름) — 시작·콜백·가입 완료. docs/features/login-contract.md 「소셜 로그인」.
 *
 * <ul>
 *   <li><b>시작</b>: 쓸 수 있는 공급자인지(켜짐·키·암호화 키·공개 주소) 보고, 일회용 state(256비트)·PKCE 검증값·구글 nonce 를 만들어 공급자 인가 주소를 돌려준다.
 *       세션에는 state·nonce 의 <b>해시</b>와 검증값만 둔다(웹 계층이 둔다).</li>
 *   <li><b>콜백</b>: 세션에서 꺼낸 흐름(일회용 — 꺼내면서 지운다)으로 공급자·만료·목적(로그인/연결)·연결한 회원을 확인하고, 코드를 회원 정보로 바꾼 뒤
 *       {@link SocialAccountWriter} 가 결과를 정한다. 공급자 통신은 트랜잭션 밖이다.</li>
 *   <li><b>가입 완료</b>: 서버 세션의 가입 대기 정보 + 닉네임·약관 동의 3개로 회원을 만든다. 공급자가 확인하지 않은 이메일이면 가입 뒤 인증 메일을 보낸다.</li>
 * </ul>
 * 이 클래스는 트랜잭션을 열지 않는다(외부 통신이 DB 연결을 붙잡지 않게). 시크릿·코드·state·토큰을 로그·응답·예외에 남기지 않는다.
 */
@Service
public class SocialLoginService {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginService.class);

    /** 콜백으로 받는 인가 코드의 최대 길이(이보다 길면 공급자가 준 값이 아니다) */
    private static final int CODE_MAX = 2048;
    /** 콜백 경로 — 공급자 콘솔에 {@code {공개 주소}/api/auth/social/{id}/callback} 으로 등록한다 */
    public static final String CALLBACK_PATH = "/api/auth/social/%s/callback";

    private final SocialClientRegistry clients;
    private final SocialEndpoints endpoints;
    private final SocialAccountWriter writer;
    private final SiteSettingsService siteSettings;
    private final EmailVerificationService verification;
    private final SocialLoginProperties props;
    private final Messages messages;
    private final Map<SocialProvider, SocialProviderAdapter> adapters = new EnumMap<>(SocialProvider.class);

    public SocialLoginService(SocialClientRegistry clients, SocialEndpoints endpoints, SocialAccountWriter writer, SiteSettingsService siteSettings,
                              EmailVerificationService verification, SocialLoginProperties props, Messages messages, List<SocialProviderAdapter> adapters) {
        this.clients = clients;
        this.endpoints = endpoints;
        this.writer = writer;
        this.siteSettings = siteSettings;
        this.verification = verification;
        this.props = props;
        this.messages = messages;
        adapters.forEach(adapter -> this.adapters.put(adapter.provider(), adapter));
    }

    /** 경로의 공급자 id({@code kakao} …). 모르는 값이면 404 — 켜짐·키 확인은 {@link #start} 가 한다 */
    public SocialProvider provider(String providerId) {
        return SocialProvider.from(providerId).filter(adapters::containsKey).orElseThrow(SocialProviderNotFoundException::new);
    }

    /**
     * 흐름을 시작한다.
     *
     * @param mode       로그인 또는 연결
     * @param linkUserId 연결이면 로그인한 회원 ID(로그인이면 null)
     * @param rawNext    로그인 뒤 돌아갈 경로(로그인만). 같은 사이트 상대 경로가 아니면 400
     * @throws SocialProviderNotFoundException 모르는·꺼진·키 없는 공급자, 암호화 키·공개 주소가 없음(404 — 구분해 알리지 않는다)
     * @throws ValidationException             {@code next} 가 같은 사이트 상대 경로가 아님({@code fields.next})
     */
    public AuthorizationStart start(SocialProvider provider, SocialMode mode, Long linkUserId, String rawNext) {
        String clientId = clients.clientId(provider).orElseThrow(SocialProviderNotFoundException::new);
        String next = mode == SocialMode.LOGIN ? SocialReturnPaths.checkNext(rawNext) : null;
        String redirectUri = callbackUrl(provider).orElseThrow(() -> {
            log.warn("공개 주소가 없어 소셜 로그인을 시작하지 못했다 — 설정에서 공개 주소를 저장해 주세요 provider={}", provider);
            return new SocialProviderNotFoundException();
        });

        SocialProviderAdapter adapter = adapters.get(provider);
        String state = Digests.newToken();
        String verifier = adapter.usesPkce() ? Digests.newToken() : null;
        String nonce = adapter.usesNonce() ? Digests.newToken() : null;
        String url = adapter.authorizeUrl(endpoints.of(provider), clientId, redirectUri, state, verifier == null ? null : s256(verifier), nonce);

        AuthorizationFlow flow = new AuthorizationFlow(Digests.sha256Hex(state), provider, mode, linkUserId, verifier,
                nonce == null ? null : Digests.sha256Hex(nonce), next, Instant.now().plus(props.stateTtl()));
        log.info("소셜 흐름 시작 provider={} mode={} userId={}", provider, mode, linkUserId);
        return new AuthorizationStart(url, flow);
    }

    /**
     * 콜백을 처리한다. 결과는 늘 {@link CallbackOutcome} 이다(던지지 않는다 — 실패도 사용자 앱의 정해진 경로로 돌려보낸다).
     *
     * @param pathProvider  경로의 공급자(모르는 값이면 null)
     * @param flow          세션에서 꺼낸(그리고 지운) 흐름 — state 가 없거나 맞는 흐름이 없으면 null
     * @param code          인가 코드
     * @param error         공급자가 준 오류 코드(취소 등). 없으면 null
     * @param state         state 원문(네이버 토큰 교환이 요구한다)
     * @param currentUserId 지금 로그인한 회원(연결 흐름 확인용). 비로그인이면 null
     */
    public CallbackOutcome callback(SocialProvider pathProvider, AuthorizationFlow flow, String code, String error, String state, Long currentUserId) {
        Instant now = Instant.now();
        if (flow == null || flow.provider() != pathProvider) {
            log.info("소셜 콜백 거절 — state 가 없거나 맞지 않는다 provider={}", pathProvider);
            return CallbackOutcome.failed(null, SocialMode.LOGIN, SocialFailure.INVALID_STATE);
        }
        SocialProvider provider = flow.provider();
        SocialMode mode = flow.mode();
        if (flow.isExpired(now)) {
            log.info("소셜 콜백 거절 — 흐름이 만료됐다 provider={}", provider);
            return CallbackOutcome.failed(provider, mode, SocialFailure.EXPIRED);
        }
        if (error != null && !error.isEmpty()) {
            boolean cancelled = "access_denied".equals(error) || "consent_required".equals(error) || "login_required".equals(error);
            log.info("소셜 콜백 — 공급자가 오류를 돌려줬다 provider={} cancelled={}", provider, cancelled);
            return CallbackOutcome.failed(provider, mode, cancelled ? SocialFailure.CANCELLED : SocialFailure.PROVIDER_ERROR);
        }
        if (code == null || code.isBlank() || code.length() > CODE_MAX) {
            return CallbackOutcome.failed(provider, mode, SocialFailure.PROVIDER_ERROR);
        }
        if (mode == SocialMode.LINK && (currentUserId == null || !currentUserId.equals(flow.linkUserId()))) {
            log.info("소셜 연결 거절 — 시작한 회원과 지금 로그인한 회원이 다르다 provider={}", provider);
            return CallbackOutcome.failed(provider, mode, SocialFailure.SESSION_CHANGED);
        }

        Optional<ClientCredentials> credentials = clients.credentials(provider);
        Optional<String> redirectUri = callbackUrl(provider);
        if (credentials.isEmpty() || redirectUri.isEmpty()) {
            log.info("소셜 콜백 거절 — 그 사이 공급자를 쓸 수 없게 됐다 provider={}", provider);
            return CallbackOutcome.failed(provider, mode, SocialFailure.PROVIDER_ERROR);
        }
        SocialProfile profile;
        try {
            profile = adapters.get(provider).fetchProfile(endpoints.of(provider), credentials.get(), redirectUri.get(), code, state,
                    flow.codeVerifier(), flow.nonceHash());
        } catch (SocialProviderException e) {
            log.warn("소셜 공급자 교환 실패 provider={} 사유={}", provider, e.getMessage());
            return CallbackOutcome.failed(provider, mode, SocialFailure.PROVIDER_ERROR);
        }

        if (mode == SocialMode.LINK) {
            try {
                return writer.link(flow.linkUserId(), profile);
            } catch (SocialAccountWriter.SocialLinkRaceException e) {
                return CallbackOutcome.linkTaken(provider);
            }
        }
        return writer.login(profile, flow.next(), now.plus(props.pendingTtl()));
    }

    /** 가입 완료 화면이 보일 정보 */
    public SocialPendingResponse pendingView(PendingSignup pending) {
        return new SocialPendingResponse(pending.provider().id(), messages.get(pending.provider().displayNameKey()), pending.nicknameCandidate(),
                maskEmail(pending.email()), pending.email() != null && pending.emailVerified(), pending.next());
    }

    /**
     * 가입을 마친다 — 입력 검사(닉네임 → 약관 → 개인정보 → 만 14세 순, 한꺼번에) → 회원 만들기 → (확인되지 않은 이메일이면) 인증 메일.
     *
     * @throws ValidationException 입력 위반({@code fields.nickname}·{@code agreeTerms}·{@code agreePrivacy}·{@code ageConfirmed})
     * @see SocialAccountWriter#createMember
     */
    public AuthUser complete(PendingSignup pending, SocialCompleteRequest body) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String nickname = body == null ? null : body.nickname();
        UserPolicy.nicknameViolation(nickname).ifPresent(message -> errors.put("nickname", message));
        if (body == null || !Boolean.TRUE.equals(body.agreeTerms())) {
            errors.put("agreeTerms", new MessageRef("user.agreeTerms.required"));
        }
        if (body == null || !Boolean.TRUE.equals(body.agreePrivacy())) {
            errors.put("agreePrivacy", new MessageRef("user.agreePrivacy.required"));
        }
        if (body == null || !Boolean.TRUE.equals(body.ageConfirmed())) {
            errors.put("ageConfirmed", new MessageRef("user.ageConfirmed.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        AuthUser member = writer.createMember(pending, nickname);
        if (pending.email() != null && !pending.emailVerified()) {
            try {
                verification.resend(member.id()); // 가입은 이미 커밋됐다 — 메일은 덤이다(못 보내도 화면의 다시 보내기로 이어진다)
            } catch (RuntimeException e) {
                log.warn("소셜 가입 뒤 인증 메일을 시작하지 못했다 userId={} 원인={}", member.id(), e.getClass().getSimpleName());
            }
        }
        return member;
    }

    /** {@code {공개 주소}/api/auth/social/{id}/callback} — 공개 주소는 사이트 설정의 값이다. 없으면 비어 있다 */
    Optional<String> callbackUrl(SocialProvider provider) {
        return siteSettings.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank())
                .map(url -> url + CALLBACK_PATH.formatted(provider.id()));
    }

    /** {@code a***@example.com} — 앞 한 글자와 도메인만 */
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return null;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** PKCE S256 — base64url(sha256(verifier)), 채움 없음 */
    static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
