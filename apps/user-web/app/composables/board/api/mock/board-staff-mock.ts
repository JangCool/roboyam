// MOCK: 게시판 매니저·스텝 제재 API(`/api/board-staff/…`)와 게시판 응답의 새 칸(`viewer.staffTools`·`viewer.writeRestriction`)의 메모리 목업.
// `useBoardStaffApi.ts`·`useBoardApi.ts`(`board` 보강)·`usePostApi.ts`/`useCommentApi.ts`(쓰기 막힘 흉내)가 부른다. 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
// 계약: `docs/features/board-sanction-contract.md`.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여러 개) ─
//   board-manager             이 게시판의 매니저로 본다(게시판 머리 운영 줄 · ⋯ 「작성자 제재」 · 제재 내역 전체 + 처리자 + [해제])
//   board-staff               이 게시판의 스텝으로 본다(내가 준 것만, 처리자 칸 없음, [해제] 없음)
//   board-limit-used          오늘 한도를 다 쓴 상태(남은 0건 — 제출하면 409 BOARD_STAFF_DAILY_LIMIT)
//   board-restricted          보는 내가 이 게시판에서 쓰기 제한 중(글쓰기 단추·글쓰기 폼·댓글 자리에 안내)
//   board-write-error         글·댓글 등록이 403 BOARD_WRITE_RESTRICTED 로 거절된다(게시판 응답은 제한 없음 — 뒤늦게 알게 되는 경우)
//   board-target-protected    대상이 제재할 수 없는 회원(대화창 열 때 409 BOARD_STAFF_TARGET_PROTECTED)
//   board-target-restricted   대상이 이미 이 게시판에서 쓰기 제한 중(제한 3개 비활성 · 경고만)
//   board-already-restricted  제출 때 409 BOARD_STAFF_ALREADY_RESTRICTED(그새 다른 처리자가 제한함)
//   board-preview-fail        대화창 현황 읽기 실패(500)      board-sanction-fail   제출 실패(500)
//   board-sanctions-empty     제재 내역이 비었다              board-sanctions-fail  제재 내역 읽기 실패(500)
//   board-lift-fail           해제 실패(500)
//
// ── 목업 규칙(가정) ─
//   · 한도는 게시판마다 기본 10건(`staffSanctionDailyLimit` ?? 기본값 — 값은 가정), 한국 시간 0시에 다시 채운다. 해제로 돌려받지 않는다.
//   · 대상의 이전 제재 수는 회원 ID 로 0~2건을 정한다(권장 단계를 골고루 보이려고). 권장 = 유효 제재 수 0 → 경고, 1 → 1일, 2 → 3일, 3 이상 → 7일.
//   · 「나」는 매니저로 볼 때 「운영매니저」, 스텝으로 볼 때 「스텝하늘」이다. 내역의 기본 줄(24건)은 계기 글이 없다.

import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type {
    BoardSanctionIssued,
    BoardSanctionIssueRequest,
    BoardSanctionKind,
    BoardSanctionLiftRequest,
    BoardSanctionPreview,
    BoardSanctionReasonCode,
    BoardStaffQuota,
    BoardStaffSanctionPage,
    BoardStaffSanctionQuery,
    BoardStaffSanctionRow,
    BoardStaffTools,
} from '~/composables/board/api/useBoardStaffApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import UtilBoardSanction from '~/utils/util-board-sanction';

const DAY = 86_400_000;
const DEFAULT_LIMIT = 10;
const PAGE_SIZE = 20;

/** 한도 사용량 — 게시판 ID → 오늘 쓴 수 */
const used = new Map<string, number>();
/** 대상별 이 게시판 이력 — `${boardId}:${userId}` → 유효 제재 수·진행 중 제한 */
const targets = new Map<string, { warnings: number; restrictions: number; activeUntil: string | null; activeId: string | null }>();
/** 목업 줄 — 어느 게시판의 것인지 · 누가 줬는지(매니저 「나」 · 스텝 「나」 · 다른 스텝) */
type MockRow = BoardStaffSanctionRow & { boardId: string; byRole: 'MANAGER' | 'STAFF' | 'OTHER' };
/** 이번 방문에 준 제재 + 기본 줄 */
let rows: MockRow[] | null = null;
let seq = 9000;

function isManager(): boolean {
    return mockFlags().has('board-manager');
}
function isStaff(): boolean {
    const flags = mockFlags();
    return flags.has('board-manager') || flags.has('board-staff');
}

/** 다음 한국 시간 0시 */
function nextKstMidnight(now = Date.now()): string {
    const kst = now + 9 * 3_600_000;
    const next = Math.floor(kst / DAY) * DAY + DAY;
    return new Date(next - 9 * 3_600_000).toISOString();
}

