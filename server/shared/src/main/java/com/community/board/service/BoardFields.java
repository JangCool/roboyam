package com.community.board.service;

import com.community.board.entity.BoardGadget;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 게시판 관리의 칸 정의·값 목록 — admin-web {@code util-board.ts} 의 {@code SPECS}·{@code TAB_SPECS}·상수와 <b>같아야 한다</b>(화면이 같은 범위로 미리 검사한다).
 * DB CHECK(V42__boards.sql)도 같은 범위다. 한쪽을 고치면 셋을 함께 고친다.
 */
public final class BoardFields {

    /** 칸 종류 */
    public enum Kind {
        NUMBER,
        BOOL,
        CHOICE,
        /** 글쓰기 최소 자격 — GUEST · 등급 키 · STAFF */
        WRITE_MIN,
        /** 댓글 최소 자격 — GUEST · 등급 키 · STAFF · OFF */
        COMMENT_MIN,
        /** 등급 키 */
        GRADE,
        /** 짧은 이름(댓글 추천·비추천 단추 이름) — 1~{@code max}자, 제어 문자 없음. 기본 설정에서도 null(= 화면 기본 이름)을 받는다 */
        LABEL,
        /** 여러 개 고르기(값 목록 안, 겹치지 않게 — 빈 목록 허용) — 고정 요청 확인자 */
        MULTI
    }

    /**
     * 칸 하나.
     *
     * @param overridable 게시판별로 덮어쓸 수 있다(정책 칸)
     * @param staffLimit  고치는 데 {@code board.staff.limit} 이 필요하다(아니면 {@code board.settings})
     */
    public record Spec(String field, Kind kind, int min, int max, List<Object> choices, boolean overridable, boolean staffLimit) {

        /** 값이 칸 범위·값 목록에 맞는가(최소 자격·등급 칸은 여기서 보지 않는다) */
        public boolean accepts(Object value) {
            return switch (kind) {
                case NUMBER -> value instanceof Integer number && number >= min && number <= max;
                case BOOL -> value instanceof Boolean;
                case CHOICE -> value != null && choices.contains(value);
                case MULTI -> value instanceof List<?> list && list.stream().allMatch(choices::contains) && list.stream().distinct().count() == list.size();
                case LABEL -> value instanceof String text && text.codePointCount(0, text.length()) >= min && text.codePointCount(0, text.length()) <= max
                        && text.chars().noneMatch(Character::isISOControl);
                default -> value instanceof String;
            };
        }
    }

    public static final String GUEST = "GUEST";
    public static final String STAFF = "STAFF";
    public static final String OFF = "OFF";

    public static final String KIND_GENERAL = "GENERAL";
    public static final String KIND_DEAL = "DEAL";
    public static final String KIND_PROMO = "PROMO";
    public static final String KIND_GROUP = "GROUP";
    public static final List<String> KINDS = List.of(KIND_GENERAL, KIND_DEAL, KIND_PROMO, KIND_GROUP);
    public static final List<String> LAYOUTS = List.of("LIST", "CARD", "GALLERY");
    public static final List<String> COLORS = List.of("BLUE", "TEAL", "AMBER", "PURPLE", "GREEN", "ORANGE", "SLATE", "RED", "PINK", "INDIGO", "SKY", "BROWN");
    public static final List<String> WRITE_LOCKS = List.of("POST", "POST_COMMENT");
    /** 공통 말머리 4개(표시 순서) */
    public static final List<String> COMMON_PREFIXES = List.of("QUESTION", "REVIEW", "CHAT", "NOTICE");
    /** 공통 말머리 이름(고정, 기본 언어) — 고유 말머리 이름 중복 검사에 영문 이름과 함께 쓴다 */
    public static final Map<String, String> COMMON_PREFIX_NAMES = orderedMap("QUESTION", "질문", "REVIEW", "후기", "CHAT", "잡담", "NOTICE", "공지");
    public static final Map<String, String> COMMON_PREFIX_NAMES_EN = orderedMap("QUESTION", "Question", "REVIEW", "Review", "CHAT", "Chat", "NOTICE", "Notice");
    public static final List<String> STAFF_ROLES = List.of("MANAGER", "STAFF");
    /** 글 에디터 — 평문(에디터 없음) · 간단 · 고급. 고급만 이미지·제목·코드·글자색·YouTube 를 받는다 */
    public static final String EDITOR_NONE = "NONE";
    public static final String EDITOR_SIMPLE = "SIMPLE";
    public static final String EDITOR_ADVANCED = "ADVANCED";

