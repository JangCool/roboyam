// MOCK: 게시판 제재 목업 — 계약 `docs/features/board-sanction-contract.md` 「API 후보 > 관리자」의 응답을 메모리에서 흉내 낸다. 서버가 붙으면 이 파일을 지운다.
// 쓰는 곳: `useBoardSanctionApi`·`useBoardSanctionReviewApi`(전부), `useAppealApi`(게시판 제재 이의제기 섞기·목업 번호 상세·인용/기각),
//          `useSanctionApi`(목업 회원의 사이트 제재 현황·부여·제재 하나 — 반복 검토의 「사이트 제재 부여」를 끝까지 눌러 볼 수 있게), `useMemberApi`(회원 상세 `boardSanctions` 요약).
//
// `?mock=` 스위치(쉼표로 여러 개):
//   board-sanction-empty         이력·반복 검토 목록을 비운다
//   board-sanction-fail          이력·반복 검토 목록이 500
//   board-sanction-stale         [해제]·[무효]가 409(`BOARD_SANCTION_NOT_LIFTABLE`·`BOARD_SANCTION_ALREADY_VOIDED`) — 그 사이 바뀐 상황
//   board-sanction-review-closed 반복 검토 닫기가 409 `BOARD_SANCTION_REVIEW_CLOSED`(다른 운영자가 먼저 닫음)
//   appeal-board                 `/appeals` 목록에 게시판 제재 이의제기 2건을 섞는다(상세·인용·기각은 스위치 없이도 목업 번호면 목업으로 답한다)
// 문구는 서버 몫이라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AppealDetail, AppealItem, AppealListView, AppealQuery } from '~/composables/sanction/api/useAppealApi.types';
import type {
    BoardSanctionBoardRef,
    BoardSanctionDetail,
    BoardSanctionIssuer,
    BoardSanctionKind,
    BoardSanctionLine,
    BoardSanctionListView,
    BoardSanctionQuery,
    BoardSanctionReasonCode,
    BoardSanctionTriggerRef,
    MemberBoardSanctionSummary,
} from '~/composables/sanction/api/useBoardSanctionApi.types';
import type {
    BoardSanctionOtherBoard,
    BoardSanctionReviewCloseRequest,
    BoardSanctionReviewDetail,
    BoardSanctionReviewItem,
    BoardSanctionReviewListView,
    BoardSanctionReviewQuery,
    BoardSanctionReviewResult,
    BoardSanctionReviewSiteSanction,
    BoardSanctionReviewSummary,
} from '~/composables/sanction/api/useBoardSanctionReviewApi.types';
import type { AppealStatus, SanctionActorRef, SanctionCreateRequest, SanctionKind, SanctionLine, SanctionPreview, SanctionUserRef } from '~/composables/sanction/api/useSanctionApi.types';

const DAY = 24 * 60 * 60 * 1000;
const NOW = Date.now();
const ago = (days: number): string => new Date(NOW - days * DAY).toISOString();
const MOCK_OPERATOR: SanctionActorRef = { id: '1', name: '목업 운영자' };

/* ── 바탕 ── */

const BOARDS = {
    free: { id: '880001', slug: 'free', name: '자유게시판', nameEn: 'Free board' },
    humor: { id: '880002', slug: 'humor', name: '유머', nameEn: 'Humor' },
    invest: { id: '880003', slug: 'invest', name: '투자 이야기', nameEn: 'Investing talk' },
    robot: { id: '880004', slug: 'robot-news', name: '로봇 뉴스', nameEn: 'Robot news' },
} satisfies Record<string, BoardSanctionBoardRef>;
type BoardKey = keyof typeof BOARDS;

const STAFF = {
    forest: { id: '870001', nickname: '숲지기', role: 'MANAGER' },
    dawn: { id: '870002', nickname: '새벽별', role: 'STAFF' },
    smile: { id: '870003', nickname: '웃음한스푼', role: 'MANAGER' },
    giggle: { id: '870004', nickname: '깔깔이', role: 'STAFF' },
    chart: { id: '870005', nickname: '차트장인', role: 'MANAGER' },
    steady: { id: '870006', nickname: '정석투자', role: 'STAFF' },
    minute: { id: '870007', nickname: '분봉러', role: 'STAFF' },
    bolt: { id: '870008', nickname: '볼트너트', role: 'MANAGER' },
} satisfies Record<string, BoardSanctionIssuer>;
type StaffKey = keyof typeof STAFF;

const USERS = {
    ad: { id: '910001', nickname: '광고왕김씨', grade: null, status: 'ACTIVE' },
    leading: { id: '910002', nickname: '리딩방초대', grade: null, status: 'ACTIVE' },
    fire: { id: '910003', nickname: '불타는키보드', grade: null, status: 'ACTIVE' },
    random: { id: '910004', nickname: '아무말대잔치', grade: null, status: 'ACTIVE' },
    offtopic: { id: '910005', nickname: '주제이탈러', grade: null, status: 'ACTIVE' },
    quiet: { id: '910006', nickname: '조용한독자', grade: null, status: 'ACTIVE' },
    robofan: { id: '910007', nickname: '로봇덕후99', grade: null, status: 'ACTIVE' },
    sprout: { id: '910008', nickname: '새싹회원', grade: null, status: 'ACTIVE' },
} satisfies Record<string, SanctionUserRef>;
type UserKey = keyof typeof USERS;

