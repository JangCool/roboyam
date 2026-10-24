package com.community.dm.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * 쪽지 규칙 — <b>서버 상수</b>다(사용자 결정 2026-10-24 「추천대로」: community {@code api-dm.md} 와 같은 값, 운영자가 바꿀 필요가 생기면 사이트 설정으로 옮긴다).
 * 화면은 {@code GET /api/me/dm} 의 {@code rules}·{@code quota} 로 같은 값을 받는다. <b>하루는 한국 시간 0시에 시작</b>한다.
 * 1년 보관은 DB 배치 {@code dm-retention}(R__batch_21)의 {@code retention_days} 기본값과 같다.
 */
public final class DmRules {

    public static final int DAILY_LIMIT = 20;
    public static final int NEW_PARTNER_LIMIT = 5;
    public static final int MIN_ACCOUNT_AGE_DAYS = 7;
    public static final Duration SEND_INTERVAL = Duration.ofSeconds(3);
    public static final int BODY_MAX = 500;
    public static final int RETENTION_DAYS = 365;
    /** 안 읽은 대화 수를 세는 상한 */
    public static final int UNREAD_CAP = 100;
    public static final int RECIPIENT_LIMIT = 10;
    public static final int SEARCH_MIN = 2;
    public static final int SEARCH_MAX = 16;
    public static final int PREVIEW_CHARS = 40;
    public static final int INBOX_SIZE = 20;
    public static final int ROOM_SIZE = 30;
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 줄바꿈(\n)만 빼고 거절하는 제어 문자 — C0·DEL·C1 과 글자 방향을 뒤집는 문자(user-web {@code UtilDmRules} 와 같다) */
    private static final Pattern INVALID_CHAR = Pattern.compile("[\\u0000-\\u0009\\u000B-\\u001F\\u007F-\\u009F\\u202A-\\u202E\\u2066-\\u2069]");

    private DmRules() {
    }

    /** 오늘(한국 시간) */
    public static LocalDate today(Instant now) {
        return now.atZone(ZONE).toLocalDate();
    }

    /** 다음 한국 시간 0시까지 남은 초 */
    public static long secondsToReset(Instant now) {
        Instant next = today(now).plusDays(1).atStartOfDay(ZONE).toInstant();
        return Math.max(0, Duration.between(now, next).toSeconds());
    }

    /** 본문 다듬기 — \r\n·\r 은 \n, 탭은 공백, 앞뒤 공백을 자른다(user-web {@code UtilDmRules.normalize}) */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').strip();
    }

    /** 본문 문제 — 비었음 → 글자 수 → 쓸 수 없는 문자 차례. 없으면 null */
    public static String bodyProblem(String body) {
        if (body.isEmpty()) {
            return "dm.body.required";
        }
        if (body.codePointCount(0, body.length()) > BODY_MAX) {
            return "dm.body.max";
        }
        if (INVALID_CHAR.matcher(body).find()) {
            return "dm.body.invalidChar";
        }
        return null;
    }

    /** 쪽지함 미리보기 — 줄바꿈·연속 공백은 한 칸, 앞 40자, 넘으면 … */
    public static String preview(String body) {
        String flat = body == null ? "" : body.replaceAll("\\s+", " ").strip();
        int count = flat.codePointCount(0, flat.length());
        return count <= PREVIEW_CHARS ? flat : flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_CHARS)) + "…";
    }
}