function quotaOf(boardId: string): BoardStaffQuota {
    if (!used.has(boardId)) used.set(boardId, mockFlags().has('board-limit-used') ? DEFAULT_LIMIT : 3);
    const n = used.get(boardId) ?? 0;
    return { limit: DEFAULT_LIMIT, used: n, remaining: Math.max(0, DEFAULT_LIMIT - n) };
}

function targetOf(boardId: string, userId: string) {
    const key = `${boardId}:${userId}`;
    let t = targets.get(key);
    if (!t) {
        const digits = userId.replace(/\D/g, '');
        const n = digits ? Number(digits.slice(-2)) % 3 : 0;
        const restricted = mockFlags().has('board-target-restricted');
        t = {
            warnings: n > 0 ? 1 : 0,
            restrictions: Math.max(0, n - 1) + (restricted ? 1 : 0),
            activeUntil: restricted ? new Date(Date.now() + 2 * DAY + 5 * 3_600_000).toISOString() : null,
            activeId: restricted ? String(++seq) : null,
        };
        targets.set(key, t);
    }
    // 끝난 제한은 읽을 때 지운다(서버의 `ends_at > now()` 판정과 같다)
    if (t.activeUntil && Date.parse(t.activeUntil) <= Date.now()) {
        t.activeUntil = null;
        t.activeId = null;
    }
    return t;
}

const NAMES = ['봄바람', '가을하늘', '코딩고양이', '주식초보', '익명의펭귄', '달빛산책', '라면러버', '새벽감성'];
const REASONS: BoardSanctionReasonCode[] = ['SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC', 'OTHER'];
const KINDS: BoardSanctionKind[] = ['WARNING', 'RESTRICT_1', 'WARNING', 'RESTRICT_3', 'RESTRICT_7', 'WARNING'];
const ISSUERS = [
    { nickname: '운영매니저', role: 'MANAGER' as const, byRole: 'MANAGER' as const },
    { nickname: '스텝하늘', role: 'STAFF' as const, byRole: 'STAFF' as const },
    { nickname: '스텝바다', role: 'STAFF' as const, byRole: 'OTHER' as const },
];

function seedRows(boardId: string) {
    if (rows) return rows;
    const now = Date.now();
    rows = Array.from({ length: 24 }, (_, i) => {
        const kind = KINDS[i % KINDS.length]!;
        const reasonCode = REASONS[i % REASONS.length]!;
        const createdAt = new Date(now - (i * 0.7 + 0.1) * DAY).toISOString();
        const days = UtilBoardSanction.daysOf(kind);
        const endsAt = days ? new Date(Date.parse(createdAt) + days * DAY).toISOString() : null;
        const issuer = ISSUERS[i % ISSUERS.length]!;
        const lifted = i === 4;
        const voided = i === 9;
        const status: BoardStaffSanctionRow['status'] = voided ? 'VOIDED' : lifted ? 'LIFTED' : endsAt && Date.parse(endsAt) <= now ? 'ENDED' : 'ACTIVE';
        return {
            id: String(8000 + i),
            no: 300 - i,
            target: { userId: String(5100 + (i % NAMES.length)), nickname: NAMES[i % NAMES.length]! },
            kind,
            reasonCode,
            reasonText: reasonCode === 'OTHER' ? '같은 글을 여러 번 올렸어요.' : i % 3 === 0 ? '안내 후에도 반복했어요.' : null,
            status,
            startsAt: createdAt,
            endsAt,
            createdAt,
            issuer: { nickname: issuer.nickname, role: issuer.role },
            lift: lifted ? { reason: '사과 글을 올려 풀어 줬어요.', at: new Date(Date.parse(createdAt) + 3_600_000).toISOString() } : null,
            source: null,
            canLift: false,
            boardId,
            byRole: issuer.byRole,
        };
    });
    return rows;
}

/** 지금 상태로 다시 계산한 줄 — 끝난 제한은 `ENDED`, 해제 단추는 매니저 · 진행 중 쓰기 제한만 */
function viewOf(row: MockRow, manager: boolean): BoardStaffSanctionRow {
    const status = row.status === 'ACTIVE' && row.endsAt && Date.parse(row.endsAt) <= Date.now() ? 'ENDED' : row.status;
    return mockClone({
        id: row.id,
        no: row.no,
        target: row.target,
        kind: row.kind,
        reasonCode: row.reasonCode,
        reasonText: row.reasonText,
        status,
        startsAt: row.startsAt,
        endsAt: row.endsAt,
        createdAt: row.createdAt,
        issuer: manager ? row.issuer : null,
        lift: row.lift,
        source: row.source,
        canLift: manager && status === 'ACTIVE' && UtilBoardSanction.isRestrict(row.kind),
    });
}

