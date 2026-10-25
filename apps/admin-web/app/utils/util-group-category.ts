import { ApiError } from '~/composables/common/useApiClient';
import type { GroupCategoryCandidate, GroupCategoryItem } from '~/composables/group/api/useGroupCategoryApi.types';

/**
 * 소모임 분류 화면(`/group-categories`)의 순수 로직 — 이름 규칙, 선택한 분류의 입력(이름 + 게시판 목록)과 저장된 값의 차이, 순서 옮기기.
 * community `util-admin-group-category.ts` 를 옮겼다. 이름 규칙은 서버(community `GroupCategoryService`)와 같다 — 판정은 서버다.
 */

/** 선택한 분류의 입력 */
export interface GroupCategoryDraft {
    name: string;
    /** 이 분류에 둘 게시판 — 보낸 순서가 할당 순서 */
    boardIds: string[];
}

/** 오른쪽 패널의 게시판 한 줄 — 원래 있던 것(`kept`) · 새로 넣는 것(`added`) · 저장하면 빠지는 것(`removed`) */
export interface GroupCategoryRow {
    boardId: string;
    slug: string;
    name: string;
    active: boolean;
    investmentNotice: boolean;
    groupCount: number;
    state: 'kept' | 'added' | 'removed';
    /** 새로 넣는 게시판이 지금 있는 다른 분류(옮겨진다) */
    from: { id: string; name: string } | null;
}

/** 이름의 문제 */
export type GroupCategoryNameProblem = 'REQUIRED' | 'TOO_LONG' | 'FORBIDDEN' | 'DUPLICATE';

export default class UtilGroupCategory {
    /** 이름 최대 글자 수(서버와 같다) */
    static readonly NAME_MAX = 20;
    /** 이보다 길면 칩에서 말줄임으로 보인다는 안내 */
    static readonly NAME_SOFT = 12;
    /** 열어 둔 분류를 싣는 주소 쿼리 이름 */
    static readonly QUERY_KEY = 'category';

    /**
     * 이름을 다듬는다 — 앞뒤를 자르고 안쪽 공백을 하나로.
     * @param {string} name - 입력.
     * @returns {string} 다듬은 이름.
     */
    static normalize(name: string): string {
        return name.trim().replace(/\s+/g, ' ');
    }

    /**
     * 이름의 문제 — 빈칸 · 20자 넘음 · `<`·`>`·제어 문자 · 같은 이름(공백·대소문자 무시, 자기 자신 제외).
     * @param {string} name - 입력.
     * @param {readonly GroupCategoryItem[]} categories - 지금 분류들.
     * @param {string | null} selfId - 고치는 분류(만들 때는 null).
     * @returns {GroupCategoryNameProblem | null} 문제. 없으면 null.
     */
    static nameProblem(name: string, categories: readonly GroupCategoryItem[], selfId: string | null): GroupCategoryNameProblem | null {
        const value = UtilGroupCategory.normalize(name);
        if (value === '') return 'REQUIRED';
        if (Array.from(value).length > UtilGroupCategory.NAME_MAX) {
            // 이관이 옮긴 옛 이름을 바꾸지 않고 저장하면 길이를 다시 따지지 않는다(서버와 같다)
            const self = categories.find((c) => c.id === selfId);
            if (!self || UtilGroupCategory.normalize(self.name) !== value) return 'TOO_LONG';
        }
        // eslint-disable-next-line no-control-regex
        if (/[<>\u0000-\u001f\u007f]/.test(value)) return 'FORBIDDEN';
        const key = value.replace(/\s+/g, '').toLowerCase();
        if (categories.some((c) => c.id !== selfId && c.name.replace(/\s+/g, '').toLowerCase() === key)) return 'DUPLICATE';
        return null;
    }