const RESTRICT_DAYS: Record<BoardSanctionKind, number> = { WARNING: 0, RESTRICT_1: 1, RESTRICT_3: 3, RESTRICT_7: 7 };

/** 메모리에 든 게시판 제재 — 상태는 읽을 때 계산한다(진행 중 판정은 `ends_at > now()`) */
interface StoredSanction {
    id: string;
    no: number;
    kind: BoardSanctionKind;
    board: BoardKey;
    user: UserKey;
    reasonCode: BoardSanctionReasonCode;
    reasonText: string | null;
    by: StaffKey;
    createdAt: string;
    trigger: BoardSanctionTriggerRef | null;
    state: 'LIVE' | 'LIFTED' | 'VOIDED';
    liftedBy: BoardSanctionLine['liftedBy'];
    liftedAt: string | null;
    liftReason: string | null;
    voidedAt: string | null;
    voidedBy: SanctionActorRef | null;
    voidSource: BoardSanctionLine['voidSource'];
    voidReason: string | null;
    acknowledgedAt: string | null;
    appealId: string | null;
}

let postSeq = 5100;
function seed(
    no: number,
    user: UserKey,
    board: BoardKey,
    kind: BoardSanctionKind,
    reasonCode: BoardSanctionReasonCode,
    by: StaffKey,
    daysAgo: number,
    extra: Partial<StoredSanction> & { on?: 'POST' | 'COMMENT' | null; title?: string } = {},
): StoredSanction {
    const on = extra.on === undefined ? 'POST' : extra.on;
    const postId = String(++postSeq);
    return {
        id: String(860000 + no),
        no,
        kind,
        board,
        user,
        reasonCode,
        reasonText: extra.reasonText ?? null,
        by,
        createdAt: ago(daysAgo),
        trigger: on ? { type: on, postId, commentId: on === 'COMMENT' ? String(Number(postId) * 10 + 3) : null, title: extra.title ?? '(제목 없는 글)' } : null,
        state: extra.state ?? 'LIVE',
        liftedBy: extra.liftedBy ?? null,
        liftedAt: extra.liftedAt ?? null,
        liftReason: extra.liftReason ?? null,
        voidedAt: extra.voidedAt ?? null,
        voidedBy: extra.voidedBy ?? null,
        voidSource: extra.voidSource ?? null,
        voidReason: extra.voidReason ?? null,
        acknowledgedAt: extra.acknowledgedAt ?? null,
        appealId: extra.appealId ?? null,
    };
}

