// MOCK: 알림·쪽지 API 의 메모리 목업 — 안 읽은 알림/쪽지 수와 알림 패널의 최근 알림. `useNotificationApi.ts`·`useDmApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `notification/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notify-none       안 읽은 알림·쪽지 수 0 (배지 없음) — 기본은 알림 3개·쪽지 2개
//   notify-many       안 읽은 알림 100개 (상한, "99+" 배지)

import { mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MockNotification } from '~/composables/notification/api/useNotificationApi.types';

function unread(base: number): number {
    const on = mockFlags();
    if (on.has('notify-none')) return 0;
    if (on.has('notify-many')) return 100;
    return base;
}

export async function mockNotificationUnreadCount(): Promise<{ count: number }> {
    await mockDelay();
    return { count: unread(3) };
}

export async function mockDmUnreadCount(): Promise<{ count: number }> {
    await mockDelay();
    return { count: unread(2) };
}

/* ── 알림 패널의 최근 알림 ── */

export async function mockRecentNotifications(): Promise<MockNotification[]> {
    await mockDelay();
    if (mockFlags().has('notify-none')) return [];
    return [
        { id: 'mock-n1', text: '내 글에 새 댓글이 달렸어요', time: '방금', unread: true },
        { id: 'mock-n2', text: '내 댓글에 답글이 달렸어요', time: '5분 전', unread: true },
        { id: 'mock-n3', text: '회원님의 글이 인기글에 올랐어요', time: '어제', unread: false },
    ];
}
