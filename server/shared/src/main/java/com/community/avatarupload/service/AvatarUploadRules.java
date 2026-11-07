package com.community.avatarupload.service;

import java.time.Duration;
import java.util.List;

/**
 * 아바타 올리기 규칙 — 서버 상수(commerce-motion-avatar-contract 「화면 확정 때 굳힌 것」). 회원 화면(api)과 운영자 심사(admin)가 같이 쓴다.
 * 화면은 {@code GET /api/me/avatar-upload} 의 {@code limits} 로 같은 값을 받는다(판정은 늘 서버). 하루는 한국 시간 0시에 시작한다.
 */
public final class AvatarUploadRules {

    /** 올리는 파일 300KB */
    public static final int MAX_BYTES = 300 * 1024;
    /** 정사각형 — 가로/세로 = 1 ± 2% */
    public static final double RATIO_TOLERANCE = 0.02;
    public static final int RATIO_TOLERANCE_PERCENT = 2;
    /** 가장 짧은 변 96px 이상 */
    public static final int MIN_SIZE = 96;
    /** 저장 크기 — 더 큰 그림은 256px 이하로 줄이고 작은 그림은 키우지 않는다(움직이는 GIF 원본·WebP 는 그대로 — 정지 그림만 줄인다) */
    public static final int MAX_SIZE = 256;
    public static final long MAX_PIXELS = 4_000_000L;
    public static final int MAX_GIF_FRAMES = 30;
    public static final List<String> FORMATS = List.of("PNG", "JPG", "GIF", "WebP");
    /**
     * 올리기 간격 · 하루 횟수 — 계약에 없는 값이라 닉네임 이미지와 같은 보수적인 값(대기 신청을 새 신청이 대체하면 같은 변경권이 넘어가므로 한 장으로 무한히 올리지 못하게).
     */
    public static final Duration UPLOAD_INTERVAL = Duration.ofSeconds(10);
    public static final int DAILY_UPLOADS = 10;
    /** 반려·해제 사유 글자 수 */
    public static final int REASON_MAX = 200;
    /** 저장소 키 머리 */
    public static final String KEY_PREFIX = "avatar-upload/";

    private AvatarUploadRules() {
    }
}
