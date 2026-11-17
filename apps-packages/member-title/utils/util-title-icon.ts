/**
 * 회원 칭호 도트 그림 — 12×12 픽셀 지도 → 색별 SVG 경로(member-title-contract 「아이콘」). 상태도 요청도 없다(그리기는 `TitleIcon`).
 * 픽셀 지도(`MAP`)·한 가지 색 그림의 색표(`PLAIN`)·동·은·금 단계 그림의 색표(`TIERED`)는 확정 시안 `docs/features/member-title-icons-mockup.html` 의 `M`·`P`·`T` 를
 * 그대로 옮겼다 — 고칠 때는 시안과 함께 고친다. 등급 도트(`util-grade-sprite`)와 같은 방식이다(이미지 파일 없음, `crispEdges`, 한 칸 = 1 CSS px × 정수배).
 *
 * 그림 키는 서버 `titles.icon` 문자열 그대로다: 기본 칭호 7종(`pen` 이달의 작가 · `trophy` 올해의 작가 · `wreath` 명예 작가 · `mag` 리뷰어 · `mic` 기자단 ·
 * `popular` 인기글 · `cal` 출석) + 운영자가 새 칭호에 고르는 범용 그림(`medal` · `bulb` · `heart`).
 * 단계 칭호는 단계마다 색 키(`BRONZE` · `SILVER` · `GOLD`) — 모양은 그대로, 색만 바뀐다(`mag` 돋보기 테 · `popular` 메달 · `medal`).
 */

/** 서버 `titles.icon` 이 가질 수 있는 그림 키 */
export type TitleIconKey = 'pen' | 'trophy' | 'wreath' | 'mag' | 'mic' | 'popular' | 'cal' | 'medal' | 'bulb' | 'heart';

/** 단계 색 키 — 단계 없는 칭호는 null */
export type TitleTier = 'BRONZE' | 'SILVER' | 'GOLD';

/** 한 색의 칸들 — SVG `<path d>`(가로로 이어진 같은 글자 칸을 한 사각형으로 묶었다) */
export interface TitleIconLayer {
    color: string;
    d: string;
}

/** 픽셀 지도: 12칸 × 12줄, `.` = 투명, 글자 = 색표의 키 */
const MAP: Readonly<Record<TitleIconKey, readonly string[]>> = {
    pen: ['..........lb', '.........lbx', '........lbx.', '.......lbx..', '......ggG...', '.....lbx....', '....lbx.....', '...nnN......', '..nkN.......', '.nN.........', 'N...........', '............'],
    trophy: ['..oooooooo..', 'ooolbbbbxooo', 'o.olbbbbxo.o', 'o.olbbbbxo.o', '.oolbbbbxoo.', '...olbbxo...', '....obxo....', '....oxxo....', '....oxxo....', '...oooooo...', '..olbbbbxo..', '..oooooooo..'],
    wreath: ['..g......g..', '.gG......Gg.', 'gG...yy...Gg', 'g...yyyy...g', 'gG.yyyyyy.Gg', 'g...yyyy...g', 'gG..y..y..Gg', '.gG......Gg.', '..gG....Gg..', '...gGrrGg...', '....r..r....', '...r....r...'],
    mag: ['..oooooo....', '.olllbbbo...', 'olbbyybbbo..', 'obyyyyyybo..', 'obbyyyybbo..', 'obbybbybbo..', '.obbbbbbo...', '..oooooox...', '........xx..', '.........xx.', '..........xx', '............'],
    mic: ['....oooo....', '...ollbbo...', '...olbbxo...', '...olbbxo...', '.o.olbbxo.o.', '.o..oooo..o.', '..o......o..', '...oooooo...', '.....xx.....', '.....xx.....', '...xxxxxx...', '............'],
    popular: ['ooooooooo...', 'obbbbbbbo...', 'obxxxxxbo...', 'obbbbbbbo...', 'obxxxxbbo...', 'obbbbbbror..', 'obbbbbbrRr..', 'obxxxxxOOO..', 'obbbbbOyyyO.', 'obxxxbOyYyO.', 'ooooooOyyyO.', '.......OOO..'],
    cal: ['...x....x...', '.orxrrrrxro.', '.orrrrrrrro.', '.obbbbbbbbo.', '.obbbbbbbgo.', '.obbbbbbgbo.', '.obgbbbgbbo.', '.obbgbgbbbo.', '.obbbgbbbbo.', '.obbbbbbbbo.', '.oooooooooo.', '............'],
    medal: ['..rr....rr..', '...rr..rr...', '....rrrr....', '...oooooo...', '..olllbbxo..', '.olbbyybbxo.', '.olbyyyybxo.', '.obbbyybbxo.', '.obbbbbbbxo.', '..obbbbbxo..', '...oooooo...', '............'],
    bulb: ['....oooo....', '...olyyyo...', '..olyyyyyo..', '..oyyyyyyo..', '..oyywwyyo..', '..oyyyyyyo..', '...oyyyyo...', '....oyyo....', '....xxxx....', '....oooo....', '....xxxx....', '.....oo.....'],
    heart: ['............', '..oo....oo..', '.olro..orro.', 'olrrroorrrro', 'olrrrrrrrrro', 'orrrrrrrrrro', '.orrrrrrrro.', '..orrrrrro..', '...orrrro...', '....orro....', '.....oo.....', '............'],
};

