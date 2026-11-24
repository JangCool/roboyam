package com.community.auth.service;

import com.community.auth.entity.AuthTokenEntity;
import com.community.auth.entity.AuthTokenPurpose;
import com.community.auth.repository.master.AuthTokenRepository;
import com.community.auth.repository.master.AuthTokenRepositoryQDSL;
import com.community.notificationtemplate.catalog.MessageCondition;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 대기열로 나가는 <b>계정 링크 메일</b>의 링크를 <b>보낼 때</b> 만든다 — 운영자 회원 계정 새로 만들기(operator-member-account-contract 「새 계정 만들기」)가 넣은
 * 이메일 인증({@link RequiredMail#MEMBER_EMAIL_VERIFY})·비밀번호 정하기({@link RequiredMail#MEMBER_PASSWORD_SETUP}) 메일. 대기열 발송({@code RequiredMailOutboxSender})이 부른다.
 *
 * <ul>
 *   <li>링크 원문은 대기열({@code mail_outbox.variables})에 넣지 않는다 — 보내기 직전에 이 클래스가 새 토큰을 만들고 <b>해시만 저장</b>한다(공개 가입·비밀번호 찾기와 같다).
 *       재시도마다 새 링크를 만들고 앞의 링크는 무효다(가장 최근 것만 쓸 수 있다).</li>
 *   <li>이메일 인증: {@code {공개 주소}/verify-email?token=}, 유효 {@code irobit.auth.verify-token-hours}(기본 24 — 공개 가입과 같은 설정 키). 이미 인증했으면 보내지 않는다.</li>
 *   <li>비밀번호 정하기: {@code {공개 주소}/password/reset?token=}(비밀번호 재설정 화면을 그대로 쓴다 — 용도 {@link AuthTokenPurpose#SETUP_PASSWORD}), 유효
 *       {@code irobit.account.setup-token-hours}(기본 72). 정하면 이메일 인증도 끝난다(api {@code PasswordResetTokenService}).</li>
 *   <li>공개 주소가 아직 없으면 링크를 만들지 않고 재시도로 미룬다(설정을 고치면 다음 시도에 나간다).</li>
 * </ul>
 * 이메일 주소·토큰을 로그에 남기지 않는다.
 */
@Component
public class AccountLinkMails {

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final AuthTokenRepositoryQDSL tokenQueries;
    private final SiteSettingsService siteSettings;
    private final SnowflakeIdGenerator ids;
    private final int verifyHours;
    private final int setupHours;

    public AccountLinkMails(UserRepository users, AuthTokenRepository tokens, AuthTokenRepositoryQDSL tokenQueries, SiteSettingsService siteSettings,
                            SnowflakeIdGenerator ids, @Value("${irobit.auth.verify-token-hours:24}") int verifyHours,
                            @Value("${irobit.account.setup-token-hours:72}") int setupHours) {
        this.users = users;
        this.tokens = tokens;
        this.tokenQueries = tokenQueries;
        this.siteSettings = siteSettings;
        this.ids = ids;
        this.verifyHours = verifyHours;
        this.setupHours = setupHours;
    }

    /** 이 종류는 보낼 때 링크를 만드는 계정 링크 메일인가 — 그렇다면 이메일 인증 전 주소로도 간다(그 주소를 인증하려는 메일이다) */
    public static boolean handles(RequiredMail kind) {
        return kind == RequiredMail.MEMBER_EMAIL_VERIFY || kind == RequiredMail.MEMBER_PASSWORD_SETUP;
    }

    /**
     * 새 링크를 만들고 문구 변수를 돌려준다(짧은 트랜잭션 — 회원 행을 잠근다, 인증·재설정 링크와 같은 잠금 순서).
     *
     * @return 보낼 변수·조건, 또는 보내지 않을 까닭·미룰 까닭({@link Prepared#stopReason()})
     */
    @Transactional
    public Prepared prepare(RequiredMail kind, long userId) {
        if (!handles(kind)) {
            return Prepared.cancelled("UNKNOWN_KIND");
        }
        String baseUrl = siteSettings.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank()).orElse(null);
        if (baseUrl == null) {
            return Prepared.retrying("NO_PUBLIC_URL");
        }
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone()).orElse(null);
        if (user == null || user.getEmail() == null) {
            return Prepared.cancelled("NO_RECIPIENT");
        }
        Instant now = Instant.now();
        String raw = Digests.newToken();
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, String> conditions = new LinkedHashMap<>();
        if (kind == RequiredMail.MEMBER_EMAIL_VERIFY) {
            if (user.isEmailVerified()) {
                return Prepared.cancelled("ALREADY_VERIFIED");
            }
            issue(userId, AuthTokenPurpose.VERIFY_EMAIL, raw, now, verifyHours);
            variables.put("verifyLink", baseUrl + "/verify-email?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8));
            variables.put("validHours", verifyHours);
            conditions.put(MessageCondition.SIGNUP_METHOD, user.hasPassword() ? MessageCondition.EMAIL : MessageCondition.SOCIAL);
        } else {
            issue(userId, AuthTokenPurpose.SETUP_PASSWORD, raw, now, setupHours);
            variables.put("setupLink", baseUrl + "/password/reset?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8));
            variables.put("validHours", setupHours);
        }
        return new Prepared(variables, conditions, null, false);
    }

    private void issue(long userId, AuthTokenPurpose purpose, String raw, Instant now, int hours) {
        tokenQueries.consumeAll(userId, purpose, now);
        tokens.persist(AuthTokenEntity.create(ids.nextId(), userId, purpose, Digests.sha256Hex(raw), now.plus(Duration.ofHours(hours)), now));
    }

    /**
     * 링크 메일 준비 결과 — {@code stopReason} 이 없으면 보낸다.
     *
     * @param variables  문구 변수(링크·유효 시간) — 토큰 원문이 들어 있으므로 로그에 남기지 않는다({@link #toString()} 도 감춘다)
     * @param conditions 특수 상황 규칙의 조건 값
     * @param stopReason 보내지 않거나 미루는 까닭(짧은 코드)
     * @param retry      true 면 미룬다(재시도), false 면 보내지 않는다
     */
    public record Prepared(Map<String, Object> variables, Map<String, String> conditions, String stopReason, boolean retry) {

        static Prepared cancelled(String reason) {
            return new Prepared(Map.of(), Map.of(), reason, false);
        }

        static Prepared retrying(String reason) {
            return new Prepared(Map.of(), Map.of(), reason, true);
        }

        @Override
        public String toString() {
            return "Prepared[variables=(감춤), stopReason=" + stopReason + ", retry=" + retry + "]";
        }
    }
}
