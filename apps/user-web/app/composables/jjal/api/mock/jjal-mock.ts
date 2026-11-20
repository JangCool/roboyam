// MOCK: 내 짤 목업(계약 docs/features/my-jjal-contract.md 「API 후보」) — 서버가 붙으면 이 파일과 `useJjalApi`·`useReportApi` 의 MOCK 줄을 지운다.
// 메모리에만 둔다(새로 고치면 처음 상태). 그림은 SVG 글자 그림(data URI)이고, 올린 짤은 만든 Blob 의 object URL 이다.
// 스위치(`?mock=`): `jjal-fail`(목록 실패) · `jjal-full`(50개 꽉 참) · `jjal-intro-done`(처음 안내 확인함) · `jjal-restricted`(정지 회원 — 올리기·담기 403) · `jjal-order-conflict`(순서 저장 409).
// 보관함 배열 순서 = 회원이 정한 순서(새로 넣은 짤은 맨 앞).
// 남의 짤(보관함에 없는 짤)을 담아 보려면 댓글(평문)에 `:jjal-j7Qb01:` ~ `:jjal-j7Qb04:` 를 적어 올린다(내려간 짤은 `:jjal-j7Qb05:`).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { ReportRequest } from '~/composables/report/api/useReportApi.types';
import type { JjalLookupResponse, JjalOrderRequest, JjalSaveResult, JjalSource, JjalView, MyJjalItem, MyJjalListResponse } from '~/composables/jjal/api/useJjalApi.types';

/** 파일 한 줄(사이트 전체 — 같은 해시는 한 번만 저장한다는 계약의 「파일」) */
interface MockFile {
    id: string;
    url: string;
    width: number;
    height: number;
    animated: boolean;
    blocked: boolean;
}

/** 내 보관함 줄(배열 차례가 순서) */
interface MockBoxRow {
    jjalId: string;
    source: JjalSource;
}

const LIMIT = 50;
const MAX_BYTES = 2 * 1024 * 1024;

