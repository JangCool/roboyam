/**
 * 설정 변경 이력의 모양 — 소셜 로그인 설정 이력(`GET /api/admin/social-providers/history`)과 메일 서버 설정 이력(`GET /api/admin/mail-settings/history`)이 같은 모양이다.
 * 서버 DTO: `SocialSettingHistoryView`·`SocialSettingHistoryChangeView` / `MailSettingHistoryItemView`·`MailSettingChangeView`(소셜은 공급자 칸이 더 있다).
 * 두 업무(social·mail)가 함께 쓰므로 공용 자리(common)에 둔다 — 각 업무의 API 타입이 이것을 import 한다.
 */

/** 이력을 만든 곳 — 설치 마법사 · 관리자 화면 */
export type SettingHistorySource = 'INSTALL' | 'ADMIN';

/** 이력의 작업 — 처음 만듦 · 고침 · 지움 */
export type SettingHistoryAction = 'CREATE' | 'UPDATE' | 'DELETE';

/** 이력 한 줄의 바뀐 칸 하나 */
export interface SettingHistoryChange {
    /** 칸 이름 코드(`clientId`·`host` …) */
    field: string;
    /** 칸 이름의 i18n 키 `settingHistory.field.<field>` — 화면이 정한다 */
    labelKey: string;
    /** 전 값(만들기면 null). 비밀 값(시크릿·비밀번호)은 표시 코드만(`SET`) */
    before: string | null;
    /** 후 값(지우기면 null). 비밀 값은 `SET`(처음 넣음) · `CHANGED`(바꿈) · `CLEARED`(지움) */
    after: string | null;
}

/** 이력 한 줄 */
export interface SettingHistoryItem {
    /** Snowflake(문자열) */
    id: string;
    action: SettingHistoryAction;
    source: SettingHistorySource;
    operatorId: string | null;
    /** 바꾼 운영자 이름 — 찾을 수 없으면 서버가 요청 언어의 「삭제된 운영자」를 넣는다 */
    operatorName: string;
    operatorDeleted: boolean;
    /** UTC ISO */
    changedAt: string;
    changes: SettingHistoryChange[];
    /** 소셜 로그인 이력만 — 공급자 코드(`KAKAO` …) */
    provider?: string;
    /** 소셜 로그인 이력만 — 요청 언어의 공급자 이름 */
    providerLabel?: string;
}

/** 이력 한 쪽 — 최신이 위, `page` 는 0 부터 */
export interface SettingHistoryPage<T extends SettingHistoryItem = SettingHistoryItem> {
    items: T[];
    total: number;
    page: number;
    size: number;
}
