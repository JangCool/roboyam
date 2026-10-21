/**
 * 관리자 알림 API 가 주고받는 모양 — 후보 계약(확정 전).
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */

/** 알림의 성격 — 아이콘·색을 고르는 데만 쓴다 */
export type NotificationKind = 'info' | 'success' | 'warning';

export interface NotificationItem {
    id: string;
    kind: NotificationKind;
    /** 서버가 요청 언어(`Accept-Language`)로 만든 제목·본문 */
    title: string;
    body: string | null;
    /** 생긴 시각 — UTC ISO */
    createdAt: string;
    read: boolean;
}

export interface NotificationListResponse {
    items: NotificationItem[];
}
