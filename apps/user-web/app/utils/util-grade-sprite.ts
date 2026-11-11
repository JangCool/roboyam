import type { GradeSpriteKey } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 도트 캐릭터 그림 — 픽셀 지도 → 색별 SVG 경로(grade-level-contract 3절). 상태도 요청도 없다(그리기는 `GradeSprite`).
 * 픽셀 지도(`HEAD`·`BIG`·등급별 머리 장식)·색(`BODY`·`EX`·`EYE`·`AURA`)·전설·신화 장면 규칙(`legend`·`myth`)은 확정 시안 `docs/features/grade-level-mockup.html` 의 것을 그대로 옮겼다 —
 * 고칠 때는 시안과 함께 고친다. 작은 판이 정본이다(큰 판 = 같은 그림을 2배로).
 *  - 기본 12×12 · 전설 16×14(큰 왕관 + 금빛 오라 + 모서리 반짝이) · 신화 20×14(황금 날개 + 금빛 오라 + 보라 화면판)
 *  - 전설·신화는 4장면(220ms 마다 — 반짝이 깜빡·오라 숨쉬기·날개 펄럭). 모든 장면에 같은 칸은 `base` 로 한 번, 장면마다 다른 칸만 `frames` 에 둔다.
 */

/** 한 색의 칸들 — SVG `<path d>`(가로로 이어진 칸을 한 사각형으로 묶었다) */
export interface SpriteLayer {
    color: string;
    d: string;
}

/** 도트 캐릭터 한 개의 그림 */
export interface SpriteArt {
    /** 칸 수(가로·세로) */
    width: number;
    height: number;
    /** 모든 장면에 같은 칸 */
    base: SpriteLayer[];
    /** 장면마다 다른 칸(움직이는 전설·신화만 4장면, 그 밖은 빈 배열) */
    frames: SpriteLayer[][];
}

/** 몸통(머리) — 12칸 × 8줄. o 테두리 · l 밝은 면 · b 몸 · x 옆 귀 · v 화면판 · w 눈 */
const HEAD = ['..oooooooo..', '.olbbbbbbbo.', 'xobvvvvvvbox', 'xobvwvvwvbox', 'xobvwvvwvbox', 'xobvvvvvvbox', '.obbbbbbbbo.', '..oooooooo..'];
/** 전설·신화의 보석 큰 왕관 — 12칸 × 4줄 */
const BIG = ['..y..yy..y..', '..yyyyyyyy..', '..yryuuyry..', '..YYYYYYYY..'];
/** 신화의 날개 한쪽(왼쪽 기준, 오른쪽은 거울) — 3칸 × 6줄 */
const WING = ['..Q', '.QQ', 'QqQ', 'QqQ', 'QQ.', 'Q..'];
/** 신화 날개가 붙는 윗줄(장면별 — 펄럭) */
const WING_TOP = [5, 4, 5, 6] as const;

/** 몸 색 묶음 — G 초록 · B 파랑 · T 청록 · P 보라 · C 주황 · A 금(대가) · L 전설 · M 신화 */
type BodyKey = 'G' | 'B' | 'T' | 'P' | 'C' | 'A' | 'L' | 'M';

/** 등급 도트 한 종류 — 몸 색 · 눈빛(0 흰색 · 1 민트 · 2 금빛) · 머리 장식(12칸 × 4줄, 전설·신화는 따로 그린다) */
interface SpriteDef {
    body: BodyKey;
    eye: 0 | 1 | 2;
    top?: readonly string[];
}

const DEFS: Readonly<Record<GradeSpriteKey, SpriteDef>> = {
    SPROUT: { body: 'G', eye: 0, top: ['..GGG..GGG..', '...GgGGgG...', '.....GG.....', '.....GG.....'] },
    BOLT: { body: 'G', eye: 0, top: ['....oooo....', '...onoono...', '....oooo....', '.....oo.....'] },
    ANTENNA: { body: 'B', eye: 0, top: ['.....aa.....', '....aaaa....', '.....oo.....', '.....oo.....'] },
    BATTERY: { body: 'B', eye: 0, top: ['...oooooo...', '...oGGnnoo..', '...oooooo...', '............'] },
    GEAR: { body: 'T', eye: 0, top: ['....n..n....', '...nnnnnn...', '...nnvvnn...', '....nnnn....'] },
    HEART: { body: 'P', eye: 1, top: ['...hh..hh...', '...hhhhhh...', '....hhhh....', '.....hh.....'] },
    LIGHTNING: { body: 'P', eye: 1, top: ['......yy....', '.....yy.....', '....yyyy....', '....yy......'] },
    STAR: { body: 'C', eye: 1, top: ['.....yy.....', '...yyyyyy...', '....yyyy....', '....y..y....'] },
    CROWN: { body: 'A', eye: 2, top: ['...y.yy.y...', '...yyyyyy...', '...YYYYYY...', '............'] },
    LEGEND: { body: 'L', eye: 2 },
    MYTH: { body: 'M', eye: 2 },
};

