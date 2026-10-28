import type { GroupBuyCloseReason, GroupBuyNotice, GroupBuyNoticeIncomplete, GroupBuySkuKind, GroupBuyStatus, NoticeCatalog, NoticeCustomItem, NoticeItemDef, NoticeItemValue } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type { GroupBuyCategory } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import type { GroupBuyOrderStatus, GroupBuyRefundState } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type { GroupBuyDeadlineLevel, GroupBuyDisposition, GroupBuyReasonCode, GroupBuyRequestStatus } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';

/** PrimeVue `Tag` 의 색 */
export type GroupBuyTagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** 등록 화면 문구 경고(결정 55·58) — 개봉하면 반품 불가 · 재판매 금지/리셀 · 최저가 · 한정 */
export type GroupBuyPhraseWarning = 'OPEN_NO_RETURN' | 'RESALE' | 'LOWEST' | 'LIMITED';

/**
 * 공구 관리 화면(공구·수량·주문·요청·커머스 설정 구획)이 같이 쓰는 순수 함수 — 상태 색, 송장·사업자등록번호 모양, 등록 화면 문구 경고, 처리 방법 고르기.
 * 판정은 서버다(서버 `GroupBuyAdminService`·`FulfillmentService`·`GroupBuyRequestService` — 같은 규칙을 미리 알리는 용도).
 * 정책 원천: `docs/features/commerce-groupbuy-contract.md`(결정 11·15·18·55·58), commerce.md 10-8.
 */
export default class UtilGroupBuy {
    /** 사유 5~200자(결제 화면과 같은 규칙 — `UtilPayment.REASON`) */
    static readonly REASON = { min: 5, max: 200 } as const;
    /** 대표 이미지 최대 장수(결정 2) */
    static readonly MAX_IMAGES = 8;
    /** 일괄 처리 최대 건수 */
    static readonly BULK_MAX = 200;
    /** 평문 설명(`summary`·`spec`) 최대 글자 */
    static readonly TEXT_MAX = 5000;
    /** 사업자 정보 배송·환불 정책 문구 최대 글자 */
    static readonly POLICY_TEXT_MAX = 3000;
    /** 거절 근거 사진 최대 장수 */
    static readonly EVIDENCE_MAX = 3;
    /** 운영 메모 최대 글자 */
    static readonly MEMO_MAX = 1000;

    /** 모집 시작 뒤에도 고칠 수 있는 칸(설명·이미지 — 결정 2, 고시 양식 — 결정 87). 나머지는 잠긴다 */
    /** 상품에서 오는 칸(결정 89) — 공구 화면에서는 고치지 않는다(상품 화면에서 고친다) */
    static readonly PRODUCT_FIELDS: readonly string[] = ['productName', 'normalPrice', 'summary', 'spec', 'description', 'images', 'normalPriceBasis', 'normalPriceSource', 'withdrawalRestriction'];
    /**
     * 카테고리 고르기 목록(결정 89 — 2단). 상품은 아래 단을 고른다(아래 단이 없는 위 카테고리는 그대로). `withParents` 면 위 단도 넣는다(목록 거르기 — 아래 단 포함).
     * @param {GroupBuyCategory[]} categories - 서버 목록(차례 `sortOrder`).
     * @param {boolean} withParents - 위 단도 넣을지.
     * @param {string} hiddenMark - 숨긴 카테고리 뒤에 붙일 글(화면이 번역해 넘긴다).
     * @returns {{ value: string; label: string }[]} 「위 › 아래」 이름(숨긴 카테고리는 「(숨김)」).
     */
    static categoryChoices(categories: readonly GroupBuyCategory[], withParents = false, hiddenMark = ''): { value: string; label: string }[] {
        const byOrder = (a: GroupBuyCategory, b: GroupBuyCategory) => a.sortOrder - b.sortOrder;
        const mark = (c: GroupBuyCategory) => (c.hidden && hiddenMark ? ` ${hiddenMark}` : '');
        return categories
            .filter((c) => c.parentId === null)
            .sort(byOrder)
            .flatMap((top) => {
                const children = categories.filter((c) => c.parentId === top.id).sort(byOrder);
                const own = { value: top.id, label: top.name + mark(top) };
                if (!children.length) return [own];
                const rows = children.map((c) => ({ value: c.id, label: `${top.name} › ${c.name}${mark(c)}` }));
                return withParents ? [own, ...rows] : rows;
            });
    }

    static readonly CONTENT_FIELDS: readonly string[] = ['summary', 'spec', 'description', 'images', 'notices'];
    /** 고시 항목 값·사유 최대 글자 */
    static readonly NOTICE_TEXT_MAX = 1000;
    /** 고시 양식 제목 최대 글자 */
    static readonly NOTICE_TITLE_MAX = 50;
    /** 판매자 추가 항목 — 이름 길이 · 양식당 줄 수(2b-2 서버 규칙) */
    static readonly NOTICE_CUSTOM_LABEL_MAX = 40;
    static readonly NOTICE_CUSTOM_MAX = 20;

