/**
 * 통계 수집 설정 — `GET/PUT /api/admin/analytics/settings` 의 모양(서버 `AnalyticsSettingsView`·`AnalyticsSettingsRequest` — `docs/features/site-analytics-contract.md` 「API (확정)」).
 * **모든 수집은 기본 꺼짐**(사용자 결정 2026-11-04 「처음부터 다 수집하지 않고 필요하면 켤 수 있게」). 관리자가 항목별로 켠다.
 * 방문 하위 항목(`visits`·`rawEvents`·`referrers`·`flows`)은 `enabled` 가 꺼지면 동작하지 않는다(값은 그대로 둔다 — 다시 켜면 예전 선택대로).
 */
import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';

/**
 * 방문 원본 기록 보관 기간(개월, **이번 달 포함**) — 고를 수 있는 값(기본 2 = 이번 달 + 지난달). 3단계 결정 ①: 달 조각 단위로 지운다(일 단위 30·90·180·365 를 대신한다).
 * 서버는 옛 칸 `raw_retention_days` 를 한 버전 더 두지만(롤링 배포) 화면은 새 칸만 읽고 보낸다. 옛 값 옮기기 30→2 · 90→3 · 180→6 · 365→12 는 서버 마이그레이션.
 */
export const ANALYTICS_RETENTION_MONTHS = [2, 3, 6, 12] as const;
export type AnalyticsRetentionMonths = (typeof ANALYTICS_RETENTION_MONTHS)[number];
/** 기본 보관 개월 수 */
export const ANALYTICS_DEFAULT_RETENTION_MONTHS: AnalyticsRetentionMonths = 2;

/** 켜고 끄는 칸 — 화면 차례(서버 설정 이력 칸 차례와 같다). `environment`·`searchTerms`·`bots` 는 2단계 */
export const ANALYTICS_SWITCHES = ['enabled', 'visits', 'rawEvents', 'referrers', 'flows', 'environment', 'searchTerms', 'bots', 'notificationSends', 'boardActivity'] as const;
export type AnalyticsSwitch = (typeof ANALYTICS_SWITCHES)[number];

/** 방문 수집(`enabled`)이 켜져야 동작하는 칸 */
export const ANALYTICS_VISIT_SWITCHES = ['visits', 'rawEvents', 'referrers', 'flows', 'environment', 'searchTerms', 'bots'] as const satisfies readonly AnalyticsSwitch[];

/** 저장 값(`PUT` 본문과 같은 칸) */
export interface AnalyticsSettingsRequest {
    /** 방문 수집 전체 — 끄면 사용자 브라우저가 방문 신호를 아예 보내지 않는다 */
    enabled: boolean;
    /** 방문자·페이지뷰·지금 접속(대시보드 「방문 현황」) · 페이지별 순위(「인기 페이지」 — 3단계) · 주·월 순방문자 — 서버가 1분마다 몰아 쓴다 */
    visits: boolean;
    /** 방문 원본 기록(실시간 분당·최근 페이지의 원천 · 나중 분석용) — 3단계부터 페이지별 순위는 `visits` 에 딸린다 */
    rawEvents: boolean;
    /** 원본 기록 보관 기간(개월, 이번 달 포함) */
    rawRetentionMonths: AnalyticsRetentionMonths;
    /** 유입 경로(「유입 경로」 가젯) */
    referrers: boolean;
    /** 이동 흐름(다음 단계 「방문 분석」 화면) */
    flows: boolean;
    /** 환경 — 세션 첫 페이지 신호의 브라우저·OS·화면 너비 분류값(원본 UA 는 저장하지 않는다) + 서버가 UA 로 가른 기기(「방문자 통계 › 환경」) */
    environment: boolean;
    /** 우리 사이트 검색어 — 서버가 검색 요청에서 센다(전화번호·이메일·주민등록번호 모양·2글자 미만·50자 초과는 저장하지 않는다, 「방문자 통계 › 검색어」) */
    searchTerms: boolean;
    /** 봇·크롤러 요청 수 — 서버가 요청의 UA 로 센다(「방문자 통계 › 봇·크롤러」) */
    bots: boolean;
    /** 알림 발송 기록(「알림 상태」 가젯) */
    notificationSends: boolean;
    /** 게시판 활동 집계(「글·댓글 활동」「가입 추이」「활발한 게시판」 — DB 크론 10분) */
    boardActivity: boolean;
}

/** `GET`·`PUT` 응답 — 저장 값 + 마지막 변경 */
export interface AnalyticsSettingsView extends AnalyticsSettingsRequest {
    /** 마지막으로 바꾼 시각(ISO) — 바꾼 적 없으면 null */
    updatedAt: string | null;
    /** 마지막으로 바꾼 운영자 이름 — 바꾼 적 없으면 null */
    updatedBy: string | null;
}

/** 변경 이력 한 줄 — 공용 설정 이력 모양 그대로(`GET /api/admin/analytics/settings/history`) */
export type AnalyticsSettingHistoryItem = SettingHistoryItem;
export type AnalyticsSettingHistoryPage = SettingHistoryPage<AnalyticsSettingHistoryItem>;
