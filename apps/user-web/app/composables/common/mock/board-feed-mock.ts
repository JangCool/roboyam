// MOCK: 게시판이 섞인 목록(최신글·인기글·통합 검색·회원 프로필 작성글)의 목업들이 같이 쓰는 「실제 게시판 글 모으기」.
// 서버에 전체 게시판을 한 번에 읽는 API(`/api/home/latest`·`/api/home/popular`·`/api/search`·`/api/users/{id}/posts`)가 없어,
// **실제 공개 API**(`GET /api/boards` + 게시판마다 `GET /api/boards/{slug}/posts` 첫 몇 쪽)로 글을 모아 그 API 들을 흉내 낸다 — 목업 화면의 글 링크가 실제 글로 열린다.
// 이 파일은 서버 주소를 모른다 — 부르는 API 컴포저블이 `useBoardApi` 의 함수를 넘긴다. 서버가 붙으면 쓰는 목업과 함께 지운다.
//
// 가정(계약 「목업 가정」): 게시판마다 앞 `PAGES` 쪽만 읽는다(오래된 글은 빠진다) · 꺼진 게시판·못 읽은 게시판은 건너뛴다 · 신고로 숨겨진 글은 뺀다 ·
// 같은 검색어(`q`)로 15초 안에 다시 물으면 모아 둔 것을 쓴다(더보기·쪽 이동마다 게시판 수만큼 요청이 나가지 않게).

import type { BoardView } from '~/composables/board/api/useBoardApi.types';
import type { PostFeedRow, PostListQuery, PostListResponse } from '~/composables/board/api/usePostApi.types';

/** 실제 공개 게시판 API — `useBoardApi()` 의 두 함수 */
export interface BoardFeedSource {
    boards: () => Promise<BoardView[]>;
    posts: (slug: string, query: PostListQuery) => Promise<PostListResponse>;
}

/** 모은 결과 — 켜진 게시판 목록과 그 글들(최신이 위) */
export interface BoardFeed {
    boards: BoardView[];
    rows: PostFeedRow[];
}

/** 게시판마다 읽는 쪽 수 */
const PAGES = 2;
const TTL_MS = 15_000;

const cache = new Map<string, { at: number; value: Promise<BoardFeed> }>();

/** Snowflake 문자열 ID 비교 — 길이가 길수록 크다 */
function compareId(a: string, b: string): number {
    return a.length - b.length || (a < b ? -1 : a > b ? 1 : 0);
}

/** 최신이 위 — 같은 시각이면 ID 가 큰 것이 위 */
export function mockNewestFirst(a: PostFeedRow, b: PostFeedRow): number {
    return Date.parse(b.createdAt) - Date.parse(a.createdAt) || compareId(b.id, a.id);
}

async function collect(source: BoardFeedSource, q: string): Promise<BoardFeed> {
    const boards = await source.boards();
    const seen = new Map<string, PostFeedRow>();
    let firstError: unknown = null;
    let anyOk = false;
    await Promise.all(
        boards.map(async (board) => {
            const ref = { slug: board.slug, name: board.name, nameEn: board.nameEn ?? null, color: board.color ?? null };
            for (let page = 1; page <= PAGES; page++) {
                let res: PostListResponse;
                try {
                    res = await source.posts(board.slug, { prefix: '', sort: 'LATEST', q, page });
                } catch (e) {
                    firstError ??= e;
                    return;
                }
                anyOk = true;
                for (const row of [...res.pinned, ...res.items]) {
                    if (row.hiddenReason || seen.has(row.id)) continue;
                    seen.set(row.id, { ...row, board: ref });
                }
                if (page >= res.totalPages) return;
            }
        }),
    );
    if (!anyOk && boards.length > 0 && firstError) throw firstError;
    return { boards, rows: [...seen.values()].sort(mockNewestFirst) };
}

/**
 * 켜진 모든 게시판의 앞쪽 글을 모은다(최신이 위). `q` 를 주면 게시판마다 서버의 게시판 안 검색(제목·내용·작성자)을 쓴다.
 * @param {BoardFeedSource} source - 실제 공개 게시판 API.
 * @param {string} q - 게시판 안 검색어. 비면 검색하지 않는다.
 * @returns {Promise<BoardFeed>} 게시판 목록과 모은 글.
 */
export function mockBoardFeed(source: BoardFeedSource, q = ''): Promise<BoardFeed> {
    const key = q.trim();
    const hit = cache.get(key);
    if (hit && Date.now() - hit.at < TTL_MS) return hit.value;
    const value = collect(source, key);
    cache.set(key, { at: Date.now(), value });
    value.catch(() => cache.delete(key));
    return value;
}

/**
 * 오프셋 커서 — 목업은 모은 목록 안의 자리(`"20"`)를 커서로 쓴다(서버는 마지막 글 ID 를 쓴다 — 화면은 커서를 읽지 않으므로 차이가 드러나지 않는다)
 * @param {string | undefined} cursor - 앞 응답의 `nextCursor`.
 * @returns {number} 시작 자리. 없거나 깨졌으면 0.
 */
export function mockCursorStart(cursor: string | undefined): number {
    const n = Number(cursor);
    return Number.isInteger(n) && n > 0 ? n : 0;
}