    public static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9-]*$");
    public static final int SLUG_MIN = 2;
    public static final int SLUG_MAX = 40;
    public static final List<String> SLUG_RESERVED = List.of("defaults", "order", "new", "writable");
    public static final int NAME_MAX = 40;
    public static final int NAME_EN_MAX = 40;
    public static final int DESCRIPTION_MAX = 200;
    public static final int TEXT_MAX = 500;
    public static final int PREFIX_NAME_MAX = 20;
    /** 사용 중인 고유 말머리 최대 수 */
    public static final int PREFIX_ACTIVE_MAX = 6;
    /** 한 회원이 맡을 수 있는 게시판 수(매니저·스텝 합산) */
    public static final int MEMBER_BOARD_MAX = 5;
    public static final int BLOCKED_WORDS_MAX = 500;
    public static final int BLOCKED_WORD_LENGTH_MAX = 30;

    /** 일반 기본 설정의 칸(화면 순서) */
    public static final Map<String, Spec> DEFAULTS;
    /** 게시판별로 덮어쓸 수 있는 칸(admin-web {@code BOARD_POLICY_FIELDS} 순서) */
    public static final List<String> POLICY_FIELDS = List.of("writeMin", "commentMin", "postEditor", "imageUploadAllowed", "commentEditor", "listSize", "pinnedMax", "indexable", "searchable",
            "inHome", "pointMode", "pointMinLengthApplied", "commentProtectMin", "commentProtectEdit", "commentProtectDelete", "commentProtectDelayMinutes", "commentGuestDelayMinutes",
            "staffHiddenNotice", "commentDislikeEnabled", "commentLikeLabel", "commentDislikeLabel", "pinRequestReviewers", "debateEnabled", "pollEnabled", "managerMax", "staffPostDailyLimit", "staffCommentDailyLimit",
            "staffSanctionDailyLimit");
    /** 댓글 추천·비추천 단추 이름 최대 글자 수(V78 varchar(10)) */
    public static final int REACTION_LABEL_MAX = 10;
    /** 의견 고정 요청을 확인하는 사람 — 글쓴이 · 그 게시판 매니저 · 운영자({@code post.manage}). 화면·저장 차례 */
    public static final String PIN_REVIEWER_POST_AUTHOR = "POST_AUTHOR";
    public static final String PIN_REVIEWER_BOARD_MANAGER = "BOARD_MANAGER";
    public static final String PIN_REVIEWER_OPERATOR = "OPERATOR";
    public static final List<String> PIN_REVIEWERS = List.of(PIN_REVIEWER_POST_AUTHOR, PIN_REVIEWER_BOARD_MANAGER, PIN_REVIEWER_OPERATOR);

    /** 고정 요청 확인자 목록 → 저장 값(쉼표로 이음, 빈 목록은 빈 글자). null 은 null */
    public static String joinReviewers(List<String> reviewers) {
        return reviewers == null ? null : String.join(",", reviewers);
    }

    /** 저장 값 → 목록(빈 글자는 빈 목록). null 은 null */
    public static List<String> splitReviewers(String stored) {
        if (stored == null) {
            return null;
        }
        return stored.isBlank() ? List.of() : List.of(stored.split(","));
    }

    /** 확인자 목록을 정해진 차례로(값이 모두 목록 안이고 겹치지 않을 때만 — 아니면 그대로 두어 검사가 잡게) */
    public static List<String> canonicalReviewers(List<String> reviewers) {
        if (reviewers == null || !reviewers.stream().allMatch(PIN_REVIEWERS::contains) || reviewers.stream().distinct().count() != reviewers.size()) {
            return reviewers;
        }
        return PIN_REVIEWERS.stream().filter(reviewers::contains).toList();
    }
    /** 하위 화면(신고·보안·알림·개인정보)의 숫자·고르기 칸 — 모두 {@code board.settings} */
    public static final Map<String, Spec> TABS;

    static {
        Map<String, Spec> d = new LinkedHashMap<>();
        choice(d, "dateDisplay", false, false, "STANDARD", "RELATIVE");
        put(d, new Spec("writeMin", Kind.WRITE_MIN, 0, 0, List.of(), true, false));
        put(d, new Spec("commentMin", Kind.COMMENT_MIN, 0, 0, List.of(), true, false));
        choice(d, "postEditor", true, false, EDITOR_NONE, EDITOR_SIMPLE, EDITOR_ADVANCED);
        bool(d, "imageUploadAllowed", true);
        choice(d, "commentEditor", true, false, EDITOR_NONE, EDITOR_SIMPLE);
        number(d, "postGapSeconds", 0, 3600, false, false);
        number(d, "postDailyLimit", 1, 1000, false, false);
        number(d, "commentGapSeconds", 0, 3600, false, false);
        number(d, "commentDailyLimit", 1, 1000, false, false);
        number(d, "listSize", 10, 100, true, false);
        number(d, "pinnedMax", 0, 5, true, false);
        bool(d, "indexable", true);
        bool(d, "searchable", true);
        bool(d, "inHome", true);
        bool(d, "attachmentPurgeEnabled", false);
        number(d, "attachmentRetentionDays", 1, 3650, false, false);
        choice(d, "pointMode", true, false, "ON", "OFF");
        bool(d, "pointMinLengthApplied", true);
        number(d, "commentProtectMin", 0, 100, true, false);
        bool(d, "commentProtectEdit", true);
        choice(d, "commentProtectDelete", true, false, "BLOCK", "DELAY", "ALLOW");
        choice(d, "commentProtectDelayMinutes", true, false, 5, 10, 30);
        choice(d, "commentGuestDelayMinutes", true, false, 0, 10, 30, 60, 180);
        bool(d, "staffHiddenNotice", true);
        bool(d, "commentDislikeEnabled", true);
        put(d, new Spec("commentLikeLabel", Kind.LABEL, 1, REACTION_LABEL_MAX, List.of(), true, false));
        put(d, new Spec("commentDislikeLabel", Kind.LABEL, 1, REACTION_LABEL_MAX, List.of(), true, false));
        put(d, new Spec("pinRequestReviewers", Kind.MULTI, 0, 0, List.of(PIN_REVIEWER_POST_AUTHOR, PIN_REVIEWER_BOARD_MANAGER, PIN_REVIEWER_OPERATOR), true, false));
        bool(d, "debateEnabled", true);
        bool(d, "pollEnabled", true);
        number(d, "bestCommentMinLikes", 1, 100, false, false);
        number(d, "managerMax", 1, 10, true, true);
        number(d, "staffMax", 1, 10, false, true);
        number(d, "staffPostDailyLimit", 1, 50, true, true);
        number(d, "staffCommentDailyLimit", 1, 100, true, true);
        number(d, "staffSanctionDailyLimit", 1, 20, true, true);
        DEFAULTS = Collections.unmodifiableMap(d);

        Map<String, Spec> t = new LinkedHashMap<>();
        // 신고 정책
        bool(t, "reportEnabled", false);
        number(t, "autoHideThreshold", 0, 100, false, false);
        choice(t, "autoHideTarget", false, false, "POST", "COMMENT", "BOTH");
        put(t, new Spec("autoHideMinGrade", Kind.GRADE, 0, 0, List.of(), false, false));
        bool(t, "autoHideExemptStaffPosts", false);
        number(t, "rightsTempMeasureDays", 1, 30, false, false);
        number(t, "dailyReportLimit", 0, 100, false, false);
        number(t, "reportCooldownSeconds", 0, 3600, false, false);
        number(t, "rejectionLimitCount", 0, 50, false, false);
        number(t, "rejectionLimitDays", 1, 365, false, false);
        number(t, "rejectionBlockDays", 1, 90, false, false);
        choice(t, "notifyOperatorScope", false, false, "PER_CASE", "PER_REPORT");
        bool(t, "notifyAuthorOnAction", false);
        bool(t, "notifyReporterOnResult", false);
        // 보안
        number(t, "viewCountDedupHours", 1, 168, false, false);
        bool(t, "blockedWordsEnabled", false);
        choice(t, "blockedWordsAction", false, false, "BLOCK", "MASK");
        number(t, "newMemberLinkLimit", 0, 20, false, false);
        number(t, "newMemberImageLimit", 0, 20, false, false);
        number(t, "duplicatePostWindowMinutes", 0, 1440, false, false);
        bool(t, "externalLinkNofollow", false);
        bool(t, "externalLinkWarn", false);
        bool(t, "guestCaptchaRequired", false);
        number(t, "guestNameMin", 1, 20, false, false);
        number(t, "guestNameMax", 1, 20, false, false);
        number(t, "guestPasswordMin", 4, 20, false, false);
        number(t, "guestLinkLimit", 0, 20, false, false);
        bool(t, "guestImageUploadAllowed", false);
        // 알림
        number(t, "groupWindowMinutes", 0, 60, false, false);
        bool(t, "emailQuietHoursEnabled", false);
        number(t, "memberNotificationRetentionDays", 7, 365, false, false);
        // 개인정보 보유기간
        bool(t, "retentionEnabled", false);
        number(t, "ipRetentionDays", 90, 3650, false, false);
        number(t, "guestRetentionDays", 0, 3650, false, false);
        choice(t, "guestMode", false, false, "ANONYMIZE", "DELETE");
        number(t, "deletedContentRetentionDays", 1, 365, false, false);
        number(t, "reportRecordRetentionDays", 90, 1825, false, false);
        number(t, "staffActionRetentionDays", 90, 1825, false, false);
        TABS = Collections.unmodifiableMap(t);
    }

    /** 개인정보 보유기간의 축 */
    public static final List<String> PRIVACY_AXES = List.of("ip", "guest", "deletedContent", "reportRecord", "staffAction");

    private BoardFields() {
    }

    /** 이 종류의 게시판이 「비회원 포함 모두」(GUEST)를 받는가 — 핫딜·직접홍보는 받지 않는다 */
    public static boolean guestAllowed(String kind) {
        return KIND_GENERAL.equals(kind) || KIND_GROUP.equals(kind);
    }

    /** 새 게시판의 가젯 노출 금지 시작값 — 핫딜·직접홍보는 인기글에서 뺀다(사용자 결정 2026-10-24), 그 밖은 없음. 만든 뒤에는 게시판 설정에서 바꾼다 */
    public static List<BoardGadget> defaultGadgetExclusions(String kind) {
        return KIND_DEAL.equals(kind) || KIND_PROMO.equals(kind) ? List.of(BoardGadget.POPULAR) : List.of();
    }

    private static void put(Map<String, Spec> map, Spec spec) {
        map.put(spec.field(), spec);
    }

    private static void number(Map<String, Spec> map, String field, int min, int max, boolean overridable, boolean staffLimit) {
        put(map, new Spec(field, Kind.NUMBER, min, max, List.of(), overridable, staffLimit));
    }

    private static void bool(Map<String, Spec> map, String field, boolean overridable) {
        put(map, new Spec(field, Kind.BOOL, 0, 0, List.of(), overridable, false));
    }

    private static void choice(Map<String, Spec> map, String field, boolean overridable, boolean staffLimit, Object... values) {
        put(map, new Spec(field, Kind.CHOICE, 0, 0, List.of(values), overridable, staffLimit));
    }

    private static Map<String, String> orderedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