    /**
     * 저장된 분류에서 입력의 처음 모습.
     * @param {GroupCategoryItem} category - 분류.
     * @returns {GroupCategoryDraft} 입력.
     */
    static draftOf(category: GroupCategoryItem): GroupCategoryDraft {
        return { name: category.name, boardIds: category.boards.map((b) => b.boardId) };
    }

    /**
     * 오른쪽 패널의 줄 — 원래 있던 게시판(저장된 순서, 뺀 것은 `removed`) 다음에 새로 넣는 게시판(넣은 순서).
     * @param {GroupCategoryItem} category - 저장된 분류.
     * @param {GroupCategoryDraft} draft - 입력.
     * @param {readonly GroupCategoryCandidate[]} available - 할당할 수 있는 게시판.
     * @returns {GroupCategoryRow[]} 줄.
     */
    static rows(category: GroupCategoryItem, draft: GroupCategoryDraft, available: readonly GroupCategoryCandidate[]): GroupCategoryRow[] {
        const keep = new Set(draft.boardIds);
        const out: GroupCategoryRow[] = category.boards.map((b) => ({ ...b, state: keep.has(b.boardId) ? 'kept' : 'removed', from: null }));
        const saved = new Set(category.boards.map((b) => b.boardId));
        for (const boardId of draft.boardIds) {
            if (saved.has(boardId)) continue;
            const c = available.find((x) => x.boardId === boardId);
            if (!c) continue;
            out.push({
                boardId,
                slug: c.slug,
                name: c.name,
                active: true,
                investmentNotice: c.investmentNotice,
                groupCount: c.groupCount,
                state: 'added',
                from: c.categoryId && c.categoryId !== category.id ? { id: c.categoryId, name: c.categoryName ?? '' } : null,
            });
        }
        return out;
    }

    /**
     * 더할 수 있는 게시판 — 켜진 소모임 게시판 중 입력에 아직 없는 것.
     * @param {readonly GroupCategoryCandidate[]} available - 할당할 수 있는 게시판.
     * @param {GroupCategoryDraft} draft - 입력.
     * @returns {GroupCategoryCandidate[]} 후보.
     */
    static candidates(available: readonly GroupCategoryCandidate[], draft: GroupCategoryDraft): GroupCategoryCandidate[] {
        const taken = new Set(draft.boardIds);
        return available.filter((c) => !taken.has(c.boardId));
    }

    /**
     * 어느 분류에도 없는 켜진 소모임 게시판.
     * @param {readonly GroupCategoryCandidate[]} available - 할당할 수 있는 게시판.
     * @param {GroupCategoryDraft | null} draft - 지금 입력(이미 넣은 것은 뺀다).
     * @returns {GroupCategoryCandidate[]} 분류 없음 게시판.
     */
    static unassigned(available: readonly GroupCategoryCandidate[], draft: GroupCategoryDraft | null): GroupCategoryCandidate[] {
        const taken = new Set(draft?.boardIds ?? []);
        return available.filter((c) => c.categoryId === null && !taken.has(c.boardId));
    }

    /**
     * 바꾼 곳의 수 — 이름(1) + 새로 넣는 게시판 수 + 빼는 게시판 수 + (같은 게시판인데 순서만 바뀌었으면 1).
     * @param {GroupCategoryItem} category - 저장된 분류.
     * @param {GroupCategoryDraft} draft - 입력.
     * @returns {number} 바꾼 곳 수.
     */
    static changeCount(category: GroupCategoryItem, draft: GroupCategoryDraft): number {
        const saved = category.boards.map((b) => b.boardId);
        const savedSet = new Set(saved);
        const draftSet = new Set(draft.boardIds);
        let count = UtilGroupCategory.normalize(draft.name) !== category.name ? 1 : 0;
        count += draft.boardIds.filter((id) => !savedSet.has(id)).length;
        count += saved.filter((id) => !draftSet.has(id)).length;
        if (count === 0 && saved.join(',') !== draft.boardIds.join(',')) count = 1;
        return count;
    }

