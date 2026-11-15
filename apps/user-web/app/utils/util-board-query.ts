import type { LocationQuery } from 'vue-router';
import type { PostListQuery, PostSort } from '~/composables/board/api/usePostApi.types';
import UtilPostOps from '~/utils/util-post-ops';
import UtilPostTag from '~/utils/util-post-tag';

/**
 * 게시판 목록 주소의 쿼리(`?prefix=&status=&tag=&sort=&q=&page=`) ↔ 목록 조건. 주소가 화면의 상태다(새 탭·공유·뒤로 가기).
 * 기본값(전체·최신순·검색 없음·1쪽)은 주소에 쓰지 않는다. 처리 상태(`status` — 상태가 켜진 게시판)는 말머리와 함께 쓸 수 있다.
 * 태그(`tag` — post-tags-contract)는 태그 글자 규칙으로 바로잡고(앞 `#` 떼기), 쓸 수 없는 값이면 거르지 않는다. 쪽·정렬을 바꿔도 남는다.
 */
export default class UtilBoardQuery {
    /**
     * 주소의 쿼리를 목록 조건으로 읽는다. 모르는 값은 기본값
     * @param {LocationQuery} query - `route.query`.
     * @returns {PostListQuery} 목록 조건.
     */
    static parse(query: LocationQuery): PostListQuery {
        const one = (value: unknown): string => (Array.isArray(value) ? String(value[0] ?? '') : typeof value === 'string' ? value : '');
        const sort: PostSort = one(query.sort) === 'oldest' ? 'OLDEST' : 'LATEST';
        const page = Number.parseInt(one(query.page), 10);
        return { prefix: one(query.prefix), status: UtilPostOps.parseStatus(query.status), tag: UtilPostTag.normalize(one(query.tag)) ?? '', sort, q: one(query.q).trim(), page: Number.isFinite(page) && page > 0 ? page : 1 };
    }

    /**
     * 목록 조건을 주소의 쿼리로 — 기본값은 뺀다
     * @param {Partial<PostListQuery>} value - 목록 조건.
     * @returns {Record<string, string>} 쿼리.
     */
    static build(value: Partial<PostListQuery>): Record<string, string> {
        const out: Record<string, string> = {};
        if (value.prefix) out.prefix = value.prefix;
        if (value.status) out.status = value.status;
        if (value.tag) out.tag = value.tag;
        if (value.sort === 'OLDEST') out.sort = 'oldest';
        if (value.q) out.q = value.q;
        if (value.page && value.page > 1) out.page = String(value.page);
        return out;
    }
}
