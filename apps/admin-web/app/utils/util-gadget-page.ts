import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import type { HomeLayoutBody, HomeLayoutGadget } from '~/composables/home/api/useHomeLayoutApi.types';
import type { MenuBoardOption, MenuNodeDto } from '~/composables/menu/api/useMenuApi.types';
import type { GadgetPageBoard } from '~/composables/page/api/useGadgetPageApi.types';

/** 처음 배치에 쓰는 제목 — 언어는 화면이 정한다(서버는 한국어 시드로 같은 모양을 만든다) */
export interface GadgetPageTitles {
    /** 「{folder} 인기글」 */
    popular: (folder: string) => string;
    /** 「{folder} 최신글」 */
    latest: (folder: string) => string;
    /** 「{board} 갤러리」 */
    gallery: (board: string) => string;
    /** 기준 폴더가 없을 때의 폴더 자리 이름(예: 「전체」) */
    all: string;
}

/** 기준 폴더로 고를 수 있는 폴더 한 개 — 공개 메뉴(main)의 폴더(깊이 상관없이), 그 안 게시판 항목(하위 폴더까지, 메뉴 차례) */
export interface GadgetPageFolder {
    id: string;
    /** 이름(위 폴더가 있으면 「위 › 이름」) */
    label: string;
    /** 폴더 이름만(화면 언어) */
    name: string;
    /** 폴더 영문 이름(메뉴에 적은 것 — 없으면 null) — 만들 때 영문 주소 채우기 */
    nameEn: string | null;
    boards: GadgetPageBoard[];
}

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
     * 메뉴 트리 → 기준 폴더로 고를 폴더들(서버는 같은 규칙으로 페이지의 `folderName`·`folderBoards` 를 푼다).
     * 게시판 항목만 센다(사이트 화면·외부 링크 항목은 건너뛴다). 같은 게시판이 두 번 들어 있으면 한 번만.
     * @param {readonly MenuNodeDto[]} nodes - 공개 메뉴 main 트리.
     * @param {readonly MenuBoardOption[]} boards - 게시판 목록(메뉴 대상 고르기 값).
     * @param {(id: string) => GadgetPageBoard['layout']} layoutOf - 게시판 ID → 목록 모양(모르면 `LIST`).
     * @param {(name: string, nameEn: string | null) => string} nameOf - 이름 고르기(화면 언어).
     * @returns {GadgetPageFolder[]} 폴더들(트리 차례).
     */
    static folders(
        nodes: readonly MenuNodeDto[],
        boards: readonly MenuBoardOption[],
        layoutOf: (id: string) => GadgetPageBoard['layout'],
        nameOf: (name: string, nameEn: string | null) => string,
    ): GadgetPageFolder[] {
        const byId = new Map(boards.map((b) => [b.id, b]));
        const out: GadgetPageFolder[] = [];
        const boardsIn = (list: readonly MenuNodeDto[]): GadgetPageBoard[] => {
            const found: GadgetPageBoard[] = [];
            for (const node of list) {
                if (node.kind === 'FOLDER') found.push(...boardsIn(node.children));
                else if (node.targetType === 'BOARD' && node.targetId) {
                    const b = byId.get(node.targetId);
                    if (b && !found.some((f) => f.slug === b.slug)) found.push({ slug: b.slug, name: nameOf(b.name, b.nameEn), nameEn: b.nameEn, layout: layoutOf(b.id) });
                }
            }
            return found;
        };
        const walk = (list: readonly MenuNodeDto[], parent: string | null) => {
            for (const node of list) {
                if (node.kind !== 'FOLDER' || !node.id) continue;
                const name = nameOf(node.label ?? '', node.nameEn) || node.id;
                const label = parent ? `${parent} › ${name}` : name;
                out.push({ id: node.id, label, name, nameEn: node.nameEn, boards: boardsIn(node.children) });
                walk(node.children, label);
            }
        };
        walk(nodes, null);
        return out;
    }

    /**
     * 처음 배치(24칸) — 커뮤니티 예: 「커뮤니티 인기글」(인기·기간 탭, 16칸) + 「영상·짤 갤러리」(8칸) / 게시판 카드 하나씩 12+12 / 「커뮤니티 최신글」(24칸).
     *   · 인기글·최신글은 대상 게시판 = 폴더 게시판 전체(`FOLDER`) — 폴더에 게시판을 넣고 빼면 따라간다.
     *   · 갤러리는 폴더 게시판 중 목록 모양이 갤러리인 첫 게시판(직접 고르기). 없으면 갤러리 가젯 없이 인기글이 24칸.
     *   · 게시판 카드는 폴더 게시판마다 하나(직접 고르기 — 그 게시판 하나), 두 개씩 나란히(12칸). 홀수면 마지막이 24칸.
     *   · 기준 폴더가 없거나 게시판이 없으면 인기글 + 최신글(대상 = 폴더 게시판 전체 → 전체 게시판과 같다).
     * 가젯 ID 는 `seed-<n>`(저장 전 — 서버가 저장 때 새 ID 를 준다).
     * @param {string | null} folderName - 기준 폴더 이름.
     * @param {readonly GadgetPageBoard[]} boards - 기준 폴더의 게시판(메뉴 차례).
     * @param {GadgetPageTitles} titles - 제목 만들기.
     * @returns {HomeLayoutBody} 배치 + 가젯.
     */
    static initialLayout(folderName: string | null, boards: readonly GadgetPageBoard[], titles: GadgetPageTitles): HomeLayoutBody {
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

        const gallery = boards.find((b) => b.layout === 'GALLERY') ?? null;
        let y = 0;
        put('POST_LIST', titles.popular(folder), { boardScope: 'FOLDER', boardIds: [], count: 10, sort: 'POPULAR', periodTabs: true }, 0, y, gallery ? 16 : 24, 16);
        if (gallery) put('GALLERY', titles.gallery(gallery.name), { boardScope: 'PICK', boardIds: [gallery.slug], count: 6 }, 16, y, 8, 16);
        y += 16;

        boards.forEach((board, index) => {
            const last = index === boards.length - 1;
            const left = index % 2 === 0;
            const w = left && last ? 24 : 12;
            put('BOARD_CARDS', board.name, { boardScope: 'PICK', boardIds: [board.slug], perBoard: 4 }, left ? 0 : 12, y, w, 20);
            if (!left || last) y += 20;
        });

        put('POST_LIST', titles.latest(folder), { boardScope: 'FOLDER', boardIds: [], count: 16, sort: 'LATEST', periodTabs: false }, 0, y, 24, 14);
        return { layout: { columns, items }, gadgets };
    }
}