    /** 옵션 축·단품(결정 88 — 축 3 · 값 30 · 조합 200 · 추가 구성상품 20 은 서버 2b-2, 글자 길이는 화면 가정) */
    static readonly SKU = { axisMax: 3, valueMax: 30, comboMax: 200, addonMax: 20, axisNameMax: 30, valueNameMax: 30, addonNameMax: 100 } as const;
    /** MAIN 단품 이름의 축 값 사이(서버가 같은 규칙으로 만든다 — 「블랙 / M」) */
    static readonly SKU_NAME_SEPARATOR = ' / ';

    static statusSeverity(status: GroupBuyStatus): GroupBuyTagSeverity {
        if (status === 'RECRUITING') return 'success';
        if (status === 'CLOSING' || status === 'CLOSED_SUCCESS' || status === 'ORDERED' || status === 'SHIPPING') return 'info';
        if (status === 'CLOSED_FAILED' || status === 'CANCELED') return 'danger';
        if (status === 'DRAFT') return 'warn';
        return 'secondary';
    }

    static closeReasonSeverity(reason: GroupBuyCloseReason): GroupBuyTagSeverity {
        return reason === 'SOLD_OUT' ? 'contrast' : reason === 'CANCELED' ? 'danger' : 'secondary';
    }

    static orderSeverity(status: GroupBuyOrderStatus): GroupBuyTagSeverity {
        if (status === 'CONFIRMED' || status === 'JOINED') return 'info';
        if (status === 'PREPARING') return 'warn';
        if (status === 'SHIPPED') return 'contrast';
        if (status === 'DELIVERED') return 'success';
        if (status === 'REFUNDED' || status === 'CANCELED') return 'danger';
        return 'secondary';
    }

    static requestSeverity(status: GroupBuyRequestStatus): GroupBuyTagSeverity {
        if (status === 'REQUESTED') return 'warn';
        if (status === 'APPROVED') return 'info';
        if (status === 'COMPLETED') return 'success';
        if (status === 'REJECTED') return 'danger';
        return 'secondary';
    }

    static deadlineSeverity(level: GroupBuyDeadlineLevel): GroupBuyTagSeverity {
        return level === 'CRITICAL' ? 'danger' : level === 'WARN' ? 'warn' : 'secondary';
    }

    static refundSeverity(state: GroupBuyRefundState): GroupBuyTagSeverity {
        if (state === 'DONE') return 'success';
        if (state === 'DELAYED') return 'danger';
        if (state === 'AWAITING_APPROVAL') return 'warn';
        if (state === 'MANUAL') return 'secondary';
        return 'info';
    }

    /** 원 금액 글자(천 단위 쉼표) — 단위는 문구가 붙인다 */
    static won(amount: number | null | undefined, locale: string): string {
        return amount === null || amount === undefined ? '—' : amount.toLocaleString(locale);
    }

    /** 사유 글자(앞뒤 공백 떼고 공백 하나로) */
    static reasonText(text: string): string {
        return text.trim().replace(/\s+/g, ' ');
    }

    static reasonOk(text: string): boolean {
        const length = Array.from(UtilGroupBuy.reasonText(text)).length;
        return length >= UtilGroupBuy.REASON.min && length <= UtilGroupBuy.REASON.max;
    }

    /** 송장번호 정리 — 공백·하이픈을 뗀다(서버 `FulfillmentService` 와 같다, 결정 11) */
    static trackingText(text: string): string {
        return text.replace(/[\s-]/g, '');
    }

    /** 송장번호 모양 — 숫자·영문 6~30자 */
    static trackingOk(text: string): boolean {
        return /^[0-9A-Za-z]{6,30}$/.test(UtilGroupBuy.trackingText(text));
    }

    /** 택배사 조회 주소 템플릿 — `https://` 로 시작하고 `{no}` 가 한 번(결정 11) */
    static carrierTemplateOk(template: string): boolean {
        return template.startsWith('https://') && template.split('{no}').length === 2;
    }

    /**
     * 사업자등록번호 체크섬(국세청 규칙 — 화면 편의, 판정은 서버). 하이픈은 있어도 없어도 된다.
     * 가중치 1,3,7,1,3,7,1,3,5 를 곱해 더하고 9번째 자리 × 5 의 십의 자리를 더한 뒤, (10 − 합 % 10) % 10 이 마지막 자리여야 한다.
     */
    static businessNoOk(text: string): boolean {
        const digits = text.replace(/-/g, '');
        if (!/^\d{10}$/.test(digits)) return false;
        const d = Array.from(digits, Number);
        const weights = [1, 3, 7, 1, 3, 7, 1, 3, 5];
        let sum = 0;
        weights.forEach((w, i) => (sum += d[i]! * w));
        sum += Math.floor((d[8]! * 5) / 10);
        return (10 - (sum % 10)) % 10 === d[9];
    }

