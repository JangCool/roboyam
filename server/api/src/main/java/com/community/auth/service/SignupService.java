package com.community.auth.service;

import com.community.auth.dto.AuthUser;
import com.community.auth.dto.request.SignupRequest;
import com.community.auth.dto.response.EmailAvailabilityResponse;
import com.community.auth.dto.response.NicknameAvailabilityResponse;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserRepository;
import com.community.user.service.UserPolicy;
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
 * 이메일 가입과 가입 화면의 이메일·닉네임 확인 (docs/features/member-account-contract.md).
 *
 * <ul>
 *   <li><b>가입 검사 순서</b>: 입력(이메일 → 비밀번호 → 닉네임(형식 → 예약어) → 약관 → 개인정보 → 만 14세, 위반을 한꺼번에 {@code 400 VALIDATION}) →
 *       이메일 중복 {@code 409 EMAIL_TAKEN} → 닉네임 중복 {@code 409 NICKNAME_TAKEN}(대소문자 무시) → 비밀번호 해시 → 저장(동시 가입은 유일 제약이 같은 409 로) →
 *       첫 인증 메일.</li>
 *   <li>첫 인증 메일은 재발송과 같은 흐름({@link EmailVerificationService#resend})이 보낸다 — 같은 {@code auth_tokens} 행이 생기므로 가입 직후 「다시 받기」도 60초 제한에 걸린다.
 *       가입은 이미 커밋됐으므로 메일을 못 보내도 가입은 성공이다(화면의 다시 받기로 이어진다).</li>
 *   <li>약관 버전은 {@link TermsVersionProvider#currentTermsVersion()} 한 곳이 정한다(소셜 가입과 같다).</li>
 * </ul>
 * 이 클래스는 트랜잭션을 열지 않는다(느린 해시가 DB 연결을 붙잡지 않게). 세션 만들기는 컨트롤러({@code LoginSession})의 일이다. 이메일·비밀번호를 로그에 남기지 않는다.
 */
@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);

    private final MemberSignupWriter writer;
    private final UserRepository users;
    private final ProfileRepositoryQDSL profileQueries;
    private final PasswordEncoder encoder;
    private final TermsVersionProvider terms;
    private final EmailVerificationService verification;

    public SignupService(MemberSignupWriter writer, UserRepository users, ProfileRepositoryQDSL profileQueries, PasswordEncoder encoder,
                         TermsVersionProvider terms, EmailVerificationService verification) {
        this.writer = writer;
        this.users = users;
        this.profileQueries = profileQueries;
        this.encoder = encoder;
        this.terms = terms;
        this.verification = verification;
    }

    /**
     * 가입한다. 성공하면 로그인시킬 주체를 돌려준다.
     *
     * @throws ValidationException 입력 위반({@code fields.email|password|nickname|agreeTerms|agreePrivacy|ageConfirmed})
     * @throws com.community.user.exception.UserEmailTakenException    409
     * @throws com.community.user.exception.UserNicknameTakenException 409
     */
    public AuthUser signup(SignupRequest body) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String email = UserPolicy.normalizeEmail(body == null ? null : body.email());
        UserPolicy.emailViolation(email).ifPresent(message -> errors.put("email", message));
        String password = body == null ? null : body.password();
        passwordViolation(password).ifPresent(message -> errors.put("password", message));
        String nickname = body == null ? null : body.nickname();
        nicknameViolation(nickname).ifPresent(message -> errors.put("nickname", message));
        if (body == null || !Boolean.TRUE.equals(body.agreeTerms())) {
            errors.put("agreeTerms", new MessageRef("user.agreeTerms.required"));
        }
        if (body == null || !Boolean.TRUE.equals(body.agreePrivacy())) {
            errors.put("agreePrivacy", new MessageRef("user.agreePrivacy.required"));
        }
        if (body == null || !Boolean.TRUE.equals(body.ageConfirmed())) {
            errors.put("ageConfirmed", new MessageRef("user.ageConfirmed.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        writer.assertAvailable(email, nickname);
        AuthUser member = writer.create(email, encoder.encode(password), nickname, terms.currentTermsVersion());
        try {
            verification.resend(member.id());
        } catch (RuntimeException e) {
            log.warn("이메일 가입 뒤 인증 메일을 시작하지 못했다 userId={} 원인={}", member.id(), e.getClass().getSimpleName());
        }
        return member;
    }

    /**
     * 그 이메일로 가입할 수 있는가(이미 가입된 회원이 없는가).
     *
     * @throws ValidationException 형식이 틀렸을 때({@code fields.email})
     */
    public EmailAvailabilityResponse emailAvailable(String rawEmail) {
        String email = UserPolicy.normalizeEmail(rawEmail);
        UserPolicy.emailViolation(email).ifPresent(message -> {
            throw ValidationException.of("email", message);
        });
        return new EmailAvailabilityResponse(!users.existsByEmail(email));
    }

    /** 그 닉네임을 쓸 수 있는가 — 까닭은 길이 → 허용 문자 → 예약어 → 중복 차례로 본다 */
    public NicknameAvailabilityResponse nicknameAvailable(String nickname) {
        Optional<MessageRef> format = UserPolicy.nicknameViolation(nickname);
        if (format.isPresent()) {
            return new NicknameAvailabilityResponse(false, "user.nickname.chars".equals(format.get().key()) ? "CHARS" : "LENGTH");
        }
        if (UserPolicy.isReservedNickname(nickname)) {
            return new NicknameAvailabilityResponse(false, "RESERVED");
        }
        if (profileQueries.existsByNickname(nickname)) {
            return new NicknameAvailabilityResponse(false, "DUPLICATE");
        }
        return new NicknameAvailabilityResponse(true, null);
    }

    /** 회원이 고르는 닉네임 — 형식, 그다음 예약어 */
    private static Optional<MessageRef> nicknameViolation(String nickname) {
        Optional<MessageRef> format = UserPolicy.nicknameViolation(nickname);
        if (format.isPresent()) {
            return format;
        }
        return UserPolicy.isReservedNickname(nickname) ? Optional.of(new MessageRef("user.nickname.reserved")) : Optional.empty();
    }

    /** 비어 있으면 「입력해 주세요」, 그 밖에는 가입 규칙({@link UserPolicy#passwordViolation} — 72바이트 포함) */
    static Optional<MessageRef> passwordViolation(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of(new MessageRef("user.password.required"));
        }
        return UserPolicy.passwordViolation(password);
    }
}
