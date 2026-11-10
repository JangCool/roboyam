package com.community.aimember.service;

import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyEntity;
import com.community.aimember.repository.master.AiProviderKeyRepository;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제공사 API 키(정책 12) — {@link SecretEncryptor}({@code ROBOYAM_CRYPTO_KEY}) 암호문을 {@code ai_provider_keys} 에 둔다. 키 값은 실행기가 호출 직전에만 푼다 —
 * 응답·로그·감사에 싣지 않는다. 키가 없거나 풀 수 없는 제공사의 AI 계정은 쉰다({@code NO_PROVIDER_KEY}).
 */
@Service
@Transactional(readOnly = true)
public class AiProviderKeys {

    private static final Logger log = LoggerFactory.getLogger(AiProviderKeys.class);

    private final AiProviderKeyRepository keys;
    private final SecretEncryptor encryptor;

    public AiProviderKeys(AiProviderKeyRepository keys, SecretEncryptor encryptor) {
        this.keys = keys;
        this.encryptor = encryptor;
    }

    /** 키 상태(값 없음) */
    public record KeyState(AiProvider provider, boolean configured, Instant updatedAt, Long updatedBy) {
    }

    public Map<AiProvider, KeyState> states() {
        Map<AiProvider, KeyState> out = new EnumMap<>(AiProvider.class);
        for (AiProvider p : AiProvider.values()) {
            out.put(p, new KeyState(p, false, null, null));
        }
        for (AiProviderKeyEntity e : keys.findAll()) {
            out.put(e.getProvider(), new KeyState(e.getProvider(), true, e.getUpdatedAt(), e.getUpdatedBy()));
        }
        return out;
    }

    /** 키가 저장된 제공사(암호화 키가 없으면 풀 수 없으므로 비어 있다 — 쉬는 이유 판정) */
    public Set<AiProvider> usable() {
        Set<AiProvider> out = EnumSet.noneOf(AiProvider.class);
        if (!encryptor.isConfigured()) {
            return out;
        }
        keys.findAll().forEach(e -> out.add(e.getProvider()));
        return out;
    }

    /** 호출에 쓸 키 — 없거나 풀 수 없으면 빈 값(값·암호문은 로그에 싣지 않는다) */
    public Optional<String> key(AiProvider provider) {
        Optional<AiProviderKeyEntity> row = keys.findById(provider);
        if (row.isEmpty() || !encryptor.isConfigured()) {
            return Optional.empty();
        }
        try {
            String value = encryptor.decrypt(row.get().getKeyCipher());
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (IllegalArgumentException e) {
            log.error("AI 제공사 키를 풀지 못했다 provider={} — 암호화 키가 바뀌었는지 확인", provider);
            return Optional.empty();
        }
    }

    public boolean cryptoConfigured() {
        return encryptor.isConfigured();
    }

    /**
     * 키 저장(바꾸기) — 검사는 부르는 쪽이 끝냈다.
     *
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public void save(AiProvider provider, String value, long operatorId, Instant now) {
        encryptor.requireConfigured();
        String cipher = encryptor.encrypt(value);
        keys.findById(provider).ifPresentOrElse(e -> e.change(cipher, operatorId, now), () -> keys.persist(AiProviderKeyEntity.create(provider, cipher, operatorId, now)));
    }
}
