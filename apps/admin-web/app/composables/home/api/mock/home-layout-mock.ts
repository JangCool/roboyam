// MOCK: 관리자 홈 배치 API(`/api/admin/home-layouts/**`)의 메모리 목업 — `useHomeLayoutApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
//
// ── 목업 가정(서버 후보 — 보고 「목업 가정」) ─
//   · 테마마다 배치 한 벌. 저장한 적 없으면 테마 기본 배치(아래 `DEFAULTS` — user-web 목업 `home-gadget-mock.ts` 의 테마별 배치와 같은 가젯·자리)를 준다.
//   · 저장은 이 메모리에만 있다 — 새로고침하면 처음(테마 기본 배치)으로 돌아가고, 공개 사이트(user-web 목업)에는 전해지지 않는다.
//   · 새 가젯(`new-<n>`)·서버가 모르는 ID 는 저장 때 새 ID 를 받는다. 새 가젯의 제목은 화면이 보낸 종류 이름, 노출은 PC·모바일 모두.
//   · 고를 수 있는 테마는 기본·다음식·네이버식·로보얌(user-web `util-theme.ts` `THEMES` 와 같은 id — 서버 `SiteThemes.IDS` 에는 아직 `default` 하나).

import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { HomeLayoutBody, HomeLayoutGadget, HomeLayoutSaveRequest, HomeLayoutView } from '~/composables/home/api/useHomeLayoutApi.types';

const THEMES: readonly string[] = ['default', 'daum', 'naver', 'roboyam'];

/** 가젯 한 개 + 자리: [종류, 제목, 데스크톱, 모바일, x, y, w, h] */
type Seed = [type: string, title: string, desktop: boolean, mobile: boolean, x: number, y: number, w: number, h: number];

const DEFAULTS: Record<string, { columns: HomeGridColumns; seeds: Seed[] }> = {
    // 기본형 — 본문 17칸 + 오른쪽 7칸(24칸)
    default: {
        columns: 24,
        seeds: [
            ['GROUP_BUY', '공동구매', false, true, 0, 0, 17, 10],
            ['HIGHLIGHT', '하이라이트', true, true, 0, 10, 17, 13],
            ['POST_LIST', '인기글', true, true, 0, 23, 17, 15],
            ['POST_LIST', '최신글', true, true, 0, 38, 17, 20],
            ['GALLERY', '영상·짤', true, true, 0, 58, 17, 11],
            ['GROUP_BUY', '진행 중 공동구매', true, false, 17, 0, 7, 12],
            ['GROUPS', '인기 소모임', true, false, 17, 12, 7, 9],
            ['NOTICE_TEXT', '운영 원칙', true, false, 17, 21, 7, 6],
        ],
    },
    // 다음식 — 본문 8칸 + 오른쪽 4칸(12칸)
    daum: {
        columns: 12,
        seeds: [
            ['HIGHLIGHT', '하이라이트', true, true, 0, 0, 8, 13],
            ['POST_LIST', '지금 뜨는 글', true, true, 0, 13, 8, 10],
            ['GALLERY', '갤러리', true, true, 0, 23, 8, 11],
            ['POST_LIST', '방금 올라온 글', true, true, 0, 34, 8, 18],
            ['GROUP_BUY', '진행 중 공동구매', true, true, 8, 0, 4, 12],
            ['POST_LIST', '로봇 인기', true, true, 8, 12, 4, 9],
            ['GROUPS', '인기 소모임', true, false, 8, 21, 4, 9],
            ['NOTICE_TEXT', '운영 원칙', true, false, 8, 30, 4, 6],
        ],
    },
    // 네이버형 — 본문 17칸 + 오른쪽 7칸(24칸)
    naver: {
        columns: 24,
        seeds: [
            ['HIGHLIGHT', '하이라이트', true, true, 0, 0, 17, 8],
            ['BOARD_CARDS', '게시판', true, true, 0, 8, 17, 30],
            ['GALLERY', '갤러리', true, true, 0, 38, 17, 11],
            ['POST_LIST', '많이 본 글', true, true, 17, 0, 7, 9],
            ['GROUP_BUY', '진행 중 공동구매', true, true, 17, 9, 7, 12],
            ['GROUPS', '인기 소모임', true, false, 17, 21, 7, 9],
        ],
    },
    // 로보얌형 — 본문 넓은 열 17칸 + 오른쪽 열 7칸(24칸)
    roboyam: {
        columns: 24,
        seeds: [
            ['HIGHLIGHT', '하이라이트', true, true, 0, 0, 17, 12],
            ['POST_LIST', '인기글', true, true, 0, 12, 17, 8],
            ['GROUP_BUY', '진행 중 공동구매', false, true, 0, 20, 17, 10],
            ['GALLERY', '영상·짤', true, true, 0, 30, 17, 7],
            ['POST_LIST', '방금 올라온 글', true, true, 0, 37, 17, 8],
            ['GROUP_BUY', '진행 중 공동구매', true, false, 17, 0, 7, 12],
            ['GROUPS', '인기 소모임', true, true, 17, 12, 7, 9],
        ],
    },
};

