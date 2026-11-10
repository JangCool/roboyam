import type { AdSenseDisplaySlot, AdSenseMode, AdSenseSetting } from '~/composables/ad/api/useAdApi.types';

/** 칸 검사 결과 코드 — 화면 문구는 `ad.adsenseProblem.<코드>` */
export type AdSenseProblem = 'clientRequired' | 'clientFormat' | 'slotFormat' | 'layoutKeyRequired' | 'layoutKeyFormat' | 'layoutKeyTooLong' | 'slotRequired';

const CLIENT_RE = /^ca-pub-\d{16}$/;
const SLOT_RE = /^\d{10}$/;
/** 레이아웃 키 — 애드센스가 주는 `-fb+5w+4e-db+86` 꼴(영문·숫자 마디를 `+`·`-` 로 이음) */
const LAYOUT_KEY_RE = /^[-+]?[0-9a-z]+(?:[-+][0-9a-z]+)+$/i;
/** 레이아웃 키 최대 길이(서버 `adsense_board_list_layout_key varchar(64)` — 넘으면 400 `fields.adsense.units.BOARD_LIST.layoutKey`) */
const LAYOUT_KEY_MAX = 64;

const DISPLAY_SLOTS: readonly AdSenseDisplaySlot[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'POST_INLINE'];

function blank(value: string | null | undefined): string | null {
    const s = (value ?? '').trim();
    return s === '' ? null : s;
}

/**
 * 구글 애드센스 설정(광고 기본 설정 › 「구글 애드센스」 탭)의 순수 계산 — 서버(shared `AdSenseSettings`·`AdPlacementStore`)가 저장 때 같은 규칙으로 다시 판정한다.
 */
