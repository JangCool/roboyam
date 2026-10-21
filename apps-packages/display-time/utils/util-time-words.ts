/**
 * 시각 글자에 들어가는 **말** — "방금"·"어제"·"오전"·"N분 전"·요일·"10월 5일" 같은 단어와 문장 틀을 언어(`ko`·`en`)별로 둔 단어표.
 * `UtilTime`(시각 글자의 순수 코어)이 `values.language` 로 고른 `TimeLanguage` 를 넘겨 쓴다. 숫자·구분자·순서(날짜 형식 `YMD_DOT` 등)는 여기가 아니라 `UtilTime` 이 정한다.
 *
 * 코어는 `vue`·`vue-i18n`·`~/` 를 쓰지 않는 순수 TypeScript 라 `t()` 를 부를 수 없다 — 그래서 문구 파일(`i18n/locales`) 대신 이 파일에 단어표를 두고 언어 값으로 고른다.
 * 요일·월 이름은 `Intl.DateTimeFormat` 에 로캘을 넘겨 얻는다(한국어 요일은 서버 `ServerTimeText` 와 글자가 같도록 한 글자 표를 그대로 쓴다).
 * **한국어 출력은 바꾸지 않는다** — 서버 `ServerTimeText`(메일·오류 문구의 시각)와 같은 말을 화면과 메일이 다르게 쓰지 않게 하려는 원본 규칙이다.
 * 모르는 언어 값·없는 값은 `ko` 로 대신한다(`languageOf`).
 *
 * 이 파일은 `apps-packages/display-time/` 에 한 벌만 있고, 순수 TypeScript 여야 한다.
 */

/** 시각 글자가 따르는 언어 — 표시 값 `language` 와 같은 값 */
export type TimeLanguage = 'ko' | 'en';

/** 한 언어의 단어표 — 모든 말이 한 곳에 모여 있다 */
export type TimeWords = {
    /** "방금" */
    justNow: string;
    /** "오늘" — 뒤에 시각을 잇는다("오늘 14:32") */
    today: string;
    /** "어제" */
    yesterday: string;
    /** 요일 이름 — 0=일 … 6=토 */
    weekday(weekday: number): string;
    /** 12시간제 시각 — `오전 9:05` · `9:05 AM`. 시는 0~23 */
    clock12(h: number, mi: string): string;
    /** "N분 전" */
    minutesAgo(n: number): string;
    /** "N시간 전" */
    hoursAgo(n: number): string;
    /** 예약 목록의 발행 시각 — 올해가 아니면 `year` 가 연, 올해면 null. `time` 은 시간 형식대로 만든 시각 글자 */
    slot(year: number | null, mo: number, d: number, weekday: number, time: string): string;
    /** 예약 완료 안내의 시각 — 올해가 아니면 `year` 가 연, 올해면 null. 시·분은 숫자 그대로 받아 시간 형식(`h12`)대로 만든다 */
    reserved(year: number | null, mo: number, d: number, h: number, mi: number, h12: boolean): string;
    /** 임시저장 만료 안내 — 며칠 남았는지(1 이상) */
    expiryDays(days: number): string;
    /** 임시저장 만료 안내 — 이미 지났거나 오늘 */
    expirySoon: string;
    /** 기다릴 시간 — 초 */
    seconds(n: number): string;
    /** 기다릴 시간 — 분과 남은 초(남은 초가 0이면 `rest` 는 0) */
    minutes(m: number, rest: number): string;
    /** 기다릴 시간 — 시간과 남은 분(남은 분이 0이면 `rest` 는 0) */
    hours(h: number, rest: number): string;
};

/** 월 이름이 필요한 언어가 쓰는 포매터 — 같은 입력에 늘 같은 결과를 주는 메모일 뿐 화면 상태가 아니다 */
const FORMATS = new Map<string, Intl.DateTimeFormat>();

function formatter(locale: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
    const key = `${locale}|${JSON.stringify(options)}`;
    let format = FORMATS.get(key);
    if (!format) {
        format = new Intl.DateTimeFormat(locale, { ...options, timeZone: 'UTC' });
        FORMATS.set(key, format);
    }
    return format;
}

/** 2023-01-01 은 일요일이다 — 요일 번호(0=일)에서 이름을 얻는 데 쓴다 */
function weekdayShort(locale: string, weekday: number): string {
    return formatter(locale, { weekday: 'short' }).format(Date.UTC(2023, 0, 1 + weekday));
}

