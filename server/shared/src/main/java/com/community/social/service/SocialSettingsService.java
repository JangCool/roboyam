package com.community.social.service;

import com.community.social.dto.SocialKeyValues;
import com.community.social.dto.SocialSettingsUpdate;
import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingEntity;
import com.community.social.repository.master.SocialProviderSettingRepository;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 클라이언트 ID·시크릿의 입력 검사와 저장. 설치 마법사가 쓴다. OAuth 로그인 클라이언트 등록·로그인 흐름·설정 화면은 이 서비스의 일이 아니다.
 *
 * <p><b>시크릿은 암호화해 저장</b>하고({@link SecretEncryptor}) <b>어떤 응답·로그·오류에도 싣지 않는다.</b> 로그에는 공급자·동작·회원 ID 만 남긴다.
 */
@Service
public class SocialSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SocialSettingsService.class);

    static final int CLIENT_ID_MAX = 200;
    static final int CLIENT_SECRET_MAX = 500;

    private final SocialProviderSettingRepository settings;
    private final SecretEncryptor encryptor;

    public SocialSettingsService(SocialProviderSettingRepository settings, SecretEncryptor encryptor) {
        this.settings = settings;
        this.encryptor = encryptor;
    }

    /**
     * 키 한 건의 입력을 검사하고 저장할 모양으로 다듬는다. 위반은 {@code errors} 에 {@code <fieldPrefix>clientId} · {@code <fieldPrefix>clientSecret} 로 모은다.
     * 오류는 메시지 키 + 인자({@link MessageRef})이고 입력한 값을 싣지 않는다. 시크릿을 비웠는지는 부르는 쪽이 가른다 — 비우면 {@link SocialKeyValues#clientSecret()} 이 null 이다
     * (설치에서는 시크릿이 필수다).
     */
    public SocialKeyValues normalize(Map<String, MessageRef> errors, String fieldPrefix, SocialSettingsUpdate request) {
        String clientId = request == null || request.clientId() == null ? null : request.clientId().strip();
        String clientSecret = request == null || request.clientSecret() == null ? null : request.clientSecret().strip();
        if (clientSecret != null && clientSecret.isEmpty()) {
            clientSecret = null;
        }
        boolean enabled = request == null || request.enabled() == null || request.enabled();

        checkField(errors, fieldPrefix + "clientId", new MessageRef("social.clientId.required"),
                new MessageRef("social.clientId.length", CLIENT_ID_MAX), new MessageRef("social.clientId.blank"), clientId, CLIENT_ID_MAX, true);
        checkField(errors, fieldPrefix + "clientSecret", null,
                new MessageRef("social.clientSecret.length", CLIENT_SECRET_MAX), new MessageRef("social.clientSecret.blank"), clientSecret,
                CLIENT_SECRET_MAX, false);
        return new SocialKeyValues(clientId, clientSecret, enabled);
    }

    /**
     * 검사를 마친 키(시크릿 포함)를 암호화해 처음 저장한다. <b>트랜잭션은 부르는 쪽이 연다</b> — 설치 마법사는 이것을 설치 트랜잭션 안에서 불러
     * 실패하면 함께 되돌린다.
     *
     * @throws com.community.zconfiguration.exception.CommonException 암호화 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    @Transactional
    public void create(SocialProvider provider, SocialKeyValues key, long adminId, Instant now) {
        settings.persist(SocialProviderSettingEntity.create(provider, key.clientId(), encryptor.encrypt(key.clientSecret()), key.enabled(), adminId, now));
        log.info("소셜 로그인 키 저장 provider={} userId={}", provider, adminId);
    }

    /** 앞뒤 공백을 자른 뒤 1~{@code max}자, 공백·제어 문자 없음. 값을 오류 문구에 싣지 않는다 */
    private static void checkField(Map<String, MessageRef> errors, String field, MessageRef requiredMessage, MessageRef tooLongMessage,
                                   MessageRef blankCharMessage, String value, int max, boolean required) {
        if (value == null || value.isEmpty()) {
            if (required) {
                errors.put(field, requiredMessage);
            }
            return;
        }
        if (value.length() > max) {
            errors.put(field, tooLongMessage);
        } else if (value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c))) {
            errors.put(field, blankCharMessage);
        }
    }
}
