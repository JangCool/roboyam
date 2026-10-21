/** 새 알림 토스트의 본문 — `useNotification` 이 만들어 PrimeVue Toast 의 `detail` 에 싣고, 셸(`layouts/default.vue`)의 틀이 그린다. */
export interface NotificationToastDetail {
    /** 알림 제목 줄들 (최대 3) */
    lines: string[];
    /** 한 건이면 그 본문 */
    summary: string | null;
    /** 「외 N건」 */
    more: string | null;
}

/** OS 알림 권한. 브라우저가 알림을 모르면 `unsupported` */
export type OsNotificationPermission = NotificationPermission | 'unsupported';