type Palette = Readonly<Record<string, string>>;

/** 한 가지 색만 있는 그림 */
const PLAIN: Readonly<Partial<Record<TitleIconKey, Palette>>> = {
    pen: { l: '#7F77DD', b: '#534AB7', x: '#26215C', g: '#FAC775', G: '#BA7517', n: '#EF9F27', N: '#854F0B', k: '#412402' },
    trophy: { o: '#854F0B', l: '#FAEEDA', b: '#FAC775', x: '#EF9F27' },
    wreath: { g: '#97C459', G: '#3B6D11', y: '#EF9F27', r: '#E24B4A' },
    mic: { o: '#444441', l: '#F1EFE8', b: '#B4B2A9', x: '#5F5E5A' },
    cal: { o: '#5F5E5A', r: '#E24B4A', x: '#2C2C2A', b: '#ffffff', g: '#3B6D11' },
    bulb: { o: '#854F0B', l: '#ffffff', y: '#FAC775', w: '#FAEEDA', x: '#888780' },
    heart: { o: '#72243E', r: '#D4537E', l: '#F4C0D1' },
};

/** 동·은·금 단계가 있는 그림 — 모양은 같고 색만 바뀐다 */
const TIERED: Readonly<Partial<Record<TitleIconKey, Readonly<Record<TitleTier, Palette>>>>> = {
    mag: {
        BRONZE: { o: '#712B13', x: '#993C1D', l: '#ffffff', b: '#E6F1FB', y: '#7F77DD' },
        SILVER: { o: '#5F5E5A', x: '#888780', l: '#ffffff', b: '#E6F1FB', y: '#7F77DD' },
        GOLD: { o: '#854F0B', x: '#EF9F27', l: '#ffffff', b: '#E6F1FB', y: '#7F77DD' },
    },
    popular: {
        BRONZE: { o: '#5F5E5A', b: '#ffffff', x: '#B4B2A9', r: '#378ADD', R: '#0C447C', O: '#712B13', y: '#D85A30', Y: '#F0997B' },
        SILVER: { o: '#5F5E5A', b: '#ffffff', x: '#B4B2A9', r: '#378ADD', R: '#0C447C', O: '#444441', y: '#B4B2A9', Y: '#F1EFE8' },
        GOLD: { o: '#5F5E5A', b: '#ffffff', x: '#B4B2A9', r: '#378ADD', R: '#0C447C', O: '#854F0B', y: '#EF9F27', Y: '#FAC775' },
    },
    medal: {
        BRONZE: { o: '#712B13', l: '#F5C4B3', b: '#D85A30', x: '#993C1D', y: '#F0997B', r: '#378ADD' },
        SILVER: { o: '#444441', l: '#ffffff', b: '#D3D1C7', x: '#888780', y: '#F1EFE8', r: '#378ADD' },
        GOLD: { o: '#854F0B', l: '#FAEEDA', b: '#FAC775', x: '#EF9F27', y: '#ffffff', r: '#378ADD' },
    },
};

