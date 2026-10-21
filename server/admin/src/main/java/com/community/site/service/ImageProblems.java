package com.community.site.service;

import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Locale;

/**
 * 이미지 올리기 거절(400 {@code VALIDATION}, {@code fields.file})을 만드는 모음 — 로고·사이트 아이콘·링크 미리보기 이미지가 같은 문구(프론트 {@code util-site-image-messages.ts} 와 같은 말)를 쓴다.
 * 인자는 모두 문자열이다(1000 이상의 수에 쉼표가 붙지 않게). 파일 이름·내용은 문구에 싣지 않고 규칙과 지금 크기·용량만 말한다.
 */
final class ImageProblems {

    /** 모든 이미지 올리기가 쓰는 용량 한도(바이트) — 2MB */
    static final long MAX_BYTES = 2L * 1024 * 1024;
    static final int MAX_MB = 2;
    /** 이미지 한 변의 상한(px) */
    static final int MAX_SIDE = 4000;

    private ImageProblems() {
    }

    static ValidationException file(String key, Object... args) {
        return ValidationException.of("file", new MessageRef(key, args));
    }

    static ValidationException empty() {
        return file("site.image.empty");
    }

    static ValidationException corrupt() {
        return file("site.image.corrupt");
    }

    /** 용량이 넘쳤다 — 지금 용량은 소수 한 자리 MB 로 올린다(한도를 막 넘은 파일이 "지금 2.0MB" 로 보이지 않게) */
    static ValidationException tooHeavy(long bytes) {
        return file("site.image.size", String.valueOf(MAX_MB), megabytes(bytes));
    }

    static ValidationException tooBig(int width, int height) {
        return file("site.image.tooBig", String.valueOf(MAX_SIDE), String.valueOf(width), String.valueOf(height));
    }

    /** 소수 한 자리 MB 올림 */
    static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f", Math.ceil(bytes / (1024.0 * 1024.0) * 10) / 10);
    }
}
