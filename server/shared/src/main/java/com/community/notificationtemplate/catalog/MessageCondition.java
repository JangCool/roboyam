package com.community.notificationtemplate.catalog;

import java.util.List;

/**
 * 특수 상황 규칙에 쓸 수 있는 조건 하나 — 코드가 정한 선택지만(자유 조건식 없음). 규칙은 「조건 이름 = 값」 하나이고, 발송 쪽이 렌더링 엔진에 그 상황의 값을 넘기면
 * 엔진이 위에서부터 맞는 첫 규칙의 문구를 고른다({@code MessageRenderRequest#condition}).
 *
 * <p>이름은 {@code notificationTemplate.condition.<key>}, 고정 선택지의 이름은 {@code notificationTemplate.condition.<key>.<value>} 다.
 * 선택지가 DB·다른 카탈로그에서 오는 조건({@link Source#BATCH_JOBS} · {@link Source#OPERATOR_ROLES})은 관리 API 가 그때그때 채운다.
 *
 * @param key    조건 이름 — 아래 상수
 * @param source 선택지의 출처
 * @param values 고정 선택지 값({@link Source#STATIC} 만, 그 밖은 빈 목록)
 */
public record MessageCondition(String key, Source source, List<String> values) {

    public enum Source {
        STATIC,
        /** 배치 카탈로그의 배치 키(이름은 {@code batch.job.<키>.name}) */
        BATCH_JOBS,
        /** 운영자 역할(역할 id · 이름) */
        OPERATOR_ROLES
    }

    /** 운영자 이벤트 이메일 — 받는 운영자가 기본 수신 규칙으로 받는가({@link #DEFAULT}) · 추가 수신 팀으로 받는가({@link #TEAM}) */
    public static final String RECIPIENT_SOURCE = "recipientSource";
    public static final String DEFAULT = "DEFAULT";
    public static final String TEAM = "TEAM";
    /** 배치 실패 — 배치 키(예: {@code grade-promotion}) */
    public static final String JOB_NAME = "jobName";
    /** 회원 「내 글에 댓글」 — 댓글 하나({@link #SINGLE}) · 묶인 댓글({@link #GROUPED}) */
    public static final String GROUPED = "grouped";
    public static final String SINGLE = "SINGLE";
    public static final String GROUPED_VALUE = "GROUPED";
    /** 회원 글·댓글 숨김 — 신고가 쌓여({@link #REPORTS}) · 권리 침해 임시 숨김({@link #RIGHTS}) · 운영자가 숨김({@link #STAFF} — 관리자 게시글 관리·신고 관리) */
    public static final String HIDE_REASON = "hideReason";
    public static final String REPORTS = "REPORTS";
    public static final String RIGHTS = "RIGHTS";
    public static final String STAFF = "STAFF";
    /** 회원 「내 신고의 처리 결과」 — 조치됨({@link #ACTIONED}) · 위반 아님({@link #NO_VIOLATION}) */
    public static final String REPORT_RESULT = "reportResult";
    public static final String ACTIONED = "ACTIONED";
    public static final String NO_VIOLATION = "NO_VIOLATION";
    /** 회원 이메일 인증 — 이메일 가입({@link #EMAIL}) · 소셜 가입의 미확인 이메일({@link #SOCIAL}) */
    public static final String SIGNUP_METHOD = "signupMethod";
    public static final String EMAIL = "EMAIL";
    public static final String SOCIAL = "SOCIAL";
    /** 운영자 초대 — 초대받는 역할 id(여럿이면 하나라도 맞으면) */
    public static final String INVITE_ROLE = "inviteRole";

    public String labelKey() {
        return "notificationTemplate.condition." + key;
    }

    public String optionLabelKey(String value) {
        return "notificationTemplate.condition." + key + "." + value;
    }

    static MessageCondition fixed(String key, String... values) {
        return new MessageCondition(key, Source.STATIC, List.of(values));
    }

    static MessageCondition dynamic(String key, Source source) {
        return new MessageCondition(key, source, List.of());
    }
}
