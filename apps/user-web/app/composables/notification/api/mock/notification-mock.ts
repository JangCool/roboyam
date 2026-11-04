// MOCK: 게시판 제재 알림(`BOARD_SANCTION_ISSUED` — board-sanction-contract 결정 6)을 실제 알림 목록 맨 위에 한 건 덧붙이는 목업. `useNotificationApi.ts` 가 부른다.
// 서버가 이 알림을 보내기 시작하면 이 파일을 지운다. 문구는 서버 몫이라 번역 키로 옮기지 않는다(`i18n.md` 5절). 처리자는 문구에 없다(결정 7).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   notification-board-sanction   알림 목록·패널 맨 위에 「게시판 제재를 받았어요」 한 건(안 읽음 — 누르면 `/sanction`, 읽음 처리는 서버에 보내지 않는다)

import type { MemberNotificationItem, MemberNotificationPage } from '~/composables/notification/api/useNotificationApi.types';
import { mockFlags } from '~/composables/common/mock/mock-util';

/** 목업 알림의 ID — 읽음 처리를 서버에 보내지 않으려고 가른다 */
export const MOCK_BOARD_SANCTION_NOTIFICATION_ID = 'mock-board-sanction-1';
let read = false;

function item(): MemberNotificationItem {
    return {
        id: MOCK_BOARD_SANCTION_NOTIFICATION_ID,
        type: 'BOARD_SANCTION_ISSUED',
        category: 'system',
        read,
        createdAt: new Date(Date.now() - 12 * 60_000).toISOString(),
        title: '자유게시판에서 쓰기 제한 3일을 받았어요.',
        body: '사유: 스팸·광고 · 같은 홍보 글을 하루에 다섯 번 올렸어요.',
        link: '/sanction',
        actor: null,
        post: null,
        commentId: null,
        commentPreview: null,
        groupCount: 1,
        points: null,
        hiddenUntil: null,
    };
}

function on(): boolean {
    return mockFlags().has('notification-board-sanction');
}

/** 알림 한 쪽 — 스위치가 켜져 있고 첫 쪽(커서 없음)·「전체」·「계정」 거르기면 맨 위에 덧붙인다 */
export function mockWithBoardSanctionNotice(page: MemberNotificationPage, query: { filter?: string; cursor?: string } = {}): MemberNotificationPage {
    if (!on() || query.cursor || query.filter === 'comment') return page;
    return { ...page, items: [item(), ...page.items], unreadCount: page.unreadCount + (read ? 0 : 1) };
}

/** 목업 알림을 읽음으로 — 목업 알림이면 true(서버에 보내지 않는다) */
export function mockMarkRead(id: string): boolean {
    if (id !== MOCK_BOARD_SANCTION_NOTIFICATION_ID) return false;
    read = true;
    return true;
}