    /**
     * 투자 고지 게시판과 다른 게시판이 섞였나(저장하면 남는 줄 기준) — 막지 않고 알린다.
     * @param {readonly GroupCategoryRow[]} rows - 패널의 줄.
     * @returns {boolean} 섞였으면 true.
     */
    static mixed(rows: readonly GroupCategoryRow[]): boolean {
        const left = rows.filter((r) => r.state !== 'removed');
        return left.some((r) => r.investmentNotice) && left.some((r) => !r.investmentNotice);
    }

    /**
     * 한 칸 옮긴 뒤의 전체 순서 — 끝이라 못 옮기면 null.
     * @param {readonly GroupCategoryItem[]} categories - 지금 순서.
     * @param {string} id - 옮길 분류.
     * @param {-1 | 1} step - 위(-1)·아래(1).
     * @returns {string[] | null} 새 순서의 ID.
     */
    static moved(categories: readonly GroupCategoryItem[], id: string, step: -1 | 1): string[] | null {
        const ids = categories.map((c) => c.id);
        const from = ids.indexOf(id);
        const to = from + step;
        if (from < 0 || to < 0 || to >= ids.length) return null;
        ids.splice(from, 1);
        ids.splice(to, 0, id);
        return ids;
    }

    /**
     * 주소의 `?category=` — 숫자 ID 만.
     * @param {Record<string, unknown>} query - 라우터 쿼리.
     * @returns {string} 분류 ID(없으면 빈 글자).
     */
    static parseQuery(query: Record<string, unknown>): string {
        const raw = query[UtilGroupCategory.QUERY_KEY];
        const value = Array.isArray(raw) ? raw[0] : raw;
        return typeof value === 'string' && /^[0-9]{1,19}$/.test(value) ? value : '';
    }

    /**
     * 지우기가 「소모임이 있어 못 지운다」(409 `GROUP_CATEGORY_NOT_EMPTY`)인가 — 그 소모임 수.
     * @param {unknown} e - 잡은 오류.
     * @returns {number | null} 소모임 수. 해당 없으면 null.
     */
    static notEmptyCount(e: unknown): number | null {
        if (!(e instanceof ApiError) || e.code !== 'GROUP_CATEGORY_NOT_EMPTY') return null;
        const n = e.extras?.groupCount;
        return typeof n === 'number' ? n : 0;
    }

    /**
     * 그 사이 다른 관리자가 먼저 고쳤다는 답인가(409 `GROUP_CATEGORY_STALE`).
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 그렇다면 true.
     */
    static isStale(e: unknown): boolean {
        return e instanceof ApiError && e.code === 'GROUP_CATEGORY_STALE';
    }

    /**
     * 분류가 이미 지워졌다는 답인가(404 `GROUP_CATEGORY_NOT_FOUND`).
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 그렇다면 true.
     */
    static isGone(e: unknown): boolean {
        return e instanceof ApiError && e.code === 'GROUP_CATEGORY_NOT_FOUND';
    }

    /**
     * 서버가 거절한 칸 — `fields.name`·`fields.boardIds`, 그리고 같은 이름(409 `GROUP_CATEGORY_NAME_TAKEN`)은 이름 칸으로.
     * @param {unknown} e - 잡은 오류.
     * @returns {{ name?: string; boardIds?: string } | null} 칸 문구. 해당 없으면 null.
     */
    static fieldErrorsOf(e: unknown): { name?: string; boardIds?: string } | null {
        if (!(e instanceof ApiError)) return null;
        if (e.code === 'GROUP_CATEGORY_NAME_TAKEN') return { name: e.message };
        if (e.status !== 400 || !e.fields) return null;
        const out: { name?: string; boardIds?: string } = {};
        if (e.fields.name) out.name = e.fields.name;
        if (e.fields.boardIds) out.boardIds = e.fields.boardIds;
        return Object.keys(out).length ? out : null;
    }
}
