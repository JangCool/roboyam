// MOCK: 짤 신고 사건(대상 종류 `JJAL`, my-jjal-contract 「신고」·「관리자」) — 서버에 아직 없다. 메모리에만 둔다(새로 고치면 처음 상태).
// 사건 셋: 대기(권리침해 2건) · 검토 중(불법촬영물 1건 — 즉시 숨김) · 처리됨(짤 내림 — 되돌림 시험). 그림은 SVG 글자 그림(data URI).
// 서버가 붙으면 이 파일과 `useReportApi` 의 MOCK 줄을 지운다.

import { ApiError } from '~/composables/common/useApiClient';
import type {
    ReportCaseDetail,
    ReportCaseItem,
    ReportCaseListView,
    ReportCaseQuery,
    ReportDismissRequest,
    ReportEntry,
    ReportEvent,
    ReportJjal,
    ReportReasonCode,
    ReportResolveRequest,
    ReportSummary,
} from '~/composables/report/api/useReportApi.types';

const PREFIX = 'mock-jjal-';

function art(emoji: string, caption: string, bg: string, width: number, height: number): string {
    const size = Math.round(Math.min(width, height) / 2.6);
    const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">` +
        `<rect width="${width}" height="${height}" rx="18" fill="${bg}"/>` +
        `<text x="${width / 2}" y="${height / 2 - size * 0.15}" font-size="${size}" text-anchor="middle" dominant-baseline="middle">${emoji}</text>` +
        `<text x="${width / 2}" y="${height - 18}" font-size="${Math.round(size / 2.6)}" font-family="sans-serif" font-weight="700" fill="#1f2937" text-anchor="middle">${caption}</text>` +
        `</svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function ago(minutes: number): string {
    return new Date(Date.now() - minutes * 60_000).toISOString();
}

function clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T;
}

function entry(id: string, name: string, reason: ReportReasonCode, minutes: number, memo: string | null = null): ReportEntry {
    return { id, reporter: { id: `m-${id}`, name, withdrawn: false }, reason, memo, createdAt: ago(minutes), status: 'PENDING' };
}

function event(id: string, type: ReportEvent['type'], minutes: number, actor: ReportEvent['actor'], detail: Record<string, unknown> | null = null): ReportEvent {
    return { id, type, actor, detail, createdAt: ago(minutes) };
}

function makeCase(no: number, jjal: ReportJjal, reports: ReportEntry[], patch: Partial<ReportCaseItem>, events: ReportEvent[]): ReportCaseDetail {
    const counts = new Map<ReportReasonCode, number>();
    for (const r of reports) counts.set(r.reason, (counts.get(r.reason) ?? 0) + 1);
    const times = reports.map((r) => r.createdAt).sort();
    const item: ReportCaseItem = {
        id: `${PREFIX}${no}`,
        caseNo: `J-${no}`,
        status: 'PENDING',
        targetType: 'JJAL',
        target: { postId: null, commentId: null, groupId: null, title: `짤 ${jjal.id}`, excerpt: null, state: jjal.blocked ? 'HIDDEN' : 'PUBLIC', hiddenReason: jjal.blocked ? 'STAFF' : null, hiddenUntil: null, jjal },
        board: null,
        author: { kind: null, id: null, name: null, withdrawn: false, sealed: false },
        reportCount: reports.length,
        reasons: [...counts].map(([code, count]) => ({ code, count })),
        firstReportedAt: times[0] ?? ago(0),
        lastReportedAt: times[times.length - 1] ?? ago(0),
        reviewer: null,
        closedAt: null,
        closedBy: null,
        action: null,
        deal: null,
        ...patch,
    };
    // 대상 칸은 합친다(짤 정보를 잃지 않게)
    if (patch.target) item.target = { ...patch.target, jjal };
    return {
        case: item,
        content: { format: 'TEXT', body: null, snapshot: null },
        reports,
        authorCard: { id: null, name: '—', grade: null, joinedAt: null, closedCases: { resolved: 0, dismissed: 0 }, sanction: null },
        events,
        publicUrl: null,
        group: null,
        issuedSanction: null,
    };
}

const SYSTEM = { kind: 'SYSTEM' as const, id: null, name: null };
const ME = { id: 'op-me', name: '운영자' };

function seed(): ReportCaseDetail[] {
    return [
        makeCase(
            1,
            { id: 'j7Qb03', url: art('🚀', '가즈아', '#bfdbfe', 220, 300), width: 220, height: 300, animated: true, blocked: false },
            [entry('e11', '루미', 'RIGHTS', 40, '제가 만든 움짤이에요. 허락 없이 올라왔어요.'), entry('e12', '파랑새', 'RIGHTS', 25)],
            {},
            [event('v11', 'REPORTED', 40, { kind: 'MEMBER', id: 'm-e11', name: '루미' }), event('v12', 'REPORTED', 25, { kind: 'MEMBER', id: 'm-e12', name: '파랑새' })],
        ),
        makeCase(
            2,
            { id: 'j7Qb02', url: art('🍜', '라면각', '#fed7aa', 300, 220), width: 300, height: 220, animated: false, blocked: false },
            [entry('e21', '도토리', 'ILLEGAL_FILMING', 90)],
            { status: 'IN_REVIEW', reviewer: ME, target: { postId: null, commentId: null, groupId: null, title: '짤 j7Qb02', excerpt: null, state: 'HIDDEN', hiddenReason: 'ILLEGAL', hiddenUntil: null } },
            [event('v21', 'REPORTED', 90, { kind: 'MEMBER', id: 'm-e21', name: '도토리' }), event('v22', 'AUTO_HIDDEN', 90, SYSTEM), event('v23', 'REVIEW_STARTED', 60, { kind: 'OPERATOR', ...ME })],
        ),
        makeCase(
            3,
            { id: 'j7Qb05', url: art('🚫', '내려감', '#e5e7eb', 240, 240), width: 240, height: 240, animated: false, blocked: true },
            [{ ...entry('e31', '구름', 'RIGHTS', 3000), status: 'ACCEPTED' }],
            { status: 'RESOLVED', reviewer: ME, closedAt: ago(2800), closedBy: ME, action: 'JJAL_BLOCK', reasonCode: 'RIGHTS', note: '권리자 요청으로 내렸어요.' },
            [event('v31', 'REPORTED', 3000, { kind: 'MEMBER', id: 'm-e31', name: '구름' }), event('v32', 'RESOLVED', 2800, { kind: 'OPERATOR', ...ME }, { action: 'JJAL_BLOCK', reasonCode: 'RIGHTS', note: '권리자 요청으로 내렸어요.' })],
        ),
    ];
}

let cases: ReportCaseDetail[] | null = null;
let seq = 100;

function store(): ReportCaseDetail[] {
    if (!cases) cases = seed();
    return cases;
}

function find(id: string): ReportCaseDetail {
    const found = store().find((c) => c.case.id === id);
    if (!found) throw new ApiError({ status: 404, code: 'REPORT_CASE_NOT_FOUND', message: '신고 사건을 찾을 수 없어요.' });
    return found;
}

function open(status: ReportCaseItem['status']): boolean {
    return status === 'PENDING' || status === 'IN_REVIEW';
}

function delay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 250 + Math.random() * 300));
}

function push(detail: ReportCaseDetail, type: ReportEvent['type'], extra: Record<string, unknown> | null): void {
    detail.events.push({ id: `v${seq++}`, type, actor: { kind: 'OPERATOR', ...ME }, detail: extra, createdAt: new Date().toISOString() });
}

/** 목업 사건 ID 인가 */
export function isMockJjalCase(id: string): boolean {
    return id.startsWith(PREFIX);
}

/** 목록에 끼울 목업 사건 — 짤 거르기(또는 전체)이고 검색·게시판·글·소모임 거르기가 없는 첫 쪽에서만 */
export function mockJjalCasesFor(query: ReportCaseQuery): ReportCaseItem[] {
    if (query.targetType && query.targetType !== 'JJAL') return [];
    if (query.q || query.board || query.postId || query.groupId || query.reason || (query.page ?? 0) > 0) return [];
    const status = query.status ?? 'OPEN';
    return store()
        .map((c) => c.case)
        .filter((c) => (status === 'ALL' ? true : status === 'OPEN' ? open(c.status) : c.status === status))
        .map(clone);
}

/** 짤 거르기만 골랐을 때 — 서버를 부르지 않고 목업만 */
export async function mockJjalList(query: ReportCaseQuery): Promise<ReportCaseListView> {
    await delay();
    const items = mockJjalCasesFor(query);
    return { total: items.length, page: query.page ?? 0, size: query.size ?? 20, items };
}

/** 대기·검토 중 수에 목업 사건을 더한다 */
export function mockJjalSummary(summary: ReportSummary): ReportSummary {
    const list = store().map((c) => c.case);
    return { pending: summary.pending + list.filter((c) => c.status === 'PENDING').length, inReview: summary.inReview + list.filter((c) => c.status === 'IN_REVIEW').length };
}

export async function mockJjalGet(id: string): Promise<ReportCaseDetail> {
    await delay();
    const detail = find(id);
    if (detail.case.status === 'PENDING') {
        detail.case.status = 'IN_REVIEW';
        detail.case.reviewer = ME;
        push(detail, 'REVIEW_STARTED', null);
    }
    return clone(detail);
}

/** 짤 내림 — 사건 처리됨, 짤은 모든 곳에서 「내려간 짤」 */
export async function mockJjalResolve(id: string, body: ReportResolveRequest): Promise<ReportCaseDetail> {
    await delay();
    const detail = find(id);
    if (!open(detail.case.status)) throw new ApiError({ status: 409, code: 'REPORT_CASE_CLOSED', message: '이미 처리된 사건이에요.' });
    if (body.action !== 'JJAL_BLOCK') throw new ApiError({ status: 409, code: 'REPORT_ACTION_INVALID', message: '짤 사건에는 짤 내림만 할 수 있어요.' });
    const c = detail.case;
    c.status = 'RESOLVED';
    c.action = 'JJAL_BLOCK';
    c.reasonCode = body.reasonCode;
    c.note = body.note;
    c.closedAt = new Date().toISOString();
    c.closedBy = ME;
    c.target = { ...c.target, state: 'HIDDEN', hiddenReason: 'STAFF', jjal: c.target.jjal ? { ...c.target.jjal, blocked: true } : null };
    detail.reports = detail.reports.map((r) => ({ ...r, status: 'ACCEPTED' }));
    push(detail, 'RESOLVED', { action: 'JJAL_BLOCK', reasonCode: body.reasonCode, note: body.note });
    return clone(detail);
}

export async function mockJjalDismiss(id: string, body: ReportDismissRequest): Promise<ReportCaseDetail> {
    await delay();
    const detail = find(id);
    if (!open(detail.case.status)) throw new ApiError({ status: 409, code: 'REPORT_CASE_CLOSED', message: '이미 처리된 사건이에요.' });
    const c = detail.case;
    c.status = 'DISMISSED';
    c.action = 'DISMISS';
    c.reasonCode = body.reasonCode ?? null;
    c.note = body.note ?? null;
    c.falseReport = body.falseReport;
    c.closedAt = new Date().toISOString();
    c.closedBy = ME;
    // 자동 숨김(불법촬영물 즉시 숨김 등)은 무시하면 다시 보인다
    if (c.target.hiddenReason && c.target.hiddenReason !== 'STAFF') c.target = { ...c.target, state: 'PUBLIC', hiddenReason: null };
    detail.reports = detail.reports.map((r) => ({ ...r, status: body.falseReport ? 'REJECTED' : 'DISMISSED' }));
    push(detail, 'DISMISSED', { falseReport: body.falseReport, note: body.note ?? null });
    return clone(detail);
}

export async function mockJjalReopen(id: string): Promise<ReportCaseDetail> {
    await delay();
    const detail = find(id);
    if (detail.case.status !== 'DISMISSED') throw new ApiError({ status: 409, code: 'REPORT_CASE_CLOSED', message: '무시한 사건만 다시 열 수 있어요.' });
    const c = detail.case;
    c.status = 'PENDING';
    c.action = null;
    c.closedAt = null;
    c.closedBy = null;
    c.falseReport = false;
    detail.reports = detail.reports.map((r) => ({ ...r, status: 'PENDING' }));
    push(detail, 'REOPENED', null);
    return clone(detail);
}

/** 되돌림 — 내린 짤을 다시 보이게(사건은 닫힌 그대로) */
export async function mockJjalRestore(id: string): Promise<ReportCaseDetail> {
    await delay();
    const detail = find(id);
    const c = detail.case;
    if (!c.target.jjal?.blocked) throw new ApiError({ status: 409, code: 'JJAL_NOT_BLOCKED', message: '내려간 짤이 아니에요.' });
    c.target = { ...c.target, state: 'PUBLIC', hiddenReason: null, jjal: { ...c.target.jjal, blocked: false } };
    push(detail, 'RESOLVED', { note: '짤 되돌림' });
    return clone(detail);
}
