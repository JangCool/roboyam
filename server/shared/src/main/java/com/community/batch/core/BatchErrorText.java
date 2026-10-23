package com.community.batch.core;

import java.util.regex.Pattern;

/**
 * 배치가 실패한 사유를 실행 로그·실패 건·워커 기록에 남길 한 줄 글로 만든다 — <b>가장 깊은 원인의 예외 이름 + 첫 줄</b>, 500자까지. 전체 스택트레이스는 서버 로그에만 남긴다.
 * 예외 문구에 섞일 수 있는 이메일·비밀번호·토큰 같은 값은 가린다(DB 제약 위반 문구가 행의 값을 싣는 경우가 있다). DB 쪽 같은 규칙은 {@code batch_error_text}(R__batch_00_helpers.sql).
 */
public final class BatchErrorText {

    public static final int MAX_LENGTH = 500;

    /** 오류 코드 칸의 길이 */
    public static final int MAX_CODE_LENGTH = 80;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|secret|token|apikey|api_key|authorization)\\s*[=:]\\s*\\S+");
    private static final Pattern KEY_VALUE_DETAIL = Pattern.compile("\\(([^)]*)\\)=\\(([^)]*)\\)");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");

    private BatchErrorText() {
    }

    public static String of(Throwable error) {
        Throwable root = rootOf(error);
        String name = root.getClass().getSimpleName();
        String message = root.getMessage();
        return mask(message == null || message.isBlank() ? name : name + ": " + firstLine(message));
    }

    /** 실패 건의 사유 — 예외 이름 없이 문구만(문구가 없으면 예외 이름), 가려서 */
    public static String messageOf(Throwable error) {
        Throwable root = error instanceof BatchItemException ? error : rootOf(error);
        String message = root.getMessage();
        return mask(message == null || message.isBlank() ? root.getClass().getSimpleName() : firstLine(message));
    }

    /** 실패 건의 오류 코드 — {@link BatchItemException} 이면 그 코드, 아니면 가장 깊은 원인의 클래스 이름 */
    public static String codeOf(Throwable error) {
        if (error instanceof BatchItemException item && item.code() != null && CODE.matcher(item.code()).matches()) {
            return item.code();
        }
        String name = rootOf(error).getClass().getSimpleName();
        if (name.isEmpty()) {
            name = "Error";
        }
        return name.length() <= MAX_CODE_LENGTH ? name : name.substring(0, MAX_CODE_LENGTH);
    }

    /** 이미 만든 글을 한 번 더 가린다(작업이 넘긴 문구 등) */
    public static String mask(String text) {
        String masked = EMAIL.matcher(text).replaceAll("(hidden)");
        masked = SECRET.matcher(masked).replaceAll("$1=(hidden)");
        masked = KEY_VALUE_DETAIL.matcher(masked).replaceAll("($1)=(hidden)"); // PostgreSQL 의 「Key (칸)=(값)」 형태에 실리는 값
        return masked.length() <= MAX_LENGTH ? masked : masked.substring(0, MAX_LENGTH - 1) + "…";
    }

    private static Throwable rootOf(Throwable error) {
        Throwable root = error;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 20; depth++) {
            root = root.getCause();
        }
        return root;
    }

    private static String firstLine(String message) {
        String stripped = message.strip();
        int end = stripped.indexOf('\n');
        return end < 0 ? stripped : stripped.substring(0, end).strip();
    }
}
