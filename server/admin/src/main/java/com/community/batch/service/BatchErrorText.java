package com.community.batch.service;

import java.util.regex.Pattern;

/**
 * 배치가 실패한 사유를 실행 로그에 남길 한 줄 글로 만든다 — <b>가장 깊은 원인의 예외 이름 + 첫 줄</b>, 500자까지. 전체 스택트레이스는 서버 로그에만 남긴다.
 * 예외 문구에 섞일 수 있는 이메일·비밀번호·토큰 같은 값은 가린다(DB 제약 위반 문구가 행의 값을 싣는 경우가 있다).
 */
final class BatchErrorText {

    static final int MAX_LENGTH = 500;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|secret|token|apikey|api_key|authorization)\\s*[=:]\\s*\\S+");
    private static final Pattern KEY_VALUE_DETAIL = Pattern.compile("\\(([^)]*)\\)=\\(([^)]*)\\)");

    private BatchErrorText() {
    }

    static String of(Throwable error) {
        Throwable root = error;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 20; depth++) {
            root = root.getCause();
        }
        String name = root.getClass().getSimpleName();
        String message = root.getMessage();
        String text = message == null || message.isBlank() ? name : name + ": " + firstLine(message);
        text = EMAIL.matcher(text).replaceAll("(hidden)");
        text = SECRET.matcher(text).replaceAll("$1=(hidden)");
        text = KEY_VALUE_DETAIL.matcher(text).replaceAll("($1)=(hidden)"); // PostgreSQL 의 「Key (칸)=(값)」 형태에 실리는 값
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…";
    }

    private static String firstLine(String message) {
        String stripped = message.strip();
        int end = stripped.indexOf('\n');
        return end < 0 ? stripped : stripped.substring(0, end).strip();
    }
}