const SANCTIONS: StoredSanction[] = [
    seed(101, 'random', 'free', 'WARNING', 'OFF_TOPIC', 'dawn', 22, { title: '오늘 점심 뭐 먹지', acknowledgedAt: ago(21.9) }),
    seed(102, 'random', 'free', 'RESTRICT_1', 'OFF_TOPIC', 'dawn', 21, { title: '아무 말 대잔치 2탄' }),
    seed(103, 'random', 'free', 'RESTRICT_3', 'ABUSE', 'forest', 20, { on: 'COMMENT', title: '다들 어떻게 생각하세요?', reasonText: '다른 회원에게 반복해서 비아냥' }),
    seed(104, 'offtopic', 'robot', 'WARNING', 'OFF_TOPIC', 'bolt', 16, { title: '주식 얘기 좀 해도 되나요', acknowledgedAt: ago(15.9) }),
    seed(105, 'offtopic', 'robot', 'RESTRICT_1', 'OFF_TOPIC', 'bolt', 15.5, {
        title: '테슬라 실적 발표 정리',
        state: 'VOIDED',
        voidedAt: ago(14),
        voidedBy: { id: '2', name: '운영팀 민지' },
        voidSource: 'REVIEW',
        voidReason: '로봇 기업 실적은 로봇 뉴스 주제에 들어가요.',
    }),
    seed(106, 'offtopic', 'robot', 'RESTRICT_3', 'OFF_TOPIC', 'bolt', 15, {
        title: '휴머노이드 관련주 정리',
        state: 'VOIDED',
        voidedAt: ago(14),
        voidedBy: { id: '2', name: '운영팀 민지' },
        voidSource: 'REVIEW',
        voidReason: '로봇 기업 실적은 로봇 뉴스 주제에 들어가요.',
    }),
    seed(107, 'sprout', 'free', 'WARNING', 'OFF_TOPIC', 'dawn', 12, {
        title: '가입 인사 드려요',
        state: 'VOIDED',
        voidedAt: ago(10),
        voidedBy: { id: '2', name: '운영팀 민지' },
        voidSource: 'APPEAL',
        voidReason: null,
        appealId: '990002',
    }),
    seed(108, 'robofan', 'robot', 'RESTRICT_1', 'SPAM', 'bolt', 9, {
        title: '직접 써 본 로봇청소기 후기',
        state: 'LIFTED',
        liftedBy: { type: 'MANAGER', id: STAFF.bolt.id, name: STAFF.bolt.nickname },
        liftedAt: ago(8.8),
        liftReason: '광고가 아니라 직접 쓴 후기였어요.',
    }),
    seed(109, 'ad', 'free', 'WARNING', 'SPAM', 'dawn', 6.5, { title: '부업으로 월 300 버는 법', acknowledgedAt: ago(6.4) }),
    seed(110, 'leading', 'invest', 'WARNING', 'INVESTMENT', 'steady', 6, { on: 'COMMENT', title: '이번 주 반도체 어떻게 보세요', reasonText: '댓글로 유료 리딩방 안내' }),
    seed(111, 'ad', 'free', 'RESTRICT_1', 'SPAM', 'dawn', 5, { title: '부업으로 월 300 버는 법 (2)' }),
    seed(112, 'fire', 'humor', 'WARNING', 'ABUSE', 'giggle', 5, { on: 'COMMENT', title: '월요일 출근길 짤' }),
    seed(113, 'leading', 'invest', 'RESTRICT_1', 'INVESTMENT', 'minute', 4, { title: '무료 종목 추천방 오픈' }),
    seed(114, 'ad', 'free', 'RESTRICT_3', 'SPAM', 'forest', 4, { title: '부업으로 월 300 버는 법 (3)', reasonText: '같은 광고 세 번째' }),
    seed(115, 'fire', 'humor', 'WARNING', 'ABUSE', 'giggle', 3, { on: 'COMMENT', title: '고양이 짤 모음' }),
    seed(116, 'quiet', 'invest', 'RESTRICT_7', 'OTHER', 'minute', 2, { on: 'COMMENT', title: '이번 분기 실적 시즌 정리', reasonText: '같은 종목 글마다 반대 의견만 반복해서 달아요', appealId: '990001' }),
    seed(117, 'leading', 'free', 'WARNING', 'SPAM', 'forest', 2, { title: '같이 공부하실 분', reasonText: '오픈 채팅 링크' }),
    seed(118, 'fire', 'humor', 'RESTRICT_1', 'ABUSE', 'giggle', 0.3, { on: 'COMMENT', title: '퇴근길 웃긴 영상' }),
    seed(119, 'leading', 'invest', 'RESTRICT_3', 'INVESTMENT', 'chart', 1, { title: '수익 인증합니다 (리딩방)' }),
    seed(120, 'ad', 'humor', 'WARNING', 'SPAM', 'giggle', 1, { title: '웃긴 짤 + 부업 링크' }),
    seed(121, 'ad', 'free', 'RESTRICT_7', 'SPAM', 'forest', 0.5, { title: '부업으로 월 300 버는 법 (4)' }),
    seed(122, 'robofan', 'humor', 'WARNING', 'OFF_TOPIC', 'smile', 0.1, { title: '로봇청소기 고장 썰' }),
];

interface StoredReview {
    id: string;
    no: number;
    user: UserKey;
    board: BoardKey;
    openedAt: string;
    closedAt: string | null;
    closedBy: SanctionActorRef | null;
    result: BoardSanctionReviewResult | null;
    memo: string | null;
    siteSanction: BoardSanctionReviewSiteSanction | null;
    voidedSanctionIds: string[];
    /** 닫힌 건의 그때 수치(열린 건은 읽을 때 센다) */
    snapshot: { recentCount: number; recentBoardCount: number; sanctionIds: string[] } | null;
}

const REVIEWS: StoredReview[] = [
    { id: '850004', no: 28, user: 'random', board: 'free', openedAt: ago(20), closedAt: ago(19), closedBy: { id: '2', name: '운영팀 민지' }, result: 'KEPT', memo: '처음 반복이라 지켜보기로 했어요. 다시 열리면 사이트 제재를 검토해요.', siteSanction: null, voidedSanctionIds: [], snapshot: { recentCount: 3, recentBoardCount: 1, sanctionIds: ['860101', '860102', '860103'] } },
    { id: '850005', no: 29, user: 'offtopic', board: 'robot', openedAt: ago(15), closedAt: ago(14), closedBy: { id: '2', name: '운영팀 민지' }, result: 'VOIDED', memo: '매니저가 주제 범위를 너무 좁게 봤어요. 매니저에게 따로 안내했어요.', siteSanction: null, voidedSanctionIds: ['860105', '860106'], snapshot: { recentCount: 3, recentBoardCount: 1, sanctionIds: ['860104', '860105', '860106'] } },
    { id: '850002', no: 30, user: 'ad', board: 'free', openedAt: ago(4), closedAt: null, closedBy: null, result: null, memo: null, siteSanction: null, voidedSanctionIds: [], snapshot: null },
    { id: '850001', no: 31, user: 'leading', board: 'invest', openedAt: ago(1), closedAt: null, closedBy: null, result: null, memo: null, siteSanction: null, voidedSanctionIds: [], snapshot: null },
    { id: '850003', no: 32, user: 'fire', board: 'humor', openedAt: ago(0.3), closedAt: null, closedBy: null, result: null, memo: null, siteSanction: null, voidedSanctionIds: [], snapshot: null },
];

