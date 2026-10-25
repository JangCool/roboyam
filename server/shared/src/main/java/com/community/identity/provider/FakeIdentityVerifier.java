package com.community.identity.provider;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.IdentityStartInfo;
import com.community.identity.dto.VerifiedPerson;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 가짜 본인인증 — <b>local·시험 전용</b>(dev·prod 프로필에서 고르면 기동을 멈춘다 — {@code IdentityConfig}). 화면의 「가짜 본인인증(개발용)」 대화상자가 생년월일·사람 번호를 보낸다.
 * <b>같은 사람 번호 = 같은 사람</b> — CI·DI 를 사람 번호로 만들어 1인 1계정·재인증 제한을 시험할 수 있다. 업체 건 번호는 {@code fake:<인증 번호>}.
 */
public class FakeIdentityVerifier implements IdentityVerifier {

    /** 사람 번호 최대 길이(화면 {@code IDENTITY_PERSON_KEY_MAX}) */
    public static final int PERSON_KEY_MAX = 40;

    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);

    private final Clock clock;

    public FakeIdentityVerifier(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IdentityProviderKind kind() {
        return IdentityProviderKind.FAKE;
    }

    @Override
    public IdentityStartInfo startInfo() {
        return IdentityStartInfo.none();
    }

    @Override
    public VerifiedPerson verify(String verificationId, FakeIdentityInput fake) {
        String birth = fake == null ? null : fake.birthDate();
        String person = fake == null || fake.personKey() == null ? null : fake.personKey().strip();
        Map<String, MessageRef> violations = new LinkedHashMap<>();
        LocalDate birthDate = null;
        if (birth == null || birth.isBlank()) {
            violations.put("fake.birthDate", new MessageRef("identity.fake.birthDate.required"));
        } else {
            birthDate = parse(birth.strip());
            if (birthDate == null || birthDate.isBefore(EARLIEST) || birthDate.isAfter(ServiceTime.today(clock))) {
                violations.put("fake.birthDate", new MessageRef("identity.fake.birthDate.invalid"));
            }
        }
        if (person == null || person.isEmpty()) {
            violations.put("fake.personKey", new MessageRef("identity.fake.personKey.required"));
        } else if (person.length() > PERSON_KEY_MAX) {
            violations.put("fake.personKey", new MessageRef("identity.fake.personKey.max", PERSON_KEY_MAX));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return new VerifiedPerson("fake-ci:" + person, "fake-di:" + person, birthDate, "fake:" + verificationId);
    }

    private static LocalDate parse(String value) {
        if (!DATE.matcher(value).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
