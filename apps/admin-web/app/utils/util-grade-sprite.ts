import type { GradeSprite } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 도트 캐릭터의 그림(grade-level-contract 3절) — 시안 `docs/features/grade-level-mockup.html` 의 **작은 판** 픽셀 지도·색을 그대로 옮겼다(공개 화면 user-web 과 같은 그림).
 * 한 칸 = 글자 하나: `.` 빈칸, 그 밖은 색 표(몸통 `BODY` → 공통 `EX` → 눈 `w` · 오라 `z`)에서 색을 찾는다. 결과는 `GradeSpriteImage`(SVG 사각형으로 그린다).
 * 크기: 보통 12×12 · 전설 16×14(오라·반짝이) · 신화 20×14(오라·날개). 전설·신화는 4장면(220ms)이 있지만 관리자 미리보기는 첫 장면만 쓴다.
 */

/** 그림 한 장 — 칸 수(가로·세로)와 칠할 칸들 */
export interface GradeSpriteFrame {
    width: number;
    height: number;
    cells: { x: number; y: number; color: string }[];
}

/** 머리(화면판·눈) — 모든 캐릭터 공통 아래 8줄 */
const HEAD = ['..oooooooo..', '.olbbbbbbbo.', 'xobvvvvvvbox', 'xobvwvvwvbox', 'xobvwvvwvbox', 'xobvvvvvvbox', '.obbbbbbbbo.', '..oooooooo..'];
/** 전설·신화의 보석 큰 왕관(위 4줄) */
const BIG = ['..y..yy..y..', '..yyyyyyyy..', '..yryuuyry..', '..YYYYYYYY..'];

/** 몸통 색 묶음 이름 — G 초록 · B 파랑 · T 청록 · P 보라 · C 주황 · A 금빛 · L 전설 · M 신화 */
type BodyKey = 'G' | 'B' | 'T' | 'P' | 'C' | 'A' | 'L' | 'M';