/** 게시판 제재 이의제기 목업 — 같은 `sanction_appeals` 표(정책 4) */
interface StoredAppeal {
    id: string;
    no: number;
    sanctionId: string;
    status: AppealStatus;
    reason: string;
    createdAt: string;
    answer: string | null;
    answeredAt: string | null;
    answeredBy: SanctionActorRef | null;
}

const APPEALS: StoredAppeal[] = [
    { id: '990001', no: 901, sanctionId: '860116', status: 'PENDING', reason: '반대 의견도 근거를 달아 쓴 댓글이에요. 욕설이나 비방은 하지 않았는데 7일 제한은 너무해요.', createdAt: ago(1.5), answer: null, answeredAt: null, answeredBy: null },
    { id: '990002', no: 902, sanctionId: '860107', status: 'ACCEPTED', reason: '가입 인사는 자유게시판에 쓰라고 안내받아서 썼어요. 주제에 맞지 않는다는 경고는 억울해요.', createdAt: ago(11.5), answer: '확인해 보니 가입 인사는 자유게시판 주제에 맞아요. 경고를 무효로 했어요.', answeredAt: ago(10), answeredBy: { id: '2', name: '운영팀 민지' } },
];

/* ── 읽을 때 계산 ── */

const byId = (id: string) => SANCTIONS.find((s) => s.id === id) ?? null;
const live = (s: StoredSanction) => s.state !== 'VOIDED';
const endsAtOf = (s: StoredSanction): string | null => (s.kind === 'WARNING' ? null : new Date(Date.parse(s.createdAt) + RESTRICT_DAYS[s.kind] * DAY).toISOString());

function statusOf(s: StoredSanction): BoardSanctionLine['status'] {
    if (s.state === 'VOIDED') return 'VOIDED';
    if (s.state === 'LIFTED') return 'LIFTED';
    if (s.kind === 'WARNING') return 'ACTIVE';
    return Date.parse(endsAtOf(s)!) > Date.now() ? 'ACTIVE' : 'ENDED';
}

function toLine(s: StoredSanction): BoardSanctionLine {
    const endsAt = endsAtOf(s);
    const status = statusOf(s);
    const appeal = s.appealId ? APPEALS.find((a) => a.id === s.appealId) : undefined;
    return {
        id: s.id,
        no: s.no,
        kind: s.kind,
        status,
        board: { ...BOARDS[s.board] },
        user: { ...USERS[s.user] },
        reasonCode: s.reasonCode,
        reasonText: s.reasonText,
        issuedBy: { ...STAFF[s.by] },
        startsAt: s.createdAt,
        endsAt,
        createdAt: s.createdAt,
        trigger: s.trigger ? { ...s.trigger } : null,
        liftedBy: s.liftedBy,
        liftedAt: s.liftedAt,
        liftReason: s.liftReason,
        voidedAt: s.voidedAt,
        voidedBy: s.voidedBy,
        voidSource: s.voidSource,
        voidReason: s.voidReason,
        endedAt: status === 'ENDED' ? endsAt : null,
        acknowledgedAt: s.acknowledgedAt,
        appeal: appeal ? { id: appeal.id, no: appeal.no, status: appeal.status } : null,
        appealDeadline: status === 'VOIDED' ? null : new Date(Date.parse(s.createdAt) + 30 * DAY).toISOString(),
    };
}

const newestFirst = (a: StoredSanction, b: StoredSanction) => Date.parse(b.createdAt) - Date.parse(a.createdAt);

function historyOf(user: UserKey): BoardSanctionLine[] {
    return SANCTIONS.filter((s) => s.user === user).sort(newestFirst).slice(0, 30).map(toLine);
}

/** 마지막으로 닫힌 검토의 닫은 시각 — 그 이전 제재는 다시 세지 않는다 */
function lastClosedAt(user: UserKey, board: BoardKey): number {
    return Math.max(0, ...REVIEWS.filter((r) => r.user === user && r.board === board && r.closedAt).map((r) => Date.parse(r.closedAt!)));
}

function recentOf(user: UserKey, board: BoardKey | null, at = Date.now()): StoredSanction[] {
    return SANCTIONS.filter((s) => s.user === user && (board === null || s.board === board) && Date.parse(s.createdAt) > at - 7 * DAY && Date.parse(s.createdAt) <= at);
}

function reviewNumbers(r: StoredReview): { recentCount: number; recentBoardCount: number; sanctionIds: string[] } {
    if (r.snapshot) return r.snapshot;
    const since = lastClosedAt(r.user, r.board);
    const inWindow = recentOf(r.user, r.board).filter((s) => Date.parse(s.createdAt) > since);
    const boards = new Set(recentOf(r.user, null).filter(live).map((s) => s.board));
    return { recentCount: inWindow.filter(live).length, recentBoardCount: boards.size, sanctionIds: inWindow.sort(newestFirst).map((s) => s.id) };
}

function toReviewItem(r: StoredReview): BoardSanctionReviewItem {
    const n = reviewNumbers(r);
    return {
        id: r.id,
        no: r.no,
        status: r.closedAt ? 'CLOSED' : 'OPEN',
        user: { ...USERS[r.user] },
        board: { ...BOARDS[r.board] },
        recentCount: n.recentCount,
        recentBoardCount: n.recentBoardCount,
        openedAt: r.openedAt,
        closedAt: r.closedAt,
        result: r.result,
    };
}

