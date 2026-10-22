import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilTime, { type DisplayValues } from './util-time';
import UtilTimeWords, { type TimeLanguage } from './util-time-words';

/**
 * 표시 값(시간대·시간 형식·날짜 형식)을 고르는 화면의 **선택지** — 두 앱의 표시 값 화면(사용자 개인 설정·관리자 「일반」 표시 기본값)이 같은 선택지·같은 이름을 쓰게
 * 이 레이어 한 곳에 둔다. 시간대 목록(주요 시간대 묶음 · 모든 시간대 · 옛 철자 별칭), 시간 형식·날짜 형식의 이름과 견본 글자를 만든다.
 * 상태도 요청도 없다 — 시각 글자는 `UtilTime` 이 만든다(날짜 글자를 직접 조립하지 않는다).
 *
 * 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다(레이어 코어와 같은 조건). 두 앱에서 `#layers/display-time/utils/util-display-choices` 로 불러 쓴다.
 * 문구 가운데 구획 안내·칸 이름·토스트는 각 앱의 messages 파일이 갖고, 여기는 **값과 이름**만 안다.
 */

/** 시간 형식 값 */
export type DisplayTimeFormat = DisplayValues['timeFormat'];

/** 날짜 형식 값 */
export type DisplayDateFormat = DisplayValues['dateFormat'];

/** 시간 형식·날짜 형식 선택지 하나 — 글자는 `이름 (견본)` 꼴 */
export type DisplayChoice<T extends string> = { value: T; label: string };

/**
 * 선택지 글자를 지금 언어로 만들 때 주는 것 — 번역 함수와 언어. 안 주면 한국어 그대로다. 이름·묶음 제목은 `t` 로, 견본 글자(오전/AM)와 시간대 도시 이름은 `language`(`ko`·`en`, 모르는 값은 `ko`)로 만든다.
 * 키는 `display.timeFormat_H24`·`display.dateFormat_YMD_DOT`·`display.zoneGroupMajor`·`display.zoneGroupAll` 이다(`i18n/locales/{ko,en}/display.ts`).
 */
export type ChoiceI18n = { t: Translate; language: string };

/**
 * 시간대 선택지 하나 — 값은 IANA 이름, 글자는 한국어 화면에서 `(UTC+09:00) Asia/Seoul · 서울` 꼴(영어 화면은 `(UTC+09:00) Asia/Seoul`).
 * `search` 는 목록 글자 찾기 보조 글자 — 두 언어의 도시 이름(`서울 Seoul`)이라 어느 화면 언어로든 찾을 수 있다(선택 상자의 `filter-fields` 에 `label`·`search` 를 준다). 이름을 모르는 시간대는 빈 글자.
 */
export type DisplayZoneOption = { value: string; label: string; search?: string };

/** 시간대 선택지 묶음 — "주요 시간대"와 "모든 시간대" */
export type DisplayZoneGroup = { label: string; items: DisplayZoneOption[] };

const MINUTE_MS = 60_000;

/**
 * 주요 시간대 — 이름을 외워 둔 고정 목록(브라우저 목록이 없을 때의 폴백이자 "주요 시간대" 묶음)과 도시 이름 `[IANA 이름, 한국어, 영어]`.
 * 한국어 이름은 한국어 화면의 선택지 뒤에 붙고, 영어 이름은 글자 찾기(`search`)에만 쓴다(영어 화면의 IANA 이름이 이미 영어 도시 이름이다).
 * 서비스 시간대는 상수에서 가져온다(서울 이름 리터럴을 화면 코드에 두지 않는다). 서버 `ZoneId` 가 아는 이름만 담는다.
 */