/** SVG 글자 그림 — 움짤은 살짝 흔들린다 */
function art(emoji: string, caption: string, bg: string, width: number, height: number, animated: boolean): string {
    const cx = width / 2;
    const cy = height / 2;
    const size = Math.round(Math.min(width, height) / 2.6);
    const wobble = animated ? `<animateTransform attributeName="transform" type="rotate" values="-8 ${cx} ${cy};8 ${cx} ${cy};-8 ${cx} ${cy}" dur="0.8s" repeatCount="indefinite"/>` : '';
    const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">` +
        `<rect width="${width}" height="${height}" rx="18" fill="${bg}"/>` +
        `<g>${wobble}<text x="${cx}" y="${cy - size * 0.15}" font-size="${size}" text-anchor="middle" dominant-baseline="middle">${emoji}</text></g>` +
        `<text x="${cx}" y="${height - 18}" font-size="${Math.round(size / 2.6)}" font-family="sans-serif" font-weight="700" fill="#1f2937" text-anchor="middle">${caption}</text>` +
        `</svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function file(id: string, emoji: string, caption: string, bg: string, width: number, height: number, animated: boolean, blocked = false): MockFile {
    return { id, url: art(emoji, caption, bg, width, height, animated), width, height, animated, blocked };
}

const FILES = new Map<string, MockFile>(
    [
        file('j7Qa01', '😂', '웃음', '#fde68a', 240, 240, true),
        file('j7Qa02', '👍', '굿', '#bbf7d0', 300, 200, false),
        file('j7Qa03', '😭', '오열', '#bfdbfe', 200, 260, true),
        file('j7Qa04', '🔥', '불타오르네', '#fecaca', 300, 300, true),
        file('j7Qa05', '🙄', '어휴', '#e9d5ff', 220, 220, false),
        file('j7Qa06', '🤔', '흠', '#fed7aa', 260, 200, false),
        file('j7Qa07', '🐶', '멍', '#fef3c7', 300, 240, true),
        file('j7Qa08', '🎉', '축하', '#fbcfe8', 280, 280, true),
        file('j7Qa09', '😎', '여유', '#a5f3fc', 240, 180, false),
        file('j7Qa10', '🙏', '감사', '#d9f99d', 200, 200, false),
        file('j7Qa11', '💤', '졸림', '#c7d2fe', 300, 150, true),
        file('j7Qa12', '🚫', '내려감', '#e5e7eb', 240, 240, false, true),
        // 남의 짤 — 내 보관함에 없다(담기 시험)
        file('j7Qb01', '🐱', '냥', '#fde68a', 260, 260, true),
        file('j7Qb02', '🍜', '라면각', '#fed7aa', 300, 220, false),
        file('j7Qb03', '🚀', '가즈아', '#bfdbfe', 220, 300, true),
        file('j7Qb04', '🤣', '빵터짐', '#bbf7d0', 280, 280, true),
        file('j7Qb05', '🚫', '내려감', '#e5e7eb', 240, 240, false, true),
    ].map((f) => [f.id, f]),
);

/** 처음 보관함 — 12개(하나는 내려간 짤). 계약 시안의 「12 / 50」 */
function seedBox(): MockBoxRow[] {
    const ids = ['j7Qa01', 'j7Qa02', 'j7Qa03', 'j7Qa04', 'j7Qa05', 'j7Qa06', 'j7Qa07', 'j7Qa08', 'j7Qa09', 'j7Qa10', 'j7Qa11', 'j7Qa12'];
    return ids.map((jjalId, i) => ({ jjalId, source: i % 3 === 1 ? 'COLLECT' : 'UPLOAD' }));
}

let box: MockBoxRow[] | null = null;
let introAcknowledged: boolean | null = null;
const reported = new Set<string>();
let seq = 1;

/** 보관함(처음 부를 때 스위치를 읽어 만든다) */
function myBox(): MockBoxRow[] {
    if (box) return box;
    box = seedBox();
    if (mockFlags().has('jjal-full')) {
        // 꽉 찬 보관함 — 같은 그림을 다른 ID 로 채운다
        for (let i = box.length; i < LIMIT; i++) {
            const source = FILES.get(`j7Qa${String((i % 11) + 1).padStart(2, '0')}`) as MockFile;
            const id = `j7Qf${String(i).padStart(2, '0')}`;
            FILES.set(id, { ...source, id });
            box.push({ jjalId: id, source: 'UPLOAD' });
        }
    }
    return box;
}

function acknowledged(): boolean {
    if (introAcknowledged === null) introAcknowledged = mockFlags().has('jjal-intro-done');
    return introAcknowledged;
}

function itemOf(row: MockBoxRow): MyJjalItem {
    const f = FILES.get(row.jjalId) as MockFile;
    return { id: f.id, url: f.url, width: f.width, height: f.height, animated: f.animated, source: row.source, blocked: f.blocked };
}

function restricted(): ApiError | null {
    return mockFlags().has('jjal-restricted') ? new ApiError({ status: 403, code: 'ACCOUNT_RESTRICTED', message: '이용이 제한된 계정이에요.' }) : null;
}

const ERRORS = {
    full: () => new ApiError({ status: 409, code: 'JJAL_LIMIT_REACHED', message: `내 짤은 ${LIMIT}개까지 담을 수 있어요.` }),
    already: () => new ApiError({ status: 409, code: 'JJAL_ALREADY_IN_BOX', message: '이미 내 짤에 있어요.' }),
    blocked: () => new ApiError({ status: 409, code: 'JJAL_BLOCKED', message: '내려간 짤이라 담을 수 없어요.' }),
    notFound: () => new ApiError({ status: 404, code: 'JJAL_NOT_FOUND', message: '짤을 찾을 수 없어요.' }),
    tooLarge: () => new ApiError({ status: 413, code: 'JJAL_TOO_LARGE', message: '2MB 이하 파일만 올릴 수 있어요.' }),
};

export async function mockMyJjals(): Promise<MyJjalListResponse> {
    await mockDelay();
    if (mockFlags().has('jjal-fail')) throw MOCK_ERRORS.internal();
    const rows = myBox();
    return mockClone({ items: rows.map(itemOf), count: rows.length, limit: LIMIT, introAcknowledged: acknowledged() });
}

export async function mockUploadJjal(blob: Blob): Promise<JjalSaveResult> {
    await mockDelay();
    const denied = restricted();
    if (denied) throw denied;
    if (blob.size > MAX_BYTES) throw ERRORS.tooLarge();
    const rows = myBox();
    if (rows.length >= LIMIT) throw ERRORS.full();
    let width = 0;
    let height = 0;
    try {
        const bitmap = await createImageBitmap(blob);
        width = bitmap.width;
        height = bitmap.height;
        bitmap.close();
    } catch {
        throw MOCK_ERRORS.validation({ file: '그림을 읽지 못했어요.' });
    }
    const id = `j7Qu${String(seq++).padStart(2, '0')}`;
    FILES.set(id, { id, url: URL.createObjectURL(blob), width, height, animated: blob.type === 'image/gif', blocked: false });
    const row: MockBoxRow = { jjalId: id, source: 'UPLOAD' };
    rows.unshift(row);
    return mockClone({ item: itemOf(row), count: rows.length, limit: LIMIT });
}

export async function mockCollectJjal(jjalId: string): Promise<JjalSaveResult> {
    await mockDelay();
    const denied = restricted();
    if (denied) throw denied;
    const f = FILES.get(jjalId);
    if (!f) throw ERRORS.notFound();
    if (f.blocked) throw ERRORS.blocked();
    const rows = myBox();
    if (rows.some((r) => r.jjalId === jjalId)) throw ERRORS.already();
    if (rows.length >= LIMIT) throw ERRORS.full();
    const row: MockBoxRow = { jjalId, source: 'COLLECT' };
    rows.unshift(row);
    return mockClone({ item: itemOf(row), count: rows.length, limit: LIMIT });
}

export async function mockRemoveJjal(jjalId: string): Promise<void> {
    await mockDelay();
    const rows = myBox();
    const at = rows.findIndex((r) => r.jjalId === jjalId);
    if (at < 0) throw ERRORS.notFound();
    rows.splice(at, 1);
}

/** 순서 저장 — 보관함 ID 와 하나도 빠짐·겹침 없이 같아야 한다(아니면 409) */
export async function mockOrderJjals(body: JjalOrderRequest): Promise<void> {
    await mockDelay();
    const rows = myBox();
    const known = new Set(rows.map((r) => r.jjalId));
    const sent = new Set(body.ids);
    const same = sent.size === body.ids.length && sent.size === known.size && body.ids.every((id) => known.has(id));
    if (mockFlags().has('jjal-order-conflict') || !same) {
        throw new ApiError({ status: 409, code: 'JJAL_ORDER_CONFLICT', message: '그사이 내 짤이 바뀌었어요. 목록을 다시 불러올게요.' });
    }
    const byId = new Map(rows.map((r) => [r.jjalId, r]));
    box = body.ids.map((id) => byId.get(id) as MockBoxRow);
}

export async function mockAckJjalIntro(): Promise<void> {
    await mockDelay();
    introAcknowledged = true;
}

/** 글·댓글 속 짤의 표시 정보 — 모르는 ID 는 빠진다(화면은 토큰 글자 그대로) */
export async function mockLookupJjals(ids: readonly string[]): Promise<JjalLookupResponse> {
    await mockDelay();
    const items: JjalView[] = [];
    for (const id of ids) {
        const f = FILES.get(id);
        if (f) items.push({ id: f.id, url: f.url, width: f.width, height: f.height, animated: f.animated, blocked: f.blocked, collectable: !f.blocked });
    }
    return mockClone({ items });
}

/** 짤 신고(대상 종류 `JJAL`) — 같은 짤은 한 번만 */
export async function mockReportJjal(body: ReportRequest): Promise<void> {
    await mockDelay();
    if (!FILES.has(body.targetId)) throw new ApiError({ status: 404, code: 'REPORT_TARGET_NOT_FOUND', message: '신고할 대상을 찾을 수 없어요.' });
    if (reported.has(body.targetId)) throw new ApiError({ status: 409, code: 'ALREADY_REPORTED', message: '이미 신고했어요.' });
    reported.add(body.targetId);
}