function toReviewDetail(r: StoredReview): BoardSanctionReviewDetail {
    const others: BoardSanctionOtherBoard[] = (Object.keys(BOARDS) as BoardKey[])
        .filter((b) => b !== r.board)
        .map((b) => {
            const all = SANCTIONS.filter((s) => s.user === r.user && s.board === b);
            const running = all.find((s) => statusOf(s) === 'ACTIVE' && s.kind !== 'WARNING');
            return { board: { ...BOARDS[b] }, recentCount: recentOf(r.user, b).filter(live).length, total: all.filter(live).length, activeUntil: running ? endsAtOf(running) : null };
        })
        .filter((o) => o.total > 0 || o.recentCount > 0);
    return {
        ...toReviewItem(r),
        sanctions: SANCTIONS.filter((s) => s.user === r.user && s.board === r.board).sort(newestFirst).slice(0, 20).map(toLine),
        reviewSanctionIds: [...reviewNumbers(r).sanctionIds],
        otherBoards: others,
        closedBy: r.closedBy,
        memo: r.memo,
        siteSanction: r.siteSanction,
        voidedSanctionIds: [...r.voidedSanctionIds],
    };
}

function voidSanction(s: StoredSanction, source: NonNullable<BoardSanctionLine['voidSource']>, reason: string | null): void {
    s.state = 'VOIDED';
    s.voidedAt = new Date().toISOString();
    s.voidedBy = MOCK_OPERATOR;
    s.voidSource = source;
    s.voidReason = reason;
}

function failIfAsked(): void {
    if (mockFlags().has('board-sanction-fail')) throw MOCK_ERRORS.internal();
}

function page<T>(all: T[], pageNo = 0, size = 20): { total: number; page: number; size: number; items: T[] } {
    return { total: all.length, page: pageNo, size, items: all.slice(pageNo * size, pageNo * size + size) };
}

/* ── 게시판 제재 `/api/admin/board-sanctions` ── */

export async function mockBoardSanctionList(query: BoardSanctionQuery): Promise<BoardSanctionListView> {
    await mockDelay();
    failIfAsked();
    const flags = mockFlags();
    const lines = flags.has('board-sanction-empty') ? [] : SANCTIONS.map(toLine);
    const isActive = (l: BoardSanctionLine) => l.status === 'ACTIVE' && l.kind !== 'WARNING';
    const isClosed = (l: BoardSanctionLine) => l.status !== 'ACTIVE';
    const q = (query.q ?? '').trim().toLowerCase();
    const from = query.from ? Date.parse(query.from) : Number.NEGATIVE_INFINITY;
    const to = query.to ? Date.parse(query.to) : Number.POSITIVE_INFINITY;
    const found = lines
        .filter((l) => (query.scope === 'ACTIVE' ? isActive(l) : query.scope === 'CLOSED' ? isClosed(l) : true))
        .filter((l) => !query.boardId || l.board.id === query.boardId)
        .filter((l) => !query.kind || l.kind === query.kind)
        .filter((l) => !query.userId || l.user.id === query.userId)
        .filter((l) => !q || (query.searchField === 'SANCTION_NO' ? String(l.no) === q : l.user.nickname.toLowerCase().includes(q)))
        .filter((l) => Date.parse(l.createdAt) >= from && Date.parse(l.createdAt) <= to)
        .sort((a, b) => (query.sort === 'oldest' ? 1 : -1) * (Date.parse(a.createdAt) - Date.parse(b.createdAt)));
    return mockClone({
        counts: { all: lines.length, active: lines.filter(isActive).length, closed: lines.filter(isClosed).length },
        ...page(found, query.page, query.size),
        boards: Object.values(BOARDS).sort((a, b) => a.name.localeCompare(b.name, 'ko')),
    });
}

function notFound(): ApiError {
    return new ApiError({ status: 404, code: 'BOARD_SANCTION_NOT_FOUND', message: '게시판 제재를 찾을 수 없어요.' });
}

function detailOf(s: StoredSanction): BoardSanctionDetail {
    return mockClone({ sanction: toLine(s), history: historyOf(s.user) });
}

export async function mockBoardSanctionGet(id: string): Promise<BoardSanctionDetail> {
    await mockDelay();
    const s = byId(id);
    if (!s) throw notFound();
    return detailOf(s);
}

function reasonError(reason: string): ApiError | null {
    const n = Array.from(reason.trim()).length;
    if (n === 0) return MOCK_ERRORS.validation({ reason: '사유를 적어 주세요.' });
    return n > 500 ? MOCK_ERRORS.validation({ reason: '사유는 500자까지 쓸 수 있어요.' }) : null;
}

