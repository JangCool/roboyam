package com.community.operator.service;

import com.community.emoticon.service.EmoticonAcquireService;
import com.community.operator.dto.request.OperatorMemberCreateRequest;
import com.community.operator.dto.response.OperatorMemberCreateResult;
import com.community.permission.dto.AdminActor;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 운영자 회원 계정 「새 계정 만들기」(docs/features/operator-member-account-contract.md 「새 계정 만들기(2026-11-24)」) —
 * {@code POST /api/admin/operators/{operatorId}/member-account}.
 *
 * <ol>
 *   <li>입력 형식({@code 400 VALIDATION}, 위반을 한꺼번에): 이메일({@link UserPolicy#emailViolation}) · 닉네임 형식({@link UserPolicy#nicknameViolation} — <b>예약어는 보지 않는다</b>,
 *       운영자 계정이라 「아이로빗」·「운영자」 같은 이름을 쓸 수 있어야 한다) · 비밀번호 방식({@code fields.passwordMode}) · DIRECT 면 비밀번호(공개 가입과 같은 규칙 —
 *       {@link UserPolicy#passwordViolation}, 비면 「입력해 주세요」).</li>
 *   <li>비밀번호 해시(트랜잭션 밖 — 느린 해시가 DB 연결을 붙잡지 않게). MAIL 이면 아무도 모르는 무작위 비밀번호의 해시를 둔다 — 링크에서 정할 때까지 로그인할 수 없고,
 *       링크가 지나도 「비밀번호 찾기」로 이어갈 수 있다.</li>
 *   <li>나머지 판정·저장은 한 트랜잭션({@link OperatorMemberAccountAdminService#createLinked}).</li>
 *   <li>커밋 뒤 가입 무료 운영 로보티콘(공개 가입과 같은 길 — 실패해도 계정은 성공).</li>
 * </ol>
 * 이 클래스는 트랜잭션을 열지 않는다. 이메일·비밀번호를 로그에 남기지 않는다.
 */
@Service
public class OperatorMemberCreateService {

    private static final Logger log = LoggerFactory.getLogger(OperatorMemberCreateService.class);

    public static final String MODE_DIRECT = "DIRECT";
    public static final String MODE_MAIL = "MAIL";

    private final OperatorMemberAccountAdminService accounts;
    private final PasswordEncoder encoder;
    private final EmoticonAcquireService emoticons;

    public OperatorMemberCreateService(OperatorMemberAccountAdminService accounts, PasswordEncoder encoder, EmoticonAcquireService emoticons) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.emoticons = emoticons;
    }

    /**
     * @throws ValidationException 입력 위반({@code fields.email|nickname|passwordMode|password})
     */
    public OperatorMemberCreateResult create(AdminActor actor, String operatorId, OperatorMemberCreateRequest body) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String email = UserPolicy.normalizeEmail(body == null ? null : body.email());
        UserPolicy.emailViolation(email).ifPresent(message -> errors.put("email", message));
        String nickname = body == null ? null : body.nickname();
        UserPolicy.nicknameViolation(nickname).ifPresent(message -> errors.put("nickname", message));
        String mode = body == null ? null : body.passwordMode();
        boolean direct = MODE_DIRECT.equals(mode);
        if (!direct && !MODE_MAIL.equals(mode)) {
            errors.put("passwordMode", new MessageRef("operatorMember.passwordMode.invalid"));
        } else if (direct) {
            passwordViolation(body.password()).ifPresent(message -> errors.put("password", message));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        String passwordHash = encoder.encode(direct ? body.password() : Digests.newToken());
        OperatorMemberCreateResult result = accounts.createLinked(actor, operatorId, email, nickname, mode, passwordHash);
        grantSignupEmoticons(Long.parseLong(result.userId()));
        return result;
    }

    /** 공개 가입과 같은 무료 운영 로보티콘 — 계정은 이미 커밋됐으므로 실패해도 성공이다(상점의 받기로 받을 수 있다) */
    private void grantSignupEmoticons(long userId) {
        try {
            emoticons.grantSignupPacks(userId);
        } catch (RuntimeException e) {
            log.warn("운영자 회원 계정을 만든 뒤 무료 로보티콘을 주지 못했다 userId={} 원인={}", userId, e.getClass().getSimpleName());
        }
    }

    /** 비어 있으면 「입력해 주세요」, 그 밖에는 가입 규칙(api {@code SignupService#passwordViolation} 과 같다) */
    private static Optional<MessageRef> passwordViolation(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of(new MessageRef("user.password.required"));
        }
        return UserPolicy.passwordViolation(password);
    }
}