const MAJOR_ZONES: readonly (readonly [string, string, string])[] = [
    ['Pacific/Honolulu', '호놀룰루', 'Honolulu'],
    ['America/Anchorage', '앵커리지', 'Anchorage'],
    ['America/Los_Angeles', '로스앤젤레스', 'Los Angeles'],
    ['America/Denver', '덴버', 'Denver'],
    ['America/Chicago', '시카고', 'Chicago'],
    ['America/New_York', '뉴욕', 'New York'],
    ['America/Sao_Paulo', '상파울루', 'Sao Paulo'],
    ['UTC', '협정 세계시', 'Coordinated Universal Time'],
    ['Europe/London', '런던', 'London'],
    ['Europe/Paris', '파리', 'Paris'],
    ['Europe/Berlin', '베를린', 'Berlin'],
    ['Europe/Moscow', '모스크바', 'Moscow'],
    ['Africa/Cairo', '카이로', 'Cairo'],
    ['Africa/Johannesburg', '요하네스버그', 'Johannesburg'],
    ['Asia/Dubai', '두바이', 'Dubai'],
    ['Asia/Karachi', '카라치', 'Karachi'],
    ['Asia/Kolkata', '콜카타', 'Kolkata'],
    ['Asia/Kathmandu', '카트만두', 'Kathmandu'],
    ['Asia/Dhaka', '다카', 'Dhaka'],
    ['Asia/Bangkok', '방콕', 'Bangkok'],
    ['Asia/Shanghai', '상하이', 'Shanghai'],
    ['Asia/Singapore', '싱가포르', 'Singapore'],
    ['Asia/Hong_Kong', '홍콩', 'Hong Kong'],
    ['Asia/Taipei', '타이베이', 'Taipei'],
    ['Asia/Manila', '마닐라', 'Manila'],
    ['Asia/Tokyo', '도쿄', 'Tokyo'],
    [UtilTime.SERVICE_ZONE, '서울', 'Seoul'],
    ['Australia/Sydney', '시드니', 'Sydney'],
    ['Pacific/Auckland', '오클랜드', 'Auckland'],
];

/**
 * 브라우저(ICU)가 목록에서 옛 철자로만 주는 시간대의 현대 철자 — 사용자가 "kathmandu"·"ho_chi_minh" 로 찾아도 나오게 "모든 시간대"에 함께 더한다.
 * 서버 `ZoneId` 는 둘 다 안다(옛 이름은 별칭으로 남아 있다). 브라우저가 목록에서 주는 옛 철자 `Asia/Katmandu`·`Asia/Calcutta`·`Asia/Saigon`·`Asia/Rangoon` 은 그대로 둔다.
 */
const ZONE_ALIASES: readonly string[] = ['Asia/Kathmandu', 'Asia/Kolkata', 'Asia/Ho_Chi_Minh', 'Asia/Yangon'];

/** 주요 시간대 밖에서 도시 이름을 붙일 별칭 */
const ALIAS_NAMES: readonly (readonly [string, string, string])[] = [
    ['Asia/Ho_Chi_Minh', '호치민', 'Ho Chi Minh City'],
    ['Asia/Yangon', '양곤', 'Yangon'],
];

/** 이름 → 한국어 이름 */
const KOREAN_NAMES: ReadonlyMap<string, string> = new Map([...MAJOR_ZONES, ...ALIAS_NAMES].map(([zone, ko]) => [zone, ko]));

/** 이름 → 영어 도시 이름 */
const ENGLISH_NAMES: ReadonlyMap<string, string> = new Map([...MAJOR_ZONES, ...ALIAS_NAMES].map(([zone, , en]) => [zone, en]));

const GROUP_MAJOR = '주요 시간대';
const GROUP_ALL = '모든 시간대';

/** 그 순간 시간대의 UTC 와의 차(분) — 숫자 조각(`parts`)으로 센다. 시간대를 모르면 한국 시간 값이 나온다(코어가 한국 시간으로 대신하므로) */
function offsetMinutes(zoneId: string, now: number): number {
    const p = UtilTime.parts(new Date(now).toISOString(), { zoneId, timeFormat: 'H24', dateFormat: 'YMD_DOT' });
    if (!p) return 0;
    return Math.round((Date.UTC(p.y, p.mo - 1, p.d, p.h, p.mi) - Math.floor(now / MINUTE_MS) * MINUTE_MS) / MINUTE_MS);
}

