import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AdPlacementHistoryField, AdPlacementHistoryItem, AdPriceField, AdPriceProduct, AdPriceUnit, AdStatCounts } from '~/composables/ad/api/useAdApi.types';
import UtilAdPrice from '~/utils/util-ad-price';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';
import type { SettingHistoryChange, SettingHistoryItem } from '~/composables/common/useSettingHistory.types';

/** 칸 코드 → 칸 이름 i18n 키 — 서버 `labelKey` 형식이 정해지기 전이라 화면이 칸 코드로 고른다(모르는 칸은 서버 `labelKey` 그대로) */
const FIELD_KEYS: Record<AdPlacementHistoryField, string> = {
    'postInline.enabled': 'ad.placementHistoryField.postInlineEnabled',
    'postInline.adType': 'ad.placementHistoryField.postInlineAdType',
    'postInline.position': 'ad.placementHistoryField.postInlinePosition',
    'wings.left': 'ad.placementHistoryField.wingsLeft',
    'wings.right': 'ad.placementHistoryField.wingsRight',
    'wings.screens': 'ad.placementHistoryField.wingsScreens',
    'boardList.enabled': 'ad.placementHistoryField.boardListEnabled',
    'boardList.after': 'ad.placementHistoryField.boardListAfter',
    'boardList.count': 'ad.placementHistoryField.boardListCount',
    'boardList.gap': 'ad.placementHistoryField.boardListGap',
    'adult.enabled': 'ad.placementHistoryField.adultEnabled',
    'refund.stopFeeRate': 'ad.placementHistoryField.refundStopFeeRate',
    'adsense.mode': 'ad.placementHistoryField.adsenseMode',
    'adsense.client': 'ad.placementHistoryField.adsenseClient',
    'adsense.adtest': 'ad.placementHistoryField.adsenseAdtest',
    'adsense.units.WIDE_BANNER': 'ad.placementHistoryField.adsenseUnitWide',
    'adsense.units.STRIP_BANNER': 'ad.placementHistoryField.adsenseUnitStrip',
    'adsense.units.SQUARE_BANNER': 'ad.placementHistoryField.adsenseUnitSquare',
    'adsense.units.SMALL_BANNER': 'ad.placementHistoryField.adsenseUnitSmall',
    'adsense.units.WING_BANNER': 'ad.placementHistoryField.adsenseUnitWing',
    'adsense.units.POST_INLINE': 'ad.placementHistoryField.adsenseUnitPost',
    'adsense.units.BOARD_LIST.slot': 'ad.placementHistoryField.adsenseUnitFeed',
    'adsense.units.BOARD_LIST.layoutKey': 'ad.placementHistoryField.adsenseLayoutKey',
};

/** 켜고 끄는 칸 — 값 `"true"`/`"false"` */
const TOGGLE_FIELDS: ReadonlySet<string> = new Set(['postInline.enabled', 'wings.left', 'wings.right', 'boardList.enabled', 'adult.enabled', 'adsense.adtest']);

/**
 * 광고 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('ad.…')` 로 부른다. `t` 를 첫 인자로 받는다.
 */
export default class UtilAdMessages {
    /**
     * 광고 자리 설정 변경 이력을 표(`SettingHistoryTable`)가 그대로 보일 모양으로 — 칸 이름 키와 전·후 값을 사람이 읽는 글자로 바꾼 사본.
     * 값 모양은 서버가 정한다(켜기 `"true"`/`"false"` · 유형·위치 enum 이름 · 화면 목록 쉼표 이음, 빈 목록 `""` · 몇 번째 뒤 숫자 글자).
     * 모르는 값은 글자 그대로, 값이 없으면(null) 그대로 두어 표가 「(없음)」으로 보인다.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수(`useI18n().te`).
     * @param items - 서버가 준 이력 줄.
     * @returns 표에 넘길 이력 줄(원본은 고치지 않는다).
     */
    static placementHistoryItems(t: Translate, te: (key: string) => boolean, items: readonly AdPlacementHistoryItem[]): AdPlacementHistoryItem[] {
        return items.map((item) => ({ ...item, changes: item.changes.map((change) => UtilAdMessages.placementHistoryChange(t, te, change)) }));
    }

