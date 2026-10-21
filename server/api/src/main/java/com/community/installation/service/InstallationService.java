package com.community.installation.service;

import com.community.installation.dto.BoardPreset;
import com.community.installation.dto.InstallCommand;
import com.community.installation.dto.request.CompleteInstallationRequest;
import com.community.installation.dto.request.InstallRootInput;
import com.community.installation.dto.request.InstallSocialInput;
import com.community.installation.dto.request.InstallSocialProviderInput;
import com.community.installation.dto.response.BoardPresetsResponse;
import com.community.installation.dto.response.InstallAuthorizedResponse;
import com.community.installation.dto.response.InstallChecksResponse;
import com.community.installation.dto.response.InstallCompletedResponse;
import com.community.installation.dto.response.InstallStatusResponse;
import com.community.installation.exception.InstallationAlreadyInstalledException;
import com.community.installation.exception.InstallationAuthRequiredException;
import com.community.installation.exception.InstallationTokenInvalidException;
import com.community.mail.dto.MailCheckResult;
import com.community.mail.dto.MailInput;
import com.community.mail.dto.MailValues;
import com.community.mail.service.MailConnectionChecker;
import com.community.mail.service.MailSettingsService;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.social.dto.SocialKeyValues;
import com.community.social.dto.SocialSettingsUpdate;
import com.community.social.entity.SocialProvider;
import com.community.social.service.SocialSettingsService;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 설치 마법사 API 의 업무 규칙. <b>로그인 없이 쓰는 구간</b>이라 보안이 핵심이다.
 *
 * <ul>
 *   <li>{@code status} 만 설치가 끝난 뒤에도 열려 있다. 나머지는 <b>설치가 끝나면 {@code 409 ALREADY_INSTALLED}</b> 로 닫힌다.</li>
 *   <li>{@code verify-token} 말고는 <b>토큰 인증을 마친 세션</b>이어야 한다({@code 401 INSTALL_AUTH_REQUIRED}). 세션은 웹 계층(컨트롤러의
 *       {@code InstallSession})이 다루고, 이 서비스는 "인증됐는가"와 "요청한 IP"만 받는다.</li>
 *   <li>검사 순서: 설치 끝남(409) → 토큰 인증(401) → (complete) 암호화 키(503) → 입력(400) → 실행. 암호화할 값이 있는데 키가 없으면 입력이 틀려도 먼저
 *       {@code 503} 이다 — 아무것도 저장하지 않는다. {@code verify-token} 은 설치 끝남(409) → 잠금(429) → 입력(400) → 비교(401).</li>
 *   <li>입력 오류는 <b>한꺼번에</b> 모아 {@code fields} 로 준다(키는 {@code root.email} · {@code site.publicUrl} · {@code social.providers[0].clientId} 같은 점·대괄호 경로).
 *       경로 키와 검사 순서가 계약이라 Bean Validation({@code @Valid})이 아니라 여기서 직접 검사한다.</li>
 *   <li>실행은 한 트랜잭션이다({@link InstallationInstaller}). 실패하면 아무것도 남지 않고 <b>토큰 인증 세션은 그대로</b>라 같은 화면에서 다시 시도할 수 있다.
 *       응답·로그 어디에도 비밀번호·시크릿·토큰을 싣지 않는다.</li>
 * </ul>
 * 이 클래스에는 {@code @Transactional} 이 없다 — 메일 서버 연결 시험처럼 응답 없는 서버를 기다리는 일이 DB 연결을 붙잡지 않게 하려는 것이다.
 * DB 를 쓰는 일은 각 서비스가 자기 트랜잭션을 연다.
 */
@Service
public class InstallationService {

    private static final Logger log = LoggerFactory.getLogger(InstallationService.class);

    private static final int MAX_SOCIAL_PROVIDERS = 3;

    /**
     * 옛 프리셋 아이디 → 지금 아이디. 예시 구성의 이름을 BOTBI 에서 ROBOYAM 으로 바꿨는데, 이미 {@code boardPreset: "BOTBI"} 를 보내는
     * 스크립트가 있을 수 있어 요청은 옛 값도 받는다. 목록({@code GET …/board-presets})에는 지금 아이디만 나온다.
     */
    private static final Map<String, String> LEGACY_PRESET_IDS = Map.of("BOTBI", "ROBOYAM");

    private final InstallationState state;
    private final InstallTokenService tokens;
    private final InstallTokenGuard tokenGuard;
    private final InstallationChecks checks;
    private final InstallationInstaller installer;
    private final BoardPresetCatalog boardPresets;
    private final SiteSettingsService siteSettings;
    private final MailSettingsService mailSettings;
    private final MailConnectionChecker mailChecker;
    private final SocialSettingsService socialSettings;
    private final SecretEncryptor encryptor;
    private final Messages messages;