const ERR = {
    forbidden: () => new ApiError({ status: 403, code: 'BOARD_STAFF_FORBIDDEN', message: '이 게시판을 운영하는 회원만 할 수 있어요.' }),
    notFound: () => new ApiError({ status: 404, code: 'BOARD_STAFF_NOT_FOUND', message: '찾을 수 없는 화면이에요.' }),
    protected: () => new ApiError({ status: 409, code: 'BOARD_STAFF_TARGET_PROTECTED', message: '제재할 수 없는 회원이에요.' }),
    already: () => new ApiError({ status: 409, code: 'BOARD_STAFF_ALREADY_RESTRICTED', message: '이미 이 게시판에서 쓰기 제한 중인 회원이에요. 경고만 줄 수 있어요.' }),
    limit: (q: BoardStaffQuota, resetsAt: string) =>
        new ApiError({ status: 409, code: 'BOARD_STAFF_DAILY_LIMIT', message: '오늘 줄 수 있는 제재를 모두 썼어요. 한국 시간 0시에 다시 채워져요.', extras: { limit: q.limit, used: q.used, resetsAt } }),
    notLiftable: () => new ApiError({ status: 409, code: 'BOARD_SANCTION_NOT_LIFTABLE', message: '진행 중인 쓰기 제한만 해제할 수 있어요.' }),
    writeRestricted: (until: string) =>
        new ApiError({ status: 403, code: 'BOARD_WRITE_RESTRICTED', message: '이 게시판에서는 지금 글·댓글을 쓸 수 없어요.', extras: { until, reasonCode: 'SPAM', boardSanctionId: '7001' } }),
};

/* ── 게시판 응답 보강 ── */

/** 보는 나의 이 게시판 쓰기 제한(`board-restricted`) — 끝 시각은 방문 동안 고정 */
let myRestrictionUntil: string | null = null;
function myRestriction() {
    myRestrictionUntil ??= new Date(Date.now() + 2 * DAY + 3 * 3_600_000).toISOString();
    return { boardSanctionId: '7001', until: myRestrictionUntil, reasonCode: 'SPAM' as const };
}

/**
 * `GET /api/boards/{slug}` 에 서버가 아직 주지 않는 `viewer.staffTools`·`viewer.writeRestriction` 을 채운다(서버가 주면 그 값 그대로).
 * 쓰기 제한 중이면 서버도 글쓰기·댓글을 막는다고 보고 `canWrite`·`canComment` 를 끄고 까닭을 `BOARD_WRITE_RESTRICTED` 로 둔다(가정).
 */
export function mockBoardViewerExtras(detail: BoardDetail): BoardDetail {
    const flags = mockFlags();
    const viewer = { ...detail.viewer };
    if (viewer.staffTools === undefined) {
        viewer.staffTools = !viewer.guest && isStaff() ? ({ sanction: quotaOf(detail.id), resetsAt: nextKstMidnight(), manager: isManager() } satisfies BoardStaffTools) : null;
    }
    if (viewer.writeRestriction === undefined) {
        if (!viewer.guest && flags.has('board-restricted')) {
            viewer.writeRestriction = myRestriction();
            viewer.canWrite = false;
            viewer.writeBlockedReason = 'BOARD_WRITE_RESTRICTED';
            viewer.canComment = false;
            viewer.commentBlockedReason = 'BOARD_WRITE_RESTRICTED';
        } else viewer.writeRestriction = null;
    }
    return { ...detail, viewer };
}

/** 글·댓글 등록 직전 — `board-write-error` 면 서버가 쓰기 제한으로 거절한 것처럼 던진다 */
export function mockBoardWriteGuard(): void {
    if (mockFlags().has('board-write-error')) throw ERR.writeRestricted(new Date(Date.now() + 3 * DAY).toISOString());
}

/* ── 매니저·스텝 API ── */

export async function mockSanctionPreview(boardId: string, userId: string): Promise<BoardSanctionPreview> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('board-preview-fail')) throw MOCK_ERRORS.internal();
    if (!isStaff()) throw ERR.forbidden();
    if (flags.has('board-target-protected')) throw ERR.protected();
    const t = targetOf(boardId, userId);
    return mockClone({
        target: { userId, nickname: `회원${userId.slice(-4)}` },
        counts: { warnings: t.warnings, restrictions: t.restrictions },
        activeRestriction: t.activeUntil && t.activeId ? { boardSanctionId: t.activeId, until: t.activeUntil } : null,
        recommended: UtilBoardSanction.recommendedOf(t.warnings + t.restrictions),
        quota: quotaOf(boardId),
        resetsAt: nextKstMidnight(),
    });
}