/** `(UTC+09:00) Asia/Seoul · 서울` — 한국어 이름은 한국어 화면에서 이름을 아는 시간대만(영어 화면은 `(UTC+09:00) Asia/Seoul`). 브라우저가 모르는 이름이면 코어가 한국 시간 이름표를 주므로 그때는 이름만 쓴다 */
function labelOf(zoneId: string, now: number, language: TimeLanguage = 'ko'): string {
    const label = UtilTime.zoneLabel({ zoneId, timeFormat: 'H24', dateFormat: 'YMD_DOT' }, now);
    if (!label.endsWith(zoneId)) return zoneId;
    const korean = language === 'ko' ? KOREAN_NAMES.get(zoneId) : undefined;
    return korean ? `${label} · ${korean}` : label;
}

/** 글자 찾기 보조 글자 — 두 언어의 도시 이름(`서울 Seoul`). 이름을 모르는 시간대는 빈 글자 */
function searchOf(zoneId: string): string {
    const names = [KOREAN_NAMES.get(zoneId), ENGLISH_NAMES.get(zoneId)].filter((n): n is string => n !== undefined);
    return names.join(' ');
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default class UtilDisplayChoices {
    /** 언어 선택지 — 이름은 그 언어로 적는다(화면 언어가 무엇이든 같다 — 영어 화면에서 한국어를 찾을 수 있게). 값은 서버가 받는 이름 */
    static readonly LANGUAGE_CHOICES: readonly DisplayChoice<DisplayValues['language']>[] = [
        { value: 'ko', label: '한국어' },
        { value: 'en', label: 'English' },
    ];

    /** 시간 형식 값의 순서 — 서버가 받는 값과 같은 이름 */
    static readonly TIME_FORMATS: readonly DisplayTimeFormat[] = ['H24', 'H12'];

    /** 날짜 형식 값의 순서(처음 기본값 `YMD_DASH` — 서버 `SiteSettingEntity.DEFAULT_DATE_FORMAT`·`UtilTime.FALLBACK_DISPLAY` — 이 먼저) — 서버가 받는 값과 같은 이름 */
    static readonly DATE_FORMATS: readonly DisplayDateFormat[] = ['YMD_DASH', 'YMD_DOT', 'DMY_SLASH', 'MDY_SLASH'];

    /** 시간 형식 이름 — 두 화면이 같은 이름을 쓴다 */
    static readonly TIME_FORMAT_NAMES: Readonly<Record<DisplayTimeFormat, string>> = { H24: '24시간', H12: '12시간' };

    /** 날짜 형식 이름 — 순서(연·월·일)를 한국어로 적는다. 사용자 개인 설정·관리자 표시 기본값·운영 로그가 같은 이름을 쓴다 */
    static readonly DATE_FORMAT_NAMES: Readonly<Record<DisplayDateFormat, string>> = {
        YMD_DOT: '연.월.일',
        YMD_DASH: '연-월-일',
        DMY_SLASH: '일/월/연',
        MDY_SLASH: '월/일/연',
    };

    /** 주요 시간대의 이름들(UTC 와의 차 순서가 아니라 선언 순서) */
    static readonly MAJOR_ZONE_IDS: readonly string[] = MAJOR_ZONES.map(([zone]) => zone);

    /** 브라우저 목록에 옛 철자로만 있어도 "모든 시간대"에 함께 더하는 현대 철자 */
    static readonly ZONE_ALIASES: readonly string[] = ZONE_ALIASES;

    /**
     * 시간 형식 선택지 — 고른 시간대의 `now` 시각을 그 형식으로 그린 견본이 글자에 들어 있다. 24시간 `24시간 (14:32)` · 12시간 `12시간 (오후 2:32)`.
     * @param {string} zoneId - 견본을 그릴 시간대.
     * @param {number} now - 견본으로 그릴 순간(밀리초).
     * @param {ChoiceI18n} [i18n] - 이름을 지금 언어로 만들 때 주는 번역 함수와 언어. 안 주면 한국어.
     * @returns {DisplayChoice<DisplayTimeFormat>[]} 선택지(값은 `H24`·`H12`).
     */
    static timeFormatChoices(zoneId: string, now: number, i18n?: ChoiceI18n): DisplayChoice<DisplayTimeFormat>[] {
        const iso = new Date(now).toISOString();
        return UtilDisplayChoices.TIME_FORMATS.map((value) => ({
            value,
            label: `${i18n ? i18n.t(`display.timeFormat_${value}`) : UtilDisplayChoices.TIME_FORMAT_NAMES[value]} (${UtilTime.clock(iso, { zoneId, timeFormat: value, dateFormat: 'YMD_DOT', language: UtilTimeWords.languageOf(i18n?.language) })})`,
        }));
    }

    /**
     * 날짜 형식 선택지 — 고른 시간대의 `now` 날짜를 그 형식으로 그린 견본이 글자에 들어 있다. `연.월.일 (2026.10.14)` · `월/일/연 (10/14/2026)`.
     * @param {string} zoneId - 견본을 그릴 시간대(자정 근처에는 시간대에 따라 하루가 다르다).
     * @param {number} now - 견본으로 그릴 순간(밀리초).
     * @param {ChoiceI18n} [i18n] - 이름을 지금 언어로 만들 때 주는 번역 함수와 언어. 안 주면 한국어.
     * @returns {DisplayChoice<DisplayDateFormat>[]} 선택지(값은 `YMD_DASH`·`YMD_DOT`·`DMY_SLASH`·`MDY_SLASH`).
     */
    static dateFormatChoices(zoneId: string, now: number, i18n?: ChoiceI18n): DisplayChoice<DisplayDateFormat>[] {
        const iso = new Date(now).toISOString();
        return UtilDisplayChoices.DATE_FORMATS.map((value) => ({
            value,
            label: `${i18n ? i18n.t(`display.dateFormat_${value}`) : UtilDisplayChoices.DATE_FORMAT_NAMES[value]} (${UtilTime.date(iso, { zoneId, timeFormat: 'H24', dateFormat: value, language: UtilTimeWords.languageOf(i18n?.language) })})`,
        }));
    }

    /**
     * 시간 형식 값의 이름 — 서버가 준 값을 사람이 읽는 말로(운영 로그 설명 등).
     * @param {string} code - 서버 값(`H24`·`H12`).
     * @returns {string | null} `24시간`·`12시간`. 모르는 값이면 null.
     */
    static timeFormatName(code: string): string | null {
        return Object.prototype.hasOwnProperty.call(UtilDisplayChoices.TIME_FORMAT_NAMES, code) ? (UtilDisplayChoices.TIME_FORMAT_NAMES as Readonly<Record<string, string>>)[code]! : null;
    }

    /**
     * 날짜 형식 값의 이름 — 서버가 준 값을 사람이 읽는 말로(운영 로그 설명 등).
     * @param {string} code - 서버 값(`YMD_DOT` 등).
     * @returns {string | null} `연.월.일` 등. 모르는 값이면 null.
     */
    static dateFormatName(code: string): string | null {
        return Object.prototype.hasOwnProperty.call(UtilDisplayChoices.DATE_FORMAT_NAMES, code) ? (UtilDisplayChoices.DATE_FORMAT_NAMES as Readonly<Record<string, string>>)[code]! : null;
    }

    /**
     * 시간대의 한국어 이름 — 주요 시간대와 별칭만 안다.
     * @param {string} zoneId - IANA 시간대 이름.
     * @returns {string | null} `서울`·`뉴욕` 등. 모르면 null.
     */
    static zoneKoreanName(zoneId: string): string | null {
        return KOREAN_NAMES.get(zoneId) ?? null;
    }

    /**
     * 시간대의 영어 도시 이름 — 주요 시간대와 별칭만 안다(영어 화면은 IANA 이름이 이미 영어라 목록 글자에는 붙이지 않고 글자 찾기에 쓴다).
     * @param {string} zoneId - IANA 시간대 이름.
     * @returns {string | null} `Seoul`·`New York` 등. 모르면 null.
     */
    static zoneEnglishName(zoneId: string): string | null {
        return ENGLISH_NAMES.get(zoneId) ?? null;
    }

    /**
     * 브라우저가 아는 시간대 이름 목록 — `Intl.supportedValuesOf('timeZone')`. 없거나(옛 브라우저·환경) 던지면 null 이다(부르는 쪽이 주요 시간대만 보인다).
     * @param {unknown} intl - `Intl` 객체. 시험이 대역을 넣을 때만 준다(안 주면 전역 `Intl`).
     * @returns {string[] | null} 시간대 이름. 쓸 수 없으면 null.
     */
    static browserZones(intl: unknown = typeof Intl === 'undefined' ? undefined : Intl): string[] | null {
        if (!isObject(intl) && typeof intl !== 'function') return null;
        const fn = (intl as { supportedValuesOf?: unknown }).supportedValuesOf;
        if (typeof fn !== 'function') return null;
        try {
            const list: unknown = fn.call(intl, 'timeZone');
            if (!Array.isArray(list)) return null;
            const names = list.filter((z): z is string => typeof z === 'string' && z !== '');
            return names.length > 0 ? names : null;
        } catch {
            return null;
        }
    }

    /**
     * 시간대 선택지 묶음 — 주요 시간대(UTC 와의 차 순서) + 브라우저가 아는 나머지에 옛 철자 별칭의 현대 철자를 더한 "모든 시간대"(이름 순서, 주요에 든 것은 뺀다).
     * 지금 고른 시간대가 어느 쪽에도 없으면(서버가 새 이름을 줬는데 이 브라우저는 모를 때) 주요 시간대에 더해 선택지에서 사라지지 않게 한다.
     * @param {readonly string[] | null} supported - 브라우저가 아는 이름(`browserZones()`). null 이면 주요 시간대만.
     * @param {string} current - 지금 고른(또는 서버가 준) 시간대 이름.
     * @param {number} now - 시차를 잴 순간(밀리초). 서머타임이 있는 시간대는 이 순간의 시차다.
     * @param {ChoiceI18n} [i18n] - 묶음 제목과 도시 이름을 지금 언어로 만들 때 주는 번역 함수와 언어. 안 주면 한국어.
     * @returns {DisplayZoneGroup[]} 묶음 둘(브라우저 목록이 없으면 하나). 선택지 글자는 한국어 `(UTC+09:00) Asia/Seoul · 서울`, 영어 `(UTC+09:00) Asia/Seoul`. 찾기 보조 글자(`search`)는 두 언어 이름.
     */
    static zoneGroups(supported: readonly string[] | null, current: string, now: number = Date.now(), i18n?: ChoiceI18n): DisplayZoneGroup[] {
        const language = UtilTimeWords.languageOf(i18n?.language);
        const major = new Set<string>(UtilDisplayChoices.MAJOR_ZONE_IDS);
        const rest = supported ? [...new Set([...supported, ...ZONE_ALIASES])].filter((z) => !major.has(z)).sort() : [];
        if (current !== '' && !major.has(current) && !rest.includes(current)) major.add(current);

        const majorItems = [...major]
            .map((value) => ({ value, label: labelOf(value, now, language), search: searchOf(value), offset: offsetMinutes(value, now) }))
            .sort((a, b) => a.offset - b.offset || (a.value < b.value ? -1 : a.value > b.value ? 1 : 0))
            .map(({ value, label, search }) => ({ value, label, search }));
        const groups: DisplayZoneGroup[] = [{ label: i18n ? i18n.t('display.zoneGroupMajor') : GROUP_MAJOR, items: majorItems }];
        if (rest.length > 0) groups.push({ label: i18n ? i18n.t('display.zoneGroupAll') : GROUP_ALL, items: rest.map((value) => ({ value, label: labelOf(value, now, language), search: searchOf(value) })) });
        return groups;
    }

    /**
     * 시간대 선택지 하나의 글자 — 선택한 값을 입력칸 옆·미리보기에서 읽을 때 쓴다.
     * @param {string} zoneId - 시간대 이름.
     * @param {number} now - 시차를 잴 순간(밀리초).
     * @param {ChoiceI18n} [i18n] - 도시 이름을 지금 언어로 만들 때 주는 번역 함수와 언어. 안 주면 한국어.
     * @returns {string} 한국어 `(UTC+09:00) Asia/Seoul · 서울`(한국어 이름은 아는 시간대만), 영어 `(UTC+09:00) Asia/Seoul`. 이 브라우저가 모르는 이름이면 이름만.
     */
    static zoneText(zoneId: string, now: number = Date.now(), i18n?: ChoiceI18n): string {
        return labelOf(zoneId, now, UtilTimeWords.languageOf(i18n?.language));
    }
}
