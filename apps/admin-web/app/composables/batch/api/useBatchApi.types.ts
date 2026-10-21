/**
 * 배치 API 가 주고받는 모양 — 서버 DTO(`BatchJobView`·`BatchLogView`·`BatchLogPage`, `BatchToggleRequest`)와 같은 칸이다.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 어디서 도는 배치인가 — 서버는 항상 `JAVA`(애플리케이션 스케줄러)다. DB 가 도는 배치는 이 제품에 없다 */
export type BatchSource = 'JAVA';

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
    /** 지금 쓰는 cron — Spring 6칸(초 분 시 일 월 요일), 서버 시간대 기준 */
    schedule: string;
    /** 처음(코드) 값 */
    defaultSchedule: string;
    enabled: boolean;
    /** 다음 실행 예정 — UTC ISO. 꺼져 있거나 더는 실행되지 않으면 null */
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
    /** 지금 실행할 수 있는가 — 서버는 항상 true */
    runnable: boolean;
}

export interface BatchLogView {
    logId: string;
    jobName: string;
    /** 배치 이름 — 카탈로그에서 사라진 배치는 키 그대로 */
    displayName: string;
    source: BatchSource;
    trigger: BatchTrigger;
    /** 수동 실행한 운영자 이름. 예약이거나 운영자가 지워졌으면 null */
    operatorName: string | null;
    /** 시작 시각 — UTC ISO */
    startedAt: string;
    /** 끝난 시각 — UTC ISO. 아직 안 끝났으면 null */
    finishedAt: string | null;
    status: string;
    /** 몇 건을 다뤘나. 끝나지 않았거나 실패면 null — 0 과 "안 셈"은 다르다 */
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