    /**
     * 바뀐 칸 하나.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수.
     * @param change - 서버가 준 바뀐 칸.
     * @returns 칸 이름 키·값 글자를 바꾼 사본.
     */
    static placementHistoryChange(t: Translate, te: (key: string) => boolean, change: SettingHistoryChange): SettingHistoryChange {
        const labelKey = FIELD_KEYS[change.field as AdPlacementHistoryField] ?? change.labelKey;
        return {
            ...change,
            labelKey,
            before: UtilAdMessages.placementHistoryValue(t, te, change.field, change.before),
            after: UtilAdMessages.placementHistoryValue(t, te, change.field, change.after),
        };
    }

    /**
     * 바뀐 값 하나를 사람이 읽는 글자로.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수.
     * @param field - 칸 코드.
     * @param value - 서버가 준 값.
     * @returns 「켜짐」·「띠 배너」·「홈, 게시판 목록」·「없음」·「5번째 글 뒤」·「10%」(모르는 값은 그대로, null 은 null).
     */
    static placementHistoryValue(t: Translate, te: (key: string) => boolean, field: string, value: string | null): string | null {
        if (value == null) return null;
        if (TOGGLE_FIELDS.has(field)) {
            if (value === 'true') return t('ad.placementHistoryOn');
            if (value === 'false') return t('ad.placementHistoryOff');
            return value;
        }
        if (field === 'postInline.adType') return UtilAdMessages.option(t, te, 'ad.postInlineTypeOption', value);
        if (field === 'postInline.position') return UtilAdMessages.option(t, te, 'ad.postInlinePositionOption', value);
        if (field === 'adsense.mode') return UtilAdMessages.option(t, te, 'ad.adsenseModeOption', value);
        if (field === 'wings.screens') {
            const screens = value
                .split(',')
                .map((screen) => screen.trim())
                .filter((screen) => screen !== '');
            if (!screens.length) return t('ad.placementHistoryNoScreens');
            return screens.map((screen) => UtilAdMessages.option(t, te, 'ad.wingScreen', screen)).join(', ');
        }
        if (field === 'boardList.after' && /^\d+$/.test(value)) return t('ad.placementHistoryAfter', { n: Number(value) });
        if (field === 'refund.stopFeeRate' && /^\d+$/.test(value)) return t('ad.placementHistoryPercent', { n: Number(value) });
        return value;
    }

    /**
     * 상품 가격(요금표) 변경 이력을 표가 그대로 보일 모양으로 — 칸 코드에서 이름(`label`)과 값 글자를 만든다.
     *  - `schedules.<상품>.effectiveAt` → 「넓은 배너 · 예약 적용 시각」 · 시각 글자(`timeText`)
     *  - `schedules.<상품>.<칸>` → 「넓은 배너 · 단가(예약)」 · 「50,000원」(지금 값 → 예약 값)
     *  - `rules.<상품>.<칸>` → 「넓은 배너 · 단가」 · 「50,000원」·「2건」·「14일」·「받음」(지금 값이 바뀜 — 예약 적용·받기)
     * 모르는 칸 코드는 그대로 둔다(표가 칸 코드·값 글자를 보인다).
     * @param t - 번역 함수.
     * @param items - 서버가 준 이력 줄.
     * @param locale - 숫자 표기 언어.
     * @param timeText - 순간 ISO → 보이는 시각 글자(표시 시간대 서식 — `useDisplayTime().full`).
     * @returns 표에 넘길 이력 줄(원본은 고치지 않는다).
     */
    static priceRuleHistoryItems(t: Translate, items: readonly SettingHistoryItem[], locale: string, timeText: (iso: string) => string): SettingHistoryItem[] {
        return items.map((item) => ({ ...item, changes: item.changes.map((change) => UtilAdMessages.priceRuleHistoryChange(t, change, locale, timeText)) }));
    }

