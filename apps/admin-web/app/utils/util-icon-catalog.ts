import type { GadgetTablerIcon, GadgetTablerPath } from '~/composables/home/api/useHomeLayoutApi.types';
import { PRIME_ICON_NAMES } from '~/utils/prime-icon-names';

/** 고르기 목록의 아이콘 하나 — 이름과 찾을 글자(이름 · 태그, 소문자) */
export interface IconCatalogItem {
    name: string;
    search: string;
}

/** Tabler 목록 — 아이콘들과 이름 → path 조각 */
export interface TablerCatalog {
    items: IconCatalogItem[];
    paths: Readonly<Record<string, GadgetTablerPath[]>>;
}

/** 검색 결과 한 번에 그리는 수 — 목록이 커도(Tabler 약 5,200개) 창이 느려지지 않게. 넘으면 「검색어를 더 적어 주세요」 */
const RESULT_MAX = 160;

/**
 * 한국어 별칭 — 한국어로 적으면 이 영어 낱말로 찾는다(이름·태그에 들어 있는 것). 자주 쓰는 것만 몇 개(사용자 2026-11-10 「가능하면 한국어 별칭 몇 개」).
 */
const KO_ALIASES: Readonly<Record<string, readonly string[]>> = {
    별: ['star'],
    즐겨찾기: ['star', 'bookmark'],
    하트: ['heart'],
    좋아요: ['heart', 'thumb'],
    추천: ['thumb', 'star'],
    사람: ['user'],
    회원: ['user'],
    친구: ['users', 'friends'],
    모임: ['users'],
    소모임: ['users'],
    쇼핑: ['shopping', 'cart', 'bag'],
    장바구니: ['cart', 'basket'],
    가방: ['bag'],
    선물: ['gift'],
    태그: ['tag'],
    할인: ['discount', 'tag', 'percent'],
    핫딜: ['flame', 'tag', 'discount'],
    공구: ['shopping', 'users'],
    인기: ['flame', 'trending'],
    불: ['flame', 'fire'],
    번개: ['bolt'],
    트로피: ['trophy'],
    우승: ['trophy', 'award'],
    왕관: ['crown'],
    공지: ['speakerphone', 'megaphone', 'bell'],
    확성기: ['speakerphone', 'megaphone'],
    알림: ['bell'],
    종: ['bell'],
    정보: ['info'],
    댓글: ['message', 'comment'],
    대화: ['message', 'comment', 'chat'],
    채팅: ['message', 'chat'],
    목록: ['list'],
    리스트: ['list'],
    사진: ['photo', 'image'],
    이미지: ['photo', 'image'],
    갤러리: ['photo', 'image'],
    영상: ['video', 'movie'],
    동영상: ['video', 'movie'],
    링크: ['link'],
    바로가기: ['link', 'external'],
    나침반: ['compass'],
    차트: ['chart'],
    그래프: ['chart'],
    통계: ['chart'],
    달력: ['calendar'],
    일정: ['calendar'],
    전구: ['bulb'],
    팁: ['bulb'],
    아이디어: ['bulb'],
    로봇: ['robot'],
    칩: ['cpu', 'microchip'],
    책: ['book'],
    강좌: ['book', 'school'],
    지구: ['world', 'globe'],
    세계: ['world', 'globe'],
    집: ['home'],
    홈: ['home'],
    검색: ['search'],
    설정: ['settings', 'cog'],
    시계: ['clock'],
    시간: ['clock'],
    지도: ['map'],
    위치: ['map-pin', 'location'],
    메일: ['mail'],
    편지: ['mail'],
    전화: ['phone'],
    카메라: ['camera'],
    음악: ['music'],
    게임: ['gamepad', 'game'],
    코드: ['code'],
    개발: ['code'],
    돈: ['coin', 'cash', 'currency', 'wallet'],
    가격: ['coin', 'currency', 'tag'],
    자동차: ['car'],
    비행기: ['plane'],
    여행: ['plane', 'map'],
    음식: ['tools-kitchen', 'pizza', 'burger'],
    커피: ['coffee'],
    운동: ['run', 'ball'],
    해: ['sun'],
    날씨: ['sun', 'cloud'],
    구름: ['cloud'],
    책갈피: ['bookmark'],
    북마크: ['bookmark'],
    깃발: ['flag'],
    체크: ['check'],
    완료: ['check'],
    경고: ['alert', 'exclamation'],
    질문: ['help', 'question'],
    잠금: ['lock'],
    열쇠: ['key'],
    파일: ['file'],
    문서: ['file', 'clipboard'],
    폴더: ['folder'],
    다운로드: ['download'],
    공유: ['share'],
    보기: ['eye'],
    조회: ['eye'],
    반짝: ['sparkles'],
    로켓: ['rocket'],
    뉴스: ['news'],
    소식: ['news'],
};

const HANGUL = /[ㄱ-ㆎ가-힣]/;