const saved = new Map<string, HomeLayoutView>();
let nextId = 8_900_000_000_000_000_000n;

function delay(ms = 200): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T;
}

function themeOf(theme: string): string {
    return THEMES.includes(theme) ? theme : 'default';
}

/** 테마 기본 배치 — 가젯 ID 는 테마마다 9001 부터(user-web 목업과 같은 차례) */
function defaults(theme: string): HomeLayoutBody {
    const def = DEFAULTS[themeOf(theme)]!;
    const gadgets: HomeLayoutGadget[] = def.seeds.map(([type, title, desktop, mobile], i) => ({ id: String(9001 + i), type, title, desktop, mobile }));
    const items = def.seeds.map(([, , , , x, y, w, h], i) => ({ gadgetId: String(9001 + i), x, y, w, h }));
    return { layout: { columns: def.columns, items }, gadgets };
}

/**
 * 테마 하나의 배치 — 저장한 것 또는 테마 기본 배치
 * @param {string} theme - 테마 id.
 * @returns {Promise<HomeLayoutView>} 배치.
 */
export async function mockHomeLayout(theme: string): Promise<HomeLayoutView> {
    await delay();
    const id = themeOf(theme);
    return clone(saved.get(id) ?? { theme: id, ...defaults(id), updatedAt: null, updatedBy: null });
}

/**
 * 테마 기본 배치(초기화용 — 저장하지 않는다)
 * @param {string} theme - 테마 id.
 * @returns {Promise<HomeLayoutBody>} 기본 배치.
 */
export async function mockHomeLayoutDefaults(theme: string): Promise<HomeLayoutBody> {
    await delay(120);
    return clone(defaults(theme));
}

/**
 * 배치 저장 — 모르는 ID(새 가젯)는 새 ID 를 주고, 배치는 공유 레이어로 다시 읽어(범위·겹침) 위로 당긴다.
 * @param {string} theme - 테마 id.
 * @param {HomeLayoutSaveRequest} body - 배치 + 가젯 목록.
 * @returns {Promise<HomeLayoutView>} 저장한 배치.
 */
export async function mockSaveHomeLayout(theme: string, body: HomeLayoutSaveRequest): Promise<HomeLayoutView> {
    await delay(300);
    const id = themeOf(theme);
    const before = saved.get(id) ?? { theme: id, ...defaults(id), updatedAt: null, updatedBy: null };
    const known = new Map(before.gadgets.map((g) => [g.id, g]));
    const seeds = new Map(defaults(id).gadgets.map((g) => [g.id, g]));
    const rename = new Map<string, string>();
    const gadgets: HomeLayoutGadget[] = body.gadgets.map((g) => {
        const existing = known.get(g.id) ?? seeds.get(g.id);
        if (existing) return { ...existing };
        nextId += 1n;
        const fresh = String(nextId);
        rename.set(g.id, fresh);
        return { id: fresh, type: g.type, title: g.title, desktop: true, mobile: true };
    });
    const raw = { columns: body.layout.columns, items: body.layout.items.map((it) => ({ ...it, gadgetId: rename.get(it.gadgetId) ?? it.gadgetId })) };
    const read = UtilHomeLayout.read(raw, gadgets.map((g) => g.id));
    const view: HomeLayoutView = { theme: id, layout: { columns: read.columns, items: UtilHomeLayout.compact(read.items) }, gadgets, updatedAt: new Date().toISOString(), updatedBy: '나(목업)' };
    saved.set(id, clone(view));
    return clone(view);
}
