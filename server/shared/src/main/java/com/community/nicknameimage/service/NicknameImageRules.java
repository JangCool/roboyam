package com.community.nicknameimage.service;

import java.time.Duration;
import java.util.List;

/**
 * 닉네임 그림 규칙 — 서버 상수(community {@code api-nickname-image.md} 1절과 같은 값, 아이템샵 변경권은 없다). 회원 화면(api)과 운영자 검토(admin)가 같이 쓴다.
 * 화면은 {@code GET /api/me/nickname-image} 의 {@code limits} 로 같은 값을 받는다(판정은 늘 서버). 하루는 한국 시간 0시에 시작한다.
 */
public final class NicknameImageRules {

    /** 올리는 파일 200KB */
    public static final int MAX_BYTES = 200 * 1024;
    /** 저장 크기(4:1) — 더 큰 그림은 줄이고 작은 그림은 키우지 않는다 */
    public static final int WIDTH = 240;
    public static final int HEIGHT = 60;
    public static final String RATIO = "4:1";
    /** 가로/세로 = 4 ± 1% */
    public static final double RATIO_MIN = 3.96;
    public static final double RATIO_MAX = 4.04;
    public static final int MIN_WIDTH = 120;
    public static final int MIN_HEIGHT = 30;
    public static final long MAX_PIXELS = 4_000_000L;
    public static final int MAX_GIF_FRAMES = 30;
    public static final List<String> FORMATS = List.of("PNG", "JPG", "GIF", "WebP");
    /** 반려 뒤 다시 신청까지 */
    public static final int REJECTED_REAPPLY_HOURS = 72;
    /** 운영자 해제 뒤 다시 신청까지(community 「운영 해제 7일」) */
    public static final Duration REVOKED_REAPPLY = Duration.ofDays(7);
    /** 올리기 간격 · 하루 횟수 */
    public static final Duration UPLOAD_INTERVAL = Duration.ofSeconds(10);
    public static final int DAILY_UPLOADS = 10;
    /** 반려·해제 사유 글자 수 */
    public static final int REASON_MAX = 200;

    private NicknameImageRules() {
    }
}
