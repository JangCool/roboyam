// MOCK: 관리자 알림 API(`GET /api/admin/notifications`, `POST …/read-all`, `POST …/{id}/read`)의 메모리 목업 — `useNotificationApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `shell/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notification-error   알림 목록 요청이 `500 INTERNAL` 로 실패
//   notification-empty   알림이 하나도 없는 상태
//   notification-new     화면을 연 5초 뒤부터 20초마다 새 알림이 하나씩 도착한다(토스트·OS 알림 확인용)
//
// ── 새 알림 도착 진입점 ─
//   개발자 도구 콘솔에서 `__mockNotify()` (여러 건은 `__mockNotify(3)`) — 새 알림이 지금 도착한 것처럼 `useNotification().receive` 를 부른다.
//   서버가 붙으면 도착 경로(폴링/푸시)가 같은 `receive` 를 부른다(`useNotificationApi().subscribe` 의 REAL 쪽).
//
// 문구는 서버가 요청 언어로 만들어 주는 값이라 번역 키로 옮기지 않았다(`.claude/guides/frontend/i18n.md` 5장). 읽음 표시는 새로고침하면 처음으로 돌아간다.

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { NotificationItem, NotificationListResponse } from '~/composables/shell/api/useNotificationApi.types';

const MINUTE = 60_000;
const ago = (minutes: number): string => new Date(Date.now() - minutes * MINUTE).toISOString();

/** 처음 상태 — 안 읽은 알림 3건 + 읽은 알림 3건 */
let items: NotificationItem[] = [
    { id: '9001', kind: 'warning', title: '로그인 실패가 반복됐어요', body: 'locked@example.com 계정이 5회 연속 실패해 10분간 잠겼어요.', createdAt: ago(4), read: false },
    { id: '9002', kind: 'info', title: '운영진 계정이 추가됐어요', body: '박운영 님이 운영진으로 등록됐어요.', createdAt: ago(55), read: false },
    { id: '9003', kind: 'success', title: '백업을 마쳤어요', body: '오늘 새벽 정기 백업이 정상적으로 끝났어요.', createdAt: ago(60 * 7), read: false },
    { id: '9004', kind: 'info', title: '사이트 설정이 변경됐어요', body: null, createdAt: ago(60 * 26), read: true },
    { id: '9005', kind: 'warning', title: '저장 공간이 80%를 넘었어요', body: '여유 공간을 확인해 주세요.', createdAt: ago(60 * 50), read: true },
    { id: '9006', kind: 'success', title: '관리자 화면이 업데이트됐어요', body: null, createdAt: ago(60 * 24 * 5), read: true },
];

/** 새로 도착하는 알림의 예시 — 돌아가며 쓴다 */
const ARRIVALS: Pick<NotificationItem, 'kind' | 'title' | 'body'>[] = [
    { kind: 'info', title: '새 운영진 초대가 수락됐어요', body: '최운영 님이 초대를 수락했어요.' },
    { kind: 'warning', title: '로그인 실패가 반복됐어요', body: '알 수 없는 주소에서 5회 연속 실패했어요.' },
    { kind: 'success', title: '백업을 마쳤어요', body: '수동 백업이 정상적으로 끝났어요.' },
];
let arrivalSeq = 0;

function createArrival(): NotificationItem {
    const sample = ARRIVALS[arrivalSeq % ARRIVALS.length]!;
    arrivalSeq++;
    return { id: String(9100 + arrivalSeq), ...sample, createdAt: new Date().toISOString(), read: false };
}

type MockWindow = Window & { __mockNotify?: (count?: number) => void };

/**
 * 새 알림 도착을 흉내 낸다 — 도착하면 `onArrive` 를 부른다. 목록(메모리)에도 더해 두어 서랍을 다시 열어도 보인다.
 * 주소 스위치 `notification-new` 가 있으면 일정 간격으로 스스로 도착하고, 없어도 콘솔의 `__mockNotify()` 로 부를 수 있다.
 * @returns 구독을 끝내는 함수.
 */
export function mockNotificationSubscribe(onArrive: (items: NotificationItem[]) => void): () => void {
    const emit = (count = 1) => {
        const created = Array.from({ length: Math.max(1, count) }, createArrival);
        items = [...created, ...items];
        onArrive(created.map((item) => ({ ...item })));
    };
    const target = window as MockWindow;
    target.__mockNotify = emit;

    if (!mockFlags().has('notification-new')) {
        return () => delete target.__mockNotify;
    }
    const first = setTimeout(() => emit(), 5000);
    const loop = setInterval(() => emit(), 20000);
    return () => {
        clearTimeout(first);
        clearInterval(loop);
        delete target.__mockNotify;
    };
}

export async function mockNotificationList(): Promise<NotificationListResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('notification-error')) throw MOCK_ERRORS.internal();
    if (flags.has('notification-empty')) return { items: [] };
    return { items: items.map((item) => ({ ...item })) };
}

export async function mockNotificationRead(id: string): Promise<void> {
    await mockDelay();
    items = items.map((item) => (item.id === id ? { ...item, read: true } : item));
}

export async function mockNotificationReadAll(): Promise<void> {
    await mockDelay();
    items = items.map((item) => ({ ...item, read: true }));
}
