/**
 * 시간을 글자로 — 시각 표시의 **순수 코어**.
 *  - 카운트다운 표시(`mmss`·`mss`)
 *  - 서버가 준 UTC 시각(ISO-8601)을 **표시 값**(`TimeValues` — 시간대·시간 형식·날짜 형식)대로 글자로 (`listLabel`·`detail`·`ago`·`full`·`date`·`dateDay`·`shortDate`·`shortDateTime`·`clock`·`slot`·`reserved`)
 *  - 시각의 연·월·일·시·분·요일을 **숫자로**(`parts`) — 결과 글자를 정규식·`slice` 로 다시 쪼개지 않고 이것을 쓴다
 *  - 입력 경계(`fromWall` — 시간대의 벽시계 → 순간), 시간대 이름표(`zoneLabel`), 서비스 날짜 글자(`serviceDate`)·순간의 서비스 날짜(`serviceDayOf`)
 *  - 기다릴 시간 문장(`duration`)·남은 날수(`daysUntil`)·임시저장 만료 안내(`expiry`) — 시간대와 무관
 *
 * ── 쓰는 법 ──
 *  새 코드는 표시 값을 묶은 객체를 쓴다: `const time = UtilTime.of(display)` → `time.full(iso)`·`time.listLabel(iso)`.
 *  컴포넌트·컴포저블은 `useDisplayTime()` 이 이 객체를 돌려준다. 정적 함수 `UtilTime.full(iso, display)` 는 `of(display).full(iso)` 와 같다.
 *  시각 함수는 표시 값 없이 쓸 수 없다 — "지금 어느 시간대로 보이나"는 늘 인자로 들어온다(이 파일은 화면 상태를 읽지 않는다).
 *  표시 값을 받기 전의 그림은 시스템 폴백(`FALLBACK_DISPLAY`)이고, 이것을 쓰는 곳은 폴백을 정의·배선하는 파일(`useDisplay`)뿐이다.
 *
 * ── 서비스 하루 ──
 *  출석·한도·인기글·정산·노출 달력 같은 **서비스 하루**는 표시 시간대와 무관하게 늘 한국 달력이다(`SERVICE_ZONE`). 순간 → 서비스 날짜는 `serviceDayOf` 로 구하고,
 *  이 날짜를 표시 시간대로 옮기지 않는다(비서울 표시 시간대에서 하루가 어긋난다). 서비스 날짜 글자에 날짜 형식만 입히는 것은 `serviceDate` 다.
 *
 * ── 규칙(docs/plans/display-settings-01-timezone-display.md 4-⑦) ──
 *  · "오늘·어제" 판정은 **표시 시간대의 날짜**로 센다(브라우저 시간대와 무관).
 *  · 시간 형식은 모든 절대 시각에 적용된다 — 24시간 `14:32`, 12시간 `오후 2:32`.
 *  · 연 없는 짧은 날짜(`09.27`)는 날짜 형식의 순서·구분자를 따른다(`09-27`·`27/09`·`09/27`).
 *  · 말로 쓴 날짜("10월 5일 (일)")는 형식을 따르지 않는다(시간대만 따른다).
 *  · `ago` 의 상대 부분("방금"·"55분 전")은 시간대·형식과 무관하다(언어만 따른다).
 *  · 모르는 시간대·모르는 형식 값은 한국 시간·`YMD_DASH`·`H24`(시스템 폴백) 로 대신한다(화면이 깨지지 않게).
 *  · **말(단어)은 표시 값의 `language` 를 따른다** — "방금/just now"·"어제/Yesterday"·"오전/AM"·요일·"10월 5일/Oct 5" 등. 단어표는 `util-time-words.ts`(`UtilTimeWords`)에 있다.
 *    `language` 가 없거나 모르는 값이면 한국어다. **한국어 출력은 서버 `ServerTimeText` 와 같은 말이라 바꾸지 않는다.**
 *    날짜 **형식**(`YMD_DOT` 등)이 정한 숫자 날짜의 순서·구분자는 영어에서도 그대로 따른다(`2026.10.02`·`10/02/2026`) — 영어로 바뀌는 것은 요일·월 이름·"어제" 같은 말뿐이다.
 *
 * 이 파일은 `apps-packages/display-time/`(user-web·admin-web 이 `extends` 하는 공유 Nuxt 레이어 `@roboyam/display-time`)에 **한 벌만** 있다.
 * `vue`·`~/`·Nuxt 자동 import 를 쓰지 않는 순수 TypeScript 여야 한다— 두 앱에서 `#layers/display-time/utils/util-time` 으로 불러 쓴다.
 * `now` 인자는 시험·고정 표시용이다 — 안 주면 지금.
 */
import UtilTimeWords, { type TimeLanguage } from './util-time-words';

/**
 * 시각을 어떻게 보여 줄지 — 표시 시간대와 시간·날짜 형식, 그리고 말의 언어. 시각 글자(`UtilTime`)는 이 칸들만 본다.
 * `language` 는 선택이다(옛 호출부가 세 칸만 줘도 된다) — 없거나 모르는 값이면 한국어로 그린다.
 */
