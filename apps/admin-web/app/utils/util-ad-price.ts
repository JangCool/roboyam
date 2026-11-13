import type { AdPriceAdjustKind, AdPriceField, AdPriceOccupancy, AdPriceProduct, AdPriceRule, AdPriceSchedule, AdPriceUnit, AdPriceValues } from '~/composables/ad/api/useAdApi.types';
import type { BizAmount } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

/** 예약으로 바꾸는 칸(받기는 빼고) */
export type AdPriceScheduledField = Exclude<AdPriceField, 'accepting'>;

/** 화면 묶음 — 광고(배너 넷·날개·한 줄) · 핫딜(글 게시·게시판 상단·말머리 상단) */
export type AdPriceGroup = 'AD' | 'HOTDEAL';

/** 바뀌는 칸 한 줄(직전 값 → 새 값) */
export interface AdPriceDiff {
    field: AdPriceScheduledField;
    before: number | null;
    after: number | null;
}

/** 예약 한 건을 「바로 앞 값」(앞 예약이 적용된 뒤 값)과 함께 */
export interface AdPriceChainItem {
    schedule: AdPriceSchedule;
    before: AdPriceValues;
    after: AdPriceValues;
    diff: AdPriceDiff[];
}

const GROUPS: readonly { key: AdPriceGroup; products: readonly AdPriceProduct[] }[] = [
    { key: 'AD', products: ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'TEXT_LINE'] },
    { key: 'HOTDEAL', products: ['HOTDEAL_POST', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'] },
];

/**
 * 광고 상품 가격(요금표 `biz_price_rules`) 화면 규칙 — 입력만 보고 답하는 순수 함수. 판정은 서버다(DB CHECK `ck_bpr_price`·`ck_bpr_capacity`·`ck_bpr_days` 와 같은 규칙 + 적용 시각은 지금보다 뒤, 같은 상품 같은 시각 중복 금지).
 * 가격 칸은 상품마다 적용 시각을 정해 예약한다(한 상품에 여러 건). 예약 하나는 네 칸 값을 통째로 가진다. 받기 켬/끔은 바로 바뀐다.
 * 금액 계산은 신청 건과 같다(`biz-application-contract.md` 「금액」 — 공급가 = 단가 × 일수(핫딜 글은 건당), 부가세 = floor(공급가 × 10%)).
 */
export default class UtilAdPrice {
    static readonly GROUPS = GROUPS;
    /** 화면 차례 */
    static readonly PRODUCTS: readonly AdPriceProduct[] = GROUPS.flatMap((group) => group.products);
    /** 예약으로 바꾸는 칸 — 이 차례로 보인다 */
    static readonly SCHEDULED_FIELDS: readonly AdPriceScheduledField[] = ['unitPrice', 'capacity', 'maxDays', 'maxLeadDays'];
    /** 칸의 아래 한계(DB CHECK — 단가 0 이상 · 한도 1 이상(하루 단가 상품만) · 최대 일수·미리 신청 일수 1 이상) */
    static readonly MIN = { unitPrice: 0, capacity: 1, maxDays: 1, maxLeadDays: 1 } as const;
    /** 미리보기 예시 — 하루 단가 상품은 7일 신청(최대 일수가 더 짧으면 최대 일수), 건당 상품은 1건 */
    static readonly EXAMPLE_DAYS = 7;
    /** 적용 시각 고르개의 분 간격 */
    static readonly EFFECTIVE_MINUTE_STEP = 10;
    /** 단가 일괄 조정 — 비율 범위(%) · 결과는 10원 단위 버림(가정) */
    static readonly BULK = { percentMin: -90, percentMax: 300, unit: 10 } as const;

    /** 상품 이름 i18n 키 — 신청 건 화면과 같은 이름(`bizApplication.adType.*`, 핫딜 글 게시는 `bizApplication.product.HOTDEAL_POST`) */
    static nameKey(product: AdPriceProduct): string {
        return product === 'HOTDEAL_POST' ? 'bizApplication.product.HOTDEAL_POST' : `bizApplication.adType.${product}`;
    }

    /** 값 넷만 새 객체로(건당 상품의 한도는 늘 null) */
    static valuesOf(unit: AdPriceUnit, source: AdPriceValues): AdPriceValues {
        return { unitPrice: source.unitPrice, capacity: unit === 'POST' ? null : source.capacity, maxDays: source.maxDays, maxLeadDays: source.maxLeadDays };
    }

    /** 두 값의 다른 칸 */
    static diff(before: AdPriceValues, after: AdPriceValues): AdPriceDiff[] {
        return UtilAdPrice.SCHEDULED_FIELDS.filter((field) => before[field] !== after[field]).map((field) => ({ field, before: before[field], after: after[field] }));
    }

    /** 적용 시각 차례의 예약(같은 시각은 없다 — 서버가 막는다) */
    static sorted(schedules: readonly AdPriceSchedule[]): AdPriceSchedule[] {
        return [...schedules].sort((a, b) => Date.parse(a.effectiveAt) - Date.parse(b.effectiveAt));
    }

    /** 예약마다 「바로 앞 값」(지금 값에 앞 예약들을 차례로 적용한 값)과 바뀌는 칸 */
    static chain(rule: AdPriceRule): AdPriceChainItem[] {
        let before = UtilAdPrice.valuesOf(rule.unit, rule);
        return UtilAdPrice.sorted(rule.schedules).map((schedule) => {
            const after = UtilAdPrice.valuesOf(rule.unit, schedule);
            const item = { schedule, before, after, diff: UtilAdPrice.diff(before, after) };
            before = after;
            return item;
        });
    }

    /** 그 시각 바로 앞 값 — 지금 값에 그 시각 전 예약들(고치는 예약 `excludeId` 는 빼고)을 차례로 적용한 값 */
    static valuesBefore(rule: AdPriceRule, effectiveAt: string, excludeId: string | null = null): AdPriceValues {
        const at = Date.parse(effectiveAt);
        let values = UtilAdPrice.valuesOf(rule.unit, rule);
        for (const schedule of UtilAdPrice.sorted(rule.schedules)) {
            if (schedule.id === excludeId) continue;
            if (Number.isNaN(at) || Date.parse(schedule.effectiveAt) >= at) break;
            values = UtilAdPrice.valuesOf(rule.unit, schedule);
        }
        return values;
    }

    /** 같은 상품에 같은 시각 예약이 이미 있나(고치는 예약은 빼고) */
    static hasScheduleAt(rule: AdPriceRule, effectiveAt: string, excludeId: string | null = null): boolean {
        const at = Date.parse(effectiveAt);
        return rule.schedules.some((s) => s.id !== excludeId && Date.parse(s.effectiveAt) === at);
    }

    /** 다가오는 예약 — 모든 상품, 가까운 적용 시각 차례(같으면 화면 차례). 전→후는 그 상품의 바로 앞 값 기준 */
    static upcoming(rules: readonly AdPriceRule[]): (AdPriceChainItem & { product: AdPriceProduct })[] {
        const order = (p: AdPriceProduct) => UtilAdPrice.PRODUCTS.indexOf(p);
        return rules
            .flatMap((rule) => UtilAdPrice.chain(rule).map((item) => ({ ...item, product: rule.product })))
            .sort((a, b) => Date.parse(a.schedule.effectiveAt) - Date.parse(b.schedule.effectiveAt) || order(a.product) - order(b.product));
    }

    /**
     * 단가 일괄 조정 — 비율은 `기준 × (1 + 비율/100)`, 정액은 `기준 + 금액`. 결과는 10원 단위 버림(가정), 0원 아래로는 내려가지 않는다.
     * @returns 새 단가(원).
     */
    static adjustPrice(base: number, kind: AdPriceAdjustKind, value: number): number {
        const raw = kind === 'PERCENT' ? (base * (100 + value)) / 100 : base + value;
        return Math.max(0, Math.floor(raw / UtilAdPrice.BULK.unit) * UtilAdPrice.BULK.unit);
    }

    /** 적용 시각 기본값 — 내일 00:00(한국 시각)의 순간 ISO */
    static defaultEffectiveAt(now = Date.now()): string {
        return UtilBizApplication.kstMidnight(UtilBizApplication.addDays(UtilBizApplication.kstToday(now), 1));
    }

    /** 적용 시각 화면 검사 — 비었으면 `required`, 지금보다 뒤가 아니면 `past`(판정은 서버 400 `fields.effectiveAt`) */
    static effectiveAtProblem(effectiveAt: string, now = Date.now()): 'required' | 'past' | null {
        const at = Date.parse(effectiveAt);
        if (!effectiveAt || Number.isNaN(at)) return 'required';
        return at <= now ? 'past' : null;
    }

    /**
     * 새 한도를 이미 넘어 있는 앞날 수 — 날짜별 최대 동시 겹침이 새 한도보다 큰 날의 합(분포는 서버가 준다 — `AdPriceOccupancy`).
     * @returns 날 수(한도 없음이면 0).
     */
    static overbookedDays(occupancy: readonly AdPriceOccupancy[], capacity: number | null): number {
        if (capacity === null) return 0;
        return occupancy.reduce((sum, item) => (item.peak > capacity ? sum + item.days : sum), 0);
    }

    /**
     * 사업자 화면 예상 금액 예시 — 하루 단가 상품은 `EXAMPLE_DAYS`일(최대 일수가 더 짧으면 최대 일수), 건당 상품은 1건.
     * @returns 일수(또는 건수)와 공급가·부가세(floor 10%)·합계.
     */
    static example(unit: AdPriceUnit, values: Pick<AdPriceValues, 'unitPrice' | 'maxDays'>): { units: number; amount: BizAmount } {
        const units = unit === 'POST' ? 1 : Math.max(1, Math.min(UtilAdPrice.EXAMPLE_DAYS, values.maxDays));
        return { units, amount: UtilBizApplication.amountOf(values.unitPrice * units) };
    }
}
