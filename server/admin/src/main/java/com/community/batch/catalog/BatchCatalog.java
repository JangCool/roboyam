package com.community.batch.catalog;

/**
 * 배치 카탈로그 — <b>배치 정의의 원천은 코드</b>다(권한 카탈로그와 같은 방식). 서버가 뜰 때 {@code batch_jobs} 표와 맞춘다(없는 배치만 기본값으로 넣는다).
 * 새 배치는 <b>① 여기에 한 줄 ② {@code BatchJob} 구현 하나(빈) ③ 메시지 번들 두 곳에 이름·설명</b>으로 더한다. 카탈로그와 구현이 짝이 맞지 않으면 서버가 뜨지 않는다
 * ({@code BatchRegistry}).
 *
 * <p>키({@link #key()})는 한 번 정하면 바꾸지 않는다 — 설정 행·실행 로그·화면이 이 값으로 배치를 가리킨다. cron 은 Spring 6칸 식(초 분 시 일 월 요일)이고
 * 시간대는 {@code roboyam.batch.zone}(Asia/Seoul)이다. 이름·설명의 문구는 {@code batch.job.<키>.name} · {@code batch.job.<키>.desc} 번들 키다.
 */
public enum BatchCatalog {

    /** 가입 일수 조건 등급 승급 — 하루 한 번. 시각은 정책 문서 미확정 11 의 가정(새벽 04:30) */
    GRADE_PROMOTION("grade-promotion", BatchCategory.MEMBER, "0 30 4 * * *", true),

    /** 보관 기간이 지난 배치 실행 로그 삭제 — 하루 한 번(새벽 03:30). 보관 일수는 {@code roboyam.batch.log-retention-days} */
    BATCH_LOG_CLEANUP("batch-log-cleanup", BatchCategory.SYSTEM, "0 30 3 * * *", true),

    /** 올리기만 하고 어디에도 지정하지 않은 채 오래된 파일(files.status = PENDING) 삭제 — 하루 한 번(새벽 03:45). 기준 시간은 {@code roboyam.media.orphan-retention-hours} */
    FILE_ORPHAN_CLEANUP("file-orphan-cleanup", BatchCategory.SYSTEM, "0 45 3 * * *", true);

    private final String key;
    private final BatchCategory category;
    private final String defaultCron;
    private final boolean defaultEnabled;

    BatchCatalog(String key, BatchCategory category, String defaultCron, boolean defaultEnabled) {
        this.key = key;
        this.category = category;
        this.defaultCron = defaultCron;
        this.defaultEnabled = defaultEnabled;
    }

    public String key() {
        return key;
    }

    public BatchCategory category() {
        return category;
    }

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

    /** 키로 찾는다. 카탈로그에 없으면 null */
    public static BatchCatalog ofKey(String key) {
        for (BatchCatalog definition : values()) {
            if (definition.key.equals(key)) {
                return definition;
            }
        }
        return null;
    }
}