export type TimeValues = {
    /** IANA 시간대 이름 — `Asia/Seoul` */
    zoneId: string;
    /** 시간 형식 — 24시간 `14:32` · 12시간 `오후 2:32`(영어 `2:32 PM`) */
    timeFormat: 'H24' | 'H12';
    /** 날짜 형식 — `2026-10-02` · `2026.10.02` · `02/10/2026` · `10/02/2026` */
    dateFormat: 'YMD_DASH' | 'YMD_DOT' | 'DMY_SLASH' | 'MDY_SLASH';
    /** 말의 언어 — `ko`(기본·폴백) · `en`. 없거나 모르는 값이면 `ko` */
    language?: 'ko' | 'en';
};

/**
 * 표시 값 — 시각을 어떻게 보여 줄지(`TimeValues`)에 **언어**를 더한 것. 서버 효과값 `display.effective` 의 `values` 와 같은 모양이다.
 * 언어(`ko`·`en`)는 화면 문구(i18n 로캘)·PrimeVue 로캘과 시각 글자의 말("어제"·"오전"·요일 …)을 정한다.
 */
export type DisplayValues = TimeValues & {
    /** 언어 — `ko`(기본·폴백) · `en` */
    language: 'ko' | 'en';
};

/**
 * 표시 시간대의 벽시계를 숫자로 뗀 조각 — `parts` 의 결과. 시는 늘 0~23(시간 형식과 무관)이고 `weekday` 는 0=일 … 6=토.
 */
export type TimeParts = {
    y: number;
    mo: number;
    d: number;
    h: number;
    mi: number;
    weekday: number;
};

/** `listLabel` 의 선택 인자 — `compact`(오늘 앞의 "오늘 "을 뺄지)·`now`(지금, 시험용) */
export type ListLabelOptions = { compact?: boolean; now?: number };

/**
 * 표시 값에 묶은 시각 함수 모음 — `UtilTime.of(display)` 가 돌려준다. 상태가 없고, 같은 입력에 같은 결과를 준다.
 * 각 함수는 `UtilTime` 의 같은 이름 정적 함수에서 `display` 인자만 뺀 것이다.
 */
export type DisplayTime = {
    /** 이 객체가 묶은 표시 값(바꿀 수 없는 사본) — 언어는 정리한 값(`ko`·`en`)이다 */
    readonly display: DisplayValues;
    /** 목록 한 줄의 시간 — 오늘은 시각, 어제는 "어제", 올해는 짧은 날짜, 그 전 해는 연 포함 */
    listLabel(iso: string | null | undefined, options?: ListLabelOptions): string;
    /** 글 상세의 시간 — "방금" · "오늘 11:30" · "어제 21:10" · "09.27 14:20" · "2025.09.27 14:20" */
    detail(iso: string | null | undefined, now?: number): string;
    /** 댓글의 시간 — "방금" · "55분 전" · "3시간 전", 하루가 넘으면 `detail` */
    ago(iso: string | null | undefined, now?: number): string;
    /** 정확한 시각 — `2026.09.30 15:43` */
    full(iso: string | null | undefined): string;
    /** 날짜만 — `2026.10.02` */
    date(iso: string | null | undefined): string;
    /** 날짜와 요일 — `2026.10.02(금)` */
    dateDay(iso: string | null | undefined): string;
    /** 연 없는 짧은 날짜 — `09.27` · `09-27` · `27/09` · `09/27`(연이 늘 빠진다 — 다른 해를 연과 함께 보일지는 부르는 쪽이 `parts` 로 판단한다) */
    shortDate(iso: string | null | undefined): string;
    /** 연 없는 짧은 날짜와 시각 — `10.13 09:14` · `10.13 오전 9:14`("오늘·어제"를 끼우지 않는다) */
    shortDateTime(iso: string | null | undefined): string;
    /** 시각만 — `09:05` · `오전 9:05` */
    clock(iso: string | null | undefined): string;
    /** 예약 목록의 발행 시각 — `10월 5일 (일) 21:00` · `10월 5일 (일) 오후 9:00` */
    slot(iso: string | null | undefined, now?: number): string;
    /** 예약 완료 안내의 시각 — `10월 5일 15시 30분` · `10월 5일 오후 3시 30분` */
    reserved(iso: string | null | undefined, now?: number): string;
    /** 연·월·일·시·분·요일 숫자 */
    parts(iso: string | null | undefined): TimeParts | null;
    /** 시간대 이름표 — `(UTC+09:00) Asia/Seoul` */
    zoneLabel(now?: number): string;
    /** 시각 뒤에 잇는 시간대 꼬리표 — `(UTC+09:00 Asia/Seoul)` (서버 메일·오류 문구의 "내 시간대" 모양) */
    zoneTag(now?: number): string;
    /** 서버가 준 서비스 날짜 글자(`YYYY-MM-DD`)를 날짜 형식대로 */
    serviceDate(ymd: string | null | undefined): string;
    /** 임시저장의 만료 안내 — `12일 뒤 삭제` · `Deletes in 12 days`(시간대와 무관, 언어만 따른다) */
    expiry(iso: string | null | undefined, now?: number): string;
    /** 기다릴 시간을 사람이 읽는 말로 — `1분 30초` · `1 minute 30 seconds`(시간대와 무관, 언어만 따른다) */
    duration(totalSeconds: number): string;
    /** 이 표시 시간대의 벽시계 → 순간(ISO-8601 UTC). 잘못된 값이면 null */
    fromWall(y: number, mo: number, d: number, h: number, mi: number): string | null;
};

