import type { LocationQuery } from 'vue-router';

/**
 * 공개 회원 프로필 화면의 작은 규칙 — 주소, 탭 쿼리(`?tab=comments`) 읽고 만들기, 수치 글자. 상태도 요청도 없다.
 * 탭이 화면의 상태다(새로고침·뒤로 가기가 같은 탭으로 돌아온다). 이상한 값은 기본 탭(작성글)으로, 기본 탭은 주소에서 뺀다.
 * 원본(community) `UtilProfile`·`UtilProfileQuery` 에서 소모임·스크랩 탭과 작성자 표시 변환을 뺐다(로보얌에 없다).
 */

/** 프로필의 탭 — 작성글 · 댓글 */
export type ProfileTab = 'posts' | 'comments';

export default class UtilProfile {
    /** 탭 차례 */
    static readonly TABS: readonly ProfileTab[] = ['posts', 'comments'];

    /**
     * 공개 프로필 주소 — ID 는 문자열 그대로(Snowflake, 숫자로 바꾸지 않는다)
     * @param {string} id - 회원 ID.
     * @returns {string} `/users/{id}`.
     */
    static path(id: string): string {
        return `/users/${encodeURIComponent(id)}`;
    }

    /**
     * 주소의 쿼리 → 탭
     * @param {LocationQuery} query - 라우트의 쿼리.
     * @returns {ProfileTab} `comments` 면 댓글, 그 밖은 작성글.
     */
    static parseTab(query: LocationQuery): ProfileTab {
        const raw = Array.isArray(query.tab) ? query.tab[0] : query.tab;
        return raw === 'comments' ? 'comments' : 'posts';
    }

    /**
     * 탭 → 주소의 쿼리(작성글이면 빈 객체)
     * @param {ProfileTab} tab - 탭.
     * @returns {Record<string, string>} 쿼리.
     */
    static buildTab(tab: ProfileTab): Record<string, string> {
        return tab === 'posts' ? {} : { tab };
    }

    /**
     * 수치 한 칸의 글자 — 천 단위 쉼표. 값이 없으면(불러오는 중) "-"
     * @param {number | null | undefined} value - 수치.
     * @returns {string} 글자.
     */
    static count(value: number | null | undefined): string {
        return value === null || value === undefined ? '-' : value.toLocaleString();
    }
}
