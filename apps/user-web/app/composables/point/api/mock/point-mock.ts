// MOCK: 내 포인트 내역 API(`GET /api/me/points/ledger`)의 메모리 목업 — `usePointApi.ts` 의 `ledger` 가 부른다. 서버가 붙으면 `point/api/mock/` 와 함께 지운다.
// 요약(`GET /api/me/points`)은 실제 서버를 부른다 — 목업 내역의 잔액은 요약 잔액과 맞지 않을 수 있다(목업 가정).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   ledger-fail    내역 요청 실패(500) → 내역 구획만 오류(요약은 그대로)
//   ledger-empty   내역 없음 → 빈 상태
//
// ── 목업 규칙(가정) ─
//   · 사유 이름(`reason`)은 서버가 요청 언어로 풀어 준다 — 목업은 한국어로만 준다.
//   · 최신이 위, 한 쪽 20줄(기본), 커서는 다음 쪽 첫 줄의 차례 번호. `kind` 는 EARN·USE·REVOKE(사용은 교환 기능이 없어 지금은 0줄).
//   · 틀린 `kind`·`size`(1~50 밖)·`cursor` 는 `400 VALIDATION fields.kind|size|cursor`.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MyPointLedgerItem, MyPointLedgerKind, MyPointLedgerPage, MyPointLedgerQuery } from '~/composables/point/api/usePointApi.types';

const HOUR = 60 * 60 * 1000;

interface Seed {
    reasonCode: MyPointLedgerItem['reasonCode'];
    reason: string;
    delta: number;
    note?: string;
    title?: string | null;
}

/** 오래된 것부터의 사건 — 차례대로 잔액을 쌓는다 */
const SEEDS: Seed[] = [
    { reasonCode: 'POST', reason: '글 작성', delta: 20, title: '처음 인사드려요' },
    { reasonCode: 'COMMENT', reason: '댓글 작성', delta: 5, title: '주말에 가볼 만한 곳 추천' },
    { reasonCode: 'COMMENT', reason: '댓글 작성', delta: 5, title: '주말에 가볼 만한 곳 추천' },
    { reasonCode: 'LIKE_BONUS', reason: '추천 보너스', delta: 10, title: '처음 인사드려요' },
    { reasonCode: 'POST', reason: '글 작성', delta: 20, title: null },
    { reasonCode: 'POST_REVOKE', reason: '글 삭제로 회수', delta: -20, title: null },
    { reasonCode: 'ADMIN_ADJUST', reason: '운영 조정', delta: 100, note: '이벤트 당첨 축하드려요' },
    { reasonCode: 'COMMENT', reason: '댓글 작성', delta: 5, title: '오늘의 날씨 이야기' },
    { reasonCode: 'COMMENT_REVOKE', reason: '댓글 삭제로 회수', delta: -5, title: '오늘의 날씨 이야기' },
    { reasonCode: 'ADMIN_ADJUST', reason: '운영 조정', delta: -30, note: '중복 지급 바로잡기' },
];

function kindOf(seed: Seed): MyPointLedgerKind {
    return seed.delta >= 0 ? 'EARN' : 'REVOKE';
}

/** 최신이 위인 전체 원장(45줄) — 사건을 몇 번 되풀이해 쪽 넘김을 볼 수 있게 한다 */
function build(): MyPointLedgerItem[] {
    const rows: MyPointLedgerItem[] = [];
    let balance = 0;
    const total = 45;
    for (let i = 0; i < total; i++) {
        const seed = SEEDS[i % SEEDS.length]!;
        const delta = seed.delta < 0 ? -Math.min(balance, -seed.delta) : seed.delta;
        balance += delta;
        const postId = String(900_000 + (i % 7));
        rows.push({
            entryId: String(5_000_000 + i),
            kind: kindOf(seed),
            reasonCode: seed.reasonCode,
            reason: seed.reason,
            note: seed.note ?? null,
            delta,
            balanceAfter: balance,
            target: seed.reasonCode === 'ADMIN_ADJUST' ? null : { postId, title: seed.title ?? null, available: seed.title != null },
            createdAt: new Date(Date.now() - (total - i) * 7 * HOUR).toISOString(),
        });
    }
    return rows.reverse();
}

let store: MyPointLedgerItem[] | null = null;

export async function mockPointLedger(query: MyPointLedgerQuery = {}): Promise<MyPointLedgerPage> {
    await mockDelay();
    const on = mockFlags();
    if (on.has('ledger-fail')) throw MOCK_ERRORS.internal();
    if (on.has('ledger-empty')) return { items: [], hasNext: false, nextCursor: null };

    const fields: Record<string, string> = {};
    if (query.kind !== undefined && !['EARN', 'USE', 'REVOKE'].includes(query.kind)) fields.kind = '알 수 없는 종류예요.';
    const size = query.size ?? 20;
    if (!Number.isInteger(size) || size < 1 || size > 50) fields.size = '1~50 사이로 입력해 주세요.';
    const start = query.cursor === undefined ? 0 : Number(query.cursor);
    if (!Number.isInteger(start) || start < 0) fields.cursor = '잘못된 커서예요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    store ??= build();
    const rows = query.kind ? store.filter((row) => row.kind === query.kind) : store;
    const items = rows.slice(start, start + size);
    const hasNext = start + size < rows.length;
    return { items: mockClone(items), hasNext, nextCursor: hasNext ? String(start + size) : null };
}
