package com.community.portone.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.identity.config.IdentityProperties;
import com.community.portone.dto.PortOneIdentityCredentials;
import com.community.portone.dto.PortOnePaymentCredentials;
import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.dto.response.PortOneFieldStateView;
import com.community.portone.dto.response.PortOneSettingsView;
import com.community.portone.exception.PortOneFieldNotFoundException;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포트원 설정(관리자 화면의 DB 설정 — 세션 정리 결정 9, docs/features/commerce-foundation-contract.md 「0-4」). 소셜 로그인 설정처럼 값을 {@link SecretEncryptor}
 * ({@code ROBOYAM_CRYPTO_KEY})로 암호화해 {@code portone_settings} 에 칸마다 한 줄로 둔다.
 *
 * <ul>
 *   <li>화면({@link #view})에는 값이 없다 — 설정 여부·마지막 변경 시각·바꾼 사람만. 감사({@code PORTONE_SETTINGS_UPDATE}·{@code PORTONE_SETTINGS_DELETE})에도 칸 이름만.</li>
 *   <li>값을 푸는 길은 {@link #identityCredentials()}(본인인증 구현이 업체를 부를 때)와 {@link #paymentCredentials()}(결제 게이트웨이, 2a) 둘 — 결과를 응답·로그에 싣지 않는다.</li>
 *   <li>관리자 앱의 화면 API(다음 단계)가 {@link #view} · {@link #saveIdentity} · {@link #deleteField} 를 부른다. 권한 검사는 컨트롤러 선언이 한다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PortOneSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PortOneSettingsService.class);

    /** 칸 값 최대 길이 */
    static final int VALUE_MAX = 500;

    private final PortOneSettingStore store;
    private final SecretEncryptor encryptor;
    private final IdentityProperties identity;
    private final AuditLogService audit;
    private final Clock clock;

    public PortOneSettingsService(PortOneSettingStore store, SecretEncryptor encryptor, IdentityProperties identity, AuditLogService audit,
                                  ObjectProvider<Clock> clocks) {
        this.store = store;
        this.encryptor = encryptor;
        this.identity = identity;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 화면 — 본인인증 칸 셋의 상태(값 없음) */
    public PortOneSettingsView view() {
        Map<String, PortOneSettingStore.Row> rows = rows();
        List<PortOneFieldStateView> fields = new ArrayList<>();
        for (PortOneSettingKey key : PortOneSettingKey.identityKeys()) {
            PortOneSettingStore.Row row = rows.get(key.key());
            fields.add(row == null ? new PortOneFieldStateView(key.key(), false, null, null)
                    : new PortOneFieldStateView(key.key(), row.configured(), row.updatedAt(), row.updatedByName()));
        }
        return new PortOneSettingsView(identity.provider(), encryptor.isConfigured(), identityReady(rows), fields);
    }

    /** 본인인증 칸 셋이 모두 설정됐는가(값을 풀지 않는다) */
    public boolean identityReady() {
        return identityReady(rows());
    }

    /**
     * 포트원 본인인증 값을 푼다. 칸이 하나라도 비었거나 풀 수 없으면(키가 바뀜 — 로그만) 빈 값이다.
     *
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING}) — 칸이 다 설정돼 있을 때만
     */
    public Optional<PortOneIdentityCredentials> identityCredentials() {
        Map<String, PortOneSettingStore.Row> rows = rows();
        if (!identityReady(rows)) {
            return Optional.empty();
        }
        Map<PortOneSettingKey, String> values = new EnumMap<>(PortOneSettingKey.class);
        for (PortOneSettingKey key : PortOneSettingKey.identityKeys()) {
            try {
                values.put(key, encryptor.decrypt(rows.get(key.key()).valueCipher()));
            } catch (IllegalArgumentException e) {
                log.error("포트원 설정 칸을 풀지 못했다 — 암호화 키가 바뀌었는지 확인 key={}", key.key()); // 값·암호문은 싣지 않는다
                return Optional.empty();
            }
        }
        return Optional.of(new PortOneIdentityCredentials(values.get(PortOneSettingKey.STORE_ID), values.get(PortOneSettingKey.API_SECRET),
                values.get(PortOneSettingKey.IDENTITY_CHANNEL_KEY)));
    }

    /**
     * 본인인증 칸을 저장한다 — <b>보낸 칸만</b>(null 은 그대로). 앞뒤 공백은 지운다.
     *
     * @return 저장한 뒤의 화면
     * @throws ValidationException 보낸 칸이 비었거나 길 때({@code fields.<칸>}), 아무 칸도 보내지 않았을 때
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public PortOneSettingsView saveIdentity(String storeId, String apiSecret, String identityChannelKey, long operatorId, String ip) {
        Map<PortOneSettingKey, String> sent = new LinkedHashMap<>();
        if (storeId != null) {
            sent.put(PortOneSettingKey.STORE_ID, storeId);
        }
        if (apiSecret != null) {
            sent.put(PortOneSettingKey.API_SECRET, apiSecret);
        }
        if (identityChannelKey != null) {
            sent.put(PortOneSettingKey.IDENTITY_CHANNEL_KEY, identityChannelKey);
        }
        Map<String, MessageRef> violations = new LinkedHashMap<>();
        Map<PortOneSettingKey, String> values = new LinkedHashMap<>();
        sent.forEach((key, raw) -> {
            String value = raw.strip();
            if (value.isEmpty()) {
                violations.put(key.key(), new MessageRef("portone.field.required"));
            } else if (value.length() > VALUE_MAX) {
                violations.put(key.key(), new MessageRef("portone.field.max", VALUE_MAX));
            } else {
                values.put(key, value);
            }
        });
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        if (values.isEmpty()) {
            throw new CommonException(ErrorCode.VALIDATION); // 바꿀 칸이 없다
        }
        encryptor.requireConfigured();
        Instant now = clock.instant();
        values.forEach((key, value) -> store.upsert(key.key(), encryptor.encrypt(value), operatorId, now));
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PORTONE_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, "identity")
                .with("fields", values.keySet().stream().map(PortOneSettingKey::key).toList()));
        return view();
    }

    /**
     * 설정된 칸 하나의 값을 지운다(바꾼 시각·사람은 남는다).
     *
     * @throws PortOneFieldNotFoundException 없는 칸 이름이거나 설정돼 있지 않은 칸
     */
    @Transactional
    public void deleteField(String key, long operatorId, String ip) {
        PortOneSettingKey field = PortOneSettingKey.ofKey(key).orElseThrow(PortOneFieldNotFoundException::new);
        if (!store.clear(field.key(), operatorId, clock.instant())) {
            throw new PortOneFieldNotFoundException();
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PORTONE_SETTINGS_DELETE, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, field.group() == PortOneSettingKey.Group.PAYMENT ? "payment" : "identity").with("field", field.key()));
    }

    /**
     * 결제에 필요한 4칸(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키) + 암호화 키가 모두 있는가 — 값을 풀지 않는다(결정 14 {@code paymentReady}).
     * 화면·관리자 API 에 싣는 것은 2a-3 이다.
     */
    public boolean paymentReady() {
        return encryptor.isConfigured() && keysReady(rows(), PortOneSettingKey.paymentKeys());
    }

    /**
     * 포트원 결제 값을 푼다 — 결제 게이트웨이만 부른다(결과를 응답·로그에 싣지 않는다). 칸이 하나라도 비었거나, 암호화 키가 없거나, 풀 수 없으면(키가 바뀜 — 로그만) 빈 값이다.
     */
    public Optional<PortOnePaymentCredentials> paymentCredentials() {
        Map<String, PortOneSettingStore.Row> rows = rows();
        if (!encryptor.isConfigured() || !keysReady(rows, PortOneSettingKey.paymentKeys())) {
            return Optional.empty();
        }
        Map<PortOneSettingKey, String> values = new EnumMap<>(PortOneSettingKey.class);
        for (PortOneSettingKey key : PortOneSettingKey.paymentKeys()) {
            try {
                values.put(key, encryptor.decrypt(rows.get(key.key()).valueCipher()));
            } catch (IllegalArgumentException e) {
                log.error("포트원 설정 칸을 풀지 못했다 — 암호화 키가 바뀌었는지 확인 key={}", key.key()); // 값·암호문은 싣지 않는다
                return Optional.empty();
            }
        }
        return Optional.of(new PortOnePaymentCredentials(values.get(PortOneSettingKey.STORE_ID), values.get(PortOneSettingKey.API_SECRET),
                values.get(PortOneSettingKey.PAYMENT_CHANNEL_KEY), values.get(PortOneSettingKey.WEBHOOK_SECRET)));
    }

    private static boolean keysReady(Map<String, PortOneSettingStore.Row> rows, List<PortOneSettingKey> keys) {
        return keys.stream().allMatch(key -> rows.containsKey(key.key()) && rows.get(key.key()).configured());
    }

    /** 감사 대상 종류 */
    public static final String AUDIT_TARGET = "PORTONE_SETTINGS";

    private Map<String, PortOneSettingStore.Row> rows() {
        return store.findAll().stream().collect(Collectors.toMap(PortOneSettingStore.Row::key, Function.identity()));
    }

    private static boolean identityReady(Map<String, PortOneSettingStore.Row> rows) {
        return PortOneSettingKey.identityKeys().stream().allMatch(key -> rows.containsKey(key.key()) && rows.get(key.key()).configured());
    }
}