/** 월 이름 3글자 약어(`Oct`) */
function monthShort(locale: string, mo: number): string {
    return formatter(locale, { month: 'short' }).format(Date.UTC(2026, mo - 1, 1));
}

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

/** 영어 복수형 — `1 minute` · `2 minutes` */
function plural(n: number, unit: string): string {
    return `${n} ${unit}${n === 1 ? '' : 's'}`;
}

/** 한국어 요일 — 한 글자(서버 `ServerTimeText` 와 같다) */
const KO_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

const KO: TimeWords = {
    justNow: '방금',
    today: '오늘',
    yesterday: '어제',
    weekday: (weekday) => KO_WEEKDAYS[weekday]!,
    clock12: (h, mi) => `${h < 12 ? '오전' : '오후'} ${h % 12 === 0 ? 12 : h % 12}:${mi}`,
    minutesAgo: (n) => `${n}분 전`,
    hoursAgo: (n) => `${n}시간 전`,
    slot: (year, mo, d, weekday, time) => `${year === null ? '' : `${year}년 `}${mo}월 ${d}일 (${KO_WEEKDAYS[weekday]}) ${time}`,
    reserved: (year, mo, d, h, mi, h12) => {
        const minutes = mi === 0 ? '' : ` ${mi}분`;
        const hour = h12 ? `${h < 12 ? '오전' : '오후'} ${h % 12 === 0 ? 12 : h % 12}시` : `${h}시`;
        return `${year === null ? '' : `${year}년 `}${mo}월 ${d}일 ${hour}${minutes}`;
    },
    expiryDays: (days) => `${days}일 뒤 삭제`,
    expirySoon: '곧 삭제돼요',
    seconds: (n) => `${n}초`,
    minutes: (m, rest) => (rest ? `${m}분 ${rest}초` : `${m}분`),
    hours: (h, rest) => (rest ? `${h}시간 ${rest}분` : `${h}시간`),
};

const EN_LOCALE = 'en-US';

/** 영어 — 12시간제는 `9:05 AM`, 날짜 말은 `Oct 5`·`Sun`(3글자 약어), 상대 시각은 `5 minutes ago` */
const EN: TimeWords = {
    justNow: 'Just now',
    today: 'Today',
    yesterday: 'Yesterday',
    weekday: (weekday) => weekdayShort(EN_LOCALE, weekday),
    clock12: (h, mi) => `${h % 12 === 0 ? 12 : h % 12}:${mi} ${h < 12 ? 'AM' : 'PM'}`,
    minutesAgo: (n) => `${plural(n, 'minute')} ago`,
    hoursAgo: (n) => `${plural(n, 'hour')} ago`,
    slot: (year, mo, d, weekday, time) => `${weekdayShort(EN_LOCALE, weekday)}, ${monthShort(EN_LOCALE, mo)} ${d}${year === null ? '' : `, ${year}`}, ${time}`,
    reserved: (year, mo, d, h, mi, h12) => {
        const day = `${monthShort(EN_LOCALE, mo)} ${d}${year === null ? '' : `, ${year}`}`;
        if (!h12) return `${day}, ${pad(h)}:${pad(mi)}`;
        const hour = h % 12 === 0 ? 12 : h % 12;
        return `${day}, ${hour}${mi === 0 ? '' : `:${pad(mi)}`} ${h < 12 ? 'AM' : 'PM'}`;
    },
    expiryDays: (days) => `Deletes in ${plural(days, 'day')}`,
    expirySoon: 'Deleting soon',
    seconds: (n) => plural(n, 'second'),
    minutes: (m, rest) => (rest ? `${plural(m, 'minute')} ${plural(rest, 'second')}` : plural(m, 'minute')),
    hours: (h, rest) => (rest ? `${plural(h, 'hour')} ${plural(rest, 'minute')}` : plural(h, 'hour')),
};

export default class UtilTimeWords {
    /**
     * 언어 값을 시각 글자가 아는 언어로 정리한다 — `en` 이면 `en`, 그 밖(없는 값·모르는 값)은 `ko`.
     * @param {unknown} language - 표시 값의 `language`(또는 아무 값).
     * @returns {TimeLanguage} `ko` 또는 `en`.
     */
    static languageOf(language: unknown): TimeLanguage {
        return language === 'en' ? 'en' : 'ko';
    }

    /**
     * 그 언어의 단어표.
     * @param {TimeLanguage} language - 정리한 언어(`languageOf` 의 결과).
     * @returns {TimeWords} 단어표.
     */
    static of(language: TimeLanguage): TimeWords {
        return language === 'en' ? EN : KO;
    }
}
