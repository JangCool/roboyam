// MOCK: 내 제재 API 의 **게시판 제재 칸**(`GET /api/me/sanctions` 의 `boardSanctions`, `POST /api/me/board-sanctions/{id}/acknowledge`, `POST /api/me/appeals` 의 `boardSanctionId`)의 메모리 목업.
// 사이트 제재는 실제 서버 그대로다 — 이 파일은 서버가 아직 주지 않는 새 칸만 채운다. `useSanctionApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
// 계약: `docs/features/board-sanction-contract.md`.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   board-sanctions       게시판 제재 4건(진행 중 쓰기 제한 3일 · 확인 안 한 경고 · 기각된 이의제기가 있는 끝난 1일 · 무효가 된 경고)
//   board-restricted      진행 중 쓰기 제한 1건만(게시판 화면의 같은 스위치와 짝)
//   board-ack-fail        「확인했어요」 실패(500)      board-appeal-fail   게시판 제재 이의제기 접수 실패(500)
//   (스위치가 없으면 게시판 제재는 빈 목록)
//
// ── 목업 규칙(가정) ─
//   · 처리자 신원은 어디에도 없다. 쓰기 제한은 늘 `acknowledged: true`, 경고만 확인한다.
//   · 이의제기 규칙은 사이트 제재와 같다 — 1번 · 시작 후 30일 · 사유 10~500자(`409 APPEAL_ALREADY`·`APPEAL_EXPIRED`·`SANCTION_NOT_APPEALABLE`, `400 VALIDATION fields.reason`).

import type { AppealCreated, MyBoardSanction, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import UtilSanction from '~/utils/util-sanction';

const DAY = 86_400_000;
let items: MyBoardSanction[] | null = null;
let appealNo = 700;

function iso(ms: number): string {
    return new Date(ms).toISOString();
}

function seed(): MyBoardSanction[] {
    if (items) return items;
    const flags = mockFlags();
    const now = Date.now();
    const free = { id: '11', slug: 'free', name: '자유게시판', nameEn: 'Free board' };
    const humor = { id: '12', slug: 'humor', name: '유머', nameEn: 'Humor' };
    const stock = { id: '13', slug: 'stock', name: '주식', nameEn: null };
    const restrictStart = now - 1 * DAY;
    const active: MyBoardSanction = {
        id: '7001',
        no: 312,
        board: free,
        kind: 'RESTRICT_3',
        status: 'ACTIVE',
        reasonCode: 'SPAM',
        reasonText: '같은 홍보 글을 하루에 다섯 번 올렸어요.',
        startsAt: iso(restrictStart),
        endsAt: iso(restrictStart + 3 * DAY),
        createdAt: iso(restrictStart),
        acknowledged: true,
        appeal: null,
        appealDeadline: iso(restrictStart + 30 * DAY),
    };
    if (flags.has('board-sanctions')) {
        items = [
            active,
            {
                id: '7002',
                no: 305,
                board: humor,
                kind: 'WARNING',
                status: 'ACTIVE',
                reasonCode: 'ABUSE',
                reasonText: null,
                startsAt: iso(now - 3 * DAY),
                endsAt: null,
                createdAt: iso(now - 3 * DAY),
                acknowledged: false,
                appeal: null,
                appealDeadline: iso(now + 27 * DAY),
            },
            {
                id: '7003',
                no: 240,
                board: stock,
                kind: 'RESTRICT_1',
                status: 'ENDED',
                reasonCode: 'INVESTMENT',
                reasonText: '특정 종목 매수를 권했어요.',
                startsAt: iso(now - 20 * DAY),
                endsAt: iso(now - 19 * DAY),
                createdAt: iso(now - 20 * DAY),
                acknowledged: true,
                appeal: { id: '6601', no: 88, status: 'REJECTED', reason: '정보 공유였어요. 권유가 아니었습니다.', answer: '종목과 매수 시점을 함께 적어 권유로 보았어요.', createdAt: iso(now - 19.5 * DAY), answeredAt: iso(now - 18 * DAY) },
                appealDeadline: iso(now + 10 * DAY),
            },
            {
                id: '7004',
                no: 201,
                board: free,
                kind: 'WARNING',
                status: 'VOIDED',
                reasonCode: 'OFF_TOPIC',
                reasonText: null,
                startsAt: iso(now - 40 * DAY),
                endsAt: null,
                createdAt: iso(now - 40 * DAY),
                acknowledged: true,
                appeal: { id: '6550', no: 61, status: 'ACCEPTED', reason: '게시판 안내에 이 주제도 된다고 적혀 있어요.', answer: '확인해 보니 맞아요. 제재를 무효로 했어요.', createdAt: iso(now - 39 * DAY), answeredAt: iso(now - 38 * DAY) },
                appealDeadline: null,
            },
        ];
    } else if (flags.has('board-restricted')) items = [active];
    else items = [];
    return items;
}

/** `GET /api/me/sanctions` 응답에 서버가 아직 주지 않는 `boardSanctions` 를 채운다(서버가 주면 그 값 그대로) */
export function mockWithBoardSanctions(mine: MySanctions): MySanctions {
    if (mine.boardSanctions !== undefined) return mine;
    // 끝난 쓰기 제한은 읽을 때 정리한다(서버의 지연 만료와 같다)
    for (const s of seed()) if (s.status === 'ACTIVE' && s.endsAt && Date.parse(s.endsAt) <= Date.now()) s.status = 'ENDED';
    return { ...mine, boardSanctions: mockClone(seed()) };
}

export async function mockBoardAcknowledge(id: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('board-ack-fail')) throw MOCK_ERRORS.internal();
    const target = seed().find((s) => s.id === id);
    if (!target) throw new ApiError({ status: 404, code: 'SANCTION_NOT_FOUND', message: '제재를 찾을 수 없어요.' });
    target.acknowledged = true;
}

export async function mockBoardAppeal(boardSanctionId: string, reason: string): Promise<AppealCreated> {
    await mockDelay();
    if (mockFlags().has('board-appeal-fail')) throw MOCK_ERRORS.internal();
    const target = seed().find((s) => s.id === boardSanctionId);
    if (!target) throw new ApiError({ status: 404, code: 'SANCTION_NOT_FOUND', message: '제재를 찾을 수 없어요.' });
    const problem = UtilSanction.appealReasonProblem(reason);
    if (problem) throw MOCK_ERRORS.validation({ reason: `이의제기 사유는 ${UtilSanction.APPEAL_REASON_MIN}~${UtilSanction.APPEAL_REASON_MAX}자로 적어 주세요.` });
    if (target.appeal) throw new ApiError({ status: 409, code: 'APPEAL_ALREADY', message: '이미 이의제기를 접수한 제재예요.' });
    if (target.status === 'VOIDED') throw new ApiError({ status: 409, code: 'SANCTION_NOT_APPEALABLE', message: '무효가 된 제재는 이의제기할 수 없어요.' });
    if (target.appealDeadline && Date.parse(target.appealDeadline) <= Date.now()) throw new ApiError({ status: 409, code: 'APPEAL_EXPIRED', message: '이의제기 기한이 지났어요.' });
    const id = String(6700 + ++appealNo);
    target.appeal = { id, no: appealNo, status: 'PENDING', reason: reason.trim(), answer: null, createdAt: new Date().toISOString(), answeredAt: null };
    return { id, no: appealNo, status: 'PENDING' };
}