/** 서비스 하루(출석·한도·인기글·정산 등)의 시간대 — 표시 값과 무관하게 늘 한국 시간이다. 서버 `ServiceZone` 과 같은 값 */
const SERVICE_ZONE = 'Asia/Seoul';

/** 시스템 폴백 — 표시 값을 아직 모를 때(또는 받지 못했을 때) 쓴다. 서버의 시스템 폴백(`SiteSettingEntity.DEFAULT_*` — 설정 행이 없을 때의 처음 값, 날짜 형식 `YMD_DASH`)과 같은 값 */
const FALLBACK: DisplayValues = Object.freeze({ zoneId: SERVICE_ZONE, timeFormat: 'H24', dateFormat: 'YMD_DASH', language: 'ko' });

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

/** 시간대별 포매터 — 시간대 이름이 맞는지 확인하고 그 시간대의 `Intl.DateTimeFormat` 을 한 번만 만든다 */
type Zone = { zoneId: string; format: Intl.DateTimeFormat };

/**
 * `Intl.DateTimeFormat` 은 만드는 값이 비싸 시간대마다 하나만 만들어 둔다.
 * 같은 입력에 늘 같은 결과를 주는 메모일 뿐 화면 상태가 아니다(순수 함수 규칙에 걸리지 않는다).
 */
const ZONES = new Map<string, Zone>();

function buildFormat(zoneId: string): Intl.DateTimeFormat {
    return new Intl.DateTimeFormat('en-CA', {
        timeZone: zoneId,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
    });
}

/** 시간대 이름으로 포매터를 얻는다. 브라우저가 모르는 시간대면 한국 시간으로 대신한다 */
function zoneOf(zoneId: string): Zone {
    let zone = ZONES.get(zoneId);
    if (!zone) {
        try {
            zone = { zoneId, format: buildFormat(zoneId) };
        } catch {
            zone = zoneOf(SERVICE_ZONE);
        }
        ZONES.set(zoneId, zone);
    }
    return zone;
}

/** 그 순간을 시간대의 연·월·일·시·분·요일 숫자로 */
function zonedParts(ms: number, zoneId: string): TimeParts {
    const found: Record<string, string> = {};
    for (const part of zoneOf(zoneId).format.formatToParts(ms)) found[part.type] = part.value;
    const y = Number(found.year);
    const mo = Number(found.month);
    const d = Number(found.day);
    return { y, mo, d, h: Number(found.hour), mi: Number(found.minute), weekday: new Date(Date.UTC(y, mo - 1, d)).getUTCDay() };
}

/** 그 순간 그 시간대의 시차(분) — 동쪽이 양수 */
function offsetMinutes(ms: number, zoneId: string): number {
    const p = zonedParts(ms, zoneId);
    return (Date.UTC(p.y, p.mo - 1, p.d, p.h, p.mi) - Math.floor(ms / 60_000) * 60_000) / 60_000;
}

/** 정규화한 표시 값 — 모르는 형식 값은 폴백으로 대신하고 시간대는 알려진 것으로 바꾼다 */
type Resolved = { zoneId: string; h12: boolean; dateFormat: TimeValues['dateFormat']; language: TimeLanguage };

function resolve(display: TimeValues): Resolved {
    const f = display.dateFormat;
    const dateFormat = f === 'YMD_DASH' || f === 'YMD_DOT' || f === 'DMY_SLASH' || f === 'MDY_SLASH' ? f : FALLBACK.dateFormat;
    return { zoneId: zoneOf(display.zoneId).zoneId, h12: display.timeFormat === 'H12', dateFormat, language: UtilTimeWords.languageOf(display.language) };
}

function parse(iso: string | null | undefined): number | null {
    if (!iso) return null;
    const ms = Date.parse(iso);
    return Number.isNaN(ms) ? null : ms;
}

/** 연·월·일 — 날짜 형식대로 `2026.10.02` */
function dateText(y: number, mo: number, d: number, dateFormat: Resolved['dateFormat']): string {
    switch (dateFormat) {
        case 'YMD_DASH':
            return `${y}-${pad(mo)}-${pad(d)}`;
        case 'DMY_SLASH':
            return `${pad(d)}/${pad(mo)}/${y}`;
        case 'MDY_SLASH':
            return `${pad(mo)}/${pad(d)}/${y}`;
        default:
            return `${y}.${pad(mo)}.${pad(d)}`;
    }
}

/** 연 없는 짧은 날짜 — 날짜 형식의 순서·구분자를 따른다(`09.27`·`09-27`·`27/09`·`09/27`) */
function shortDateText(mo: number, d: number, dateFormat: Resolved['dateFormat']): string {
    switch (dateFormat) {
        case 'YMD_DASH':
            return `${pad(mo)}-${pad(d)}`;
        case 'DMY_SLASH':
            return `${pad(d)}/${pad(mo)}`;
        case 'MDY_SLASH':
            return `${pad(mo)}/${pad(d)}`;
        default:
            return `${pad(mo)}.${pad(d)}`;
    }
}

