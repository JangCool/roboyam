// MOCK: 관리자 알림 API(`GET /api/admin/notifications`, `GET …/summary`, `POST …/read-all`, `POST …/{id}/read`)의 메모리 목업 — `useNotificationApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `shell/api/mock/` 와 함께 지운다. 응답 모양은 `docs/features/notification-contract.md` 「API」(설계 확정)를 따른다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notification-error       알림 목록 요청이 `500 INTERNAL` 로 실패
//   notification-more-error  「더 보기」(두 번째 쪽부터)만 `500 INTERNAL` 로 실패
//   notification-empty       알림이 하나도 없는 상태
//   notification-few         알림이 6건뿐인 상태(한 쪽에 다 들어가 「더 보기」가 안 보인다). 기본은 45건 — 30건 + 「더 보기」 15건
//   notification-new         화면을 연 5초 뒤부터 20초마다 서버에 새 알림이 하나씩 생긴다(폴링이 가져와 토스트·OS 알림을 띄운다 — 최대 20초 걸린다)
//
// ── 새 알림 도착 진입점 ─
//   개발자 도구 콘솔에서 `__mockNotify()` (여러 건은 `__mockNotify(3)`) — 서버에 새 알림이 생긴 것으로 하고 **폴링을 바로 한 번** 돌려 20초를 기다리지 않게 한다.
//
// 문구는 서버가 요청 언어로 만들어 주는 값이라 번역 키로 옮기지 않았다(`.claude/guides/frontend/i18n.md` 5장). 읽음 표시는 새로고침하면 처음으로 돌아간다.

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { NotificationItem, NotificationKind, NotificationListQuery, NotificationListResponse, NotificationSummaryResponse } from '~/composables/shell/api/useNotificationApi.types';

const MINUTE = 60_000;
const ago = (minutes: number): string => new Date(Date.now() - minutes * MINUTE).toISOString();
const DEFAULT_SIZE = 30;

type Seed = [eventCode: string, area: string, kind: NotificationKind, title: string, body: string | null, link: string | null, minutesAgo: number, read: boolean];

/** 처음 상태 — 최근 것부터. 앞의 몇 건은 안 읽음 */
const SEEDS: Seed[] = [
    ['OPERATOR_LOCKED_BY_FAILURES', 'security', 'critical', '로그인 실패로 계정이 잠겼어요', 'locked@example.com 계정이 5회 연속 실패해 10분간 잠겼어요.', '/settings/operators', 4, false],
    ['BATCH_RUN_FAILED', 'system', 'warning', '배치가 실패했어요', '일수 승급(grade-promotion) 배치가 오류로 끝났어요.', '/settings/batch-logs?jobName=grade-promotion', 21, false],
    ['OPERATOR_INVITATION_ACCEPTED', 'system', 'info', '운영자 초대가 수락됐어요', '최운영 님이 초대를 수락했어요.', '/settings/operators', 55, false],
    ['PASSWORD_CHANGED_ME', 'security', 'warning', '비밀번호가 바뀌었어요', '내 비밀번호가 변경됐어요. 본인이 아니라면 바로 관리자에게 알려 주세요.', '/me?tab=password', 60 * 7, true],
    ['GRADE_PROMOTION_RUN', 'member', 'info', '등급 승급 배치가 끝났어요', '회원 12명의 등급이 올랐어요.', '/settings/batch-logs?jobName=grade-promotion', 60 * 9, true],
    ['ROLES_CHANGED_ME', 'security', 'info', '내 역할이 추가됐어요', '「콘텐츠 운영자」 역할이 추가됐어요.', '/me', 60 * 24, true],
    ['SITE_SETTINGS_CHANGED', 'system', 'info', '사이트 설정이 변경됐어요', '박운영 님이 사이트 이름을 바꿨어요.', '/settings/general', 60 * 26, true],
    ['GRADE_SETTINGS_SAVED', 'member', 'info', '등급 설정이 저장됐어요', '박운영 님이 등급 승급 조건을 바꿨어요.', '/grades', 60 * 30, true],
    ['OPERATOR_ACCOUNT_CREATED', 'system', 'info', '운영자 계정이 추가됐어요', '정운영 님이 운영자로 등록됐어요.', '/settings/operators', 60 * 50, true],
    ['OPERATOR_STATUS_CHANGED_ME', 'security', 'warning', '내 계정 상태가 바뀌었어요', '계정 사용 기간이 변경됐어요.', '/me', 60 * 72, true],
    ['PASSWORD_RESET_DONE_ME', 'security', 'warning', '비밀번호가 재설정됐어요', '승인번호로 비밀번호를 재설정했어요.', '/me?tab=password', 60 * 24 * 4, true],
    ['BATCH_RUN_FAILED', 'system', 'warning', '배치가 실패했어요', '알림 정리(notification-cleanup) 배치가 오류로 끝났어요.', '/settings/batch-logs?jobName=notification-cleanup', 60 * 24 * 5, true],
    // 링크가 없는 알림(읽음 처리만 된다)
    ['SITE_SETTINGS_CHANGED', 'system', 'info', '사이트 설정이 변경됐어요', null, null, 60 * 24 * 6, true],
];

/** 한 쪽(30건)을 넘겨 「더 보기」가 보이도록 채우는 오래된 알림들 */
const FILLER_TITLES: [eventCode: string, area: string, kind: NotificationKind, title: string, link: string][] = [
    ['SITE_SETTINGS_CHANGED', 'system', 'info', '사이트 설정이 변경됐어요', '/settings/general'],
    ['GRADE_PROMOTION_RUN', 'member', 'info', '등급 승급 배치가 끝났어요', '/settings/batch-logs?jobName=grade-promotion'],
    ['OPERATOR_ACCOUNT_CREATED', 'system', 'info', '운영자 계정이 추가됐어요', '/settings/operators'],
];