/** 캐릭터마다 — 몸통 색 묶음 · 눈빛(0 흰색 · 1 민트 · 2 금빛) · 머리 위 장식(위 4줄, 전설·신화는 따로 만든다) */
const DEFS: Record<GradeSprite, { body: BodyKey; eye: 0 | 1 | 2; top?: readonly string[] }> = {
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
const BODY: Record<BodyKey, Record<string, string>> = {
    G: { b: '#97C459', l: '#C0DD97', o: '#27500A', x: '#639922' },
    B: { b: '#85B7EB', l: '#B5D4F4', o: '#0C447C', x: '#378ADD' },
    T: { b: '#5DCAA5', l: '#9FE1CB', o: '#085041', x: '#1D9E75' },
    P: { b: '#AFA9EC', l: '#CECBF6', o: '#3C3489', x: '#7F77DD' },
    C: { b: '#F0997B', l: '#F5C4B3', o: '#712B13', x: '#D85A30' },
    A: { b: '#FAC775', l: '#FAEEDA', o: '#854F0B', x: '#EF9F27', y: '#EF9F27' },
    L: { b: '#EF9F27', l: '#FAC775', o: '#633806', x: '#BA7517', y: '#FAC775' },
    M: { b: '#FAC775', l: '#ffffff', o: '#854F0B', x: '#EF9F27', y: '#EF9F27', r: '#378ADD', u: '#E24B4A', v: '#26215C', q: '#FAEEDA', Q: '#BA7517' },
};
/** 공통 색(장식) — 몸통 묶음에 같은 글자가 있으면 몸통 쪽이 이긴다 */
const EX: Record<string, string> = { g: '#97C459', G: '#3B6D11', a: '#FAC775', n: '#B4B2A9', h: '#D4537E', y: '#EF9F27', Y: '#854F0B', r: '#E24B4A', u: '#378ADD', v: '#042C53', S: '#EF9F27', s: '#ffffff' };
/** 오라 색 — 장면마다 */
const AURA: Partial<Record<BodyKey, readonly string[]>> = {
    L: ['#FAC775', '#EF9F27', '#FAC775', '#FAEEDA'],
    M: ['#EF9F27', '#FAC775', '#FAEEDA', '#FAC775'],
};

/** 지도 둘레에 빈칸을 덧댄다 */
function pad(map: readonly string[], left: number, right: number, top: number, bottom: number): string[] {
    const width = (map[0]?.length ?? 0) + left + right;
    const empty = '.'.repeat(width);
    return [...Array<string>(top).fill(empty), ...map.map((row) => '.'.repeat(left) + row + '.'.repeat(right)), ...Array<string>(bottom).fill(empty)];
}

/** 그림에 붙은 빈칸(상하좌우)을 오라(`z`)로 칠한다 */
function aura(map: readonly string[]): string[] {
    const grid = map.map((row) => [...row]);
    const out = grid.map((row) => row.slice());
    for (let y = 0; y < grid.length; y++) {
        for (let x = 0; x < (grid[0]?.length ?? 0); x++) {
            if (grid[y]![x] !== '.') continue;
            const near = [[0, 1], [1, 0], [0, -1], [-1, 0]].some(([dx, dy]) => {
                const cell = grid[y + dy!]?.[x + dx!];
                return cell !== undefined && cell !== '.';
            });
            if (near) out[y]![x] = 'z';
        }
    }
    return out.map((row) => row.join(''));
}

/** 정해진 칸들을 한 글자로 칠한다 */
function put(map: readonly string[], points: readonly (readonly [number, number])[], ch: string): string[] {
    const grid = map.map((row) => [...row]);
    for (const [x, y] of points) if (grid[y]?.[x] !== undefined) grid[y]![x] = ch;
    return grid.map((row) => row.join(''));
}

/** 전설 — 보석 큰 왕관 + 금빛 오라 + 네 귀퉁이 반짝이(장면마다 하나씩 꺼진다) */
function legend(frame: number): string[] {
    let map = aura(pad([...BIG, ...HEAD], 2, 2, 1, 1));
    const corners: readonly (readonly [number, number])[] = [[0, 0], [15, 0], [0, 13], [15, 13]];
    corners.forEach((point, i) => {
        const phase = (frame + i) % 4;
        if (phase !== 2) map = put(map, [point], phase === 0 ? 's' : 'S');
    });
    return map;
}

/** 신화 — 밝은 황금 몸 + 보석 큰 왕관 + 황금 날개(장면마다 오르내린다) + 금빛 오라 */
function myth(frame: number): string[] {
    let map = pad([...BIG, ...HEAD], 4, 4, 1, 1);
    const wing = ['..Q', '.QQ', 'QqQ', 'QqQ', 'QQ.', 'Q..'];
    const offset = [5, 4, 5, 6][frame] ?? 5;
    wing.forEach((part, i) => {
        const y = offset + i;
        const line = map[y];
        if (!line) return;
        const row = [...line];
        [...part].forEach((c, j) => {
            if (c === '.') return;
            row[1 + j] = c;
            row[18 - j] = c;
        });
        map[y] = row.join('');
    });
    map = aura(map);
    if (frame === 1) map = put(map, [[9, 1], [10, 1]], 's');
    return map;
}

export default class UtilGradeSprite {
    /** 도트 캐릭터 11종 — 서열 순(새 등급·옛 응답의 기본값이 이 차례) */
    static readonly SPRITES: readonly GradeSprite[] = ['SPROUT', 'BOLT', 'ANTENNA', 'BATTERY', 'GEAR', 'HEART', 'LIGHTNING', 'STAR', 'CROWN', 'LEGEND', 'MYTH'];

    /**
     * 서열의 기본 도트 — 1단계 새싹 … 11단계 신화. 범위 밖은 끝 값.
     * @param {number} index - 0부터 센 서열 자리.
     * @returns {GradeSprite} 기본 도트.
     */
    static defaultFor(index: number): GradeSprite {
        const list = UtilGradeSprite.SPRITES;
        return list[Math.min(Math.max(index, 0), list.length - 1)]!;
    }

    /** 아는 도트 이름인가 */
    static isSprite(value: unknown): value is GradeSprite {
        return typeof value === 'string' && (UtilGradeSprite.SPRITES as readonly string[]).includes(value);
    }

    /**
     * 도트 한 장면의 칠할 칸들.
     * @param {GradeSprite} sprite - 도트 이름.
     * @param {number} [frame] - 장면(0~3, 전설·신화만 다르다). 기본 첫 장면.
     * @returns {GradeSpriteFrame} 칸 수와 칠할 칸.
     */
    static frame(sprite: GradeSprite, frame = 0): GradeSpriteFrame {
        const def = DEFS[sprite];
        const f = ((frame % 4) + 4) % 4;
        const map = def.body === 'L' ? legend(f) : def.body === 'M' ? myth(f) : [...(def.top ?? []), ...HEAD];
        const palette: Record<string, string | undefined> = { ...EX, ...BODY[def.body], w: EYE[def.eye], z: AURA[def.body]?.[f] };
        const cells: GradeSpriteFrame['cells'] = [];
        map.forEach((row, y) => {
            [...row].forEach((ch, x) => {
                const color = ch === '.' ? undefined : palette[ch];
                if (color) cells.push({ x, y, color });
            });
        });
        return { width: map[0]?.length ?? 0, height: map.length, cells };
    }
}