export async function mockBoardSanctionLift(id: string, reason: string): Promise<BoardSanctionDetail> {
    await mockDelay();
    const s = byId(id);
    if (!s) throw notFound();
    const invalid = reasonError(reason);
    if (invalid) throw invalid;
    if (mockFlags().has('board-sanction-stale') || s.kind === 'WARNING' || statusOf(s) !== 'ACTIVE') {
        throw new ApiError({ status: 409, code: 'BOARD_SANCTION_NOT_LIFTABLE', message: '진행 중인 쓰기 제한만 풀 수 있어요. 이미 끝났거나 풀린 제재예요.' });
    }
    s.state = 'LIFTED';
    s.liftedBy = { type: 'OPERATOR', id: MOCK_OPERATOR.id, name: MOCK_OPERATOR.name };
    s.liftedAt = new Date().toISOString();
    s.liftReason = reason.trim();
    return detailOf(s);
}

export async function mockBoardSanctionVoid(id: string, reason: string): Promise<BoardSanctionDetail> {
    await mockDelay();
    const s = byId(id);
    if (!s) throw notFound();
    const invalid = reasonError(reason);
    if (invalid) throw invalid;
    if (mockFlags().has('board-sanction-stale') || s.state === 'VOIDED') {
        throw new ApiError({ status: 409, code: 'BOARD_SANCTION_ALREADY_VOIDED', message: '이미 무효가 된 제재예요.' });
    }
    voidSanction(s, 'OPERATOR', reason.trim());
    return detailOf(s);
}

/* ── 반복 검토 `/api/admin/board-sanction-reviews` ── */

export async function mockReviewList(query: BoardSanctionReviewQuery): Promise<BoardSanctionReviewListView> {
    await mockDelay();
    failIfAsked();
    const all = mockFlags().has('board-sanction-empty') ? [] : REVIEWS.map(toReviewItem);
    const status = query.status ?? 'OPEN';
    const found = all
        .filter((r) => r.status === status)
        .sort((a, b) => (status === 'OPEN' ? Date.parse(a.openedAt) - Date.parse(b.openedAt) : Date.parse(b.closedAt ?? '') - Date.parse(a.closedAt ?? '')));
    return mockClone({ counts: { open: all.filter((r) => r.status === 'OPEN').length, closed: all.filter((r) => r.status === 'CLOSED').length }, ...page(found, query.page, query.size) });
}

export async function mockReviewSummary(): Promise<BoardSanctionReviewSummary> {
    await mockDelay();
    return { open: mockFlags().has('board-sanction-empty') ? 0 : REVIEWS.filter((r) => !r.closedAt).length };
}

function reviewNotFound(): ApiError {
    return new ApiError({ status: 404, code: 'BOARD_SANCTION_REVIEW_NOT_FOUND', message: '반복 검토 건을 찾을 수 없어요.' });
}

export async function mockReviewGet(id: string): Promise<BoardSanctionReviewDetail> {
    await mockDelay();
    const r = REVIEWS.find((x) => x.id === id);
    if (!r) throw reviewNotFound();
    return mockClone(toReviewDetail(r));
}

export async function mockReviewClose(id: string, body: BoardSanctionReviewCloseRequest): Promise<BoardSanctionReviewDetail> {
    await mockDelay();
    const r = REVIEWS.find((x) => x.id === id);
    if (!r) throw reviewNotFound();
    if (mockFlags().has('board-sanction-review-closed') || r.closedAt) {
        throw new ApiError({ status: 409, code: 'BOARD_SANCTION_REVIEW_CLOSED', message: '이미 닫힌 검토예요. 다른 운영자가 먼저 닫았어요.' });
    }
    const memo = body.memo?.trim() || null;
    if (memo && Array.from(memo).length > 200) throw MOCK_ERRORS.validation({ memo: '메모는 200자까지 쓸 수 있어요.' });
    const numbers = reviewNumbers(r);
    let site: BoardSanctionReviewSiteSanction | null = null;
    if (body.result === 'SITE_SANCTIONED') {
        const line = SITE_CREATED.find((l) => l.id === body.siteSanctionId);
        if (!body.siteSanctionId || (line && line.user.id !== USERS[r.user].id)) throw MOCK_ERRORS.validation({ siteSanctionId: '이 회원에게 준 사이트 제재가 아니에요.' });
        site = { id: body.siteSanctionId, no: line?.no ?? 0, kind: line?.kind ?? 'WARNING' };
    }
    const toVoid = body.result === 'VOIDED' ? body.voidSanctionIds.map(byId).filter((s): s is StoredSanction => s !== null && numbers.sanctionIds.includes(s.id) && s.state !== 'VOIDED') : [];
    if (body.result === 'VOIDED' && toVoid.length === 0) throw MOCK_ERRORS.validation({ voidSanctionIds: '무효로 할 제재를 하나 이상 골라 주세요.' });
    // 닫기 전 수치를 남긴다(닫힌 건은 그때 기준)
    r.snapshot = { recentCount: numbers.recentCount, recentBoardCount: numbers.recentBoardCount, sanctionIds: [...numbers.sanctionIds] };
    for (const s of toVoid) voidSanction(s, 'REVIEW', memo);
    r.closedAt = new Date().toISOString();
    r.closedBy = MOCK_OPERATOR;
    r.result = body.result;
    r.memo = memo;
    r.siteSanction = site;
    r.voidedSanctionIds = toVoid.map((s) => s.id);
    return mockClone(toReviewDetail(r));
}

/* ── 이의제기 `/api/admin/appeals` 보강 ── */