    /** 사업자등록번호를 `000-00-00000` 으로 */
    static businessNoFormat(text: string): string {
        const digits = text.replace(/\D/g, '').slice(0, 10);
        if (digits.length <= 3) return digits;
        if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
        return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
    }

    /**
     * 등록 화면 문구 경고 — 법보다 넓게 막는 문구(「개봉하면 반품 불가」), 「재판매 금지·리셀」(공정위 2023 시정), 「최저가」(표시·광고), 「한정」(시스템은 「총 N개」만).
     * 서버는 막지 않는다(경고만) — 화면이 미리 알린다. HTML 은 태그를 떼고 본다.
     */
    static phraseWarnings(...texts: string[]): GroupBuyPhraseWarning[] {
        const text = texts.join(' ').replace(/<[^>]*>/g, ' ');
        const out: GroupBuyPhraseWarning[] = [];
        if (/개봉[^.。\n]{0,20}(반품|교환|환불)[^.。\n]{0,10}(불가|안\s?돼|안\s?됩|못)/.test(text)) out.push('OPEN_NO_RETURN');
        if (/재판매|리셀/.test(text)) out.push('RESALE');
        if (/최저가/.test(text)) out.push('LOWEST');
        if (/한정/.test(text)) out.push('LIMITED');
        return out;
    }

    /**
     * 반품 줄에 고를 수 있는 처리 방법(결정 15) — 다시 판매는 공구가 모집 중이고 줄 사유가 단순 변심일 때만 보인다.
     */
    static dispositionsFor(reason: GroupBuyReasonCode, resellAllowed: boolean): GroupBuyDisposition[] {
        const base: GroupBuyDisposition[] = ['DISCARD', 'RETURN_TO_SUPPLIER', 'KEEP'];
        return resellAllowed && reason === 'CHANGE_OF_MIND' ? ['RESELL', ...base] : base;
    }

    /** 0 이상 정수 글자 → 수. 비었거나 정수가 아니면 null */
    static int(text: string | number | null | undefined): number | null {
        if (typeof text === 'number') return Number.isInteger(text) ? text : null;
        const cleaned = (text ?? '').replace(/,/g, '').trim();
        return /^\d{1,12}$/.test(cleaned) ? Number(cleaned) : null;
    }

    /** 남은 수량이 적은가(남은 10% 이하 또는 5개 이하 — 회원 목록 표시와 같은 기준) */
    static lowStock(max: number, current: number, reserved: number): boolean {
        const left = Math.max(0, max - current - reserved);
        return left > 0 && (left <= 5 || left <= max * 0.1);
    }

    /** 오늘(브라우저 시간과 무관하게 한국 날짜) `YYYY-MM-DD` */
    static kstToday(now: number = Date.now()): string {
        return new Date(now + 9 * 3_600_000).toISOString().slice(0, 10);
    }

    // ── 상품정보제공고시(결정 87) — 판정은 서버(모집 시작 `409 GROUP_BUY_NOTICE_INCOMPLETE`), 화면은 같은 규칙으로 미리 센다 ──

    static noticeItemValue(values: readonly NoticeItemValue[], itemKey: string): NoticeItemValue | undefined {
        return values.find((v) => v.itemKey === itemKey);
    }

    /** 고시 항목 한 칸이 비었나 — 「제공 불가」면 사유가, 아니면 값이 있어야 한다(원칙 3) */
    static noticeValueEmpty(def: NoticeItemDef, value: NoticeItemValue | undefined): boolean {
        if (value?.unavailable) return !value.unavailableReason.trim();
        return def.required && !(value?.value ?? '').trim();
    }

    /** 양식 한 장의 빈 항목 키(카탈로그 항목 차례). 판매자 추가 항목은 세지 않는다 */
    static noticeEmptyKeys(values: readonly NoticeItemValue[], items: readonly NoticeItemDef[]): string[] {
        return items.filter((def) => UtilGroupBuy.noticeValueEmpty(def, UtilGroupBuy.noticeItemValue(values, def.key))).map((def) => def.key);
    }

    // ── 옵션 축·단품(결정 88) ──

    /** MAIN 단품 이름 — 축 값 이름을 축 차례로 잇는다(축이 없으면 빈 글자) */
    static skuMainName(valueNames: readonly string[]): string {
        return valueNames.map((v) => v.trim()).join(UtilGroupBuy.SKU_NAME_SEPARATOR);
    }