/** 단계 그림인데 단계가 없을 때(예: 체험단 기능 전의 리뷰어 — 계약 「구현 결정」 6) 쓰는 색 — 시안에 없는 경우라 가운데인 은색으로 둔다 */
const NO_TIER_FALLBACK: TitleTier = 'SILVER';

/** 모르는 그림 키(서버에 새 키가 생겼는데 화면이 아직 모를 때)를 대신 그리는 그림 */
const FALLBACK_ICON: TitleIconKey = 'medal';

const cache = new Map<string, TitleIconLayer[]>();

export default class UtilTitleIcon {
    /** 시안의 그림 키 목록(관리자 그림 고르기의 차례 — 기본 칭호 7종 다음 범용 3종) */
    static readonly ICONS: readonly TitleIconKey[] = ['pen', 'trophy', 'wreath', 'mag', 'mic', 'popular', 'cal', 'medal', 'bulb', 'heart'];

    /** 단계 색 차례(낮은 것부터) */
    static readonly TIERS: readonly TitleTier[] = ['BRONZE', 'SILVER', 'GOLD'];

    /** 한 변의 칸 수 */
    static readonly SIZE = 12;

    /**
     * 아는 그림 키인가.
     * @param {string | null | undefined} icon - 서버 `icon`.
     * @returns {boolean} 시안에 있는 키면 true.
     */
    static isIcon(icon: string | null | undefined): icon is TitleIconKey {
        return typeof icon === 'string' && Object.prototype.hasOwnProperty.call(MAP, icon);
    }

    /**
     * 단계 색이 있는 그림인가(리뷰어 돋보기 · 인기글 · 메달).
     * @param {string | null | undefined} icon - 그림 키.
     * @returns {boolean} 단계마다 색이 바뀌면 true.
     */
    static isTiered(icon: string | null | undefined): boolean {
        return UtilTitleIcon.isIcon(icon) && TIERED[icon] !== undefined;
    }

    /**
     * 그림 한 개의 색별 경로 — 같은 키·단계는 한 번만 만든다.
     * @param {string} icon - 그림 키(모르면 메달로 그린다).
     * @param {TitleTier | null} tier - 단계(단계 없는 그림은 무시한다).
     * @returns {TitleIconLayer[]} 색별 경로.
     */
    static art(icon: string, tier: TitleTier | null): TitleIconLayer[] {
        const key: TitleIconKey = UtilTitleIcon.isIcon(icon) ? icon : FALLBACK_ICON;
        const tiered = TIERED[key];
        const usedTier = tiered ? (tier ?? NO_TIER_FALLBACK) : null;
        const cacheKey = `${key}:${usedTier ?? ''}`;
        const hit = cache.get(cacheKey);
        if (hit) return hit;
        const palette: Palette = tiered && usedTier ? tiered[usedTier] : (PLAIN[key] ?? {});
        const paths = new Map<string, string>();
        MAP[key].forEach((row, y) => {
            let x = 0;
            while (x < row.length) {
                const ch = row[x]!;
                const color = ch === '.' ? undefined : palette[ch];
                if (!color) {
                    x += 1;
                    continue;
                }
                let w = 1;
                while (x + w < row.length && row[x + w] === ch) w += 1;
                paths.set(color, `${paths.get(color) ?? ''}M${x} ${y}h${w}v1h-${w}z`);
                x += w;
            }
        });
        const layers = [...paths].map(([color, d]) => ({ color, d }));
        cache.set(cacheKey, layers);
        return layers;
    }
}