const EYE = ['#ffffff', '#5DCAA5', '#FAC775'] as const;
const BODY: Readonly<Record<BodyKey, Readonly<Record<string, string>>>> = {
    G: { b: '#97C459', l: '#C0DD97', o: '#27500A', x: '#639922' },
    B: { b: '#85B7EB', l: '#B5D4F4', o: '#0C447C', x: '#378ADD' },
    T: { b: '#5DCAA5', l: '#9FE1CB', o: '#085041', x: '#1D9E75' },
    P: { b: '#AFA9EC', l: '#CECBF6', o: '#3C3489', x: '#7F77DD' },
    C: { b: '#F0997B', l: '#F5C4B3', o: '#712B13', x: '#D85A30' },
    A: { b: '#FAC775', l: '#FAEEDA', o: '#854F0B', x: '#EF9F27', y: '#EF9F27' },
    L: { b: '#EF9F27', l: '#FAC775', o: '#633806', x: '#BA7517', y: '#FAC775' },
    M: { b: '#FAC775', l: '#ffffff', o: '#854F0B', x: '#EF9F27', y: '#EF9F27', r: '#378ADD', u: '#E24B4A', v: '#26215C', q: '#FAEEDA', Q: '#BA7517' },
};
/** 장식 색 — 몸 색 묶음이 같은 글자를 가지면 그쪽이 이긴다 */
const EX: Readonly<Record<string, string>> = {
    g: '#97C459',
    G: '#3B6D11',
    a: '#FAC775',
    n: '#B4B2A9',
    h: '#D4537E',
    y: '#EF9F27',
    Y: '#854F0B',
    r: '#E24B4A',
    u: '#378ADD',
    v: '#042C53',
    S: '#EF9F27',
    s: '#ffffff',
};
/** 금빛 오라(z) — 장면별 색(숨쉬기) */
const AURA: Partial<Record<BodyKey, readonly string[]>> = {
    L: ['#FAC775', '#EF9F27', '#FAC775', '#FAEEDA'],
    M: ['#EF9F27', '#FAC775', '#FAEEDA', '#FAC775'],
};

/** 서열(1~11) → 기본 도트(관리자 등급 설정의 기본과 같은 차례) */
const ORDER: readonly GradeSpriteKey[] = ['SPROUT', 'BOLT', 'ANTENNA', 'BATTERY', 'GEAR', 'HEART', 'LIGHTNING', 'STAR', 'CROWN', 'LEGEND', 'MYTH'];

function pad(map: readonly string[], left: number, right: number, top: number, bottom: number): string[] {
    const width = map[0]!.length + left + right;
    const empty = '.'.repeat(width);
    return [...Array<string>(top).fill(empty), ...map.map((row) => '.'.repeat(left) + row + '.'.repeat(right)), ...Array<string>(bottom).fill(empty)];
}

/** 빈 칸 중 상하좌우로 그림에 닿는 칸을 오라(z)로 */
function aura(map: readonly string[]): string[] {
    const grid = map.map((row) => [...row]);
    const out = grid.map((row) => row.slice());
    const near: readonly (readonly [number, number])[] = [
        [0, 1],
        [1, 0],
        [0, -1],
        [-1, 0],
    ];
    for (let y = 0; y < grid.length; y++) {
        for (let x = 0; x < grid[0]!.length; x++) {
            if (grid[y]![x] !== '.') continue;
            if (near.some(([dx, dy]) => {
                const cell = grid[y + dy]?.[x + dx];
                return cell !== undefined && cell !== '.';
            })) out[y]![x] = 'z';
        }
    }
    return out.map((row) => row.join(''));
}

function put(map: readonly string[], points: readonly (readonly [number, number])[], ch: string): string[] {
    const grid = map.map((row) => [...row]);
    for (const [x, y] of points) if (grid[y]?.[x] !== undefined) grid[y]![x] = ch;
    return grid.map((row) => row.join(''));
}

/** 전설 한 장면 — 큰 왕관 + 몸 + 오라, 네 모서리 반짝이가 차례로 깜빡인다 */
function legend(frame: number): string[] {
    let map = aura(pad([...BIG, ...HEAD], 2, 2, 1, 1));
    const corners: readonly (readonly [number, number])[] = [
        [0, 0],
        [15, 0],
        [0, 13],
        [15, 13],
    ];
    corners.forEach(([x, y], i) => {
        const phase = (frame + i) % 4;
        if (phase !== 2) map = put(map, [[x, y]], phase === 0 ? 's' : 'S');
    });
    return map;
}

