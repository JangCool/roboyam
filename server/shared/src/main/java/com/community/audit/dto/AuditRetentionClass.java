package com.community.audit.dto;

/**
 * 감사 줄의 보관 분류(docs/domain/commerce.md 13-4) — {@code audit_logs.retention_class}. 정리 배치 {@code audit-log-retention} 이 분류별 기간이 지난 줄을 지운다.
 * 기간은 {@code batch_params}(작업 {@code audit-log-retention}, 칸 {@link #paramKey()})에 있고 관리자 화면(감사 로그 › 보관 기간, 최고 관리자만)이 바꾼다.
 * 하한·상한·기본값은 {@code R__batch_13_audit_log_retention.sql} 의 {@code batch_param(…)} 범위와 같다 — 한쪽을 고치면 다른 쪽도 고친다.
 */
public enum AuditRetentionClass {

    /** 일반 운영 — 설정 변경, 게시판·메뉴 관리, 로그인 등. 1년(하한 없음 = 1일) */
    GENERAL("retention_days", 365, 1),
    /** 개인정보 열람·내려받기 — 회원 상세, 배송지 열람, 배송 명단 내려받기 등. 2년(하한 1년 — 접속기록 최소 보관) */
    PERSONAL_DATA("personal_data_retention_days", 730, 365),
    /** 돈 관련 — 환불 실행, 포인트 수동 조정, 대사 불일치 처리, 판매 켜기/끄기 등. 5년(하한 5년 — 거래 기록 보관) */
    MONEY("money_retention_days", 1825, 1825);

    /** 모든 분류의 상한(약 10년) */
    public static final int MAX_DAYS = 3650;

    /** 보관 기간이 있는 배치 */
    public static final String JOB_KEY = "audit-log-retention";

    private final String paramKey;
    private final int defaultDays;
    private final int minDays;

    AuditRetentionClass(String paramKey, int defaultDays, int minDays) {
        this.paramKey = paramKey;
        this.defaultDays = defaultDays;
        this.minDays = minDays;
    }

    public String paramKey() {
        return paramKey;
    }

    public int defaultDays() {
        return defaultDays;
    }

    public int minDays() {
        return minDays;
    }

    public int maxDays() {
        return MAX_DAYS;
    }

    /** 이 일수를 쓸 수 있는가(하한 ~ 상한) */
    public boolean allows(long days) {
        return days >= minDays && days <= MAX_DAYS;
    }
}