export async function mockSanctionIssue(body: BoardSanctionIssueRequest, nickname: string): Promise<BoardSanctionIssued> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('board-sanction-fail')) throw MOCK_ERRORS.internal();
    if (!isStaff()) throw ERR.forbidden();
    if (flags.has('board-target-protected')) throw ERR.protected();
    if (!UtilBoardSanction.isReasonCode(body.reasonCode)) throw MOCK_ERRORS.validation({ reasonCode: '사유를 골라 주세요.' });
    const textProblem = UtilBoardSanction.textProblem(body.reasonText ?? '', body.reasonCode);
    if (textProblem === 'TOO_LONG') throw MOCK_ERRORS.validation({ reasonText: `덧붙임은 ${UtilBoardSanction.TEXT_MAX}자까지 쓸 수 있어요.` });
    if (textProblem === 'OTHER_TOO_SHORT') throw MOCK_ERRORS.validation({ reasonText: `기타 사유는 ${UtilBoardSanction.OTHER_TEXT_MIN}자 이상 적어 주세요.` });
    const t = targetOf(body.boardId, body.userId);
    const restrict = UtilBoardSanction.isRestrict(body.kind);
    if (restrict && (t.activeUntil || flags.has('board-already-restricted'))) throw ERR.already();
    const quota = quotaOf(body.boardId);
    const resetsAt = nextKstMidnight();
    if (quota.remaining <= 0) throw ERR.limit(quota, resetsAt);

    used.set(body.boardId, quota.used + 1);
    const now = new Date();
    const days = UtilBoardSanction.daysOf(body.kind);
    const endsAt = days ? new Date(now.getTime() + days * DAY).toISOString() : null;
    const id = String(++seq);
    if (restrict) {
        t.restrictions += 1;
        t.activeUntil = endsAt;
        t.activeId = id;
    } else t.warnings += 1;
    const list = seedRows(body.boardId);
    const me = isManager() ? ISSUERS[0]! : ISSUERS[1]!;
    list.unshift({
        id,
        no: 301 + (seq - 9000),
        target: { userId: body.userId, nickname },
        kind: body.kind,
        reasonCode: body.reasonCode,
        reasonText: body.reasonText,
        status: 'ACTIVE',
        startsAt: now.toISOString(),
        endsAt,
        createdAt: now.toISOString(),
        issuer: { nickname: me.nickname, role: me.role },
        lift: null,
        source: body.postId ? { postId: body.postId, commentId: body.commentId, title: null } : null,
        canLift: false,
        boardId: body.boardId,
        byRole: me.byRole,
    });
    return { id, no: 301 + (seq - 9000), kind: body.kind, startsAt: now.toISOString(), endsAt, quota: quotaOf(body.boardId), resetsAt };
}

export async function mockStaffSanctions(boardId: string, query: BoardStaffSanctionQuery): Promise<BoardStaffSanctionPage> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('board-sanctions-fail')) throw MOCK_ERRORS.internal();
    if (!isStaff()) throw ERR.notFound();
    const manager = isManager();
    const all = flags.has('board-sanctions-empty') ? [] : seedRows(boardId);
    const mine = all.filter((r) => manager || r.byRole === 'STAFF').map((r) => viewOf(r, manager));
    const scoped = mine.filter((r) => (query.scope === 'ALL' ? true : query.scope === 'ACTIVE' ? r.status === 'ACTIVE' : r.status !== 'ACTIVE'));
    const totalPages = Math.max(1, Math.ceil(scoped.length / PAGE_SIZE));
    const page = Math.min(Math.max(1, query.page), totalPages);
    return { manager, items: scoped.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE), page, totalPages, totalCount: scoped.length };
}

export async function mockStaffLift(id: string, body: BoardSanctionLiftRequest): Promise<BoardStaffSanctionRow> {
    await mockDelay();
    if (mockFlags().has('board-lift-fail')) throw MOCK_ERRORS.internal();
    if (!isManager()) throw ERR.forbidden();
    const problem = UtilBoardSanction.liftReasonProblem(body.reason ?? '');
    if (problem === 'EMPTY') throw MOCK_ERRORS.validation({ reason: '해제 사유를 적어 주세요.' });
    if (problem === 'TOO_LONG') throw MOCK_ERRORS.validation({ reason: `해제 사유는 ${UtilBoardSanction.LIFT_REASON_MAX}자까지 쓸 수 있어요.` });
    const row = rows?.find((r) => r.id === id);
    if (!row) throw new ApiError({ status: 404, code: 'BOARD_SANCTION_NOT_FOUND', message: '없는 제재예요.' });
    const current = viewOf(row, true);
    if (!current.canLift) throw ERR.notLiftable();
    row.status = 'LIFTED';
    row.lift = { reason: body.reason.trim(), at: new Date().toISOString() };
    const t = targets.get(`${row.boardId}:${row.target.userId}`);
    if (t && t.activeId === row.id) {
        t.activeUntil = null;
        t.activeId = null;
    }
    return viewOf(row, true);
}