/** 시각 — 24시간 `09:05`, 12시간 `오전 9:05`(영어 `9:05 AM`). 정오는 "오후 12", 자정은 "오전 12" */
function timeText(h: number, mi: number, r: Resolved): string {
    if (!r.h12) return `${pad(h)}:${pad(mi)}`;
    return UtilTimeWords.of(r.language).clock12(h, pad(mi));
}

/** 달력으로 며칠째인지(일 단위) — 오늘·어제를 가르는 데 쓴다 */
function dayNumber(p: TimeParts): number {
    return Math.floor(Date.UTC(p.y, p.mo - 1, p.d) / 86_400_000);
}

/** 오늘 0 · 어제 1 · 그 전 2… (미래는 음수) */
function daysAgo(then: TimeParts, now: TimeParts): number {
    return dayNumber(now) - dayNumber(then);
}

/** 올해가 아니면 그 연(말로 쓴 날짜에 붙인다 — "2027년 "·"…, 2027"), 올해면 null */
function yearIfOther(then: TimeParts, now: TimeParts): number | null {
    return then.y === now.y ? null : then.y;
}

// ── 표시 값을 받는 구현(정적 함수와 `of` 객체가 함께 쓴다) ──

function listLabelAt(iso: string | null | undefined, display: TimeValues, options: ListLabelOptions): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const now = zonedParts(options.now ?? Date.now(), r.zoneId);
    const then = zonedParts(ms, r.zoneId);
    const diff = daysAgo(then, now);
    const words = UtilTimeWords.of(r.language);
    if (diff <= 0) return options.compact ? timeText(then.h, then.mi, r) : `${words.today} ${timeText(then.h, then.mi, r)}`;
    if (diff === 1) return words.yesterday;
    return then.y === now.y ? shortDateText(then.mo, then.d, r.dateFormat) : dateText(then.y, then.mo, then.d, r.dateFormat);
}

function detailAt(iso: string | null | undefined, display: TimeValues, now: number): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const words = UtilTimeWords.of(r.language);
    if (now - ms < 60_000) return words.justNow;
    const today = zonedParts(now, r.zoneId);
    const then = zonedParts(ms, r.zoneId);
    const time = timeText(then.h, then.mi, r);
    const diff = daysAgo(then, today);
    if (diff <= 0) return `${words.today} ${time}`;
    if (diff === 1) return `${words.yesterday} ${time}`;
    const day = then.y === today.y ? shortDateText(then.mo, then.d, r.dateFormat) : dateText(then.y, then.mo, then.d, r.dateFormat);
    return `${day} ${time}`;
}

function agoAt(iso: string | null | undefined, display: TimeValues, now: number): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const words = UtilTimeWords.of(resolve(display).language);
    const seconds = Math.floor((now - ms) / 1000);
    if (seconds < 60) return words.justNow;
    if (seconds < 3600) return words.minutesAgo(Math.floor(seconds / 60));
    if (seconds < 86_400) return words.hoursAgo(Math.floor(seconds / 3600));
    return detailAt(iso, display, now);
}

function fullAt(iso: string | null | undefined, display: TimeValues): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return `${dateText(p.y, p.mo, p.d, r.dateFormat)} ${timeText(p.h, p.mi, r)}`;
}

function dateAt(iso: string | null | undefined, display: TimeValues): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return dateText(p.y, p.mo, p.d, r.dateFormat);
}

function dateDayAt(iso: string | null | undefined, display: TimeValues): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return `${dateText(p.y, p.mo, p.d, r.dateFormat)}(${UtilTimeWords.of(r.language).weekday(p.weekday)})`;
}

function clockAt(iso: string | null | undefined, display: TimeValues): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return timeText(p.h, p.mi, r);
}

function slotAt(iso: string | null | undefined, display: TimeValues, now: number): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return UtilTimeWords.of(r.language).slot(yearIfOther(p, zonedParts(now, r.zoneId)), p.mo, p.d, p.weekday, timeText(p.h, p.mi, r));
}

function reservedAt(iso: string | null | undefined, display: TimeValues, now: number): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return UtilTimeWords.of(r.language).reserved(yearIfOther(p, zonedParts(now, r.zoneId)), p.mo, p.d, p.h, p.mi, r.h12);
}

function shortDateAt(iso: string | null | undefined, display: TimeValues): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return shortDateText(p.mo, p.d, r.dateFormat);
}

function shortDateTimeAt(iso: string | null | undefined, display: TimeValues): string {
    const ms = parse(iso);
    if (ms === null) return '';
    const r = resolve(display);
    const p = zonedParts(ms, r.zoneId);
    return `${shortDateText(p.mo, p.d, r.dateFormat)} ${timeText(p.h, p.mi, r)}`;
}

function partsAt(iso: string | null | undefined, display: TimeValues): TimeParts | null {
    const ms = parse(iso);
    return ms === null ? null : zonedParts(ms, resolve(display).zoneId);
}

