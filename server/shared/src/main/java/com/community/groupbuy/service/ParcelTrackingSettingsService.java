package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.ParcelTrackingStore;
import com.community.groupbuy.repository.master.ParcelTrackingStore.SettingsRow;
import com.community.groupbuy.tracking.ParcelTrackingClient;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 조회 설정(「설정 › 커머스」 택배사 구획, 계약 결정 90 — {@code commerce_tracking_settings} 1행). 범위·처음 값은 여기가 정본이고 화면은 응답의 {@code ranges}·{@code defaults} 로 그린다.
 *
 * <ul>
 *   <li>숫자·스위치 칸: 자동 조회 켜기(기본 꺼짐) · 주기 {@value #INTERVAL_MIN}~{@value #INTERVAL_MAX}분(기본 {@value #INTERVAL_DEFAULT}) · 발송 뒤 N일 자동 배송완료 켜기(기본 꺼짐) ·
 *       N {@value #DAYS_MIN}~{@value #DAYS_MAX}일(기본 {@value #DAYS_DEFAULT}). 바뀐 칸만 전→후 감사 {@code COMMERCE_TRACKING_SETTINGS_UPDATE}.</li>
 *   <li>API 키: {@link SecretEncryptor}({@code IROBIT_CRYPTO_KEY}) 암호문만 저장한다(공공데이터포털 키·AI 제공사 키와 같은 방식). 화면에는 「설정됨」 여부만 — 값을 돌려주지 않는다.
 *       감사 {@code COMMERCE_TRACKING_KEY_UPDATE}·{@code _DELETE} 에도 칸 이름만. 키를 바꾸거나 지우면 키 오류 알림 상태를 지운다.</li>
 *   <li>이용권 남은 양({@link #usage})은 스마트택배를 한 번 부른다(트랜잭션 밖).</li>
 * </ul>
 * 권한 검사는 컨트롤러 선언이 한다({@code commerce.settings}).
 */
@Service
public class ParcelTrackingSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ParcelTrackingSettingsService.class);

    public static final int INTERVAL_MIN = 60;
    public static final int INTERVAL_MAX = 1440;
    public static final int INTERVAL_DEFAULT = 180;
    public static final int DAYS_MIN = 3;
    public static final int DAYS_MAX = 30;
    public static final int DAYS_DEFAULT = 10;
    /** 키 최대 길이 */
    static final int KEY_MAX = 200;
    /** 감사 대상(커머스 설정 — 판매 스위치·공구 설정과 같은 종류) · 대상 ID */
    public static final String AUDIT_TARGET = "COMMERCE_SETTINGS";
    public static final String AUDIT_TARGET_ID = "parcel-tracking";
    /** 감사의 키 칸 이름 */
    public static final String KEY_FIELD = "apiKey";

    /** 화면 값 */
    public record Values(boolean enabled, int intervalMinutes, boolean autoDeliverEnabled, int autoDeliverDays) {
    }

    /** 화면 — 키 값은 없다(설정됨 여부·마지막 변경만). {@code keyAlertCode} 지금 키가 받는 오류(101·102·103, 없으면 null) */
    public record View(Values values, boolean apiKeyConfigured, boolean cryptoConfigured, Instant keyUpdatedAt, String keyUpdatedBy, String keyAlertCode, Instant keyAlertAt,
                       Instant updatedAt, String updatedBy) {
    }

    /** 이용권 남은 양 — 조회가 안 되면 {@code outcome} 이 OK 가 아니고 {@code errorCode} */
    public record UsageView(String outcome, String errorCode, String startDate, String endDate, Long totalAmount, Long leftAmount, Instant checkedAt) {
    }

    private final ParcelTrackingStore store;
    private final SecretEncryptor encryptor;
    private final ParcelTrackingClient client;
    private final AuditLogService audit;
    private final ObjectProvider<Clock> clocks;

    public ParcelTrackingSettingsService(ParcelTrackingStore store, SecretEncryptor encryptor, ParcelTrackingClient client, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.encryptor = encryptor;
        this.client = client;
        this.audit = audit;
        this.clocks = clocks;
    }

    /** 처음 값(화면 「처음 값으로」) */
    public static Values defaults() {
        return new Values(false, INTERVAL_DEFAULT, false, DAYS_DEFAULT);
    }

    @Transactional(readOnly = true)
    public View view() {
        SettingsRow s = store.settings();
        return new View(new Values(s.enabled(), s.intervalMinutes(), s.autoDeliverEnabled(), s.autoDeliverDays()), s.apiKeyCipher() != null, encryptor.isConfigured(),
                s.keyUpdatedAt(), s.keyUpdatedBy(), s.alertCode(), s.alertAt(), s.updatedAt(), s.updatedBy());
    }

    /** 통째 저장 — 빠진 칸·범위 밖은 400 {@code fields.values.<이름>}. 바뀐 칸만 감사 */
    @Transactional
    public View save(Boolean enabled, Integer intervalMinutes, Boolean autoDeliverEnabled, Integer autoDeliverDays, long operatorId, String ip) {
        if (enabled == null) {
            throw ValidationException.of("values.enabled", new MessageRef("groupbuy.tracking.settings.required"));
        }
        if (autoDeliverEnabled == null) {
            throw ValidationException.of("values.autoDeliverEnabled", new MessageRef("groupbuy.tracking.settings.required"));
        }
        if (intervalMinutes == null || intervalMinutes < INTERVAL_MIN || intervalMinutes > INTERVAL_MAX) {
            throw ValidationException.of("values.intervalMinutes", new MessageRef("groupbuy.settings.range", INTERVAL_MIN, INTERVAL_MAX));
        }
        if (autoDeliverDays == null || autoDeliverDays < DAYS_MIN || autoDeliverDays > DAYS_MAX) {
            throw ValidationException.of("values.autoDeliverDays", new MessageRef("groupbuy.settings.range", DAYS_MIN, DAYS_MAX));
        }
        store.lockSettings();
        SettingsRow before = store.settings();
        store.saveSettings(enabled, intervalMinutes, autoDeliverEnabled, autoDeliverDays, operatorId, now());
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        changed("enabled", before.enabled(), enabled, from, to);
        changed("intervalMinutes", before.intervalMinutes(), intervalMinutes, from, to);
        changed("autoDeliverEnabled", before.autoDeliverEnabled(), autoDeliverEnabled, from, to);
        changed("autoDeliverDays", before.autoDeliverDays(), autoDeliverDays, from, to);
        if (!to.isEmpty()) {
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.COMMERCE_TRACKING_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, AUDIT_TARGET_ID)
                    .with("before", from).with("after", to));
        }
        return view();
    }

    /**
     * API 키 저장 — 앞뒤 공백을 지우고, 빈 값·가운데 공백·{@value #KEY_MAX}자 초과는 400 {@code fields.apiKey}.
     *
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public View saveKey(String apiKey, long operatorId, String ip) {
        String value = apiKey == null ? "" : apiKey.strip();
        if (value.isEmpty()) {
            throw ValidationException.of(KEY_FIELD, new MessageRef("groupbuy.tracking.key.required"));
        }
        if (value.length() > KEY_MAX || value.chars().anyMatch(Character::isWhitespace)) {
            throw ValidationException.of(KEY_FIELD, new MessageRef("groupbuy.tracking.key.invalid", KEY_MAX));
        }
        encryptor.requireConfigured();
        store.lockSettings();
        store.saveKey(encryptor.encrypt(value), operatorId, now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.COMMERCE_TRACKING_KEY_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, AUDIT_TARGET_ID)
                .with("fields", List.of(KEY_FIELD)));
        return view();
    }

    /** API 키 지우기 — 없으면 그대로(감사 없음). 자동 조회는 키가 생길 때까지 쉰다 */
    @Transactional
    public View deleteKey(long operatorId, String ip) {
        store.lockSettings();
        if (store.clearKey(operatorId, now())) {
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.COMMERCE_TRACKING_KEY_DELETE, AuditResult.SUCCESS).target(AUDIT_TARGET, AUDIT_TARGET_ID)
                    .with("field", KEY_FIELD));
        }
        return view();
    }

    /**
     * 이용권 남은 양 — 저장된 키로 스마트택배를 한 번 부른다(트랜잭션 밖). 키가 없으면 409 {@code COMMERCE_TRACKING_KEY_MISSING}.
     */
    public UsageView usage() {
        String key = apiKey().orElseThrow(GroupBuyException::trackingKeyMissing);
        ParcelTrackingClient.Usage u = client.usage(key);
        return new UsageView(u.outcome().name(), u.errorCode(), u.startDate(), u.endDate(), u.totalAmount(), u.leftAmount(), now());
    }

    /**
     * 지금 쓸 키(풀어서) — 없거나 풀 수 없으면(암호화 키가 없거나 바뀜 — 로그만) 비어 있다. 결과를 응답·로그에 싣지 않는다.
     */
    public Optional<String> apiKey() {
        String cipher = store.settings().apiKeyCipher();
        if (cipher == null) {
            return Optional.empty();
        }
        if (!encryptor.isConfigured()) {
            log.error("스마트택배 API 키를 풀 수 없다 — 암호화 키(IROBIT_CRYPTO_KEY)가 없다. 자동 조회를 쉰다");
            return Optional.empty();
        }
        try {
            String value = encryptor.decrypt(cipher);
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (IllegalArgumentException e) {
            log.error("스마트택배 API 키를 풀지 못했다 — 암호화 키가 바뀌었는지 확인. 자동 조회를 쉰다"); // 값·암호문은 싣지 않는다
            return Optional.empty();
        }
    }

    private static void changed(String name, Object before, Object after, Map<String, Object> from, Map<String, Object> to) {
        if (!Objects.equals(before, after)) {
            from.put(name, before);
            to.put(name, after);
        }
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant().truncatedTo(ChronoUnit.MICROS);
    }
}