    public InstallationService(InstallationState state, InstallTokenService tokens, InstallTokenGuard tokenGuard,
                               InstallationChecks checks, InstallationInstaller installer, BoardPresetCatalog boardPresets,
                               SiteSettingsService siteSettings, MailSettingsService mailSettings, MailConnectionChecker mailChecker,
                               SocialSettingsService socialSettings, SecretEncryptor encryptor, Messages messages) {
        this.state = state;
        this.tokens = tokens;
        this.tokenGuard = tokenGuard;
        this.checks = checks;
        this.installer = installer;
        this.boardPresets = boardPresets;
        this.siteSettings = siteSettings;
        this.mailSettings = mailSettings;
        this.mailChecker = mailChecker;
        this.socialSettings = socialSettings;
        this.encryptor = encryptor;
        this.messages = messages;
    }

    /** 설치 상태. 이 밖의 정보는 담지 않는다. 설치가 끝났으면 인증 표시도 거짓이다 */
    public InstallStatusResponse status(boolean sessionAuthorized) {
        boolean installed = state.isInstalled();
        return new InstallStatusResponse(installed, !installed && sessionAuthorized);
    }

    /**
     * 설치 토큰을 확인한다. 맞으면 정상적으로 돌아온다 — 호출하는 쪽(컨트롤러)이 이 세션을 "설치 인증됨" 으로 만든다.
     *
     * @param clientIp 요청자가 꾸밀 수 없는 값으로 고른 IP(IP 별 잠금의 키)
     */
    public InstallAuthorizedResponse verifyToken(String clientIp, String rawToken) {
        requireNotInstalled();
        tokenGuard.checkAllowed(clientIp); // 잠겼거나 서버 전체 상한을 넘으면 429
        String token = rawToken == null ? "" : rawToken.strip();
        if (token.isEmpty()) {
            throw ValidationException.of("token", new MessageRef("install.token.required")); // 실패로 세지 않는다
        }
        if (!tokens.matches(token)) {
            tokenGuard.recordFailure(clientIp);
            throw new InstallationTokenInvalidException(); // 입력한 토큰은 남기지 않는다
        }
        tokenGuard.recordSuccess(clientIp);
        log.info("설치 토큰 인증 성공 ip={}", clientIp);
        return new InstallAuthorizedResponse(true);
    }

    /** 설치가 돌아갈 수 있는지 점검한 결과 */
    public InstallChecksResponse checks(boolean sessionAuthorized) {
        requireOpen(sessionAuthorized);
        return checks.run();
    }

    /** 고를 수 있는 기본 게시판 구성(프리셋)과 기본값 — 이름·설명·게시판 이름·말머리 이름만 */
    public BoardPresetsResponse boardPresets(boolean sessionAuthorized) {
        requireOpen(sessionAuthorized);
        return boardPresets.toResponse(messages.locale());
    }

    /** 메일 서버에 접속·인증까지만 해 본다(메일은 보내지 않는다). 입력이 틀리면 400 */
    public MailCheckResult mailCheck(boolean sessionAuthorized, MailInput input) {
        requireOpen(sessionAuthorized);
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        MailValues values = mailSettings.normalize(errors, "", input, false);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return mailChecker.check(values.host(), values.port(), values.username(), values.password(), values.security());
    }

