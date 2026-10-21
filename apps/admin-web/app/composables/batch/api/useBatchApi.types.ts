/**
 * 배치 API 가 주고받는 모양 — 서버 DTO(`BatchJobView`·`BatchLogView`·`BatchLogPage`, `BatchToggleRequest`·`BatchScheduleRequest`)와 같은 칸이다.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 어디서 도는 배치인가 — `JAVA` 관리자 서버의 스케줄러(cron 은 Spring 6칸) · `POSTGRESQL` DB 의 저장 프로시저를 pg_cron 이 부른다(cron 은 5칸) */
export type BatchSource = 'JAVA' | 'POSTGRESQL';

/** 배치가 속한 묶음 — `MEMBER` 회원 관련 · `SYSTEM` 시스템 관리 */
export type BatchCategory = 'MEMBER' | 'SYSTEM';

/** 실행 결과 — `RUNNING` 실행 중 · `OK` 성공 · `FAIL` 실패 · `SKIP` 할 일 없음(처리 건수 0) */
export type BatchStatus = 'RUNNING' | 'OK' | 'FAIL' | 'SKIP';

/** 실행을 시작한 방식 — `SCHEDULED` 예약(cron) · `MANUAL` 운영자가 「지금 실행」 */
export type BatchTrigger = 'SCHEDULED' | 'MANUAL';

export interface BatchJobView {
    /** 배치 키 — 예: `grade-promotion` */
    jobName: string;
    /** 서버가 요청 언어로 풀어 준 이름 */
    displayName: string;
    /** 서버가 요청 언어로 풀어 준 설명 */
    description: string | null;
    /** 항상 null — 이름이 이미 번역돼 있다 */
    i18n: string | null;
    /** 항상 null — 설명이 이미 번역돼 있다 */
    descI18n: string | null;
    category: BatchCategory;
    source: BatchSource;
    /** 지금 쓰는 cron — `JAVA` 는 Spring 6칸(초 분 시 일 월 요일), `POSTGRESQL` 은 pg_cron 5칸(분 시 일 월 요일). 시간대는 `scheduleZone`. 쓸 수 없는 DB 배치는 기본 cron(모르면 빈 글) */
    schedule: string;
    /** 처음(코드) 값 — `schedule` 과 같은 문법 */
    defaultSchedule: string;
    enabled: boolean;
    /** 다음 실행 예정 — UTC ISO. 꺼져 있거나 쓸 수 없거나 더는 실행되지 않으면 null */
    nextRunAt: string | null;
    /** 마지막 실행 시작 시각 — UTC ISO. 한 번도 안 돌았으면 null */
    lastRunAt: string | null;
    /** `RUNNING` · `OK` · `FAIL` · `SKIP`. 한 번도 안 돌았으면 null */
    lastStatus: string | null;
    /** 마지막 실행의 처리 건수. 모르면 null */
    lastAffectedCount: number | null;
    /** 마지막 실행의 요약(실패면 사유) — 서버가 요청 언어로 풀어 준다 */
    lastMessage: string | null;
    /** 지금 실행 중인가 */
    running: boolean;
    /** 지금 실행할 수 있는가 — `JAVA` 는 항상 true, `POSTGRESQL` 은 `available` 과 같다 */
    runnable: boolean;
    /** 쓸 수 있는가 — `JAVA` 는 항상 true. `POSTGRESQL` 은 pg_cron 을 쓸 수 없거나 DB 에 등록돼 있지 않으면 false(이때 켜기/끄기·주기 변경·지금 실행은 409 `BATCH_UNAVAILABLE`) */
    available: boolean;
    /** `available=false` 인 이유 — 서버가 요청 언어로 풀어 준 글. 쓸 수 있으면 null */
    unavailableReason: string | null;
    /** `schedule` 을 해석하는 시간대(IANA 이름) — 예: `Asia/Seoul`. `JAVA` 는 서버 설정, `POSTGRESQL` 은 pg_cron 의 `cron.timezone`(설정이 없으면 `GMT`) */
    scheduleZone: string | null;
}

export interface BatchLogView {
    logId: string;
    jobName: string;
    /** 배치 이름 — 카탈로그에서 사라진 배치는 키 그대로 */
    displayName: string;
    /** 그 줄을 만든 쪽 — `JAVA` 관리자 서버 · `POSTGRESQL` DB(저장 프로시저) */
    source: BatchSource;
    trigger: BatchTrigger;
    /** 수동 실행한 운영자 이름. 예약이거나 운영자가 지워졌으면 null(DB 가 돌린 줄은 수동이어도 비어 있을 수 있다) */
    operatorName: string | null;
    /** 시작 시각 — UTC ISO */
    startedAt: string;
    /** 끝난 시각 — UTC ISO. 아직 안 끝났으면 null */
    finishedAt: string | null;
    status: string;
    /** 몇 건을 다뤘나. 끝나지 않았거나 센 값이 없으면 null — 0 과 "안 셈"은 다르다. 실패한 DB 배치 줄은 실패하기 전까지 처리한 건수일 수 있다 */
    affectedCount: number | null;
    /** 성공·건너뜀이면 결과 요약, 실패면 사유 */
    message: string | null;
}

export interface BatchLogPage {
    items: BatchLogView[];
    total: number;
    /** 0 부터 */
    page: number;
    size: number;
}

export interface BatchLogQuery {
    jobName?: string;
    status?: string;
    trigger?: string;
    /** 시작 시각의 하한 — UTC ISO 순간 */
    from?: string;
    /** 시작 시각의 상한 — UTC ISO 순간 */
    to?: string;
    /** 0 부터 */
    page?: number;
    /** 1~100 */
    size?: number;
}

/** 켜고 끄기 요청 */
export interface BatchToggleRequest {
    enabled: boolean;
}

/** 주기(cron) 변경 요청 — 문법은 배치의 `source` 가 정한다(`JAVA` 6칸 · `POSTGRESQL` 5칸). 판정은 서버가 하고, 틀리면 400 `fields.schedule` */
export interface BatchScheduleRequest {
    schedule: string;
}
