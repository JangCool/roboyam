import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import type { HomeLayoutBody, HomeLayoutGadget } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetPageBoard } from '~/composables/page/api/useGadgetPageApi.types';

/** 처음 배치에 쓰는 제목 — 언어는 화면이 정한다(서버는 한국어 시드로 같은 모양을 만든다) */
export interface GadgetPageTitles {
    /** 「{folder} 인기글」 */
    popular: (folder: string) => string;
    /** 「{folder} 최신글」 */
    latest: (folder: string) => string;
    /** 기준 폴더가 없을 때의 폴더 자리 이름(예: 「전체」) */
    all: string;
}

/** 줄마다 높이(행) — 인기글 · 갤러리 · 게시판 카드 줄 · 최신글(서버 `GadgetPageInitial` 과 같다) */
const TOP_H = 15;
const GALLERY_H = 11;
const CARDS_H = 13;
const LATEST_H = 15;

/** 영문 주소 — 소문자로 시작, 소문자·숫자·`-`, 2~30자 */
const KEY_PATTERN = /^[a-z][a-z0-9-]{1,29}$/;

/**
 * 가젯 페이지의 순수 계산 — 영문 주소 검사, 만들 때의 처음 배치(기준 폴더의 게시판으로 채운다, 사용자 결정 2026-10-29).
 * 처음 배치는 서버(만들기 API — `GadgetPageInitial`)가 같은 규칙으로 만든다. 화면은 만들기 창의 「처음 배치」 미리 보기에만 쓴다(켜진 게시판만 센다).
 */
export default class UtilGadgetPage {
    static readonly NAME_MAX = 40;
    static readonly DESCRIPTION_MAX = 200;
    static readonly KEY_MAX = 30;

    /**
     * 영문 주소 모양이 맞는가
     * @param {string} key - 주소.
     * @returns {boolean} 맞으면 true.
     */
    static validKey(key: string): boolean {
        return KEY_PATTERN.test(key);
    }

    /**
     * 공개 주소
     * @param {string} key - 영문 주소.
     * @returns {string} `/p/{key}`.
     */
    static path(key: string): string {
        return `/p/${key}`;
    }

    /**
     * 처음 배치(24칸, 줄마다 꽉 차게 — 사용자 결정 2026-10-29) — 커뮤니티 예: 「커뮤니티 인기글」(인기·기간 탭, 24칸) / 「영상·짤」 갤러리(24칸) / 게시판 카드 자유게시판·유머게시판(12+12 두 열) / 「커뮤니티 최신글」(24칸).
     *   · 나란한 가젯은 같은 높이라 아래 끝이 맞고, 높이는 보통 내용에 맞췄다(내용이 길면 격자가 늘린다). 켜진 게시판만 쓴다.
     *   · 인기글·최신글은 대상 게시판 = 폴더 게시판 전체(`FOLDER`) — 폴더에 게시판을 넣고 빼면 따라간다.
     *   · 갤러리는 사진·영상 게시판 — 목록 모양이 갤러리인 첫 게시판, 없으면 갤러리 보기 전환이 켜진 첫 게시판(직접 고르기, 제목 = 게시판 이름). 없으면 그 줄이 없다.
     *   · 게시판 카드는 갤러리로 쓴 게시판을 뺀 폴더 게시판마다 하나, 두 열(12+12) — 홀수면 마지막이 24칸.
     *   · 게시판 카드는 폴더 게시판마다 하나(직접 고르기 — 그 게시판 하나), 두 개씩 나란히(12칸). 홀수면 마지막이 24칸.
     *   · 기준 폴더가 없거나 게시판이 없으면 인기글 + 최신글(대상 = 폴더 게시판 전체 → 전체 게시판과 같다).
     * 가젯 ID 는 `seed-<n>`(저장 전 — 서버가 저장 때 새 ID 를 준다).
     * @param {string | null} folderName - 기준 폴더 이름.
     * @param {readonly GadgetPageBoard[]} boards - 기준 폴더의 게시판(메뉴 차례).
     * @param {GadgetPageTitles} titles - 제목 만들기.
     * @returns {HomeLayoutBody} 배치 + 가젯.
     */
    static initialLayout(folderName: string | null, allBoards: readonly GadgetPageBoard[], titles: GadgetPageTitles): HomeLayoutBody {
        const boards = allBoards.filter((b) => b.active);
        const columns: HomeGridColumns = 24;
        const folder = folderName ?? titles.all;
        const gadgets: HomeLayoutGadget[] = [];
        const items: HomeLayoutBody['layout']['items'] = [];
        let seq = 0;
        const put = (type: string, title: string, config: HomeLayoutGadget['config'], x: number, y: number, w: number, h: number) => {
            seq += 1;
            const id = `seed-${seq}`;
            gadgets.push({ id, type, title, desktop: true, mobile: true, config });
            items.push({ gadgetId: id, x, y, w, h });
        };

        const gallery = boards.find((b) => b.layout === 'GALLERY') ?? boards.find((b) => b.viewToggle) ?? null;
        let y = 0;
        put('POST_LIST', titles.popular(folder), { boardScope: 'FOLDER', boardIds: [], count: 10, sort: 'POPULAR', periodTabs: true }, 0, y, 24, TOP_H);
        y += TOP_H;
        if (gallery) {
            put('GALLERY', gallery.name, { boardScope: 'PICK', boardIds: [gallery.slug], count: 8 }, 0, y, 24, GALLERY_H);
            y += GALLERY_H;
        }

        const cards = boards.filter((b) => b !== gallery);
        cards.forEach((board, index) => {
            const last = index === cards.length - 1;
            const left = index % 2 === 0;
            const w = left && last ? 24 : 12;
            put('BOARD_CARDS', board.name, { boardScope: 'PICK', boardIds: [board.slug], perBoard: 4 }, left ? 0 : 12, y, w, CARDS_H);
            if (!left || last) y += CARDS_H;
        });

        put('POST_LIST', titles.latest(folder), { boardScope: 'FOLDER', boardIds: [], count: 16, sort: 'LATEST', periodTabs: false }, 0, y, 24, LATEST_H);
        return { layout: { columns, items }, gadgets };
    }
}