export function isMockBoardAppeal(id: string): boolean {
    return APPEALS.some((a) => a.id === id);
}

function appealUser(a: StoredAppeal): SanctionUserRef {
    return { ...USERS[byId(a.sanctionId)!.user] };
}

function toAppealItem(a: StoredAppeal): AppealItem {
    const line = toLine(byId(a.sanctionId)!);
    return {
        id: a.id,
        no: a.no,
        status: a.status,
        user: appealUser(a),
        kind: 'BOARD',
        sanction: null,
        boardSanction: { id: line.id, no: line.no, kind: line.kind, status: line.status, board: line.board, issuedBy: line.issuedBy, startsAt: line.startsAt, endsAt: line.endsAt },
        createdAt: a.createdAt,
        answeredAt: a.answeredAt,
    };
}

function toAppealDetail(a: StoredAppeal): AppealDetail {
    const s = byId(a.sanctionId)!;
    return mockClone({
        id: a.id,
        no: a.no,
        status: a.status,
        reason: a.reason,
        createdAt: a.createdAt,
        answer: a.answer,
        answeredAt: a.answeredAt,
        answeredBy: a.answeredBy,
        user: appealUser(a),
        kind: 'BOARD',
        sanction: null,
        history: [],
        boardSanction: toLine(s),
        boardHistory: historyOf(s.user),
    });
}

/** 실제 목록 응답에 게시판 제재 이의제기를 섞는다 — `?mock=appeal-board` 일 때만, 첫 쪽에만 */
export function mockMixBoardAppeals(view: AppealListView, query: AppealQuery): AppealListView {
    if (!mockFlags().has('appeal-board')) return view;
    const status = query.status ?? 'ALL';
    const extra = APPEALS.filter((a) => status === 'ALL' || a.status === status).map(toAppealItem);
    const counts = {
        pending: view.counts.pending + APPEALS.filter((a) => a.status === 'PENDING').length,
        accepted: view.counts.accepted + APPEALS.filter((a) => a.status === 'ACCEPTED').length,
        rejected: view.counts.rejected + APPEALS.filter((a) => a.status === 'REJECTED').length,
    };
    if ((query.page ?? 0) > 0) return { ...view, counts, total: view.total + extra.length };
    const dir = query.sort === 'newest' ? -1 : 1;
    const items = [...view.items, ...mockClone(extra)].sort((a, b) => dir * (Date.parse(a.createdAt) - Date.parse(b.createdAt)));
    return { ...view, counts, total: view.total + extra.length, items };
}

export async function mockBoardAppealGet(id: string): Promise<AppealDetail> {
    await mockDelay();
    const a = APPEALS.find((x) => x.id === id);
    if (!a) throw new ApiError({ status: 404, code: 'APPEAL_NOT_FOUND', message: '이의제기를 찾을 수 없어요.' });
    return toAppealDetail(a);
}

export async function mockBoardAppealAnswer(id: string, accept: boolean, answer: string): Promise<AppealDetail> {
    await mockDelay();
    const a = APPEALS.find((x) => x.id === id);
    if (!a) throw new ApiError({ status: 404, code: 'APPEAL_NOT_FOUND', message: '이의제기를 찾을 수 없어요.' });
    const n = Array.from(answer.trim()).length;
    if (n === 0 || n > 500) throw MOCK_ERRORS.validation({ answer: '답변은 1~500자로 적어 주세요.' });
    if (a.status !== 'PENDING') throw new ApiError({ status: 409, code: 'APPEAL_CLOSED', message: '이미 처리된 이의제기예요.' });
    a.status = accept ? 'ACCEPTED' : 'REJECTED';
    a.answer = answer.trim();
    a.answeredAt = new Date().toISOString();
    a.answeredBy = MOCK_OPERATOR;
    if (accept) voidSanction(byId(a.sanctionId)!, 'APPEAL', null);
    return toAppealDetail(a);
}

/* ── 사이트 제재 `/api/admin/sanctions` 보강 — 목업 회원만(반복 검토의 「사이트 제재 부여」를 끝까지 눌러 보게) ── */

const SITE_CREATED: SanctionLine[] = [];
let siteSeq = 0;
const SITE_LADDER: { kind: SanctionKind; rank: number }[] = [
    { kind: 'WARNING', rank: 1 },
    { kind: 'SUSPEND_3', rank: 3 },
    { kind: 'SUSPEND_7', rank: 4 },
    { kind: 'SUSPEND_30', rank: 5 },
    { kind: 'BAN', rank: 6 },
];

export function isMockBoardUser(userId: string): boolean {
    return Object.values(USERS).some((u) => u.id === userId);
}

export function isMockSiteSanction(id: string): boolean {
    return SITE_CREATED.some((l) => l.id === id);
}