/** 찾을 낱말 — 영어는 그대로(공백으로 나눈 것), 한국어는 별칭의 영어 낱말로 바꾼다(별칭에 없는 한국어는 찾을 낱말이 없다) */
function terms(query: string): string[] {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    if (!HANGUL.test(q)) return q.split(/\s+/).filter(Boolean);
    const out = new Set<string>();
    for (const [ko, words] of Object.entries(KO_ALIASES)) {
        if (q.includes(ko) || (ko.length >= 2 && ko.includes(q))) words.forEach((w) => out.add(w));
    }
    return [...out];
}

let tablerLoad: Promise<TablerCatalog> | null = null;

/**
 * 가젯 제목 아이콘 고르기의 목록·검색(관리자 화면만) — PrimeIcons 8.0.2 전부(`prime-icon-names.ts`) · Tabler Icons 3.49.0 outline 전부(`@tabler/icons`, MIT — 고르기 창을 열 때만 늦게 읽는다).
 * 검색은 이름(영어)·Tabler 태그로, 한국어는 몇 가지 별칭(`KO_ALIASES`)으로. 한 번에 {@link RESULT_MAX}개까지만 돌려준다.
 */
export default class UtilIconCatalog {
    static readonly RESULT_MAX = RESULT_MAX;

    /** PrimeIcons 목록(`pi-…` 이름) */
    static prime(): IconCatalogItem[] {
        return PRIME_ICON_NAMES.map((name) => ({ name, search: name.slice(3).replaceAll('-', ' ') }));
    }

    /**
     * Tabler 목록 — 한 번만 읽는다(약 4MB 의 목록 두 개를 `?raw` 글로 받아 풀기 — 관리자 고르기 창에서만)
     * @returns {Promise<TablerCatalog>} 목록.
     */
    static tabler(): Promise<TablerCatalog> {
        tablerLoad ??= Promise.all([import('#tabler-icons/tabler-nodes-outline.json?raw'), import('#tabler-icons/icons.json?raw')]).then(([nodesModule, metaModule]) => {
            const nodes = JSON.parse(nodesModule.default) as Record<string, [string, Record<string, string>][]>;
            const meta = JSON.parse(metaModule.default) as Record<string, { tags?: (string | number)[]; category?: string; styles?: { outline?: unknown } }>;
            const paths: Record<string, GadgetTablerPath[]> = {};
            const items: IconCatalogItem[] = [];
            for (const [name, list] of Object.entries(nodes)) {
                const read = list.filter(([tag]) => tag === 'path').map(([, a]) => UtilIconCatalog.pathOf(a));
                if (read.length === 0) continue;
                paths[name] = read;
                const m = meta[name];
                const tags = (m?.tags ?? []).map(String).join(' ');
                items.push({ name, search: `${name.replaceAll('-', ' ')} ${tags} ${m?.category ?? ''}`.toLowerCase() });
            }
            return { items, paths };
        });
        return tablerLoad;
    }

    /**
     * 받은 path 속성 → 저장 모양(아는 칸만)
     * @param {Record<string, string>} a - 속성.
     * @returns {GadgetTablerPath} path.
     */
    static pathOf(a: Record<string, string>): GadgetTablerPath {
        const out: GadgetTablerPath = { d: a.d ?? '' };
        if (a.fill === 'currentColor' || a.fill === 'none') out.fill = a.fill;
        if (a.stroke === 'currentColor' || a.stroke === 'none') out.stroke = a.stroke;
        if (typeof a.opacity === 'string') out.opacity = a.opacity;
        return out;
    }

    /**
     * Tabler 아이콘 한 개(저장할 값)
     * @param {TablerCatalog} catalog - 목록.
     * @param {string} name - 이름.
     * @returns {GadgetTablerIcon | null} 아이콘(목록에 없으면 null).
     */
    static tablerIcon(catalog: TablerCatalog, name: string): GadgetTablerIcon | null {
        const paths = catalog.paths[name];
        return paths ? { name, paths: paths.map((p) => ({ ...p })) } : null;
    }

    /**
     * 찾기 — 빈 검색어면 처음부터, 아니면 낱말 하나라도 이름·태그에 든 것(이름에 든 것이 앞)
     * @param {IconCatalogItem[]} items - 목록.
     * @param {string} query - 검색어(영어 · 한국어 별칭).
     * @returns {{ items: IconCatalogItem[]; total: number }} 보일 것(최대 {@link RESULT_MAX}개)과 맞은 수.
     */
    static search(items: readonly IconCatalogItem[], query: string): { items: IconCatalogItem[]; total: number } {
        const words = terms(query);
        if (!query.trim()) return { items: items.slice(0, RESULT_MAX), total: items.length };
        if (words.length === 0) return { items: [], total: 0 };
        const byName: IconCatalogItem[] = [];
        const byTag: IconCatalogItem[] = [];
        for (const it of items) {
            if (words.some((w) => it.name.includes(w))) byName.push(it);
            else if (words.some((w) => it.search.includes(w))) byTag.push(it);
        }
        const all = [...byName, ...byTag];
        return { items: all.slice(0, RESULT_MAX), total: all.length };
    }
}
