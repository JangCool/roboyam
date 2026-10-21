package com.community.operator.service;

import com.community.user.service.UserPolicy;
import java.security.SecureRandom;

/**
 * 운영자를 바로 만들 때 서버가 정하는 임시 비밀번호. 공개 사이트 가입 규칙({@link UserPolicy#passwordViolation})을 채우는 14자(영문 대소문자·숫자, 헷갈리는 글자 제외)다.
 * 값은 해시로만 저장하고 이메일로 안내한 뒤에는 어디에도 남지 않는다.
 */
final class TemporaryPassword {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final int LENGTH = 14;

    private TemporaryPassword() {
    }

    static String generate() {
        while (true) {
            StringBuilder password = new StringBuilder(LENGTH);
            String all = LETTERS + DIGITS;
            for (int i = 0; i < LENGTH; i++) {
                password.append(all.charAt(RANDOM.nextInt(all.length())));
            }
            // 영문과 숫자를 모두 담도록 한 자리씩 보장한다(우연히 한쪽만 나온 경우 다시 뽑는 대신 바로잡는다)
            password.setCharAt(RANDOM.nextInt(LENGTH / 2), LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
            password.setCharAt(LENGTH / 2 + RANDOM.nextInt(LENGTH / 2), DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
            String value = password.toString();
            if (UserPolicy.passwordViolation(value).isEmpty()) {
                return value;
            }
        }
    }
}