function buildInitial(): NotificationItem[] {
    const few = mockFlags().has('notification-few');
    if (mockFlags().has('notification-empty')) return [];
    const seeds: Seed[] = [...SEEDS.slice(0, few ? 6 : SEEDS.length)];
    if (!few) {
        // 13건 + 32건 = 45건
        for (let i = 0; i < 32; i++) {
            const [code, area, kind, title, link] = FILLER_TITLES[i % FILLER_TITLES.length]!;
            seeds.push([code, area, kind, title, `${i + 1}번째 예전 알림이에요.`, link, 60 * 24 * (7 + i), true]);
        }
    }
    // id 는 시간이 갈수록 커진다(Snowflake) — 오래된 것부터 번호를 매긴다
    const total = seeds.length;
    return seeds.map(([eventCode, area, kind, title, body, link, minutesAgo, read], index) => ({
        id: String(9000 + (total - index)),
        eventCode,
        area,
        kind,
        title,
        body,
        link: link ? { path: link } : null,
        createdAt: ago(minutesAgo),
        read,
    }));
}

/** 서버 메모리 — 처음 부를 때 만든다(주소의 `?mock=` 스위치를 그때 읽는다) */
let items: NotificationItem[] | null = null;
let idSeq = 9100;

function store(): NotificationItem[] {
    return (items ??= buildInitial());
}

const unreadOf = (list: NotificationItem[]): number => list.filter((item) => !item.read).length;

/** 새로 도착하는 알림의 예시 — 돌아가며 쓴다 */
const ARRIVALS: Pick<NotificationItem, 'eventCode' | 'area' | 'kind' | 'title' | 'body' | 'link'>[] = [
    { eventCode: 'OPERATOR_INVITATION_ACCEPTED', area: 'system', kind: 'info', title: '운영자 초대가 수락됐어요', body: '한운영 님이 초대를 수락했어요.', link: { path: '/settings/operators' } },
    { eventCode: 'OPERATOR_LOCKED_BY_FAILURES', area: 'security', kind: 'critical', title: '로그인 실패로 계정이 잠겼어요', body: 'someone@example.com 계정이 5회 연속 실패해 잠겼어요.', link: { path: '/settings/operators' } },
    { eventCode: 'BATCH_RUN_FAILED', area: 'system', kind: 'warning', title: '배치가 실패했어요', body: '일수 승급(grade-promotion) 배치가 오류로 끝났어요.', link: { path: '/settings/batch-logs?jobName=grade-promotion' } },
    { eventCode: 'GRADE_PROMOTION_RUN', area: 'member', kind: 'success', title: '등급 승급 배치가 끝났어요', body: '회원 3명의 등급이 올랐어요.', link: { path: '/settings/batch-logs?jobName=grade-promotion' } },
];
let arrivalSeq = 0;

/** 서버에 새 알림이 생긴 것으로 한다 */
function createArrivals(count: number): void {
    const created = Array.from({ length: Math.max(1, count) }, (): NotificationItem => {
        const sample = ARRIVALS[arrivalSeq % ARRIVALS.length]!;
        arrivalSeq++;
        idSeq++;
        return { id: String(idSeq),...sample, createdAt: new Date().toISOString(), read: false };
    });
    items = [...created.reverse(), ...store()];
}

type MockWindow = Window & { __mockNotify?: (count?: number) => void };

/**
 * 새 알림 도착을 흉내 낸다 — 서버(메모리)에 알림을 더할 뿐이고, 화면은 **폴링**(`poll`)으로 가져간다.
 * 콘솔의 `__mockNotify()` 는 더한 뒤 폴링을 바로 한 번 돌린다. 주소 스위치 `notification-new` 가 있으면 5초 뒤부터 20초마다 스스로 생긴다(폴링 주기를 기다린다).
 * @returns 끝내는 함수.
 */
export function mockNotificationBind(poll: () => void): () => void {
    const target = window as MockWindow;
    target.__mockNotify = (count = 1) => {
        createArrivals(count);
        poll();
    };

    if (!mockFlags().has('notification-new')) {
        return () => delete target.__mockNotify;
    }
    const first = setTimeout(() => createArrivals(1), 5000);
    const loop = setInterval(() => createArrivals(1), 20000);
    return () => {
        clearTimeout(first);
        clearInterval(loop);
        delete target.__mockNotify;
    };
}

export async function mockNotificationList(query: NotificationListQuery = {}): Promise<NotificationListResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('notification-error')) throw MOCK_ERRORS.internal();
    if (query.cursor && flags.has('notification-more-error')) throw MOCK_ERRORS.internal();
    const all = store();
    const size = query.size ?? DEFAULT_SIZE;
    const rest = query.cursor ? all.filter((item) => Number(item.id) < Number(query.cursor)) : all;
    const page = rest.slice(0, size);
    // 다음 쪽이 있으면 이 쪽 마지막 id 가 커서다
    const nextCursor = rest.length > page.length ? page[page.length - 1]!.id : null;
    return { items: page.map((item) => ({ ...item, link: item.link ? { ...item.link } : null })), nextCursor, unread: unreadOf(all) };
}

export async function mockNotificationSummary(): Promise<NotificationSummaryResponse> {
    await mockDelay();
    const all = store();
    return { unread: unreadOf(all), latestId: all[0]?.id ?? null };
}

export async function mockNotificationRead(id: string): Promise<void> {
    await mockDelay();
    items = store().map((item) => (item.id === id ? { ...item, read: true } : item));
}

export async function mockNotificationReadAll(): Promise<void> {
    await mockDelay();
    items = store().map((item) => ({ ...item, read: true }));
}