    /** 설치를 실행한다. 성공하면 정상적으로 돌아온다 — 호출하는 쪽(컨트롤러)이 설치 인증 세션을 없앤다 */
    public InstallCompletedResponse complete(boolean sessionAuthorized, CompleteInstallationRequest body) {
        requireOpen(sessionAuthorized);
        if (needsCryptoKey(body)) {
            encryptor.requireConfigured(); // 입력이 틀려도 먼저 503
        }
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        InstallCommand command = validate(body, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        installer.install(command); // 한 트랜잭션. 던지면 아무것도 남지 않는다
        return new InstallCompletedResponse(true);
    }

    // ── 검사 ──

    /** 암호화해서 저장할 값(메일 비밀번호·소셜 시크릿)이 들어 있는가 */
    private static boolean needsCryptoKey(CompleteInstallationRequest body) {
        if (body == null) {
            return false;
        }
        boolean mailPassword = body.mail() != null && body.mail().password() != null && !body.mail().password().isBlank();
        boolean social = body.social() != null && body.social().providers() != null && !body.social().providers().isEmpty();
        return mailPassword || social;
    }

    /** 요청 전체를 검사해 위반을 {@code errors} 에 한꺼번에 모은다. 위반이 없을 때만 돌려준 값을 쓴다 */
    private InstallCommand validate(CompleteInstallationRequest body, Map<String, MessageRef> errors) {
        InstallRootInput root = body == null ? null : body.root();

        String email = UserPolicy.normalizeEmail(root == null ? null : root.email());
        UserPolicy.emailViolation(email).ifPresent(violation -> errors.put("root.email", violation));
        String nickname = root == null || root.nickname() == null ? null : root.nickname().trim();
        UserPolicy.nicknameViolation(nickname).ifPresent(violation -> errors.put("root.nickname", violation));
        String password = root == null ? null : root.password();
        UserPolicy.passwordViolation(password).ifPresent(violation -> errors.put("root.password", violation));

        SiteValues site = siteSettings.normalize(errors, "site.", body == null ? null : body.site());

        MailValues mail = null;
        if (body != null && body.mail() != null) {
            mail = mailSettings.normalize(errors, "mail.", body.mail(), true);
        }

        List<InstallCommand.Social> social = validateSocial(body == null ? null : body.social(), errors);
        BoardPreset preset = resolvePreset(body == null ? null : body.boardPreset(), errors);

        // 슬로건 — 입력한 값이 이기고, 비어 있으면 고른 프리셋이 정한 슬로건(데이터)을 넣는다. 둘 다 없으면 문구 없음
        if (site.tagline() == null && preset.siteTagline() != null) {
            site = new SiteValues(site.name(), site.publicUrl(), preset.siteTagline());
        }
        return new InstallCommand(email, nickname, password, site, mail, social, preset.id());
    }

    /** 요청이 고른 기본 게시판 구성. 생략하면(null·빈 글자) 기본 프리셋, 모르는 아이디면 {@code boardPreset} 오류 */
    private BoardPreset resolvePreset(String id, Map<String, MessageRef> errors) {
        if (id == null || id.isBlank()) {
            return boardPresets.defaultPreset();
        }
        String stripped = id.strip();
        return boardPresets.find(LEGACY_PRESET_IDS.getOrDefault(stripped, stripped)).orElseGet(() -> {
            errors.put("boardPreset", new MessageRef("install.boardPreset.invalid"));
            return boardPresets.defaultPreset();
        });
    }

    private List<InstallCommand.Social> validateSocial(InstallSocialInput input, Map<String, MessageRef> errors) {
        List<InstallCommand.Social> entries = new ArrayList<>();
        List<InstallSocialProviderInput> providers = input == null || input.providers() == null ? List.of() : input.providers();
        if (providers.size() > MAX_SOCIAL_PROVIDERS) {
            errors.put("social.providers", new MessageRef("install.social.providers.max", MAX_SOCIAL_PROVIDERS));
        }
        Set<SocialProvider> seen = EnumSet.noneOf(SocialProvider.class);
        for (int i = 0; i < providers.size() && i < MAX_SOCIAL_PROVIDERS; i++) {
            String prefix = "social.providers[" + i + "].";
            InstallSocialProviderInput item = providers.get(i);
            if (item == null) {
                errors.put(prefix + "provider", new MessageRef("install.social.provider.required"));
                continue;
            }
            Optional<SocialProvider> provider = SocialProvider.from(item.provider());
            if (provider.isEmpty()) {
                errors.put(prefix + "provider", new MessageRef("install.social.provider.invalid"));
            } else if (!seen.add(provider.get())) {
                errors.put(prefix + "provider", new MessageRef("install.social.provider.duplicate"));
            }
            SocialKeyValues key = socialSettings.normalize(errors, prefix,
                    new SocialSettingsUpdate(item.clientId(), item.clientSecret(), item.enabled()));
            if (key.clientSecret() == null && !errors.containsKey(prefix + "clientSecret")) {
                errors.put(prefix + "clientSecret", new MessageRef("social.clientSecret.required")); // 설치에서는 시크릿이 필수다
            }
            provider.ifPresent(p -> entries.add(new InstallCommand.Social(p, key)));
        }
        return entries;
    }

    // ── 가드 ──

    /** 설치 끝남(409) → 토큰 인증(401) */
    private void requireOpen(boolean sessionAuthorized) {
        requireNotInstalled();
        if (!sessionAuthorized) {
            throw new InstallationAuthRequiredException();
        }
    }

    private void requireNotInstalled() {
        if (state.isInstalled()) {
            throw new InstallationAlreadyInstalledException();
        }
    }
}
