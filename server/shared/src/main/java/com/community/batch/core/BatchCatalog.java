package com.community.batch.core;

import com.community.batch.entity.BatchSource;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 배치 카탈로그 — <b>배치 정의의 원천은 코드</b>다(권한 카탈로그와 같은 방식). 두 종류가 한 목록에 있다.
 *
 * <ul>
 *   <li>{@link BatchSource#JAVA} — 서버가 뜰 때 {@code batch_jobs} 표와 맞춘다(없는 배치만 기본값으로 넣는다). 새 배치는 <b>① 여기에 한 줄 ② {@code BatchJob} 구현 하나(빈)
 *       ③ 메시지 번들 두 곳에 이름·설명</b>으로 더한다. 카탈로그와 구현이 짝이 맞지 않으면 서버가 뜨지 않는다({@code BatchRegistry}). cron 은 Spring 6칸 식(초 분 시 일 월 요일),
 *       시간대는 {@code roboyam.batch.zone}(Asia/Seoul)이다.</li>
 *   <li>{@link BatchSource#POSTGRESQL} — DB 의 저장 프로시저를 pg_cron 이 부른다. 새 배치는 <b>① 여기에 한 줄(이름·분류) ② 반복 마이그레이션
 *       {@code R__batch_<번호>_<이름>.sql}(프로시저 {@code batch_<키의 하이픈을 밑줄로>} 정의 + {@code batch_define} 로 기본 cron 등록) ③ 메시지 번들 두 곳</b>이다.
 *       사용 여부·현재 cron 은 pg_cron({@code cron.job})이 원천이고, 기본 cron 은 마이그레이션이 {@code batch_db_jobs} 에 둔다 — 그래서 여기에는 cron 이 없다.
 *       카탈로그에는 있는데 DB 에 정의·cron 이 없으면 목록에 「등록되지 않음」으로 보인다.</li>
 * </ul>
 * 키({@link #key()})는 한 번 정하면 바꾸지 않는다 — 설정 행·실행 로그·화면이 이 값으로 배치를 가리킨다. 이름·설명의 문구는 {@code batch.job.<키>.name} ·
 * {@code batch.job.<키>.desc} 번들 키다. Java 배치의 단계·파라미터 이름은 {@code batch.job.<키>.step.<단계>} · {@code batch.job.<키>.param.<이름>}(+ {@code .help}) 이고
 * 없으면 공통 이름({@code batch.step.<단계>})이나 키 그대로 보인다. 실행 요청·재실행·단계·건별 실패는 {@code BatchRunner}·{@code BatchRequestExecutor} 가 맡는다.
 */
public enum BatchCatalog implements BatchDefinition {

    /**
     * 가입 일수 조건 등급 승급 — <b>DB 배치</b>, 기본 매일 04:30 KST(정책 문서 미확정 11 의 가정, 기본 cron 은 {@code R__batch_15_grade_promotion.sql} 이 가진다). 예전에는 Java 배치였다(키를 이어 쓴다).
     * 같은 판정이 Java({@code GradePromotionService.promoteAll} — 등급 저장 직후 소급 승급)에도 있다 — 한쪽을 고치면 다른 쪽도 고친다. <b>업무 배치</b>라 pg_cron 이 없으면 승급이 멈춘다({@link #isBusinessCritical}).
     */
    GRADE_PROMOTION("grade-promotion", BatchCategory.MEMBER, BatchSource.POSTGRESQL, null, true),

    /** 만료된 이메일 인증·비밀번호 재설정 토큰 삭제 — DB 배치, 기본 매일 03:00 KST */
    AUTH_TOKEN_CLEANUP("auth-token-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 오래된 로그인 실패 기록 삭제(공개·관리자) — DB 배치, 기본 매일 03:10 KST */
    LOGIN_ATTEMPT_CLEANUP("login-attempt-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 만료·수락된 운영자 초대 삭제 — DB 배치, 기본 매일 03:20 KST */
    OPERATOR_INVITATION_CLEANUP("operator-invitation-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 보관 분류별 기간(일반 1년 · 개인정보 열람 2년 · 돈 관련 5년, 하한 있음 — commerce.md 13-4)이 지난 감사 로그 삭제 — DB 배치, 기본 매일 03:30 KST */
    AUDIT_LOG_RETENTION("audit-log-retention", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 보관 기간(기본 90일)이 지난 배치 실행 로그 삭제 — DB 배치, 기본 매일 03:40 KST. 예전에는 Java 배치였다(키를 이어 쓴다) */
    BATCH_LOG_CLEANUP("batch-log-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 관리자 알림 정리(읽은 지 30일 · 안 읽은 채 90일 · 운영자당 500건 초과) — DB 배치, 기본 매일 03:50 KST. 보관 값은 {@code batch_params} (docs/features/notification-contract.md) */
    NOTIFICATION_CLEANUP("notification-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 회원 알림 정리(탈퇴 회원의 알림 · 읽은 지 30일 · 만든 지 보관 일수(게시판 기본 설정 「알림」, 기본 90일) · 회원당 500건 초과) — DB 배치, 기본 매일 04:00 KST
     * ({@code R__batch_20_member_notification_cleanup.sql}, docs/features/member-notification-contract.md).
     */
    MEMBER_NOTIFICATION_CLEANUP("member-notification-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 관리자 2차 인증 기록 정리(기간이 끝난 신뢰 기기 · 10분 안에 끝내지 않은 OTP 등록 중 비밀키) — DB 배치, 기본 매일 04:10 KST.
     * 표가 admin 스트림(V3)이라 프로시저도 admin 스트림의 {@code R__batch_30_admin_mfa_cleanup.sql} 에 있다(docs/features/commerce-foundation-contract.md 「0-3」).
     */
    ADMIN_MFA_CLEANUP("admin-mfa-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 글 표(posts) 월 조각 유지 — DB 배치, 기본 매일 00:20 KST({@code R__batch_17_board_post_partitions.sql}). 지난달~6개월 뒤 조각이 없으면 만든다.
     * 앱이 뜰 때도 같은 함수를 부르므로({@code BoardPartitionBootstrap}) pg_cron 이 없어도 서버를 다시 띄우면 채워진다 — 그래서 정리(SYSTEM) 분류로 둔다.
     */
    BOARD_POST_PARTITIONS("board-post-partitions", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 게시판 개인정보 보유기간 적용(작성 IP·비회원 정보·삭제된 원문) — DB 배치, 기본 매일 03:30 KST({@code R__batch_18_board_privacy_retention.sql}).
     * 설정은 관리자 화면(board_privacy_settings)이 원천이고 전체 스위치가 꺼져 있으면 SKIP. 관리자 화면의 축별 「지금 실행」도 같은 DB 함수를 부른다.
     */
    BOARD_PRIVACY_RETENTION("board-privacy-retention", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 공개 게시판 글 정리 — DB 배치, 기본 매일 00:40 KST({@code R__batch_19_board_post_upkeep.sql}). 예약 삭제 시각이 지난 글의 삭제 확정 + 조회수 중복 방지 기록 정리.
     * 공개 서버는 시각이 지난 예약 삭제 글을 이미 지운 글로 보고 거르므로 늦게 돌아도 화면은 같다 — 그래서 정리(SYSTEM) 분류로 둔다.
     */
    BOARD_POST_UPKEEP("board-post-upkeep", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 24시간이 지난 멱등 키(idempotency_requests) 삭제 — DB 배치, 기본 매일 04:20 KST({@code R__batch_25_idempotency_request_cleanup.sql}, commerce-foundation-contract 「0-5」) */
    IDEMPOTENCY_REQUEST_CLEANUP("idempotency-request-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 요청 속도 카운터(request_rate_counters)의 지난 1분 창 삭제 — DB 배치, 기본 <b>매시</b> 05분 KST({@code R__batch_26_request_rate_counter_cleanup.sql}) */
    REQUEST_RATE_COUNTER_CLEANUP("request-rate-counter-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /** 끝난 지 보관 기간(기본 365일)이 지난 커머스 메일 대기열 줄 삭제 — DB 배치, 기본 매일 04:40 KST({@code R__batch_27_mail_outbox_cleanup.sql}) */
    MAIL_OUTBOX_CLEANUP("mail-outbox-cleanup", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),
    /**
     * 인기글 기간 탭(실시간·어제·이번 주) 순위 계산 — DB 배치, 기본 5분마다({@code R__batch_24_popular_rank.sql}). 순위 스냅샷({@code post_rank_snapshots})을 기간마다 통째로 바꿔 쓰고
     * 공개 서버({@code GET /api/home/popular})가 읽는다. <b>업무 배치</b>(분류 MEMBER)라 pg_cron 이 없으면 순위가 계산되지 않는다({@link #isBusinessCritical} — 기동 WARN).
     */
    POPULAR_RANK("popular-rank", BatchCategory.MEMBER, BatchSource.POSTGRESQL, null, true),

    /**
     * 탈퇴 대비·본인인증 개인정보 정리(기간이 끝난 재가입 제한 해시 · 파기 예정일이 지난 탈퇴 회원 연락처 · 30일 지난 본인인증 시도) — DB 배치, 기본 매일 04:25 KST
     * ({@code R__batch_28_member_privacy_retention.sql}, docs/features/commerce-foundation-contract.md 「0-4」).
     */
    MEMBER_PRIVACY_RETENTION("member-privacy-retention", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 데이터 키 교체 — 예전 데이터 키로 잠긴 개인정보 칸을 지금 키로 다시 암호화한다(Java 배치 {@code DataKeyRotationJob}, 칸마다 한 단계, docs/features/commerce-foundation-contract.md 「0-1」·「0-4」).
     * 키를 바꾼 뒤 수동으로 「지금 실행」하는 배치라 <b>예약은 기본 꺼짐</b>(켜면 매일 05:00).
     */
    DATA_KEY_ROTATION("data-key-rotation", BatchCategory.SYSTEM, BatchSource.JAVA, "0 0 5 * * *", false),

    /** 올리기만 하고 어디에도 지정하지 않은 채 오래된 파일(files.status = PENDING) 삭제 — Java 배치(저장소 파일을 지우므로 DB 만으로 끝나지 않는다), 하루 한 번(새벽 03:45). 기준 시간은 {@code roboyam.media.orphan-retention-hours} */
    FILE_ORPHAN_CLEANUP("file-orphan-cleanup", BatchCategory.SYSTEM, BatchSource.JAVA, "0 45 3 * * *", true),

    /**
     * 쪽지 1년 보관 정리 — DB 배치, 기본 매일 04:25 KST({@code R__batch_21_dm_retention.sql}, docs/features/member-social-contract.md 「쪽지」). 마지막 쪽지가 1년보다 오래된 대화는 통째로,
     * 남는 대화는 1년 지난 쪽지만 지운다. 신고된 대화의 보관은 신고 대상에 쪽지가 생길 때 더한다.
     */
    DM_RETENTION("dm-retention", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true),

    /**
     * 포인트 소멸 — 소멸일이 지난 무상 묶음 전부를 {@code PointService#expireLot} 로 소멸(Java 배치 {@code PointExpireJob}, 덩어리·체크포인트·「실패 지점부터 다시」, commerce.md 15-5).
     * 기본 매일 00:10 KST. 회원 포인트가 바뀌는 업무라 분류는 회원(MEMBER).
     */
    POINT_EXPIRE("point-expire", BatchCategory.MEMBER, BatchSource.JAVA, "0 10 0 * * *", true),

    /**
     * 포인트 소멸 예정 알림 — 소멸일이 {@code point_settings.expiry_notice_days}(기본 30일) 안인 무상 묶음이 있는 회원에게 회원 알림 {@code POINTS_EXPIRING} 한 건(Java 배치
     * {@code PointExpiryNoticeJob}, commerce.md 14). 낮에 받게 기본 매일 11:00 KST. 분류는 회원(MEMBER).
     */
    POINT_EXPIRY_NOTICE("point-expiry-notice", BatchCategory.MEMBER, BatchSource.JAVA, "0 0 11 * * *", true),

    /**
     * 포인트 불변식 검사(읽기만) — 회원별·종류별 원장 합 = 잔액 = 남은 묶음 합, 묶음 추적 뒤 원장 줄의 묶음 반영 합 = delta(commerce.md 5-5). 어긋나면 FAIL 로 끝나 기존 배치 실패 알림이
     * 알린다. DB 배치, 기본 매일 05:30 KST({@code R__batch_29_point_invariant_check.sql}). 고치지 않고 알리기만 하므로 정리(SYSTEM) 분류.
     */
    POINT_INVARIANT_CHECK("point-invariant-check", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true);

    private static final Pattern PROCEDURE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final String key;
    private final BatchCategory category;
    private final BatchSource source;
    private final String defaultCron;
    private final boolean defaultEnabled;

    BatchCatalog(String key, BatchCategory category, BatchSource source, String defaultCron, boolean defaultEnabled) {
        this.key = key;
        this.category = category;
        this.source = source;
        this.defaultCron = defaultCron;
        this.defaultEnabled = defaultEnabled;
    }

    public String key() {
        return key;
    }

    public BatchCategory category() {
        return category;
    }

    public BatchSource source() {
        return source;
    }

    public boolean isJava() {
        return source == BatchSource.JAVA;
    }

    /**
     * 업무가 걸린 배치인가 — 정리(SYSTEM) 배치는 하루 늦어도 쌓일 뿐이지만, 이 배치는 멈추면 회원 등급 같은 서비스 동작이 멈춘다. DB 배치인데 pg_cron 을 쓸 수 없으면 기동 때 WARN 으로 알린다
     * ({@code DbBatchGateway#prepare}). Java 폴백은 두지 않는다 — DB 배치로 하기로 한 배치다.
     */
    public boolean isBusinessCritical() {
        return source == BatchSource.POSTGRESQL && category != BatchCategory.SYSTEM;
    }

    /** Java 배치의 기본 cron(Spring 6칸). DB 배치는 null — 기본 cron 은 마이그레이션이 정한다({@code batch_db_jobs}) */
    public String defaultCron() {
        return defaultCron;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    /** 이름의 메시지 번들 키 */
    public String nameKey() {
        return "batch.job." + key + ".name";
    }

    /** 설명의 메시지 번들 키 */
    public String descriptionKey() {
        return "batch.job." + key + ".desc";
    }

    /**
     * DB 배치가 부르는 저장 프로시저 이름 — {@code batch_} + 키의 하이픈을 밑줄로 바꾼 것(예: {@code audit-log-retention} → {@code batch_audit_log_retention}).
     * 카탈로그 상수에서만 만들어지고 식별자 규칙을 지키므로 SQL 에 그대로 쓸 수 있다. Java 배치에는 없다.
     */
    public String procedureName() {
        if (source != BatchSource.POSTGRESQL) {
            throw new IllegalStateException("DB 배치가 아니다: " + key);
        }
        String name = "batch_" + key.replace('-', '_');
        if (!PROCEDURE_NAME.matcher(name).matches()) {
            throw new IllegalStateException("프로시저 이름 규칙에 맞지 않는다: " + name);
        }
        return name;
    }

    /** 키로 찾는다. 카탈로그에 없으면 null */
    public static BatchCatalog ofKey(String key) {
        for (BatchCatalog definition : values()) {
            if (definition.key.equals(key)) {
                return definition;
            }
        }
        return null;
    }

    /** Java 배치만 (카탈로그 순서) */
    public static List<BatchCatalog> javaJobs() {
        return Arrays.stream(values()).filter(BatchCatalog::isJava).toList();
    }

    /** DB 배치만 (카탈로그 순서) */
    public static List<BatchCatalog> databaseJobs() {
        return Arrays.stream(values()).filter(definition -> !definition.isJava()).toList();
    }
}
