// MOCK: 차단(닉네임·단어)·이용자 메모 API 의 메모리 목업 — `useBlockApi.ts`·`useMemoApi.ts` 가 부르고, 쪽지 목업(차단 관계)과 공개 프로필 목업(`blockedByMe`·`memo`)이 상태를 읽는다.
// 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 값으로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   block-fail          차단·차단 해제 실패(500)            blocks-users-fail  차단한 회원 목록 실패(500)
//   blocks-words-fail   차단 단어 목록 실패(500)             blocks-empty       차단한 회원·단어가 없다(처음 상태)
//   memo-save-fail      메모 저장·삭제 실패(500)            memos-fail         메모 목록 실패(500)
//   memos-empty         남긴 메모가 없다(처음 상태)
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 처음 상태: 명부의 「로봇팔장인」을 사흘 전에 차단, 단어 「스포일러」, 메모 2개(「로봇덕후」·「휴머노이드」).
//   · 상한(서버 설정값 후보): 차단 500명 · 단어 50개(2~20자) · 메모 500개(20자). 목업 한 쪽 = 20줄.
//   · 보는 사람은 한 명이라고 본다(계정을 바꿔도 같은 목업 상태).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardFeedSource } from '~/composables/common/mock/board-feed-mock';
import { mockCursorStart } from '~/composables/common/mock/board-feed-mock';
import { mockMemberOf } from '~/composables/common/mock/member-mock';
import type { MockMember } from '~/composables/common/mock/member-mock';
import type { BlockedUserList, BlockedUserView, BlockedWordList, BlockedWordView, BlockPageQuery, BlockResult } from '~/composables/block/api/useBlockApi.types';
import type { MemoList, MemoResult, MemoView } from '~/composables/block/api/useMemoApi.types';

const DAY = 24 * 60 * 60 * 1000;
const PAGE_SIZE = 20;
const LIMITS = { users: 500, words: 50, wordMin: 2, wordMax: 20, memos: 500, memoMax: 20 };
// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f-\u009f]/;

interface BlockRow {
    member: MockMember;
    blockedAt: string;
}
interface MemoRow {
    member: MockMember;
    memo: string;
    createdAt: string;
    updatedAt: string;
}

const ago = (ms: number) => new Date(Date.now() - ms).toISOString();

/** 메모리 상태 — 처음 부를 때 만든다(`?mock=blocks-empty`·`memos-empty` 면 빈 채로) */
let users: BlockRow[] | null = null;
let words: BlockedWordView[] | null = null;
let memos: MemoRow[] | null = null;
let seq = 1000;

function seedUsers(): BlockRow[] {
    if (users) return users;
    users = mockFlags().has('blocks-empty')
        ? []
        : [{ member: { id: '900000000000000105', nickname: '로봇팔장인', grade: 'LOYAL', gradeLabel: '단골', role: 'USER', acceptDm: true }, blockedAt: ago(3 * DAY) }];
    return users;
}

function seedWords(): BlockedWordView[] {
    if (words) return words;
    words = mockFlags().has('blocks-empty') ? [] : [{ id: 'w1', word: '스포일러', createdAt: ago(2 * DAY) }];
    return words;
}

function seedMemos(): MemoRow[] {
    if (memos) return memos;
    memos = mockFlags().has('memos-empty')
        ? []
        : [
              { member: { id: '900000000000000103', nickname: '휴머노이드', grade: 'VETERAN', gradeLabel: '베테랑', role: 'USER', acceptDm: true }, memo: '자료 공유해 주신 분', createdAt: ago(1 * DAY), updatedAt: ago(1 * DAY) },
              { member: { id: '900000000000000101', nickname: '로봇덕후', grade: 'LOYAL', gradeLabel: '단골', role: 'USER', acceptDm: true }, memo: '하드웨어 고수', createdAt: ago(5 * DAY), updatedAt: ago(4 * DAY) },
          ];
    return memos;
}

function notFound(): ApiError {
    return new ApiError({ status: 404, code: 'PROFILE_NOT_FOUND', message: '탈퇴했거나 없는 회원이에요.' });
}

function page<T>(list: readonly T[], query: BlockPageQuery): { items: T[]; hasNext: boolean; nextCursor: string | null } {
    const start = mockCursorStart(query.cursor);
    const end = start + Math.min(50, Math.max(1, query.size ?? PAGE_SIZE));
    const hasNext = end < list.length;
    return { items: list.slice(start, end), hasNext, nextCursor: hasNext ? String(end) : null };
}

/* ── 다른 목업이 읽는 상태 ── */

/** 내가 이 회원을 차단했는가 */
export function mockIsBlocked(userId: string | null | undefined): boolean {
    return Boolean(userId) && seedUsers().some((r) => r.member.id === userId);
}

/** 이 회원에게 남긴 메모(없으면 null) */
export function mockMemoOf(userId: string | null | undefined): string | null {
    return seedMemos().find((r) => r.member.id === userId)?.memo ?? null;
}

/** 지금 차단 상태 한 벌 — 이 기기의 표시(`useBlockOverrides`)를 목업 상태로 맞출 때 쓴다 */
export async function mockBlockSnapshot(): Promise<{ users: string[]; words: string[]; memos: Record<string, string> }> {
    await mockDelay();
    return {
        users: seedUsers().map((r) => r.member.id),
        words: seedWords().map((w) => w.word),
        memos: Object.fromEntries(seedMemos().map((r) => [r.member.id, r.memo])),
    };
}

/* ── 닉네임 차단 ── */