export default class UtilAdSense {
    static readonly MODES: readonly AdSenseMode[] = ['OFF', 'VERIFY', 'SHOW'];
    static readonly DISPLAY_SLOTS = DISPLAY_SLOTS;
    /** 배너 가젯 넷 — 칸 묶음 「배너 가젯」 */
    static readonly GADGET_SLOTS: readonly AdSenseDisplaySlot[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER'];
    /** 애드센스 스크립트 주소(서버가 index.html 머리에 넣는다 — `?client=` 뒤에 게시자 ID) */
    static readonly SCRIPT_BASE = 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js';
    /** `/ads.txt` 한 줄의 구글 인증 기관 ID(구글이 정한 고정 값) */
    static readonly ADS_TXT_CERT = 'f08c47fec0942fa0';

    /** 저장한 적 없음·옛 서버 = 끔 */
    static initial(): AdSenseSetting {
        return {
            mode: 'OFF',
            client: null,
            adtest: false,
            units: { WIDE_BANNER: null, STRIP_BANNER: null, SQUARE_BANNER: null, SMALL_BANNER: null, WING_BANNER: null, POST_INLINE: null, BOARD_LIST: { slot: null, layoutKey: null } },
        };
    }

    /** 편집용 사본 — 모르는 값·빠진 칸은 기본값 */
    static form(view: AdSenseSetting | null | undefined): AdSenseSetting {
        const base = UtilAdSense.initial();
        if (!view) return base;
        const units = { ...base.units };
        for (const key of DISPLAY_SLOTS) units[key] = view.units?.[key] ?? null;
        units.BOARD_LIST = { slot: view.units?.BOARD_LIST?.slot ?? null, layoutKey: view.units?.BOARD_LIST?.layoutKey ?? null };
        return {
            mode: UtilAdSense.MODES.find((m) => m === view.mode) ?? 'OFF',
            client: view.client ?? null,
            adtest: view.adtest === true,
            units,
        };
    }

    /** 보낼 값 — 빈 글자는 null, 게시자 ID 는 `ca-pub-…` 꼴로(`pub-…` 만 붙여 넣어도 받는다) */
    static body(form: AdSenseSetting): AdSenseSetting {
        const units = { ...form.units };
        for (const key of DISPLAY_SLOTS) units[key] = blank(UtilAdSense.normalizeSlot(form.units[key]));
        units.BOARD_LIST = { slot: blank(UtilAdSense.normalizeSlot(form.units.BOARD_LIST.slot)), layoutKey: blank(UtilAdSense.normalizeLayoutKey(form.units.BOARD_LIST.layoutKey)) };
        return { mode: form.mode, client: blank(UtilAdSense.normalizeClient(form.client)), adtest: form.adtest === true, units };
    }

    /**
     * 게시자 ID 다듬기 — 앞뒤 공백을 빼고, `pub-` + 숫자 16자리면 앞에 `ca-` 를 붙인다.
     * 스크립트 태그·`/ads.txt` 줄을 통째로 붙여 넣어도 그 안의 `ca-pub-…`/`pub-…` 를 뽑는다. 못 찾으면 받은 글자 그대로(검사가 알려 준다).
     */
    static normalizeClient(text: string | null | undefined): string {
        const s = (text ?? '').trim();
        if (s === '') return '';
        const hit = /(?:ca-)?pub-\d{16}(?!\d)/i.exec(s);
        if (!hit) return s;
        const id = hit[0].toLowerCase();
        return id.startsWith('ca-') ? id : `ca-${id}`;
    }

    /** 광고 단위 ID 다듬기 — 공백을 빼고, 광고 코드(`data-ad-slot="1234567890"`)를 붙여 넣으면 그 숫자만 뽑는다 */
    static normalizeSlot(text: string | null | undefined): string {
        const s = (text ?? '').trim();
        if (s === '') return '';
        const code = /data-ad-slot\s*=\s*["']?(\d{10})(?!\d)/i.exec(s);
        if (code?.[1]) return code[1];
        return s.replace(/\s+/g, '');
    }

    /** 레이아웃 키 다듬기 — 광고 코드(`data-ad-layout-key="-fb+5w+4e-db+86"`)를 붙여 넣으면 그 값만 뽑는다 */
    static normalizeLayoutKey(text: string | null | undefined): string {
        const s = (text ?? '').trim();
        if (s === '') return '';
        const code = /data-ad-layout-key\s*=\s*["']?([-+0-9a-z]+)/i.exec(s);
        if (code?.[1]) return code[1];
        return s.replace(/\s+/g, '');
    }

    static validClient(client: string | null | undefined): boolean {
        return CLIENT_RE.test(UtilAdSense.normalizeClient(client));
    }

    /**
     * 저장 전에 알려 줄 칸 문제 — 키는 서버 오류 키와 같은 점 경로(`adsense.client`, `adsense.units.WIDE_BANNER`, `adsense.units.BOARD_LIST.layoutKey`).
     * 게시자 ID 는 「사이트 확인만」·「광고 표시」일 때 필수, 다른 칸은 비어 있으면 괜찮고 넣었으면 형식만 본다(끔이어도 형식은 본다 — 틀린 값을 저장해 두지 않게).
     * 인피드는 단위 ID 와 레이아웃 키를 함께 넣는다(애드센스 인피드 코드에 둘 다 필요).
     */
    static problems(form: AdSenseSetting): Record<string, AdSenseProblem> {
        const out: Record<string, AdSenseProblem> = {};
        const client = UtilAdSense.normalizeClient(form.client);
        if (client === '') {
            if (form.mode !== 'OFF') out['adsense.client'] = 'clientRequired';
        } else if (!CLIENT_RE.test(client)) out['adsense.client'] = 'clientFormat';
        for (const key of DISPLAY_SLOTS) {
            const slot = UtilAdSense.normalizeSlot(form.units[key]);
            if (slot !== '' && !SLOT_RE.test(slot)) out[`adsense.units.${key}`] = 'slotFormat';
        }
        const feedSlot = UtilAdSense.normalizeSlot(form.units.BOARD_LIST.slot);
        const feedKey = UtilAdSense.normalizeLayoutKey(form.units.BOARD_LIST.layoutKey);
        if (feedSlot !== '' && !SLOT_RE.test(feedSlot)) out['adsense.units.BOARD_LIST.slot'] = 'slotFormat';
        else if (feedSlot === '' && feedKey !== '') out['adsense.units.BOARD_LIST.slot'] = 'slotRequired';
        if (feedKey.length > LAYOUT_KEY_MAX) out['adsense.units.BOARD_LIST.layoutKey'] = 'layoutKeyTooLong';
        else if (feedKey !== '' && !LAYOUT_KEY_RE.test(feedKey)) out['adsense.units.BOARD_LIST.layoutKey'] = 'layoutKeyFormat';
        else if (feedKey === '' && feedSlot !== '') out['adsense.units.BOARD_LIST.layoutKey'] = 'layoutKeyRequired';
        return out;
    }

    /** 저장된 값과 다른 칸 수(다듬은 값끼리 비교) */
    static changes(form: AdSenseSetting, saved: AdSenseSetting): number {
        const a = UtilAdSense.body(form);
        const b = UtilAdSense.body(UtilAdSense.form(saved));
        let n = 0;
        if (a.mode !== b.mode) n++;
        if (a.client !== b.client) n++;
        if (a.adtest !== b.adtest) n++;
        for (const key of DISPLAY_SLOTS) if (a.units[key] !== b.units[key]) n++;
        if (a.units.BOARD_LIST.slot !== b.units.BOARD_LIST.slot) n++;
        if (a.units.BOARD_LIST.layoutKey !== b.units.BOARD_LIST.layoutKey) n++;
        return n;
    }

    /** 애드센스를 쓰는 자리 수(단위 ID 가 있는 자리 — 인피드는 두 칸이 다 있어야) */
    static usedSlots(form: AdSenseSetting): number {
        const a = UtilAdSense.body(form);
        return DISPLAY_SLOTS.filter((key) => a.units[key] !== null).length + (a.units.BOARD_LIST.slot && a.units.BOARD_LIST.layoutKey ? 1 : 0);
    }

    /** 서버가 머리에 넣을 스크립트 한 줄(안내용 — 게시자 ID 가 맞지 않으면 null) */
    static scriptTag(client: string | null | undefined): string | null {
        const id = UtilAdSense.normalizeClient(client);
        if (!CLIENT_RE.test(id)) return null;
        return `<script async src="${UtilAdSense.SCRIPT_BASE}?client=${id}" crossorigin="anonymous"></script>`;
    }

    /** 서버가 `/ads.txt` 로 낼 줄(안내용 — `ca-` 를 뺀 `pub-…`) */
    static adsTxtLine(client: string | null | undefined): string | null {
        const id = UtilAdSense.normalizeClient(client);
        if (!CLIENT_RE.test(id)) return null;
        return `google.com, ${id.slice(3)}, DIRECT, ${UtilAdSense.ADS_TXT_CERT}`;
    }
}
