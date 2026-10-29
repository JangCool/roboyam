import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { GroupRow } from '~/composables/group/api/useGroupApi.types';
import type { GroupBuyListItem } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { PopularPeriod } from '~/composables/home/api/useHomeApi.types';
import type {
    BoardCard,
    BoardCardsConfig,
    GadgetBadgeConfig,
    GadgetEmptyStyle,
    GadgetKind,
    GadgetMore,
    GadgetStyle,
    GalleryItem,
    HighlightConfig,
    HomeGadget,
    HomeGadgetsResponse,
    HomeHighlight,
    NoticeTextData,
    PostListConfig,
    PostListMeta,
    PostListThumbnail,
    QuickLinkItem,
    QuickLinksConfig,
} from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import UtilColor from '~/utils/util-color';
import UtilGroupBuy from '~/utils/util-group-buy';

/**
 * 홈 가젯의 순수 계산 — 서버 응답(`GET /api/home/gadgets`)을 읽고(없는 칸·모르는 값에도 깨지지 않게), 가젯을 폭에 따라 거르고,
 * 「더보기」·바로가기 주소, 글 줄 모양, 꾸미기 CSS 변수를 정한다. 상태도 요청도 없다. community `utils/util-home-gadget.ts` 를 로보얌 가젯 8종으로 줄였다.
 * 색 값은 `UtilColor`(프리셋 12색·자유 색 보정), 종류 → 컴포넌트는 `UtilHomeGadgetRegistry`.
 *
 * **모르는 키는 건너뛴다**: 모르는 종류(`type`)는 `isVisible` 이 false(그리지 않는다), 모르는 값은 칸마다 기본값.
 */

/** 가젯 종류 8종 — 화면이 아는 것 */
const KINDS: readonly GadgetKind[] = ['HIGHLIGHT', 'POST_LIST', 'BOARD_CARDS', 'GALLERY', 'GROUPS', 'GROUP_BUY', 'QUICK_LINKS', 'NOTICE_TEXT'];

const META_KEYS: readonly PostListMeta[] = ['BOARD', 'PREFIX', 'COMMENTS', 'TIME', 'AUTHOR'];
/** 글 가젯의 기본 표시 칸(community 계약서 2-1) */
const DEFAULT_META: readonly PostListMeta[] = ['BOARD', 'PREFIX', 'COMMENTS', 'TIME'];

/** 비어도 영역을 남기는 종류(키가 빠진 응답의 기본값) — 제목이 있는 내용 영역. 공구·바로가기·안내 글은 비면 사라진다 */
const KEEP_KINDS: readonly GadgetKind[] = ['POST_LIST', 'BOARD_CARDS', 'GALLERY', 'HIGHLIGHT', 'GROUPS'];

const ICON = /^pi-[a-z0-9-]{2,30}$/;
const SLUG = /^[A-Za-z0-9_-]{1,60}$/;
/** 응답 한 번에 받을 가젯 상한 — 이상한 응답에 화면이 무거워지지 않게(서버 한도는 템플릿당 20) */
const MAX_GADGETS = 40;

