package com.community.aimember.service;

import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyEntity;
import com.community.aimember.entity.AiProviderKeyStatus;
import com.community.aimember.repository.master.AiProviderKeyRepository;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제공사 API 키(정책 12) — {@link SecretEncryptor}({@code IROBIT_CRYPTO_KEY}) 암호문을 {@code ai_provider_keys} 에 둔다. 키 값은 실행기가 호출 직전에만 푼다 —
 * 응답·로그·감사에 싣지 않는다. 키가 없거나 풀 수 없는 제공사의 AI 계정은 쉰다({@code NO_PROVIDER_KEY}).
 *
 * <p><b>제공사 쉬게 하기</b>(V189, 2026-11-11): 제공사가 결제·크레딧 부족이나 키 오류로 거절하면 실행기가 {@link #pause} 로 상태를 바꾸고 — 그 제공사의 계정은 「키 없음」과 같은 방식으로
 * 쉰다({@link #paused}) — 상태가 바뀐 그 순간에만 관리자 알림 {@link AdminNotificationEvent#AI_PROVIDER_PAUSED} 를 한 번 낸다. 키를 다시 저장하면({@link #save}) {@code OK} 로 돌아온다.
 * 호출 중에 키가 바뀌었으면(옛 키의 거절) 쉬게 하지 않는다 — 키를 읽은 때의 {@code updatedAt} 과 맞춰 본다.
 */
@Service
@Transactional(readOnly = true)
public class AiProviderKeys {

    private static final Logger log = LoggerFactory.getLogger(AiProviderKeys.class);

    private final AiProviderKeyRepository keys;
    private final SecretEncryptor encryptor;
    private final NotificationPublisher notifications;

    public AiProviderKeys(AiProviderKeyRepository keys, SecretEncryptor encryptor, NotificationPublisher notifications) {
        this.keys = keys;
        this.encryptor = encryptor;
        this.notifications = notifications;
    }

    /** 키 상태(값 없음) — 저장 안 됐으면 {@code configured=false} · 상태 {@code OK} */
    public record KeyState(AiProvider provider, boolean configured, Instant updatedAt, Long updatedBy, AiProviderKeyStatus status, String statusDetail,
                           Instant statusAt) {
    }

    public Map<AiProvider, KeyState> states() {
        Map<AiProvider, KeyState> out = new EnumMap<>(AiProvider.class);
        for (AiProvider p : AiProvider.values()) {
            out.put(p, new KeyState(p, false, null, null, AiProviderKeyStatus.OK, null, null));
        }
        for (AiProviderKeyEntity e : keys.findAll()) {
            out.put(e.getProvider(), new KeyState(e.getProvider(), true, e.getUpdatedAt(), e.getUpdatedBy(), e.getStatus(), e.getStatusDetail(), e.getStatusAt()));
        }
        return out;
    }

    /** 키가 저장된 제공사(암호화 키가 없으면 풀 수 없으므로 비어 있다 — 쉬는 이유 판정). 쉬는 중인 제공사도 들어 있다 — {@link #paused} 를 같이 본다 */
    public Set<AiProvider> usable() {
        Set<AiProvider> out = EnumSet.noneOf(AiProvider.class);
        if (!encryptor.isConfigured()) {
            return out;
        }
        keys.findAll().forEach(e -> out.add(e.getProvider()));
        return out;
    }

    /** 쉬는 중인 제공사 → 상태(결제·크레딧 부족 · 키 오류). 다 정상이면 빈 맵 */
    public Map<AiProvider, AiProviderKeyStatus> paused() {
        Map<AiProvider, AiProviderKeyStatus> out = new EnumMap<>(AiProvider.class);
        for (AiProviderKeyEntity e : keys.findAll()) {
            if (e.getStatus() != AiProviderKeyStatus.OK) {
                out.put(e.getProvider(), e.getStatus());
            }
        }
        return out;
    }

    /** 호출에 쓸 키와 그 키를 저장한 때 — 쉬게 하기가 옛 키의 거절을 거르는 데 쓴다 */
    public record LoadedKey(String value, Instant keyUpdatedAt) {

        @Override
        public String toString() {
            return "LoadedKey[***]"; // 값은 로그에 싣지 않는다
        }
    }

    /** 호출에 쓸 키 — 없거나 풀 수 없으면 빈 값(값·암호문은 로그에 싣지 않는다) */
    public Optional<String> key(AiProvider provider) {
        return load(provider).map(LoadedKey::value);
    }

    public Optional<LoadedKey> load(AiProvider provider) {
        Optional<AiProviderKeyEntity> row = keys.findById(provider);
        if (row.isEmpty() || !encryptor.isConfigured()) {
            return Optional.empty();
        }
        try {
            String value = encryptor.decrypt(row.get().getKeyCipher());
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(new LoadedKey(value, row.get().getUpdatedAt()));
        } catch (IllegalArgumentException e) {
            log.error("AI 제공사 키를 풀지 못했다 provider={} — 암호화 키가 바뀌었는지 확인", provider);
            return Optional.empty();
        }
    }

    public boolean cryptoConfigured() {
        return encryptor.isConfigured();
    }

    /**
     * 키 저장(바꾸기) — 검사는 부르는 쪽이 끝냈다. 쉬는 중이던 제공사는 {@code OK} 로 돌아온다.
     *
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public void save(AiProvider provider, String value, long operatorId, Instant now) {
        encryptor.requireConfigured();
        String cipher = encryptor.encrypt(value);
        keys.findWithLockByProvider(provider).ifPresentOrElse(e -> e.change(cipher, operatorId, now),
                () -> keys.persist(AiProviderKeyEntity.create(provider, cipher, operatorId, now)));
    }

    /**
     * 제공사를 쉬게 한다(실행기 — 제공사가 결제·크레딧 부족이나 키 오류로 거절했을 때). 상태가 바뀌었으면 같은 트랜잭션에서 관리자 알림을 한 번 내고 true.
     * 같은 상태로 이미 쉬는 중이거나 그 사이 키가 다시 저장됐으면(옛 키의 거절) 아무것도 하지 않고 false.
     *
     * @param reason       실패 까닭 코드({@code BILLING_REQUIRED}·{@code KEY_INVALID} — 그 밖이면 false)
     * @param detail       제공사 메시지(가린 것 · 300자 이하)
     * @param keyUpdatedAt 호출에 쓴 키를 저장한 때({@link LoadedKey#keyUpdatedAt()})
     */
    @Transactional
    public boolean pause(AiProvider provider, String reason, String detail, Instant keyUpdatedAt, Instant now) {
        AiProviderKeyStatus next = AiProviderKeyStatus.ofFailure(reason);
        if (next == null) {
            return false;
        }
        AiProviderKeyEntity row = keys.findWithLockByProvider(provider).orElse(null);
        if (row == null || !Objects.equals(row.getUpdatedAt(), keyUpdatedAt) || !row.pause(next, detail, now)) {
            return false;
        }
        log.warn("AI 제공사를 쉬게 했다 provider={} status={}", provider, next);
        notifications.publish(AdminNotificationEvent.AI_PROVIDER_PAUSED, NotificationContext.empty()
                .args(new MessageRef("aiMember.provider." + provider.name()), new MessageRef("aiMember.keyStatus." + next.name()))
                .dedupe(provider.name()));
        return true;
    }
}
