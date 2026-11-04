import type { VisitKind } from '#layers/analytics/utils/util-visit-signal';

/**
 * 방문 신호의 화면 종류(`kind`) — 이 앱의 라우트(`pages/`) 경로로 정한다. 상태도 요청도 없다(신호는 `@roboyam/analytics`, 거는 곳은 `plugins/04.analytics.client.ts`).
 * 서버가 인기 페이지의 묶음·제목 찾기(글이면 글 제목)에 쓴다(`docs/features/site-analytics-contract.md`).
 *
 * | 경로 | 종류 |
 * |---|---|
 * | `/` | HOME |
 * | `/boards` · `/boards/{slug}` · `/latest` · `/popular` · `/write` · `/posts/{id}/edit` | BOARD |
 * | `/posts/{id}` | POST |
 * | `/group-buys/**` | GROUP_BUY |
 * | `/business/deals/**`(사업자 핫딜 신청) | HOTDEAL |
 * | `/groups/**` | GROUP |
 * | `/point-shop/**` | POINT_SHOP |
 * | `/search` | SEARCH |
 * | `/me/**` · `/settings/**` · `/messages/**` · `/notifications/**` · `/business/**`(그 밖 사업자 센터) · `/sanction/**` | MY |
 * | 그 밖(`/p/{key}` 가젯 페이지 · `/users/{id}` 공개 프로필 · 로그인·가입·비밀번호·약관·안내 화면 등) | OTHER |
 *
 * ⚠ 핫딜 게시판(`kind DEAL`)의 목록·글은 주소가 일반 게시판과 같아(`/boards/{slug}`·`/posts/{id}`) 주소만으로는 가를 수 없다 — BOARD·POST 로 보낸다(서버가 게시판 종류로 다시 가를 수 있다).
 */

/** 앞부분이 같은 주소 묶음 — 위에서부터 처음 맞는 줄 */
const PREFIXES: readonly (readonly [prefix: string, kind: VisitKind])[] = [
    ['/boards', 'BOARD'],
    ['/latest', 'BOARD'],
    ['/popular', 'BOARD'],
    ['/write', 'BOARD'],
    ['/group-buys', 'GROUP_BUY'],
    ['/business/deals', 'HOTDEAL'],
    ['/groups', 'GROUP'],
    ['/point-shop', 'POINT_SHOP'],
    ['/search', 'SEARCH'],
    ['/me', 'MY'],
    ['/settings', 'MY'],
    ['/messages', 'MY'],
    ['/notifications', 'MY'],
    ['/business', 'MY'],
    ['/sanction', 'MY'],
];

/** 글 보기 `/posts/{id}`(끝 `/` 허용) — 고치기(`/posts/{id}/edit`)는 글쓰기라 BOARD */
const POST_VIEW = /^\/posts\/[^/]+\/?$/;
const POST_EDIT = /^\/posts\/[^/]+\/edit\/?$/;

export default class UtilVisitKind {
    /**
     * 경로의 화면 종류.
     * @param {string} path - 쿼리·해시를 뺀 라우트 경로.
     * @returns {VisitKind} 종류. 표에 없으면 OTHER.
     */
    static of(path: string): VisitKind {
        if (path === '/' || path === '') return 'HOME';
        if (POST_VIEW.test(path)) return 'POST';
        if (POST_EDIT.test(path)) return 'BOARD';
        for (const [prefix, kind] of PREFIXES) {
            if (path === prefix || path.startsWith(`${prefix}/`)) return kind;
        }
        return 'OTHER';
    }
}
