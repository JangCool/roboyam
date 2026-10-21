/**
 * 알림 API 가 주고받는 모양 중 알림 패널의 최근 알림 한 줄 — 임시 모양이다. 알림 화면을 만들 때 원본(community)의 `NotificationItem` 으로 바꾼다.
 */

export interface MockNotification {
    id: string;
    text: string;
    time: string;
    unread: boolean;
}
