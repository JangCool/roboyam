package com.community.social.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.social.dto.SocialKeyValues;
import com.community.social.dto.SocialSettingChange;
import com.community.social.dto.SocialSettingsUpdate;
import com.community.social.dto.request.SocialProviderSaveRequest;
import com.community.social.dto.response.SocialProviderSettingView;
import com.community.social.dto.response.SocialProviderSettingsView;
import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingEntity;
import com.community.social.entity.SocialProviderSettingHistoryEntity;
import com.community.social.exception.SocialProviderUnknownException;
import com.community.social.repository.master.SocialProviderSettingRepository;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 소셜 로그인 공급자 설정(카카오·네이버·구글) — 조회·등록/수정·삭제 (docs/features/login-contract.md 「관리자 소셜 로그인 설정」).
 *
 * <ul>
 *   <li><b>시크릿은 어떤 응답·로그·오류·감사·이력에도 싣지 않는다.</b> 입력됨 여부({@code secretSet})만 보이고, 감사·이력에는 「바뀜/지움」 표시만 남는다.</li>
 *   <li>저장 검사 순서: 공급자 이름(404) → 암호화 키(503 {@code CRYPTO_KEY_MISSING}) → 입력(400, {@code fields.clientId} → {@code clientSecret} → {@code enabled} 한꺼번에).</li>
 *   <li>시크릿을 생략하거나 비우면 저장된 값을 유지한다. 저장된 것이 없거나 지금 키로 풀 수 없으면 필수다. 같은 시크릿을 다시 넣으면 바꾸지 않는다.</li>
 *   <li>사이트 공개 주소({@code site_settings.public_url})가 없으면 켜기를 거절한다 — 콜백 주소를 만들 수 없어 공개 서버의 시작이 404 가 된다.</li>
 *   <li>바뀐 칸이 없으면 쓰지 않는다(시각·감사·이력 그대로). 공개 서버는 캐시가 없어 저장 직후 요청부터 새 값을 쓴다.</li>
 * </ul>
 */
@Service
public class SocialProviderAdminService {

    private static final Logger log = LoggerFactory.getLogger(SocialProviderAdminService.class);

    /** 화면 순서 — 공개 로그인 버튼({@code SocialProviderService})과 같다 */
    static final List<SocialProvider> ORDER = List.of(SocialProvider.KAKAO, SocialProvider.NAVER, SocialProvider.GOOGLE);

    private static final String TARGET_TYPE = "SOCIAL_PROVIDER";
    private static final String SOURCE_SETTINGS = "SETTINGS";
    private static final String SOURCE_NONE = "NONE";

    private final SocialProviderSettingRepository settings;
    private final SocialSettingsService policy;
    private final SocialSettingsHistory history;
    private final SecretEncryptor encryptor;
    private final SiteSettingsService site;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final Messages messages;