function obj(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function text(value: unknown, fallback: string, max = 1000): string {
    return typeof value === 'string' ? value.slice(0, max) : fallback;
}

function bool(value: unknown, fallback: boolean): boolean {
    return typeof value === 'boolean' ? value : fallback;
}

function int(value: unknown, fallback: number, min: number, max: number): number {
    const n = typeof value === 'number' ? value : typeof value === 'string' && value.trim() !== '' ? Number(value) : Number.NaN;
    return Number.isFinite(n) ? Math.min(max, Math.max(min, Math.trunc(n))) : fallback;
}

function oneOf<T extends string>(value: unknown, list: readonly T[], fallback: T): T {
    return list.find((v) => v === value) ?? fallback;
}

function list(value: unknown): unknown[] {
    return Array.isArray(value) ? value : [];
}

/** 이 사이트 안 경로인가 — `/` 로 시작하고 `//`·스킴·공백·제어 문자·역슬래시가 없다 */
function safePath(value: unknown): string | null {
    if (typeof value !== 'string') return null;
    if (!value.startsWith('/') || value.startsWith('//')) return null;
    // eslint-disable-next-line no-control-regex
    if (/[\s\\\u0000-\u001f]/.test(value) || /^\/[a-zA-Z][a-zA-Z0-9+.-]*:/.test(value)) return null;
    return value.length <= 300 ? value : null;
}

/** 게시판이 섞인 글 줄로 쓸 수 있는가 — 그릴 때 꼭 필요한 칸만 본다 */
function isFeedRow(value: unknown): value is PostFeedRow {
    const v = obj(value);
    const board = obj(v.board);
    return typeof v.id === 'string' && typeof v.title === 'string' && typeof v.createdAt === 'string' && typeof board.slug === 'string' && typeof board.name === 'string' && typeof v.author === 'object' && v.author !== null;
}

function isKnownKind(type: unknown): type is GadgetKind {
    return typeof type === 'string' && (KINDS as readonly string[]).includes(type);
}

function readEmpty(raw: unknown, type: unknown): GadgetEmptyStyle {
    const e = obj(raw);
    return { keep: bool(e.keep, UtilHomeGadget.defaultKeep(type)), message: bool(e.message, true), text: text(e.text, '', 40) };
}

function readStyle(raw: unknown, type: unknown): GadgetStyle {
    const s = obj(raw);
    return {
        icon: typeof s.icon === 'string' && ICON.test(s.icon) ? s.icon : null,
        headerStyle: oneOf(s.headerStyle, ['BAR', 'UNDERLINE', 'FILLED', 'PLAIN', 'HIDDEN'] as const, 'BAR'),
        titleSize: oneOf(s.titleSize, ['S', 'M', 'L'] as const, 'M'),
        titleColor: UtilColor.normalize(s.titleColor),
        subtitle: text(s.subtitle, '', 40),
        cardLook: oneOf(s.cardLook, ['DEFAULT', 'FLAT', 'OUTLINE', 'TINT'] as const, 'DEFAULT'),
        cardColor: UtilColor.normalize(s.cardColor),
        mobileFold: oneOf(s.mobileFold, ['NONE', 'OPEN', 'CLOSED'] as const, 'NONE'),
        empty: readEmpty(s.empty, type),
    };
}

function readMore(raw: unknown): GadgetMore | null {
    const m = obj(raw);
    const to = safePath(m.to);
    if (to === null) return null;
    return { label: typeof m.label === 'string' && m.label.trim() !== '' ? m.label.trim().slice(0, 12) : null, to };
}

function readBadge(raw: unknown): GadgetBadgeConfig {
    const b = obj(raw);
    const mode = oneOf(b.mode, ['AUTO', 'FIXED', 'NEUTRAL'] as const, 'AUTO');
    const color = UtilColor.normalize(b.color);
    // 색 없는 고정은 그릴 수 없다 — 자동으로
    return mode === 'FIXED' && color === null ? { mode: 'AUTO', color: null } : { mode, color: mode === 'FIXED' ? color : null };
}

function readGadget(raw: unknown): HomeGadget | null {
    const g = obj(raw);
    const id = typeof g.id === 'string' && g.id !== '' ? g.id : typeof g.id === 'number' && Number.isFinite(g.id) ? String(g.id) : '';
    if (id === '' || typeof g.type !== 'string' || g.type === '') return null;
    return {
        id,
        type: g.type,
        title: text(g.title, '', 40),
        desktop: bool(g.desktop, true),
        mobile: bool(g.mobile, true),
        empty: bool(g.empty, false),
        locked: bool(g.locked, false),
        style: readStyle(g.style, g.type),
        config: obj(g.config),
        more: readMore(g.more),
        data: g.data ?? null,
    };
}

export default class UtilHomeGadget {
    /** 데스크톱으로 보는 폭 — Tailwind `lg`(1024px). 가젯의 PC/모바일 노출(`desktop`·`mobile`)과 홈 격자(`HomeGadgetGrid`)의 데스크톱 배치가 이 경계를 쓴다 */
    static readonly WIDE_QUERY = '(min-width: 1024px)';

    /** 가젯 종류 8종 */
    static readonly KINDS: readonly GadgetKind[] = KINDS;

    /**
     * 서버 응답을 읽는다 — 없는 칸·모르는 값은 기본값, ID 없는 가젯·같은 ID 는 버린다. 배치는 `UtilHomeLayout.read`(모르는 가젯 자리는 버리고, 자리가 없는 가젯은 맨 아래에).
     * @param {unknown} raw - 서버 응답.
     * @returns {HomeGadgetsResponse | null} 읽은 응답. `gadgets` 가 배열이 아니면 null(화면은 오류로 본다).
     */
    static sanitize(raw: unknown): HomeGadgetsResponse | null {
        const r = obj(raw);
        if (!Array.isArray(r.gadgets)) return null;
        const seen = new Set<string>();
        const gadgets: HomeGadget[] = [];
        for (const rawGadget of r.gadgets) {
            if (gadgets.length >= MAX_GADGETS) break;
            const gadget = readGadget(rawGadget);
            if (gadget === null || seen.has(gadget.id)) continue;
            seen.add(gadget.id);
            gadgets.push(gadget);
        }
        return {
            theme: text(r.theme, '', 40),
            layout: UtilHomeLayout.read(r.layout, gadgets.map((g) => g.id)),
            gadgets,
        };
    }

    /**
     * 비었을 때 영역을 남기는 종류별 기본값
     * @param {unknown} type - 가젯 종류.
     * @returns {boolean} 기본이 「남김」이면 true.
     */
    static defaultKeep(type: unknown): boolean {
        return typeof type === 'string' && (KEEP_KINDS as readonly string[]).includes(type);
    }

    /**
     * 비었을 때 통째로 감추는가 — 내용이 없고 영역 유지가 꺼져 있을 때. 잠긴 가젯은 감추지 않는다
     * @param {HomeGadget} gadget - 가젯.
     * @returns {boolean} 감추면 true.
     */
    static hiddenWhenEmpty(gadget: HomeGadget): boolean {
        return !gadget.locked && gadget.empty && !gadget.style.empty.keep;
    }

    /**
     * 지금 폭에서 그리는가 — 아는 종류 · 이 폭에서 켜짐(`desktop`/`mobile`) · 비었는데 감추는 가젯이 아님
     * @param {HomeGadget} gadget - 가젯.
     * @param {boolean} wide - 데스크톱 폭(`lg` 이상)인가.
     * @returns {boolean} 그리면 true.
     */
    static isVisible(gadget: HomeGadget, wide: boolean): boolean {
        if (!isKnownKind(gadget.type)) return false;
        if (!(wide ? gadget.desktop : gadget.mobile)) return false;
        return !UtilHomeGadget.hiddenWhenEmpty(gadget);
    }

    /**
     * 바로가기 한 개의 주소 — `ALL` `/` · `POPULAR` `/popular` · `LATEST` `/latest` · `GROUPS` `/groups` · `GROUP_BUY` `/group-buys` · `BOARD` `/boards/{slug}` · `PATH` 그대로.
     * community 의 `HOTDEAL` 은 로보얌에 핫딜이 없어 뺐다(모르는 종류 = 그리지 않는다).
     * @param {{ kind: string; value: string | null }} item - 항목.
     * @returns {string | null} 사이트 안 경로. 모르는 종류·틀린 값이면 null.
     */
    static linkOf(item: { kind: string; value: string | null }): string | null {
        switch (item.kind) {
            case 'ALL':
                return '/';
            case 'POPULAR':
                return '/popular';
            case 'LATEST':
                return '/latest';
            case 'GROUPS':
                return '/groups';
            case 'GROUP_BUY':
                return '/group-buys';
            case 'BOARD':
                return typeof item.value === 'string' && SLUG.test(item.value) ? `/boards/${item.value}` : null;
            case 'PATH':
                return safePath(item.value);
            default:
                return null;
        }
    }

    /**
     * 바로가기 목록 — 이름이 없거나 주소를 만들 수 없는 항목은 뺀다
     * @param {unknown} data - 가젯 `data`.
     * @returns {QuickLinkItem[]} 항목.
     */
    static quickLinkItems(data: unknown): QuickLinkItem[] {
        const out: QuickLinkItem[] = [];
        for (const raw of list(obj(data).items)) {
            const it = obj(raw);
            const label = typeof it.label === 'string' ? it.label.trim().slice(0, 12) : '';
            const item: QuickLinkItem = { label, kind: typeof it.kind === 'string' ? it.kind : '', value: typeof it.value === 'string' ? it.value : null };
            if (label !== '' && UtilHomeGadget.linkOf(item) !== null) out.push(item);
        }
        return out;
    }

    /**
     * 바로가기 설정
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {QuickLinksConfig} 모양(둥근 버튼·밑줄 탭).
     */
    static quickLinksConfig(config: Record<string, unknown>): QuickLinksConfig {
        return { shape: oneOf(obj(config).shape, ['PILL', 'TAB'] as const, 'PILL') };
    }

    /**
     * 안내 글 — 평문을 줄로 나눠 빈 줄은 뺀다(HTML 로 해석하지 않는다)
     * @param {unknown} data - 가젯 `data`.
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {{ lines: string[]; format: NoticeTextData['format'] }} 줄과 모양.
     */
    static noticeText(data: unknown, config: Record<string, unknown>): { lines: string[]; format: NoticeTextData['format'] } {
        const d = obj(data);
        const raw = typeof d.text === 'string' ? d.text.slice(0, 300) : '';
        const format = oneOf(d.format ?? obj(config).format, ['PLAIN', 'BULLETS'] as const, 'PLAIN');
        return {
            lines: raw
                .split(/\r?\n/)
                .map((l) => l.trim())
                .filter((l) => l !== ''),
            format,
        };
    }

    /* ───────────── 글 목록 ───────────── */

    /**
     * 글 가젯의 글 줄 — 글 한 줄로 쓸 수 없는 값은 뺀다
     * @param {unknown} data - 가젯 `data`(`{ items }`).
     * @returns {PostFeedRow[]} 글 줄.
     */
    static postItems(data: unknown): PostFeedRow[] {
        return list(obj(data).items).filter(isFeedRow);
    }

    /**
     * 인기글 기간 탭의 기간별 글(`data.periods`) — 기간 키가 하나도 없으면 null(탭을 그리지 않는다).
     * @param {unknown} data - 가젯 `data`.
     * @returns {Partial<Record<PopularPeriod, PostFeedRow[]>> | null} 기간 → 글.
     */
    static postPeriods(data: unknown): Partial<Record<PopularPeriod, PostFeedRow[]>> | null {
        const periods = obj(obj(data).periods);
        const out: Partial<Record<PopularPeriod, PostFeedRow[]>> = {};
        for (const key of ['REALTIME', 'YESTERDAY', 'WEEK'] as const) {
            if (Array.isArray(periods[key])) out[key] = list(periods[key]).filter(isFeedRow);
        }
        return Object.keys(out).length ? out : null;
    }

    /**
     * 글 목록 설정(기본값 채움)
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {PostListConfig} 정규화한 설정.
     */
    static postList(config: Record<string, unknown>): PostListConfig {
        const c = obj(config);
        const rank = obj(c.rank);
        return {
            sort: oneOf(c.sort, ['LATEST', 'POPULAR', 'COMMENTS', 'VIEWS', 'LIKES'] as const, 'LATEST'),
            rowStyle: oneOf(c.rowStyle, ['HEADLINE', 'RANK', 'ARTICLE', 'COMPACT'] as const, 'HEADLINE'),
            thumbnail: oneOf(c.thumbnail, ['AUTO', 'NONE', 'RIGHT', 'LEFT'] as const, 'AUTO'),
            meta: Array.isArray(c.meta) ? META_KEYS.filter((k) => (c.meta as unknown[]).includes(k)) : [...DEFAULT_META],
            badge: readBadge(c.badge),
            lead: oneOf(c.lead, ['NONE', 'HERO'] as const, 'NONE'),
            columns: c.columns === 2 || c.columns === '2' ? 2 : 1,
            rank: { show: typeof rank.show === 'boolean' ? rank.show : null, color: UtilColor.normalize(rank.color) },
            titleLines: c.titleLines === 1 || c.titleLines === 2 ? c.titleLines : 0,
            newBadgeHours: int(c.newBadgeHours, 0, 0, 72),
            hotBadgeLikes: int(c.hotBadgeLikes, 0, 0, 1000),
            dividers: bool(c.dividers, true),
            allBoards: !Array.isArray(c.boardIds) || c.boardIds.length === 0,
        };
    }

    /**
     * 사진 자리 — `AUTO` 는 줄 모양에 따른다(헤드라인 오른쪽 · 기사 왼쪽 · 순위·촘촘 없음)
     * @param {PostListConfig} cfg - 설정.
     * @returns {Exclude<PostListThumbnail, 'AUTO'>} 자리.
     */
    static thumbPlacement(cfg: PostListConfig): Exclude<PostListThumbnail, 'AUTO'> {
        if (cfg.thumbnail !== 'AUTO') return cfg.thumbnail;
        return cfg.rowStyle === 'HEADLINE' ? 'RIGHT' : cfg.rowStyle === 'ARTICLE' ? 'LEFT' : 'NONE';
    }

    /**
     * 제목 줄 수 — 0 은 줄 모양에 따른다(촘촘은 1줄, 나머지 2줄)
     * @param {PostListConfig} cfg - 설정.
     * @returns {1 | 2} 줄 수.
     */
    static titleLineCount(cfg: PostListConfig): 1 | 2 {
        if (cfg.titleLines === 1 || cfg.titleLines === 2) return cfg.titleLines;
        return cfg.rowStyle === 'COMPACT' ? 1 : 2;
    }

    /**
     * 순위 번호를 그리는가 — `rank.show` 가 null 이면 줄 모양이 `RANK` 일 때만
     * @param {PostListConfig} cfg - 설정.
     * @returns {boolean} 그리면 true.
     */
    static rankShown(cfg: PostListConfig): boolean {
        return cfg.rank.show ?? cfg.rowStyle === 'RANK';
    }

    /**
     * 추천이 기준 이상인 글인가(HOT)
     * @param {number} likeCount - 추천 수.
     * @param {number} threshold - 기준(0 끔).
     * @returns {boolean} HOT 이면 true.
     */
    static isHot(likeCount: number, threshold: number): boolean {
        return threshold > 0 && likeCount >= threshold;
    }

    /* ───────────── 게시판 카드 · 갤러리 · 하이라이트 · 소모임 · 공구 ───────────── */

    /**
     * 게시판 카드 설정
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {BoardCardsConfig} 설정.
     */
    static boardCardsConfig(config: Record<string, unknown>): BoardCardsConfig {
        const c = obj(config);
        const columns = c.columns === 2 || c.columns === '2' ? 2 : c.columns === 3 || c.columns === '3' ? 3 : 'AUTO';
        return { columns, badge: readBadge(c.badge), cardHead: c.cardHead !== false };
    }

    /**
     * 게시판 카드 — 게시판 slug·이름이 없는 카드는 뺀다(글이 없는 카드는 남는다)
     * @param {unknown} data - 가젯 `data`(`{ cards }`).
     * @returns {BoardCard[]} 카드.
     */
    static boardCards(data: unknown): BoardCard[] {
        const out: BoardCard[] = [];
        for (const raw of list(obj(data).cards)) {
            const c = obj(raw);
            const b = obj(c.board);
            if (typeof b.slug !== 'string' || !SLUG.test(b.slug) || typeof b.name !== 'string') continue;
            out.push({
                board: {
                    slug: b.slug,
                    name: b.name,
                    nameEn: typeof b.nameEn === 'string' ? b.nameEn : null,
                    color: typeof b.color === 'string' ? b.color : null,
                    investmentNotice: b.investmentNotice === true,
                },
                lead: isFeedRow(c.lead) ? c.lead : null,
                items: list(c.items).filter(isFeedRow),
            });
        }
        return out;
    }

    /**
     * 갤러리 칸 — 글 번호·제목이 없는 칸은 뺀다
     * @param {unknown} data - 가젯 `data`(`{ items }`).
     * @returns {GalleryItem[]} 칸.
     */
    static galleryItems(data: unknown): GalleryItem[] {
        const out: GalleryItem[] = [];
        for (const raw of list(obj(data).items)) {
            const g = obj(raw);
            const board = obj(g.board);
            if (typeof g.postId !== 'string' || g.postId === '' || typeof g.title !== 'string') continue;
            out.push({
                postId: g.postId,
                title: g.title,
                thumbnailUrl: typeof g.thumbnailUrl === 'string' && g.thumbnailUrl !== '' ? g.thumbnailUrl : null,
                kind: g.kind === 'GIF' || g.kind === 'VIDEO' ? g.kind : null,
                board: { slug: typeof board.slug === 'string' ? board.slug : '', name: typeof board.name === 'string' ? board.name : '', nameEn: typeof board.nameEn === 'string' ? board.nameEn : null },
                likeCount: typeof g.likeCount === 'number' ? g.likeCount : 0,
                createdAt: typeof g.createdAt === 'string' ? g.createdAt : '',
            });
        }
        return out.slice(0, 12);
    }

    /**
     * 하이라이트 설정
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {HighlightConfig} 설정.
     */
    static highlightConfig(config: Record<string, unknown>): HighlightConfig {
        const c = obj(config);
        return { lead: oneOf(c.lead, ['NONE', 'HERO'] as const, 'HERO'), thumbs: oneOf(c.thumbs, ['ALL', 'FIRST', 'NONE'] as const, 'ALL') };
    }

    /**
     * 하이라이트 칸 — 글이 아닌 칸은 건너뛴다(최대 6)
     * @param {unknown} data - 가젯 `data`(`{ picks }`).
     * @returns {HomeHighlight[]} 하이라이트.
     */
    static highlights(data: unknown): HomeHighlight[] {
        const out: HomeHighlight[] = [];
        for (const raw of list(obj(data).picks)) {
            const h = obj(raw);
            if (typeof h.id !== 'string' || !isFeedRow(h.post)) continue;
            out.push({
                id: h.id,
                post: h.post,
                title: typeof h.title === 'string' && h.title.trim() !== '' ? h.title : null,
                summary: typeof h.summary === 'string' && h.summary.trim() !== '' ? h.summary : null,
                imageUrl: typeof h.imageUrl === 'string' && h.imageUrl !== '' ? h.imageUrl : null,
            });
        }
        return out.slice(0, 6);
    }

    /**
     * 소모임 줄 — 이름·ID 가 없는 줄은 뺀다. 개수는 `config.count`(1~6, 기본 3)로 자른다
     * @param {unknown} data - 가젯 `data`(`{ items }`).
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {GroupRow[]} 소모임 줄.
     */
    static groupRows(data: unknown, config: Record<string, unknown>): GroupRow[] {
        const count = int(obj(config).count, 3, 1, 6);
        return list(obj(data).items)
            .filter((v): v is GroupRow => {
                const g = obj(v);
                return typeof g.id === 'string' && typeof g.name === 'string' && typeof g.memberCount === 'number';
            })
            .slice(0, count);
    }

    /**
     * 공구 장(회원 공구 목록 줄) — ID·이름·공구가·마감 시각이 없는 장은 뺀다. 서버가 가젯 `count` 만큼 주지만 10장을 넘기지 않는다
     * @param {unknown} data - 가젯 `data`(`{ items, salesOpen }`).
     * @returns {GroupBuyListItem[]} 공구.
     */
    static groupBuyItems(data: unknown): GroupBuyListItem[] {
        return list(obj(data).items)
            .filter((v): v is GroupBuyListItem => {
                const g = obj(v);
                return typeof g.id === 'string' && typeof g.title === 'string' && typeof g.groupPrice === 'number' && typeof g.endAt === 'string';
            })
            .slice(0, 10);
    }

    /**
     * 판매 스위치 — 칸이 없으면(옛 응답) 켜짐으로 본다
     * @param {unknown} data - 가젯 `data`.
     * @returns {boolean} 주문을 받으면 true.
     */
    static groupBuySalesOpen(data: unknown): boolean {
        return obj(data).salesOpen !== false;
    }

    /**
     * 할인율(%) — 정상가가 없거나 공구가보다 싸지 않으면 null
     * @param {GroupBuyListItem} item - 공구.
     * @returns {number | null} 반올림한 할인율.
     */
    static discountPercent(item: GroupBuyListItem): number | null {
        if (item.normalPrice === null || item.normalPrice <= item.groupPrice) return null;
        return Math.round((1 - item.groupPrice / item.normalPrice) * 100);
    }

    /**
     * 진행 막대(0~100) — 성사형은 확정 / 성사 최소(`UtilGroupBuy.progress`, 목록 화면과 같다), 선착순은 확정 / 최대
     * @param {GroupBuyListItem} item - 공구.
     * @returns {number | null} 비율. 최대 수량이 없으면 null.
     */
    static soldPercent(item: GroupBuyListItem): number | null {
        if (item.minQty > 0) return UtilGroupBuy.progress(item.currentQty, item.minQty);
        if (item.maxQty <= 0) return null;
        return Math.min(100, Math.max(0, Math.round((item.currentQty / item.maxQty) * 100)));
    }

    /**
     * 마감까지 남은 날 — 24시간이 안 남았으면 0(오늘 마감), 지났으면 음수
     * @param {string} endsAt - 마감 시각(ISO).
     * @param {number} now - 지금(밀리초).
     * @returns {number | null} 남은 날(내림). 시각이 틀리면 null.
     */
    static daysLeft(endsAt: string, now: number): number | null {
        const at = Date.parse(endsAt);
        if (!Number.isFinite(at)) return null;
        return Math.floor((at - now) / 86_400_000);
    }

    /* ───────────── 투자 고지 ───────────── */

    /**
     * 홈에 산업·주식(투자 고지) 게시판 글이 보이는가 — 글 줄의 게시판을 `isNotice` 로 판정하고, 게시판 카드는 카드의 `investmentNotice` 도 본다.
     * @param {HomeGadgetsResponse} res - 응답.
     * @param {(slug: string) => boolean} isNotice - 게시판 slug 가 투자 고지 게시판인가(게시판 목록 `investmentNotice` — slug 로 짐작하지 않는다).
     * @returns {boolean} 하나라도 있으면 true.
     */
    static hasInvestmentBoard(res: HomeGadgetsResponse, isNotice: (slug: string) => boolean): boolean {
        for (const g of res.gadgets) {
            if (g.locked) continue;
            if (g.type === 'POST_LIST' && UtilHomeGadget.postItems(g.data).some((r) => isNotice(r.board.slug))) return true;
            if (g.type === 'HIGHLIGHT' && UtilHomeGadget.highlights(g.data).some((h) => isNotice(h.post.board.slug))) return true;
            if (g.type === 'GALLERY' && UtilHomeGadget.galleryItems(g.data).some((i) => isNotice(i.board.slug))) return true;
            if (g.type === 'BOARD_CARDS' && UtilHomeGadget.boardCards(g.data).some((c) => c.board.investmentNotice || isNotice(c.board.slug))) return true;
        }
        return false;
    }

    /* ───────────── 꾸미기 → CSS ───────────── */

    /**
     * 꾸미기를 CSS 변수로 — 색은 라이트·다크 두 값을 `light-dark()` 로 한 번에(자유 색은 두 바탕에서 읽히게 보정, `UtilColor`).
     * `--gx-c` 제목·막대·밑줄·채움 색 / `--gx-on` 채움 위 글자색 / `--gx-card-c` 카드 테두리·바탕 색. 색이 없으면(자동) 걸지 않아 CSS 기본(주 색상)이 쓰인다.
     * @param {GadgetStyle} style - 꾸미기.
     * @returns {Record<string, string>} 인라인 스타일 변수.
     */
    static frameVars(style: GadgetStyle): Record<string, string> {
        const vars: Record<string, string> = {};
        const title = UtilColor.cssPair(style.titleColor);
        const on = UtilColor.cssOnPair(style.titleColor);
        const card = UtilColor.cssPair(style.cardColor ?? style.titleColor);
        if (title) vars['--gx-c'] = title;
        if (on) vars['--gx-on'] = on;
        if (card) vars['--gx-card-c'] = card;
        return vars;
    }

    /**
     * 색 하나만 CSS 변수로(배지·순위 번호)
     * @param {string} name - 변수 이름.
     * @param {unknown} color - 색 값.
     * @returns {Record<string, string>} 변수 한 개 또는 빈 객체.
     */
    static colorVar(name: string, color: unknown): Record<string, string> {
        const css = UtilColor.cssPair(color);
        return css ? { [name]: css } : {};
    }

    /**
     * 제목 글자 크기 — 기본형(`classic`) M 은 구역 이름 크기(`text-sm`), 카드형(`card`) M 은 카드 머리 크기(`text-base`)
     * @param {'S' | 'M' | 'L'} size - 제목 크기.
     * @param {'classic' | 'card'} look - 가젯 모양.
     * @returns {string} Tailwind 클래스.
     */
    static titleSizeClass(size: 'S' | 'M' | 'L', look: 'classic' | 'card'): string {
        if (look === 'classic') return size === 'S' ? 'text-xs' : size === 'L' ? 'text-base' : 'text-sm';
        return size === 'S' ? 'text-sm' : size === 'L' ? 'text-lg' : 'text-base';
    }

    /**
     * 모바일에서 접을 수 있는 가젯인가
     * @param {GadgetStyle} style - 꾸미기.
     * @returns {boolean} 접을 수 있으면 true.
     */
    static foldable(style: GadgetStyle): boolean {
        return style.mobileFold !== 'NONE' && style.headerStyle !== 'HIDDEN';
    }
}