    /** 축마다 값 목록 → 모든 조합(축 차례, 앞 축이 바깥). 축이 없으면 빈 조합 하나 */
    static skuCombinations<T>(axes: readonly (readonly T[])[]): T[][] {
        return axes.reduce<T[][]>((acc, values) => acc.flatMap((combo) => values.map((v) => [...combo, v])), [[]]);
    }

    /** 조합 수(축이 없으면 1) */
    static skuComboCount(valueCounts: readonly number[]): number {
        return valueCounts.reduce((n, c) => n * c, 1);
    }

    /** 이름 겹침 찾기 — 앞 줄과 같은 이름(공백 무시)이면 그 차례들 */
    static repeatedNames(names: readonly string[]): number[] {
        const seen = new Set<string>();
        const out: number[] = [];
        names.forEach((name, i) => {
            const key = UtilGroupBuy.noticeNameKey(name);
            if (!key) return;
            if (seen.has(key)) out.push(i);
            seen.add(key);
        });
        return out;
    }

    /**
     * 모집 시작 때 막힐 것 — 서버 `extras` 와 같은 모양. 양식 없음 · 빈 항목 · 어느 양식에도 들지 않은 단품
     * (추가 구성상품은 늘 자기 양식이 있어야 하고, MAIN 단품은 기본 양식이 없을 때만).
     * `skus` 는 고시 대상 단품 — 판매하는 단품 중 이름이 있는 것(축 없는 단품 하나는 늘 기본 양식이라 빼도 된다).
     */
    static noticeProblems(notices: readonly Pick<GroupBuyNotice, 'categoryCode' | 'title' | 'skuNames' | 'values'>[], catalog: NoticeCatalog, skus: readonly { name: string; kind: GroupBuySkuKind }[]): GroupBuyNoticeIncomplete {
        const problems = notices
            .map((n, index) => {
                const items = catalog.categories.find((c) => c.code === n.categoryCode)?.items ?? [];
                return { index, title: n.title, categoryCode: n.categoryCode, missingKeys: UtilGroupBuy.noticeEmptyKeys(n.values, items) };
            })
            .filter((p) => p.missingKeys.length > 0);
        const hasBase = notices.some((n) => n.skuNames.length === 0);
        const assigned = new Set(notices.flatMap((n) => n.skuNames));
        const unassignedSkus = skus.filter((s) => !assigned.has(s.name) && (s.kind === 'ADDON' || (notices.length > 0 && !hasBase))).map((s) => s.name);
        return { noNotice: notices.length === 0, notices: problems, unassignedSkus };
    }

    static noticeComplete(problems: GroupBuyNoticeIncomplete): boolean {
        return !problems.noNotice && problems.notices.length === 0 && problems.unassignedSkus.length === 0;
    }

    /** 「상세페이지 참조」뿐인 값 — 막지 않고 경고(원칙 3 취지: 항목마다 직접 적기). 참조 문구를 떼면 거의 남는 글이 없을 때 */
    static noticeRefersDetail(text: string): boolean {
        const pattern = /상세\s*(페이지|설명|정보|이미지)?\s*(참조|참고)/g;
        if (!pattern.test(text)) return false;
        return text.replace(pattern, '').replace(/[\s.,()·~-]/g, '').length < 6;
    }

    /** 품목 항목에 맞춘 값 줄 — 같은 키의 이전 값은 살린다(품목을 바꿀 때) */
    static noticeValuesFor(items: readonly NoticeItemDef[], previous: readonly NoticeItemValue[] = []): NoticeItemValue[] {
        return items.map((def) => {
            const before = UtilGroupBuy.noticeItemValue(previous, def.key);
            return before ? { ...before } : { itemKey: def.key, value: '', unavailable: false, unavailableReason: '' };
        });
    }

    /** 이름 비교 — 공백을 무시한다(서버 규칙) */
    static noticeNameKey(text: string): string {
        return text.replace(/\s/g, '');
    }

    /** 판매자 추가 항목 이름이 그 양식의 고시 항목 이름과 같은가 — 「크기, 무게」처럼 쉼표로 나뉜 각 이름도 막는다(서버 규칙) */
    static noticeCustomNameTaken(name: string, items: readonly NoticeItemDef[]): boolean {
        const key = UtilGroupBuy.noticeNameKey(name);
        if (!key) return false;
        return items.some((def) => [def.name, ...def.name.split(',')].some((part) => UtilGroupBuy.noticeNameKey(part) === key));
    }

    /** 판매자 추가 항목 k 번째 이름이 같은 양식의 앞선 줄과 겹치나 */
    static noticeCustomNameRepeated(customs: readonly Pick<NoticeCustomItem, 'name'>[], k: number): boolean {
        const key = UtilGroupBuy.noticeNameKey(customs[k]?.name ?? '');
        return !!key && customs.slice(0, k).some((c) => UtilGroupBuy.noticeNameKey(c.name) === key);
    }
}
