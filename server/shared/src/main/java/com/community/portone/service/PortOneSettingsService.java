package com.community.portone.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.identity.config.IdentityProperties;
import com.community.payment.config.PaymentProperties;
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
import java.util.Base64;
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
 *   <li>관리자 앱의 화면 API 가 {@link #view} · {@link #save} · {@link #deleteField} 를 부른다. 저장·지우기는 카드(공통·본인인증·결제 — 2a 결정 14) 단위이고 감사 대상 ID 가 카드 이름이다.
 *       권한 검사는 컨트롤러 선언이 한다.</li>
 *   <li>웹훅 비밀키는 Standard Webhooks 형식({@code whsec_} + base64)만 받는다 — 서명 검증이 풀 수 없는 값을 저장해 두면 웹훅이 모두 거절되기 때문이다.</li>
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
    private final PaymentProperties payment;
    private final AuditLogService audit;
    private final Clock clock;

    public PortOneSettingsService(PortOneSettingStore store, SecretEncryptor encryptor, IdentityProperties identity, PaymentProperties payment, AuditLogService audit,
                                  ObjectProvider<Clock> clocks) {
        this.store = store;
        this.encryptor = encryptor;
        this.identity = identity;
        this.payment = payment;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 화면 — 다섯 칸의 상태(값 없음)와 지금 구현·준비 여부 */
    public PortOneSettingsView view() {
        Map<String, PortOneSettingStore.Row> rows = rows();
        List<PortOneFieldStateView> fields = new ArrayList<>();
        for (PortOneSettingKey key : PortOneSettingKey.values()) {
            PortOneSettingStore.Row row = rows.get(key.key());
            fields.add(row == null ? new PortOneFieldStateView(key.key(), false, null, null)
                    : new PortOneFieldStateView(key.key(), row.configured(), row.updatedAt(), row.updatedByName()));
        }
        return new PortOneSettingsView(identity.provider(), payment.gateway(), encryptor.isConfigured(), identityReady(rows),
                encryptor.isConfigured() && keysReady(rows, PortOneSettingKey.paymentKeys()), fields);
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
     * 한 카드의 칸을 저장한다 — <b>보낸 칸만</b>(맵에 없거나 null 인 칸은 그대로). 앞뒤 공백은 지운다. 감사는 대상 ID 가 카드 이름(common·identity·payment)이고 칸 이름만 남긴다.
     *
     * @param sent 칸 → 보낸 값. 모두 그 카드의 칸이어야 한다
     * @return 저장한 뒤의 화면
     * @throws ValidationException 보낸 칸이 비었거나 길거나 형식이 틀릴 때({@code fields.<칸>}), 아무 칸도 보내지 않았을 때
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public PortOneSettingsView save(PortOneSettingKey.Group group, Map<PortOneSettingKey, String> sent, long operatorId, String ip) {
        if (sent.keySet().stream().anyMatch(key -> key.group() != group)) {
            throw new IllegalArgumentException("다른 카드의 칸이다: " + group);
        }
        Map<String, MessageRef> violations = new LinkedHashMap<>();
        Map<PortOneSettingKey, String> values = new LinkedHashMap<>();
        for (PortOneSettingKey key : PortOneSettingKey.of(group)) {
            String raw = sent.get(key);
            if (raw == null) {
                continue;
            }
            String value = raw.strip();
            MessageRef problem = violation(key, value);
            if (problem != null) {
                violations.put(key.key(), problem);
            } else {
                values.put(key, value);
            }
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        if (values.isEmpty()) {
            throw new CommonException(ErrorCode.VALIDATION); // 바꿀 칸이 없다
        }
        encryptor.requireConfigured();
        Instant now = clock.instant();
        values.forEach((key, value) -> store.upsert(key.key(), encryptor.encrypt(value), operatorId, now));
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PORTONE_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, groupName(group))
                .with("fields", values.keySet().stream().map(PortOneSettingKey::key).toList()));
        return view();
    }

    /** 칸 값 검사 — 빈 값·{@value #VALUE_MAX}자 초과, 웹훅 비밀키는 {@code whsec_} + base64. 문제가 없으면 null */
    static MessageRef violation(PortOneSettingKey key, String value) {
        if (value.isEmpty()) {
            return new MessageRef("portone.field.required");
        }
        if (value.length() > VALUE_MAX) {
            return new MessageRef("portone.field.max", VALUE_MAX);
        }
        if (key == PortOneSettingKey.WEBHOOK_SECRET && !webhookSecretShape(value)) {
            return new MessageRef("portone.field.webhookSecret");
        }
        return null;
    }

    /** Standard Webhooks 비밀키 모양 — {@code whsec_} 뒤가 비어 있지 않은 base64 */
    static boolean webhookSecretShape(String value) {
        if (!value.startsWith(WEBHOOK_SECRET_PREFIX) || value.length() == WEBHOOK_SECRET_PREFIX.length()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(value.substring(WEBHOOK_SECRET_PREFIX.length())).length > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 감사 대상 ID·주소에 쓰는 카드 이름(common·identity·payment) */
    public static String groupName(PortOneSettingKey.Group group) {
        return group.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 설정된 칸 하나의 값을 지운다(바꾼 시각·사람은 남는다).
     *
     * @throws PortOneFieldNotFoundException 그 카드의 칸이 아니거나(모르는 칸 포함) 설정돼 있지 않은 칸
     */
    @Transactional
    public void deleteField(PortOneSettingKey.Group group, String key, long operatorId, String ip) {
        PortOneSettingKey field = PortOneSettingKey.ofKey(key).filter(k -> k.group() == group).orElseThrow(PortOneFieldNotFoundException::new);
        if (!store.clear(field.key(), operatorId, clock.instant())) {
            throw new PortOneFieldNotFoundException();
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PORTONE_SETTINGS_DELETE, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, groupName(group)).with("field", field.key()));
    }

    /**
     * 결제에 필요한 4칸(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키) + 암호화 키가 모두 있는가 — 값을 풀지 않는다(결정 14 {@code paymentReady}, 화면에도 싣는다).
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
    /** Standard Webhooks 비밀키 머리 */
    static final String WEBHOOK_SECRET_PREFIX = "whsec_";

    private Map<String, PortOneSettingStore.Row> rows() {
        return store.findAll().stream().collect(Collectors.toMap(PortOneSettingStore.Row::key, Function.identity()));
    }

    private static boolean identityReady(Map<String, PortOneSettingStore.Row> rows) {
        return PortOneSettingKey.identityKeys().stream().allMatch(key -> rows.containsKey(key.key()) && rows.get(key.key()).configured());
    }
}
