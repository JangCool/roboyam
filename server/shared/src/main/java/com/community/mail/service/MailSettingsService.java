package com.community.mail.service;

import com.community.mail.dto.MailInput;
import com.community.mail.dto.MailValues;
import com.community.mail.entity.MailSecurity;
import com.community.mail.entity.MailSettingEntity;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 서버 설정의 입력 검사와 첫 저장. 설치 마법사가 쓰고, 입력 검사({@link #normalize})는 관리자 화면의 저장(admin {@code MailSettingsAdminService})도 같이 쓴다.
 *
 * <ul>
 *   <li>첫 저장({@link #create})은 설정 이력에 출처 {@code INSTALL} 한 줄을 함께 남긴다({@link MailSettingHistoryService}).</li>
 *   <li><b>비밀번호는 암호화해 저장</b>하고({@link SecretEncryptor}) <b>어떤 응답·로그·오류에도 싣지 않는다.</b></li>
 *   <li><b>사용자 이름과 비밀번호는 "둘 다 있거나 둘 다 비움"</b>이다 — 사용자 이름이 있으면 비밀번호가 필수이고(여기서는 저장된 값이 없다),
 *       비밀번호만 있으면 사용자 이름 오류다.</li>
 * </ul>
 */
@Service
public class MailSettingsService {

    private static final Logger log = LoggerFactory.getLogger(MailSettingsService.class);

    static final int HOST_MAX = 255;
    static final int USERNAME_MAX = 255;
    static final int PASSWORD_MAX = 500;

    private final MailSettingRepository settings;
    private final SecretEncryptor encryptor;
    private final MailSettingHistoryService history;

    public MailSettingsService(MailSettingRepository settings, SecretEncryptor encryptor, MailSettingHistoryService history) {
        this.settings = settings;
        this.encryptor = encryptor;
        this.history = history;
    }

    /**
     * 입력을 검사하고 저장할 모양으로 다듬는다. 위반은 {@code errors} 에 {@code <fieldPrefix>host} · {@code port} · {@code from} · {@code security} ·
     * {@code username} · {@code password} 로 모은다(설치 마법사는 {@code mail.} 을 붙인다). 오류는 메시지 키 + 인자({@link MessageRef})이고 입력한 값을 싣지 않는다.
     *
     * @param fromRequired 보내는 사람이 필수인가(연결 시험은 보내는 사람을 쓰지 않으므로 비워도 된다 — 넣었으면 형식은 본다)
     */
    public MailValues normalize(Map<String, MessageRef> errors, String fieldPrefix, MailInput input, boolean fromRequired) {
        MailInput in = input == null ? new MailInput(null, null, null, null, null, null) : input;

        String host = in.host() == null ? null : in.host().strip();
        if (host == null || host.isEmpty()) {
            errors.put(fieldPrefix + "host", new MessageRef("mail.host.required"));
        } else if (host.length() > HOST_MAX) {
            errors.put(fieldPrefix + "host", new MessageRef("mail.host.length", HOST_MAX));
        } else if (host.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c))) {
            errors.put(fieldPrefix + "host", new MessageRef("mail.host.blank"));
        }

        int port = 0;
        if (in.port() == null) {
            errors.put(fieldPrefix + "port", new MessageRef("mail.port.required"));
        } else if (in.port() < 1 || in.port() > 65535) {
            errors.put(fieldPrefix + "port", new MessageRef("mail.port.range"));
        } else {
            port = in.port();
        }

        String from = UserPolicy.normalizeEmail(in.from());
        if (from == null || from.isEmpty()) {
            if (fromRequired) {
                errors.put(fieldPrefix + "from", new MessageRef("mail.from.required"));
            }
        } else if (!UserPolicy.isValidEmail(from)) {
            errors.put(fieldPrefix + "from", new MessageRef("mail.from.format"));
        }

        MailSecurity security = MailSecurity.NONE; // 생략하면 암호화하지 않는다
        if (in.security() != null && !in.security().isBlank()) {
            Optional<MailSecurity> parsed = MailSecurity.fromName(in.security());
            if (parsed.isPresent()) {
                security = parsed.get();
            } else {
                errors.put(fieldPrefix + "security", new MessageRef("mail.security.invalid"));
            }
        }

        String username = in.username() == null || in.username().isBlank() ? null : in.username().strip();
        String password = in.password() == null || in.password().isBlank() ? null : in.password();
        if (username != null && username.length() > USERNAME_MAX) {
            errors.put(fieldPrefix + "username", new MessageRef("mail.username.length", USERNAME_MAX));
        } else if (username != null && username.chars().anyMatch(Character::isISOControl)) {
            errors.put(fieldPrefix + "username", new MessageRef("mail.username.control"));
        }
        if (password != null && password.length() > PASSWORD_MAX) {
            errors.put(fieldPrefix + "password", new MessageRef("mail.password.length", PASSWORD_MAX));
        } else if (password != null && password.chars().anyMatch(Character::isISOControl)) {
            errors.put(fieldPrefix + "password", new MessageRef("mail.password.control"));
        }
        if (username == null && password != null) {
            errors.put(fieldPrefix + "username", new MessageRef("mail.username.required"));
        }
        if (username != null && password == null) {
            errors.put(fieldPrefix + "password", new MessageRef("mail.password.required"));
        }
        if (username == null) {
            password = null; // 인증 없이 보낸다
        }
        return new MailValues(host, port, username, password, from, security);
    }

    /**
     * 검사를 마친 값을 처음 저장한다. <b>트랜잭션은 부르는 쪽이 연다</b> — 설치 마법사는 이것을 설치 트랜잭션 안에서 불러 실패하면 함께 되돌린다.
     * 비밀번호가 있으면 암호화 키가 있어야 한다(없으면 {@code 503 CRYPTO_KEY_MISSING}). 설정 이력에 출처 {@code INSTALL} 첫 줄을 같은 트랜잭션으로 남긴다.
     */
    @Transactional
    public void create(MailValues values, long adminId, Instant now) {
        String passwordEnc = values.password() == null ? null : encryptor.encrypt(values.password());
        settings.persist(MailSettingEntity.create(values.host(), values.port(), values.username(), passwordEnc, values.from(),
                values.security(), adminId, now));
        history.recordInstall(values, adminId, now);
        log.info("메일 서버 설정 저장(인증 {}) userId={}", values.username() == null ? "없음" : "사용", adminId);
    }
}