    public SocialProviderAdminService(SocialProviderSettingRepository settings, SocialSettingsService policy, SocialSettingsHistory history,
                                      SecretEncryptor encryptor, SiteSettingsService site, OperatorRepository operators, AuditLogService audit,
                                      Messages messages) {
        this.settings = settings;
        this.policy = policy;
        this.history = history;
        this.encryptor = encryptor;
        this.site = site;
        this.operators = operators;
        this.audit = audit;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public SocialProviderSettingsView list() {
        String publicUrl = publicUrl().orElse(null);
        Map<SocialProvider, SocialProviderSettingEntity> rows = new EnumMap<>(SocialProvider.class);
        settings.findAll().forEach(row -> rows.put(row.getProvider(), row));
        List<SocialProviderSettingView> views = ORDER.stream().map(provider -> view(provider, rows.get(provider), publicUrl)).toList();
        return new SocialProviderSettingsView(encryptor.isConfigured(), publicUrl != null, views);
    }

    @Transactional
    public SocialProviderSettingView save(AdminActor actor, String providerName, SocialProviderSaveRequest request) {
        SocialProvider provider = SocialProvider.from(providerName).orElseThrow(SocialProviderUnknownException::new);
        encryptor.requireConfigured();

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        SocialKeyValues key = policy.normalize(errors, "", request == null ? null
                : new SocialSettingsUpdate(request.clientId(), request.clientSecret(), request.enabled()));
        SocialProviderSettingEntity row = settings.findWithLockByProvider(provider).orElse(null);
        Optional<String> stored = row == null ? Optional.empty() : readSecret(row);
        if (key.clientSecret() == null && !errors.containsKey("clientSecret") && stored.isEmpty()) {
            errors.put("clientSecret", new MessageRef(row == null ? "socialSetting.clientSecret.required" : "socialSetting.clientSecret.unreadable"));
        }
        String publicUrl = publicUrl().orElse(null);
        if (key.enabled() && publicUrl == null) {
            errors.put("enabled", new MessageRef("socialSetting.enabled.publicUrlMissing"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Instant now = Instant.now();
        boolean secretChanged = key.clientSecret() != null && !stored.map(key.clientSecret()::equals).orElse(false);
        if (row == null) {
            row = SocialProviderSettingEntity.create(provider, key.clientId(), encryptor.encrypt(key.clientSecret()), key.enabled(), actor.operatorId(), now);
            settings.persist(row);
            List<SocialSettingChange> changes = List.of(
                    new SocialSettingChange(SocialSettingsHistory.FIELD_CLIENT_ID, null, SocialSettingsHistory.maskClientId(key.clientId())),
                    new SocialSettingChange(SocialSettingsHistory.FIELD_CLIENT_SECRET, null, SocialSettingsHistory.SECRET_SET),
                    new SocialSettingChange(SocialSettingsHistory.FIELD_ENABLED, null, String.valueOf(key.enabled())));
            history.record(provider, SocialProviderSettingHistoryEntity.ACTION_CREATE, SocialProviderSettingHistoryEntity.SOURCE_ADMIN, actor.operatorId(), now, changes);
            recordAudit(actor, provider, changes, null, key.enabled());
            log.info("소셜 로그인 설정을 등록했다 provider={} operatorId={}", provider, actor.operatorId());
            return view(provider, row, publicUrl);
        }

        List<SocialSettingChange> changes = new ArrayList<>();
        if (!Objects.equals(row.getClientId(), key.clientId())) {
            changes.add(new SocialSettingChange(SocialSettingsHistory.FIELD_CLIENT_ID, SocialSettingsHistory.maskClientId(row.getClientId()),
                    SocialSettingsHistory.maskClientId(key.clientId())));
        }
        if (secretChanged) {
            changes.add(new SocialSettingChange(SocialSettingsHistory.FIELD_CLIENT_SECRET, SocialSettingsHistory.SECRET_SET, SocialSettingsHistory.SECRET_CHANGED));
        }
        boolean wasEnabled = row.isEnabled();
        if (wasEnabled != key.enabled()) {
            changes.add(new SocialSettingChange(SocialSettingsHistory.FIELD_ENABLED, String.valueOf(wasEnabled), String.valueOf(key.enabled())));
        }
        if (changes.isEmpty()) {
            return view(provider, row, publicUrl);
        }
        row.change(key.clientId(), secretChanged ? encryptor.encrypt(key.clientSecret()) : null, key.enabled(), actor.operatorId(), now);
        history.record(provider, SocialProviderSettingHistoryEntity.ACTION_UPDATE, SocialProviderSettingHistoryEntity.SOURCE_ADMIN, actor.operatorId(), now, changes);
        recordAudit(actor, provider, changes, wasEnabled, key.enabled());
        log.info("소셜 로그인 설정을 바꿨다 provider={} 칸={} operatorId={}", provider, changes.stream().map(SocialSettingChange::field).toList(), actor.operatorId());
        return view(provider, row, publicUrl);
    }

    /** 설정을 지운다 — 그 공급자는 꺼지고 미등록이 된다. 없는 설정을 지워도 그대로 끝난다(멱등, 감사·이력 없음) */
    @Transactional
    public void delete(AdminActor actor, String providerName) {
        SocialProvider provider = SocialProvider.from(providerName).orElseThrow(SocialProviderUnknownException::new);
        Optional<SocialProviderSettingEntity> found = settings.findWithLockByProvider(provider);
        if (found.isEmpty()) {
            return;
        }
        SocialProviderSettingEntity row = found.get();
        Instant now = Instant.now();
        List<SocialSettingChange> changes = List.of(
                new SocialSettingChange(SocialSettingsHistory.FIELD_CLIENT_ID, SocialSettingsHistory.maskClientId(row.getClientId()), null),
                new SocialSettingChange(SocialSettingsHistory.FIELD_CLIENT_SECRET, SocialSettingsHistory.SECRET_SET, SocialSettingsHistory.SECRET_CLEARED),
                new SocialSettingChange(SocialSettingsHistory.FIELD_ENABLED, String.valueOf(row.isEnabled()), null));
        boolean wasEnabled = row.isEnabled();
        settings.delete(row);
        history.record(provider, SocialProviderSettingHistoryEntity.ACTION_DELETE, SocialProviderSettingHistoryEntity.SOURCE_ADMIN, actor.operatorId(), now, changes);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SOCIAL_PROVIDER_DELETE, AuditResult.SUCCESS).target(TARGET_TYPE, provider.name())
                .with("before", Map.of(SocialSettingsHistory.FIELD_ENABLED, wasEnabled)));
        log.info("소셜 로그인 설정을 지웠다 provider={} operatorId={}", provider, actor.operatorId());
    }

    /**
     * 감사 — 켜짐만 before/after 값, 클라이언트 ID·시크릿은 칸 이름과 「바뀜」 표시만(값·가린 값도 싣지 않는다. 가린 값은 이력에만 있다).
     */
    private void recordAudit(AdminActor actor, SocialProvider provider, List<SocialSettingChange> changes, Boolean wasEnabled, boolean enabled) {
        List<String> changed = changes.stream().map(SocialSettingChange::field).toList();
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SOCIAL_PROVIDER_UPDATE, AuditResult.SUCCESS).target(TARGET_TYPE, provider.name())
                .with("changed", changed);
        if (changed.contains(SocialSettingsHistory.FIELD_ENABLED)) {
            Map<String, Object> before = new LinkedHashMap<>();
            if (wasEnabled != null) {
                before.put(SocialSettingsHistory.FIELD_ENABLED, wasEnabled);
            }
            event = event.with("before", before).with("after", Map.of(SocialSettingsHistory.FIELD_ENABLED, enabled));
        }
        // 만들기(wasEnabled == null)는 SET, 고치기는 CHANGED — 값은 싣지 않는다
        String mark = wasEnabled == null ? SocialSettingsHistory.SECRET_SET : SocialSettingsHistory.SECRET_CHANGED;
        if (changed.contains(SocialSettingsHistory.FIELD_CLIENT_ID)) {
            event = event.with(SocialSettingsHistory.FIELD_CLIENT_ID, mark);
        }
        if (changed.contains(SocialSettingsHistory.FIELD_CLIENT_SECRET)) {
            event = event.with(SocialSettingsHistory.FIELD_CLIENT_SECRET, mark);
        }
        audit.record(event);
    }

    private SocialProviderSettingView view(SocialProvider provider, SocialProviderSettingEntity row, String publicUrl) {
        String label = messages.get(provider.displayNameKey());
        String callbackUrl = publicUrl == null ? null : publicUrl + provider.callbackPath();
        if (row == null) {
            return new SocialProviderSettingView(provider.name(), provider.id(), label, callbackUrl, SOURCE_NONE, false, false, null, false, false, null, null);
        }
        boolean secretSet = encryptor.isConfigured() ? readSecret(row).isPresent() : row.hasKeys();
        boolean configured = row.getClientId() != null && !row.getClientId().isBlank() && secretSet;
        // 공개 GET /api/auth/providers 와 같은 기준(켜짐 + 키 있음 + 암호화 키 있음)
        boolean active = encryptor.isConfigured() && row.isEnabled() && row.hasKeys();
        String updatedBy = row.getUpdatedBy() == null ? null : operators.findById(row.getUpdatedBy()).map(o -> o.getOperatorName()).orElse(null);
        return new SocialProviderSettingView(provider.name(), provider.id(), label, callbackUrl, SOURCE_SETTINGS, row.isEnabled(), active, row.getClientId(),
                secretSet, configured, row.getUpdatedAt(), updatedBy);
    }

    /** 저장된 시크릿을 지금 키로 풀어 본다. 풀 수 없거나(키가 바뀜·암호문이 깨짐) 키가 없으면 비어 있다. 값은 비교에만 쓰고 밖으로 내지 않는다 */
    private Optional<String> readSecret(SocialProviderSettingEntity row) {
        if (!encryptor.isConfigured() || !row.hasKeys()) {
            return Optional.empty();
        }
        try {
            return Optional.of(encryptor.decrypt(row.getClientSecretEnc()));
        } catch (RuntimeException e) {
            log.warn("저장된 소셜 로그인 시크릿을 풀 수 없다 provider={} 원인={} — 설정 화면에서 다시 입력해야 한다", row.getProvider(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 공개 서버와 같은 값 — 저장된 {@code site_settings.public_url} 그대로(비었으면 없음) */
    private Optional<String> publicUrl() {
        return site.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank());
    }
}