/** 시차 글자 — `+09:00` · `−04:00`(음수는 가운뎃점 없는 유니코드 마이너스 U+2212, 서버 `ServerTimeText` 와 같다) */
function offsetText(minutes: number): string {
    const abs = Math.abs(minutes);
    return `${minutes < 0 ? '−' : '+'}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

function zoneLabelAt(display: TimeValues, now: number): string {
    const { zoneId } = resolve(display);
    return `(UTC${offsetText(offsetMinutes(now, zoneId))}) ${zoneId}`;
}

function zoneTagAt(display: TimeValues, now: number): string {
    const { zoneId } = resolve(display);
    return `(UTC${offsetText(offsetMinutes(now, zoneId))} ${zoneId})`;
}

function serviceDateAt(ymd: string | null | undefined, display: TimeValues): string {
    const found = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd ?? '');
    if (!found) return '';
    const y = Number(found[1]);
    const mo = Number(found[2]);
    const d = Number(found[3]);
    if (mo < 1 || mo > 12 || d < 1 || d > new Date(Date.UTC(y, mo, 0)).getUTCDate()) return '';
    return dateText(y, mo, d, resolve(display).dateFormat);
}

/** 순간(ISO-8601 글자 또는 밀리초) → 서비스 하루(`SERVICE_ZONE` 달력)의 날짜 `YYYY-MM-DD`. 읽을 수 없으면 null */
function serviceDayAt(instant: string | number | null | undefined): string | null {
    const ms = typeof instant === 'number' ? (Number.isFinite(instant) && Math.abs(instant) <= 8.64e15 ? instant : null) : parse(instant);
    if (ms === null) return null;
    const p = zonedParts(ms, SERVICE_ZONE);
    return `${String(p.y).padStart(4, '0')}-${pad(p.mo)}-${pad(p.d)}`;
}

function fromWallAt(y: number, mo: number, d: number, h: number, mi: number, zoneId: string): string | null {
    const whole = [y, mo, d, h, mi].every((n) => Number.isInteger(n));
    if (!whole || y < 1000 || y > 9999 || mo < 1 || mo > 12 || h < 0 || h > 23 || mi < 0 || mi > 59) return null;
    if (d < 1 || d > new Date(Date.UTC(y, mo, 0)).getUTCDate()) return null;
    const zone = resolve({ ...FALLBACK, zoneId }).zoneId;
    const wall = Date.UTC(y, mo - 1, d, h, mi);
    // 그날 앞뒤 하루의 시차 — 전환(서머타임)이 그날 안에 있으면 둘이 다르다
    const before = offsetMinutes(wall - 86_400_000, zone);
    const after = offsetMinutes(wall + 86_400_000, zone);
    const valid: number[] = [];
    for (const offset of new Set([before, after])) {
        const instant = wall - offset * 60_000;
        if (offsetMinutes(instant, zone) === offset) valid.push(instant);
    }
    // 겹침(가을 전환 — 같은 벽시계가 두 번)이면 이른 순간, 틈(봄 전환 — 없는 벽시계)이면 전환 앞의 시차로 틈만큼 뒤로 민다
    const instant = valid.length > 0 ? Math.min(...valid) : wall - before * 60_000;
    return new Date(instant).toISOString();
}

export default class UtilTime {
    /**
     * 표시 값을 아직 모를 때(또는 받지 못했을 때) 쓰는 시스템 폴백 — 서버의 시스템 폴백과 같은 값(Asia/Seoul·24시간·`YYYY.MM.DD`).
     * 값을 아직 받지 못했을 때의 배선(`useDisplay`)이 시작 값으로만 쓴다 — 그 밖의 화면 코드는 쓰지 않는다.
     * 서비스 날짜는 이 값이 아니라 `serviceDayOf` 로 구한다.
     */
    static readonly FALLBACK_DISPLAY: DisplayValues = FALLBACK;

    /**
     * 서비스 하루(출석·한도·인기글·정산 등)의 시간대 — 표시 값과 무관하게 늘 한국 시간이다(서버 `ServiceZone` 과 같은 값).
     * 사용자가 표시 시간대를 바꿔도 "오늘"의 서비스 하루는 이 시간대로 센다.
     */
    static readonly SERVICE_ZONE: string = SERVICE_ZONE;

    /**
     * 표시 값을 묶은 시각 함수 모음을 만든다 — `time.full(iso)` 꼴로 쓴다. 새로 만들어 돌려줄 뿐 상태가 없다.
     * @param {TimeValues} display - 표시 값(시간대·시간 형식·날짜 형식·언어). 언어가 없거나 모르는 값이면 한국어.
     * @returns {DisplayTime} `display` 에 묶인 시각 함수 모음.
     * @example UtilTime.of({ zoneId: 'America/New_York', timeFormat: 'H12', dateFormat: 'MDY_SLASH', language: 'ko' }).full('2026-09-30T16:00:00Z') // '09/30/2026 오후 12:00'
     * @example UtilTime.of({ zoneId: 'America/New_York', timeFormat: 'H12', dateFormat: 'MDY_SLASH', language: 'en' }).full('2026-09-30T16:00:00Z') // '09/30/2026 12:00 PM'
     */
    static of(display: TimeValues): DisplayTime {
        const language = UtilTimeWords.languageOf(display.language);
        const bound: DisplayValues = Object.freeze({ zoneId: display.zoneId, timeFormat: display.timeFormat, dateFormat: display.dateFormat, language });
        return {
            display: bound,
            listLabel: (iso, options = {}) => listLabelAt(iso, bound, options),
            detail: (iso, now = Date.now()) => detailAt(iso, bound, now),
            ago: (iso, now = Date.now()) => agoAt(iso, bound, now),
            full: (iso) => fullAt(iso, bound),
            date: (iso) => dateAt(iso, bound),
            dateDay: (iso) => dateDayAt(iso, bound),
            shortDate: (iso) => shortDateAt(iso, bound),
            shortDateTime: (iso) => shortDateTimeAt(iso, bound),
            clock: (iso) => clockAt(iso, bound),
            slot: (iso, now = Date.now()) => slotAt(iso, bound, now),
            reserved: (iso, now = Date.now()) => reservedAt(iso, bound, now),
            parts: (iso) => partsAt(iso, bound),
            zoneLabel: (now = Date.now()) => zoneLabelAt(bound, now),
            zoneTag: (now = Date.now()) => zoneTagAt(bound, now),
            serviceDate: (ymd) => serviceDateAt(ymd, bound),
            expiry: (iso, now = Date.now()) => UtilTime.expiry(iso, now, language),
            duration: (totalSeconds) => UtilTime.duration(totalSeconds, language),
            fromWall: (y, mo, d, h, mi) => fromWallAt(y, mo, d, h, mi, bound.zoneId),
        };
    }

    /**
     * 시각의 연·월·일·시·분·요일을 표시 시간대의 **숫자**로 — `full()` 같은 결과 글자를 정규식·`slice` 로 다시 쪼개는 대신 쓴다.
     * 시간·날짜 형식과 무관하게 늘 같은 숫자(시는 0~23)를 돌려준다. 모르는 시간대 이름이면 한국 시간으로 계산한다.
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값(시간대를 읽는다).
     * @returns {TimeParts | null} `{ y, mo, d, h, mi, weekday }`(`weekday` 0=일). 시각이 없거나 잘못됐으면 null.
     * @example UtilTime.parts('2026-09-30T16:00:00Z', UtilTime.FALLBACK_DISPLAY) // { y: 2026, mo: 10, d: 1, h: 1, mi: 0, weekday: 4 }
     */
    static parts(iso: string | null | undefined, display: TimeValues): TimeParts | null {
        return partsAt(iso, display);
    }

    /**
     * 표시 시간대의 벽시계(그 시간대에서 본 연·월·일·시·분)를 **진짜 순간**으로 — 입력칸의 값을 서버로 보내기 전에 쓴다.
     * 서머타임의 틈(없는 벽시계, 예 뉴욕 봄 02:30)은 틈만큼 뒤로 밀고(03:30), 겹침(두 번 있는 벽시계, 예 뉴욕 가을 01:30)은 이른 쪽을 고른다.
     * @param {number} y - 연.
     * @param {number} mo - 월(1~12).
     * @param {number} d - 일.
     * @param {number} h - 시(0~23).
     * @param {number} mi - 분(0~59).
     * @param {string} zoneId - IANA 시간대 이름. 모르는 이름이면 한국 시간으로 본다.
     * @returns {string | null} UTC ISO-8601 글자(`2026-10-05T12:30:00.000Z`). 정수가 아니거나 범위 밖(존재하지 않는 날짜 포함)이면 null.
     * @example UtilTime.fromWall(2026, 10, 5, 21, 30, 'Asia/Seoul') // '2026-10-05T12:30:00.000Z'
     */
    static fromWall(y: number, mo: number, d: number, h: number, mi: number, zoneId: string): string | null {
        return fromWallAt(y, mo, d, h, mi, zoneId);
    }

    /**
     * 시간대 이름표 — 입력칸 옆에 "지금 어느 시간대로 입력하는지" 적는다. 시차는 그 순간(서머타임 반영)의 값이다(DS-Q3 ① 형식).
     * @param {TimeValues} display - 표시 값(시간대를 읽는다).
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @returns {string} `(UTC+09:00) Asia/Seoul` 꼴의 글자(음수는 유니코드 마이너스 `(UTC−04:00) America/New_York`, UTC 는 `(UTC+00:00) UTC`). 모르는 시간대면 한국 시간 이름표.
     */
    static zoneLabel(display: TimeValues, now: number = Date.now()): string {
        return zoneLabelAt(display, now);
    }

    /**
     * 시각 **뒤에 잇는** 시간대 꼬리표 — "2026.10.21 11:00 (UTC−04:00 America/New_York)" 의 괄호 부분. 입력칸 옆 이름표(`zoneLabel`, 괄호 밖 이름)와 모양이 다르다.
     * 서버가 메일·오류 문구에 덧붙이는 "내 시간대" 시각의 모양(`ServerTimeText.personalMoment`)과 같다 — 같은 말을 화면과 메일이 다르게 쓰지 않게 한다.
     * @param {TimeValues} display - 표시 값(시간대를 읽는다).
     * @param {number} now - 시차를 잴 순간(밀리초). 서머타임이 있는 시간대는 이 순간의 시차다. 안 주면 현재 시각.
     * @returns {string} `(UTC+09:00 Asia/Seoul)` 꼴의 글자(음수는 유니코드 마이너스 `(UTC−04:00 America/New_York)`). 모르는 시간대면 한국 시간 꼬리표.
     */
    static zoneTag(display: TimeValues, now: number = Date.now()): string {
        return zoneTagAt(display, now);
    }

    /**
     * 서버가 준 서비스 날짜 글자(`YYYY-MM-DD` — 출석일·인기글 기준일 같은 서비스 하루)를 **시간대 변환 없이 날짜 형식만** 입힌다.
     * 서비스 하루는 한국 달력이라 표시 시간대로 옮기면 하루가 어긋난다.
     * @param {string | null | undefined} ymd - `2026-10-02` 꼴의 서비스 날짜.
     * @param {TimeValues} display - 표시 값(날짜 형식만 읽는다).
     * @returns {string} 날짜 형식대로 쓴 글자(`2026.10.02`). 형식이 맞지 않거나 없는 날짜면 빈 문자열.
     */
    static serviceDate(ymd: string | null | undefined, display: TimeValues): string {
        return serviceDateAt(ymd, display);
    }

    /**
     * 순간이 **서비스 하루**(출석·한도·인기글·정산·노출 달력 — 늘 한국 달력)로 어느 날인지 — `YYYY-MM-DD`. 표시 시간대와 상관없이 `SERVICE_ZONE` 으로 센다.
     * 표시 글자가 아니라 서비스 날짜·열쇠(`YYYY-MM-DD`·`YYYY-MM`)를 만들 때 쓴다. 이 날짜에 날짜 형식만 입히는 것은 `serviceDate` 다.
     * @param {string | number | null | undefined} instant - 순간 — 서버가 준 UTC 시각(ISO-8601, 날짜만 와도 된다) 또는 밀리초.
     * @returns {string | null} `2026-10-02`. 읽을 수 없는 값이면 null.
     * @example UtilTime.serviceDayOf('2026-10-01T16:00:00Z') // '2026-10-02'(한국은 UTC+09:00)
     */
    static serviceDayOf(instant: string | number | null | undefined): string | null {
        return serviceDayAt(instant);
    }

    /**
     * 09:41 — 잠금 남은 시간처럼 분이 두 자리로 고정된 자리
     * @param {number} totalSeconds - 남은 시간(초).
     * @returns {string} "09:41" 꼴의 글자(분이 두 자리).
     */
    static mmss(totalSeconds: number): string {
        const s = Math.max(0, Math.ceil(totalSeconds));
        return `${pad(Math.floor(s / 60))}:${pad(s % 60)}`;
    }

    /**
     * 0:42 — 버튼 안의 짧은 표시
     * @param {number} totalSeconds - 남은 시간(초).
     * @returns {string} "0:42" 꼴의 글자.
     */
    static mss(totalSeconds: number): string {
        const s = Math.max(0, Math.ceil(totalSeconds));
        return `${Math.floor(s / 60)}:${pad(s % 60)}`;
    }

    /**
     * 목록 한 줄의 시간 — 오늘은 시각, 어제는 "어제", 올해는 짧은 날짜(`09.27`), 그 전 해는 연 포함(`2025.09.27`).
     * `compact` 가 false(모바일 행)이면 오늘 앞에 "오늘 " 을 붙인다("오늘 14:32").
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @param {ListLabelOptions} options - `compact`(오늘 앞의 "오늘 "을 뺄지)·`now`(지금, 시험용). 안 주면 둘 다 기본값.
     * @returns {string} 목록 한 줄의 시간. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static listLabel(iso: string | null | undefined, display: TimeValues, options: ListLabelOptions = {}): string {
        return listLabelAt(iso, display, options);
    }

    /**
     * 글 상세의 시간 — "방금" · "오늘 11:30" · "어제 21:10" · "09.27 14:20" · "2025.09.27 14:20"
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @returns {string} 글 상세의 시간. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static detail(iso: string | null | undefined, display: TimeValues, now: number = Date.now()): string {
        return detailAt(iso, display, now);
    }

    /**
     * 댓글의 시간 — "방금" · "55분 전" · "3시간 전", 하루가 넘으면 `detail` 과 같다(상대 부분은 표시 값과 무관하다)
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값(하루가 넘었을 때만 쓴다).
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @returns {string} 댓글의 시간. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static ago(iso: string | null | undefined, display: TimeValues, now: number = Date.now()): string {
        return agoAt(iso, display, now);
    }

    /**
     * 마우스를 올렸을 때(title)·스크린리더용 정확한 시각 — `2026.09.30 15:43`(12시간이면 `2026.09.30 오후 3:43`)
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @returns {string} 날짜와 시각 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static full(iso: string | null | undefined, display: TimeValues): string {
        return fullAt(iso, display);
    }

    /**
     * 날짜만 — `2026.10.02` (제재 안내의 처리일·정지 기간처럼 시각이 필요 없는 자리). 날짜 형식대로 쓴다
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @returns {string} 표시 시간대의 날짜 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static date(iso: string | null | undefined, display: TimeValues): string {
        return dateAt(iso, display);
    }

    /**
     * 날짜와 요일 — `2026.10.02(금)` (정지 해제 예정일). 날짜 형식대로 쓴다
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @returns {string} 표시 시간대의 날짜와 요일 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static dateDay(iso: string | null | undefined, display: TimeValues): string {
        return dateDayAt(iso, display);
    }

    /**
     * 연 없는 짧은 날짜 — `09.27`(점)·`09-27`(대시)·`27/09`(일/월)·`09/27`(월/일). 날짜 형식의 순서·구분자를 따르고 표시 시간대의 날짜로 보인다.
     * "오늘"·"어제"·"방금" 같은 말을 끼우지 않고 **연은 늘 뺀다** — 올해가 아닌 날짜에 연을 붙일지는 부르는 쪽이 정한다(`parts` 로 연을 비교해 `date` 를 쓴다).
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값(시간대·날짜 형식을 읽는다).
     * @returns {string} 표시 시간대의 연 없는 날짜 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     * @example UtilTime.shortDate('2026-10-13T00:14:00Z', UtilTime.FALLBACK_DISPLAY) // '10-13'
     */
    static shortDate(iso: string | null | undefined, display: TimeValues): string {
        return shortDateAt(iso, display);
    }

    /**
     * 연 없는 짧은 날짜와 시각 — 24시간 `10.13 09:14`, 12시간 `10.13 오전 9:14`. 날짜는 날짜 형식(`shortDate`), 시각은 시간 형식을 따르고 표시 시간대로 보인다.
     * "오늘"·"어제"·"방금"을 끼우지 않아 앞으로의 시각(제한이 끝나는 때)도 그대로 보인다. 연은 늘 뺀다 — 올해가 아닌 시각에 연을 붙일지는 부르는 쪽이 정한다.
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @returns {string} 표시 시간대의 연 없는 날짜와 시각 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     * @example UtilTime.shortDateTime('2026-10-13T00:14:00Z', UtilTime.FALLBACK_DISPLAY) // '10-13 09:14'
     */
    static shortDateTime(iso: string | null | undefined, display: TimeValues): string {
        return shortDateTimeAt(iso, display);
    }

    /**
     * 저장 표시의 시각 — 24시간 `15:12`, 12시간 `오후 3:12` (자동 저장됨 · 15:12)
     * @param {string | null | undefined} iso - 서버가 준 UTC 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @returns {string} 시각 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static clock(iso: string | null | undefined, display: TimeValues): string {
        return clockAt(iso, display);
    }

    /**
     * 예약 목록의 발행 시각 — "10월 5일 (일) 21:00"(12시간이면 "10월 5일 (일) 오후 9:00"), 올해가 아니면 앞에 "2027년 "
     * @param {string | null | undefined} iso - 예약 발행 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @returns {string} 예약 발행 시각 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static slot(iso: string | null | undefined, display: TimeValues, now: number = Date.now()): string {
        return slotAt(iso, display, now);
    }

    /**
     * 예약 완료 안내의 시각 — "10월 5일 15시" · "10월 5일 15시 30분"(12시간이면 "10월 5일 오후 3시 30분")
     * @param {string | null | undefined} iso - 예약 발행 시각(ISO-8601).
     * @param {TimeValues} display - 표시 값.
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @returns {string} 예약 완료 안내의 시각 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static reserved(iso: string | null | undefined, display: TimeValues, now: number = Date.now()): string {
        return reservedAt(iso, display, now);
    }

    /**
     * 그 시각까지 며칠 남았는가(올림). 잘못된 시각이면 null — 시간대와 무관
     * @param {string | null | undefined} iso - 목표 시각(ISO-8601).
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @returns {number | null} 남은 날수(올림). 잘못된 시각이면 null.
     */
    static daysUntil(iso: string | null | undefined, now: number = Date.now()): number | null {
        const ms = parse(iso);
        return ms === null ? null : Math.ceil((ms - now) / 86_400_000);
    }

    /**
     * 임시저장의 만료 안내 — "12일 뒤 삭제"(영어 "Deletes in 12 days") (시간대와 무관, 언어만 따른다)
     * @param {string | null | undefined} iso - 만료 시각(ISO-8601).
     * @param {number} now - 지금(밀리초). 시험용이며 안 주면 현재 시각.
     * @param {string} [language] - 말의 언어(`ko`·`en`). 안 주거나 모르는 값이면 한국어(`UtilTime.of(values).expiry(iso)` 가 표시 값의 언어로 부른다).
     * @returns {string} "12일 뒤 삭제" 꼴의 글자. 시각이 잘못됐으면 빈 문자열.
     */
    static expiry(iso: string | null | undefined, now: number = Date.now(), language?: string): string {
        const days = UtilTime.daysUntil(iso, now);
        if (days === null) return '';
        const words = UtilTimeWords.of(UtilTimeWords.languageOf(language));
        return days <= 0 ? words.expirySoon : words.expiryDays(days);
    }

    /**
     * 기다릴 시간을 사람이 읽는 말로 — "57초" · "1분 30초" · "3시간 12분"(영어 "57 seconds" · "1 minute 30 seconds" · "3 hours 12 minutes") (시간대와 무관, 언어만 따른다)
     * @param {number} totalSeconds - 기다릴 시간(초).
     * @param {string} [language] - 말의 언어(`ko`·`en`). 안 주거나 모르는 값이면 한국어(`UtilTime.of(values).duration(seconds)` 가 표시 값의 언어로 부른다).
     * @returns {string} "57초" · "1분 30초" · "3시간 12분" 꼴의 글자.
     */
    static duration(totalSeconds: number, language?: string): string {
        const words = UtilTimeWords.of(UtilTimeWords.languageOf(language));
        const s = Math.max(0, Math.ceil(totalSeconds));
        if (s < 60) return words.seconds(s);
        if (s < 3600) return words.minutes(Math.floor(s / 60), s % 60);
        return words.hours(Math.floor(s / 3600), Math.floor((s % 3600) / 60));
    }
}