export async function mockSitePreview(userId: string): Promise<SanctionPreview> {
    await mockDelay();
    const user = Object.values(USERS).find((u) => u.id === userId)!;
    const history = SITE_CREATED.filter((l) => l.user.id === userId).sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    const warningCount = Math.min(2, history.filter((l) => l.kind === 'WARNING' && l.status !== 'VOIDED').length);
    const active = history.find((l) => l.kind !== 'WARNING' && l.status === 'ACTIVE' && (!l.endsAt || Date.parse(l.endsAt) > Date.now())) ?? null;
    const topRank = Math.max(0, ...history.filter((l) => l.kind !== 'WARNING').map((l) => l.rank));
    const nextRank = warningCount < 2 ? warningCount + 1 : Math.max(3, topRank + 1);
    const next = nextRank <= 2 ? { kind: 'WARNING' as const, rank: nextRank } : (SITE_LADDER.find((s) => s.rank === nextRank) ?? null);
    const blockedReason = active?.kind === 'BAN' ? 'ALREADY_BANNED' : active ? 'SUSPENDED' : null;
    return mockClone({
        user: { ...user },
        warningCount,
        blockedReason,
        next,
        options: SITE_LADDER.map((s) => {
            const rank = s.kind === 'WARNING' ? Math.min(nextRank, 2) : s.rank;
            const state = blockedReason === 'ALREADY_BANNED' || (blockedReason === 'SUSPENDED' && s.kind !== 'BAN') ? 'BLOCKED' : rank < nextRank ? 'LOWER' : rank === nextRank || (s.kind === 'WARNING' && nextRank <= 2) ? 'NEXT' : 'SKIP';
            return { kind: s.kind, rank, state, requiresBan: s.kind === 'BAN' || state === 'SKIP' };
        }),
        active,
        history,
    } satisfies SanctionPreview);
}

export async function mockSiteCreate(body: SanctionCreateRequest): Promise<SanctionLine> {
    await mockDelay();
    const user = Object.values(USERS).find((u) => u.id === body.userId)!;
    const n = Array.from(body.reason.trim()).length;
    if (n === 0 || n > 500) throw MOCK_ERRORS.validation({ reason: '사유는 1~500자로 적어 주세요.' });
    const days: Partial<Record<SanctionKind, number>> = { SUSPEND_3: 3, SUSPEND_7: 7, SUSPEND_30: 30 };
    const warnings = SITE_CREATED.filter((l) => l.user.id === body.userId && l.kind === 'WARNING' && l.status !== 'VOIDED').length;
    const now = new Date();
    siteSeq++;
    const line: SanctionLine = {
        id: String(840000 + siteSeq),
        no: 700 + siteSeq,
        kind: body.kind,
        status: 'ACTIVE',
        rank: SITE_LADDER.find((s) => s.kind === body.kind)?.rank ?? 1,
        warningNo: body.kind === 'WARNING' ? Math.min(2, warnings + 1) : null,
        skipped: body.skipStep,
        user: { ...user },
        startsAt: now.toISOString(),
        endsAt: days[body.kind] ? new Date(now.getTime() + days[body.kind]! * DAY).toISOString() : null,
        reason: body.reason.trim(),
        issuedBy: MOCK_OPERATOR,
        createdAt: now.toISOString(),
        reportCase: null,
        liftedBy: null,
        liftedAt: null,
        liftReason: null,
        voidedAt: null,
        endedAt: null,
        appeal: null,
        appealDeadline: new Date(now.getTime() + 30 * DAY).toISOString(),
    };
    SITE_CREATED.push(line);
    return mockClone(line);
}

export async function mockSiteGet(id: string): Promise<SanctionLine> {
    await mockDelay();
    return mockClone(SITE_CREATED.find((l) => l.id === id)!);
}

/* ── 회원 상세 `boardSanctions` 보강 ── */

/** 회원 번호로 정한(늘 같은) 목업 요약 — 넷에 하나는 게시판 제재가 없는 회원. 이력 줄의 회원은 그 회원으로 바꿔 보인다 */
export function mockMemberBoardSanctions(member: { memberNo: string; nickname: string; status: string }): MemberBoardSanctionSummary {
    const hash = Array.from(member.memberNo).reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 9973, 7);
    const keys = Object.keys(USERS) as UserKey[];
    const source = keys[hash % keys.length]!;
    if (hash % 4 === 0) return { total: 0, active: 0, openReviews: 0, boards: [], recent: [] };
    const mine = SANCTIONS.filter((s) => s.user === source);
    const asMember = (l: BoardSanctionLine): BoardSanctionLine => ({ ...l, user: { id: member.memberNo, nickname: member.nickname, grade: null, status: member.status } });
    const boards = [...new Set(mine.map((s) => s.board))].map((b) => {
        const list = mine.filter((s) => s.board === b && live(s));
        const running = list.find((s) => s.kind !== 'WARNING' && statusOf(s) === 'ACTIVE');
        return { board: { ...BOARDS[b] }, warningCount: list.filter((s) => s.kind === 'WARNING').length, restrictCount: list.filter((s) => s.kind !== 'WARNING').length, activeUntil: running ? endsAtOf(running) : null };
    });
    return mockClone({
        total: mine.filter(live).length,
        active: mine.filter((s) => s.kind !== 'WARNING' && statusOf(s) === 'ACTIVE').length,
        openReviews: REVIEWS.filter((r) => r.user === source && !r.closedAt).length,
        boards,
        recent: [...mine].sort(newestFirst).slice(0, 5).map(toLine).map(asMember),
    });
}