export async function mockBlock(source: BoardFeedSource | null, meId: string | null, userId: string): Promise<BlockResult> {
    await mockDelay();
    if (mockFlags().has('block-fail')) throw MOCK_ERRORS.internal();
    if (userId === meId) throw new ApiError({ status: 400, code: 'BLOCK_SELF', message: '나를 차단할 수는 없어요.' });
    const member = await mockMemberOf(source, userId);
    if (!member) throw notFound();
    if (member.role === 'STAFF' || member.role === 'ADMIN') throw new ApiError({ status: 403, code: 'BLOCK_TARGET_PROTECTED', message: '운영진은 차단할 수 없어요.' });
    const list = seedUsers();
    const have = list.find((r) => r.member.id === userId);
    if (have) return { blocked: true, blockedAt: have.blockedAt };
    if (list.length >= LIMITS.users) throw new ApiError({ status: 409, code: 'BLOCK_LIMIT_REACHED', message: `차단은 ${LIMITS.users}명까지 할 수 있어요.` });
    const row = { member, blockedAt: new Date().toISOString() };
    users = [row, ...list];
    return { blocked: true, blockedAt: row.blockedAt };
}

export async function mockUnblock(userId: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('block-fail')) throw MOCK_ERRORS.internal();
    users = seedUsers().filter((r) => r.member.id !== userId);
}

export async function mockBlockedUsers(query: BlockPageQuery): Promise<BlockedUserList> {
    await mockDelay();
    if (mockFlags().has('blocks-users-fail')) throw MOCK_ERRORS.internal();
    const rows: BlockedUserView[] = seedUsers().map((r) => ({ id: r.member.id, nickname: r.member.nickname, grade: r.member.grade, gradeLabel: r.member.gradeLabel, role: r.member.role, blockedAt: r.blockedAt }));
    return mockClone({ total: rows.length, ...page(rows, query) });
}

/* ── 차단 단어 ── */

function normalizeWord(text: string): string {
    return text.trim().replace(/\s+/g, ' ');
}

export async function mockBlockedWords(): Promise<BlockedWordList> {
    await mockDelay();
    if (mockFlags().has('blocks-words-fail')) throw MOCK_ERRORS.internal();
    return mockClone({ limit: LIMITS.words, minLength: LIMITS.wordMin, maxLength: LIMITS.wordMax, items: seedWords() });
}

export async function mockAddWord(raw: string): Promise<BlockedWordView> {
    await mockDelay();
    const word = normalizeWord(raw ?? '');
    const length = Array.from(word).length;
    if (CONTROL_CHARS.test(raw ?? '') || length < LIMITS.wordMin || length > LIMITS.wordMax) {
        throw MOCK_ERRORS.validation({ word: `차단할 단어는 ${LIMITS.wordMin}자 이상 ${LIMITS.wordMax}자 이하로 입력해 주세요.` });
    }
    const list = seedWords();
    if (list.some((w) => w.word.toLowerCase() === word.toLowerCase())) throw new ApiError({ status: 409, code: 'BLOCKED_WORD_DUPLICATE', message: '이미 차단한 단어예요.' });
    if (list.length >= LIMITS.words) throw new ApiError({ status: 409, code: 'BLOCKED_WORD_LIMIT', message: `차단 단어는 ${LIMITS.words}개까지 더할 수 있어요.` });
    const created: BlockedWordView = { id: `w${++seq}`, word, createdAt: new Date().toISOString() };
    words = [created, ...list];
    return mockClone(created);
}

export async function mockRemoveWord(id: string): Promise<void> {
    await mockDelay();
    words = seedWords().filter((w) => w.id !== id);
}

/* ── 이용자 메모 ── */

export async function mockSaveMemo(source: BoardFeedSource | null, meId: string | null, userId: string, raw: string): Promise<MemoResult> {
    await mockDelay();
    if (mockFlags().has('memo-save-fail')) throw MOCK_ERRORS.internal();
    const memo = (raw ?? '').trim();
    if (Array.from(memo).length > LIMITS.memoMax) throw MOCK_ERRORS.validation({ memo: `메모는 ${LIMITS.memoMax}자까지 쓸 수 있어요.` });
    if (CONTROL_CHARS.test(memo)) throw MOCK_ERRORS.validation({ memo: '메모에는 줄바꿈 같은 제어 문자를 쓸 수 없어요.' });
    if (userId === meId) throw new ApiError({ status: 400, code: 'MEMO_SELF', message: '나에게는 메모를 남길 수 없어요.' });
    const member = await mockMemberOf(source, userId);
    if (!member) throw notFound();
    const list = seedMemos();
    if (memo === '') {
        memos = list.filter((r) => r.member.id !== userId);
        return { memo: null };
    }
    const have = list.find((r) => r.member.id === userId);
    const now = new Date().toISOString();
    if (have) {
        memos = list.map((r) => (r.member.id === userId ? { ...r, memo, updatedAt: now } : r));
    } else {
        if (list.length >= LIMITS.memos) throw new ApiError({ status: 409, code: 'MEMO_LIMIT_REACHED', message: `메모는 ${LIMITS.memos}개까지 남길 수 있어요.` });
        memos = [{ member, memo, createdAt: now, updatedAt: now }, ...list];
    }
    return { memo };
}

export async function mockRemoveMemo(userId: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('memo-save-fail')) throw MOCK_ERRORS.internal();
    memos = seedMemos().filter((r) => r.member.id !== userId);
}

export async function mockMemos(query: BlockPageQuery): Promise<MemoList> {
    await mockDelay();
    if (mockFlags().has('memos-fail')) throw MOCK_ERRORS.internal();
    const rows: MemoView[] = seedMemos().map((r) => ({ id: r.member.id, nickname: r.member.nickname, grade: r.member.grade, gradeLabel: r.member.gradeLabel, role: r.member.role, memo: r.memo, updatedAt: r.updatedAt }));
    return mockClone({ total: rows.length, ...page(rows, query) });
}