/** 신화 한 장면 — 큰 왕관 + 몸 + 양쪽 날개(펄럭) + 오라, 둘째 장면에 왕관 위 반짝 */
function myth(frame: number): string[] {
    let map = pad([...BIG, ...HEAD], 4, 4, 1, 1);
    const top = WING_TOP[frame]!;
    WING.forEach((wing, i) => {
        const y = top + i;
        if (!map[y]) return;
        const row = [...map[y]!];
        [...wing].forEach((ch, j) => {
            if (ch === '.') return;
            row[1 + j] = ch;
            row[18 - j] = ch;
        });
        map[y] = row.join('');
    });
    map = aura(map);
    if (frame === 1) map = put(map, [[9, 1], [10, 1]], 's');
    return map;
}

/** 한 장면의 칸별 색(빈 칸 null) */
function colors(key: GradeSpriteKey, frame: number): (string | null)[][] {
    const def = DEFS[key];
    const map = def.body === 'L' ? legend(frame) : def.body === 'M' ? myth(frame) : [...(def.top ?? []), ...HEAD];
    const palette: Record<string, string | undefined> = { ...EX, ...BODY[def.body], w: EYE[def.eye], z: AURA[def.body]?.[frame] };
    return map.map((row) => [...row].map((ch) => (ch !== '.' ? (palette[ch] ?? null) : null)));
}

/** 칸 색 표 → 색별 경로(가로로 이어진 같은 색 칸은 한 사각형) */
function layers(grid: readonly (readonly (string | null)[])[]): SpriteLayer[] {
    const byColor = new Map<string, string>();
    grid.forEach((row, y) => {
        let x = 0;
        while (x < row.length) {
            const color = row[x];
            if (!color) {
                x++;
                continue;
            }
            let end = x + 1;
            while (end < row.length && row[end] === color) end++;
            byColor.set(color, `${byColor.get(color) ?? ''}M${x} ${y}h${end - x}v1h-${end - x}z`);
            x = end;
        }
    });
    return [...byColor].map(([color, d]) => ({ color, d }));
}

function build(key: GradeSpriteKey): SpriteArt {
    const animated = key === 'LEGEND' || key === 'MYTH';
    if (!animated) {
        const grid = colors(key, 0);
        return { width: grid[0]!.length, height: grid.length, base: layers(grid), frames: [] };
    }
    const scenes = [0, 1, 2, 3].map((f) => colors(key, f));
    const first = scenes[0]!;
    const same = first.map((row, y) => row.map((color, x) => (color && scenes.every((s) => s[y]![x] === color) ? color : null)));
    const frames = scenes.map((scene) => layers(scene.map((row, y) => row.map((color, x) => (same[y]![x] ? null : color)))));
    return { width: first[0]!.length, height: first.length, base: layers(same), frames };
}

const cache = new Map<GradeSpriteKey, SpriteArt>();

export default class UtilGradeSprite {
    /** 도트 종류(서열 순) */
    static readonly KEYS: readonly GradeSpriteKey[] = ORDER;

    /** 움직이는 장면 한 장의 시간(ms) — 4장면을 돈다 */
    static readonly FRAME_MS = 220;

    /**
     * 도트 종류 이름인가.
     * @param {unknown} value - 서버 값.
     * @returns {boolean} 11가지 중 하나면 true.
     */
    static isKey(value: unknown): value is GradeSpriteKey {
        return typeof value === 'string' && (ORDER as readonly string[]).includes(value);
    }

    /**
     * 서열의 기본 도트 — 1~11 밖이면 가까운 끝(1 아래는 새싹, 11 위는 신화).
     * @param {number} level - 등급 서열(1부터).
     * @returns {GradeSpriteKey} 도트.
     */
    static defaultFor(level: number): GradeSpriteKey {
        const index = Math.min(ORDER.length, Math.max(1, Math.floor(Number.isFinite(level) ? level : 1))) - 1;
        return ORDER[index]!;
    }

    /**
     * 도트 그림(한 번 만들면 기억한다).
     * @param {GradeSpriteKey} key - 도트 종류.
     * @returns {SpriteArt} 칸 수와 색별 경로.
     */
    static art(key: GradeSpriteKey): SpriteArt {
        let art = cache.get(key);
        if (!art) {
            art = build(key);
            cache.set(key, art);
        }
        return art;
    }
}
