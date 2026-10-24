package com.community.user.service;

import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 회원 입력 규칙 — 이메일 정규화·형식, 닉네임 형식, 비밀번호 규칙. 화면(`util-validation.ts`)이 같은 규칙으로 미리 검사하고 판정은 서버가 한다.
 * 위반은 문구가 아니라 메시지 키 + 인자({@link MessageRef})로 돌려주고, 응답을 만드는 자리가 요청 언어로 푼다. 위반 문구에는 입력한 값을 싣지 않는다.
 *
 * <p>닉네임은 형식(2~12자, 한글 완성형·영문·숫자·밑줄, 글자 사이의 공백 한 칸)만 본다. 앞뒤 공백과 연속 공백은 눈으로 같은 이름이 다르게 저장되므로 허용하지 않는다. 예약어 검사는 하지 않는다 — 설치 마법사가 루트 계정에 "운영자" 같은 이름을 쓸 수 있어야 한다.
 */
public final class UserPolicy {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 12;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 64;
    /** bcrypt 가 다루는 한도(UTF-8 바이트) */
    public static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern NICKNAME_CHARS = Pattern.compile("^[가-힣a-zA-Z0-9_]+( [가-힣a-zA-Z0-9_]+)*$");

    private UserPolicy() {
    }

    /** 앞뒤 공백을 자르고 소문자로 바꾼다. 서버는 이 값으로 저장·비교한다 */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 이메일 문제가 있으면 위반 문구(키)를 돌려준다. {@code normalized} 는 {@link #normalizeEmail} 을 거친 값이다 */
    public static Optional<MessageRef> emailViolation(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Optional.of(new MessageRef("user.email.required"));
        }
        if (!isValidEmail(normalized)) {
            return Optional.of(new MessageRef("user.email.format"));
        }
        return Optional.empty();
    }

    public static boolean isValidEmail(String normalized) {
        return normalized != null && normalized.length() <= EMAIL_MAX_LENGTH && EMAIL_FORMAT.matcher(normalized).matches();
    }

    /** 닉네임 형식을 어기면 위반 문구를 돌려준다(길이 → 허용 문자 순). 중복은 저장소가 본다 */
    public static Optional<MessageRef> nicknameViolation(String nickname) {
        if (nickname == null) {
            return Optional.of(new MessageRef("user.nickname.length", NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH));
        }
        int length = nickname.codePointCount(0, nickname.length());
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            return Optional.of(new MessageRef("user.nickname.length", NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH));
        }
        if (!NICKNAME_CHARS.matcher(nickname).matches()) {
            return Optional.of(new MessageRef("user.nickname.chars"));
        }
        return Optional.empty();
    }

    /**
     * 회원이 고르는 닉네임의 예약어 — 운영자로 오해될 이름. 소문자로 바꾸고 공백·밑줄을 뺀 닉네임에 <b>들어 있으면</b> 쓸 수 없다(community 입력 규칙 「예약어 불가」).
     * 형식 검사({@link #nicknameViolation})와 따로 둔다 — 설치 마법사의 루트 계정은 예약어를 쓸 수 있어야 한다. 회원 가입·닉네임 확인(·닉네임 변경)이 부른다.
     */
    public static final java.util.List<String> RESERVED_NICKNAME_WORDS = java.util.List.of("admin", "운영자", "관리자", "운영진", "roboyam", "로보얌");

    /** 예약어가 들어 있는 닉네임인가({@link #RESERVED_NICKNAME_WORDS}) — 위반 문구는 {@code user.nickname.reserved} */
    public static boolean isReservedNickname(String nickname) {
        if (nickname == null) {
            return false;
        }
        String folded = nickname.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        return RESERVED_NICKNAME_WORDS.stream().anyMatch(folded::contains);
    }

    /**
     * 비밀번호 규칙(8~64자, UTF-8 72바이트 이하, 영문과 숫자를 모두 포함)을 어기면 위반 문구를 돌려준다.
     * bcrypt 는 72바이트를 넘는 부분을 무시하므로 해시하기 전에 거절한다 — 비밀번호를 해시하는 모든 입구가 이 검사를 거친다.
     */
    public static Optional<MessageRef> passwordViolation(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            return Optional.of(new MessageRef("user.password.length", PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return Optional.of(new MessageRef("user.password.tooLong", PASSWORD_MAX_BYTES));
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLetter = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            return Optional.of(new MessageRef("user.password.mix"));
        }
        return Optional.empty();
    }
}
