import UtilI18n from '#layers/i18n/utils/util-i18n';
import type { DisplayValues } from './util-time';

/**
 * 표시 값(시간대·시간 형식·날짜 형식·언어) 자체를 다루는 순수 규칙 — 서버·저장소에서 온 값 검사와 비교.
 * 상태도 요청도 없다(상태는 `useDisplay`, 서버 호출은 쓰는 앱의 `useDisplayApi`). 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다.
 */

const TIME_FORMATS: readonly string[] = ['H24', 'H12'];
const DATE_FORMATS: readonly string[] = ['YMD_DASH', 'YMD_DOT', 'DMY_SLASH', 'MDY_SLASH'];
/** 시간대 이름 길이 상한 — 서버(`DisplayRules`)와 같다 */
const ZONE_MAX_LENGTH = 64;

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default class UtilDisplayValues {
    /**
     * 표시 값으로 쓸 수 있는 모양인가 — 시간대·시간 형식·날짜 형식 세 칸이 모두 있고 허용된 값이다. 시간대는 비어 있지 않은 글자(64자 이하)면 된다
     * (브라우저가 모르는 이름이면 `UtilTime` 이 한국 시간으로 그린다). 언어는 `ko`·`en` 이 아니거나 없으면 `ko` 로 받는다(옛 서버 응답·옛 기억값에 칸이 없다).
     * @param {unknown} value - 후보(서버 응답의 `values`·저장해 둔 값).
     * @returns {DisplayValues | null} 정리한 세 칸. 쓸 수 없으면 null.
     */
    static sanitize(value: unknown): DisplayValues | null {
        if (!isObject(value)) return null;
        const { zoneId, timeFormat, dateFormat, language } = value;
        if (typeof zoneId !== 'string') return null;
        const zone = zoneId.trim();
        // eslint-disable-next-line no-control-regex
        if (zone === '' || zone.length > ZONE_MAX_LENGTH || /[\u0000-\u001f\u007f]/.test(zone)) return null;
        if (typeof timeFormat !== 'string' || !TIME_FORMATS.includes(timeFormat)) return null;
        if (typeof dateFormat !== 'string' || !DATE_FORMATS.includes(dateFormat)) return null;
        return { zoneId: zone, timeFormat: timeFormat as DisplayValues['timeFormat'], dateFormat: dateFormat as DisplayValues['dateFormat'], language: UtilI18n.languageOf(language) };
    }

    /**
     * 두 값이 같은가 — 네 칸을 글자 그대로 견준다.
     * @param {DisplayValues | null | undefined} a - 한쪽.
     * @param {DisplayValues | null | undefined} b - 다른 쪽.
     * @returns {boolean} 둘 다 있고 네 칸이 같으면 true.
     */
    static isSame(a: DisplayValues | null | undefined, b: DisplayValues | null | undefined): boolean {
        return Boolean(a && b && a.zoneId === b.zoneId && a.timeFormat === b.timeFormat && a.dateFormat === b.dateFormat && a.language === b.language);
    }

    /**
     * 네 칸을 복사한다(원본을 건드리지 않는다).
     * @param {DisplayValues} values - 표시 값.
     * @returns {DisplayValues} 복사본.
     */
    static copy(values: DisplayValues): DisplayValues {
        return { zoneId: values.zoneId, timeFormat: values.timeFormat, dateFormat: values.dateFormat, language: values.language };
    }
}