    /** 요금표 이력의 바뀐 칸 하나 — 모르는 칸 코드면 그대로 */
    static priceRuleHistoryChange(t: Translate, change: SettingHistoryChange, locale: string, timeText: (iso: string) => string): SettingHistoryChange {
        const match = /^(schedules|rules)\.([A-Z_]+)\.(effectiveAt|unitPrice|capacity|maxDays|maxLeadDays|accepting)$/.exec(change.field);
        if (!match || !(UtilAdPrice.PRODUCTS as readonly string[]).includes(match[2]!)) return change;
        const scheduled = match[1] === 'schedules';
        const product = match[2] as AdPriceProduct;
        const productName = t(UtilAdPrice.nameKey(product));
        if (match[3] === 'effectiveAt') {
            return { ...change, label: t('ad.priceHistoryEffectiveAt', { product: productName }), before: change.before ? timeText(change.before) : null, after: change.after ? timeText(change.after) : null };
        }
        const field = match[3] as AdPriceField;
        const params = { product: productName, field: t(`ad.priceField.${field}`) };
        return {
            ...change,
            label: scheduled ? t('ad.priceHistoryScheduledLabel', params) : t('ad.priceHistoryLabel', params),
            before: UtilAdMessages.priceRuleValue(t, field, change.before, locale),
            after: UtilAdMessages.priceRuleValue(t, field, change.after, locale),
        };
    }

    /** 요금표 값 하나 — 단가 「50,000원」 · 한도 「2건」(null 은 「한도 없음」) · 일수 「14일」 · 받기 「받음/받지 않음」. 모르는 값은 그대로 */
    /**
     * 사업자 화면 상품 카드의 가격 글자 — 「하루 50,000원」·「게시 1건 20,000원」.
     */
    static priceCardPrice(t: Translate, unit: AdPriceUnit, unitPrice: number, locale: string): string {
        const amount = UtilBizApplicationMessages.won(t, unitPrice, locale);
        return unit === 'POST' ? t('ad.pricePreviewPerPost', { amount }) : t('ad.pricePreviewDaily', { amount });
    }

    /**
     * 사업자 화면 상품 카드의 위치 줄 — 「홈 맨 위 큰 자리 · 1200×300 · 동시 2개」(건당 상품은 한도 없이).
     */
    static priceCardWhere(t: Translate, product: AdPriceProduct, capacity: number | null): string {
        return [t(`ad.pricePreviewWhere.${product}`), capacity !== null ? t('ad.pricePreviewCapacity', { n: capacity }) : null].filter(Boolean).join(' · ');
    }

    static priceRuleValue(t: Translate, field: AdPriceField, value: string | null, locale: string): string | null {
        if (field === 'capacity' && value == null) return t('ad.priceNoLimit');
        if (value == null) return null;
        if (field === 'accepting') {
            if (value === 'true') return t('ad.priceAcceptingOn');
            if (value === 'false') return t('ad.priceAcceptingOff');
            return value;
        }
        if (!/^\d+$/.test(value)) return value;
        const n = Number(value);
        if (field === 'unitPrice') return UtilBizApplicationMessages.won(t, n, locale);
        if (field === 'capacity') return t('ad.priceCapacityValue', { n });
        return t('ad.priceDaysValue', { n });
    }

    /**
     * 성과 차트의 스크린 리더 요약(`role="img"` 의 `aria-label`) — 기간 · 날 수 · 합계 · 노출이 가장 많은 날.
     * @param t - 번역 함수.
     * @param days - 차트 날짜들(오래된 차례).
     * @param locale - 숫자 표기 언어.
     * @param dateText - 날짜(`YYYY-MM-DD`) → 보이는 글자(표시 시간대 서식).
     */
    static chartSummary(t: Translate, days: readonly (AdStatCounts & { date: string })[], locale: string, dateText: (ymd: string) => string): string {
        if (!days.length) return '';
        const from = dateText(days[0]!.date);
        const to = dateText(days[days.length - 1]!.date);
        const sum = days.reduce((acc, d) => ({ impressions: acc.impressions + d.impressions, clicks: acc.clicks + d.clicks }), { impressions: 0, clicks: 0 });
        if (UtilAdStats.isEmpty(sum)) return t('ad.chartSummaryEmpty', { from, to, days: days.length });
        const peak = days.reduce((best, d) => (d.impressions > best.impressions ? d : best), days[0]!);
        return t('ad.chartSummary', { from, to, days: days.length, impressions: UtilAdStats.num(sum.impressions, locale), clicks: UtilAdStats.num(sum.clicks, locale), peak: dateText(peak.date) });
    }

    /** 고르기 칸의 이름(`<묶음 키>.<값>`) — 모르는 값은 그대로 */
    private static option(t: Translate, te: (key: string) => boolean, base: string, value: string): string {
        const key = `${base}.${value}`;
        return te(key) ? t(key) : value;
    }
}
