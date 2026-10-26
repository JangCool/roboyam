import type {
    PointShopAdminItem,
    PointShopBorderStyle,
    PointShopItemField,
    PointShopItemKind,
    PointShopItemSaveRequest,
    PointShopSaleState,
    PointShopUsageType,
} from '~/composables/pointshop/api/usePointShopAdminApi.types';
import { POINT_SHOP_BORDER_STYLES, POINT_SHOP_CREATABLE_KINDS, POINT_SHOP_ITEM_KINDS } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilColor from '~/utils/util-color';

/**
 * 포인트 상점 관리 화면의 순수 규칙 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 규칙 이름(서버 단계에 만든다 — 계약 B절): 품목 칸은 `PointShopCatalogService`(+ `point_shop_items` CHECK), 예외 환불 사유는 `PointShopRefundService`, 환불 기한은 `point_shop_settings` CHECK(1~30).
 *
 * **테두리 팔레트**(`BORDER_GRADIENTS`) — 키는 서버 CHECK 와 같은 일곱 개(코드 고정). 그림은 「바탕색 틈 하나 + 그라데이션 고리」(무지개만 conic)이고 두 테마에서 같은 모양이다.
 * ⚠ **user-web `apps/user-web/app/utils/util-point-shop.ts` 사본과 같아야 한다**(키·그라데이션 값) — 다르면 관리자 미리보기와 공개 화면의 테두리가 어긋난다.
 */

/** 품목 칸 하나의 입력 문제 */
export type PointShopItemProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE' | 'CODE_FORMAT' | 'TOO_LONG' | 'COLOR_FORMAT' | 'END_BEFORE_START' | 'NOT_ALLOWED';

/** 품목 편집 입력값 — 숫자 칸은 글자로 들고 저장할 때 바꾼다(`InputNumber` 는 범위 밖 값을 말없이 고친다). 종류를 아직 안 골랐으면 `kind` 가 null */
export interface PointShopItemForm {
    kind: PointShopItemKind | null;
    code: string;
    usageType: PointShopUsageType | null;
    name: string;
    nameEn: string;
    description: string;
    descriptionEn: string;
    /** `#rrggbb` 글자(닉네임 색만) */
    color: string;
    borderStyle: PointShopBorderStyle | null;
    /** 아바타 카탈로그 ID(아바타만, 읽기 전용) — 그 밖은 '' */
    avatarId: string;
    pricePoint: string;
    durationDays: string;
    /** 빈칸 = 무제한 */
    stock: string;
    /** 판매 시작·끝(ISO). 빈칸 = 제한 없음 */
    saleStartAt: string;
    saleEndAt: string;
    active: boolean;
    sortOrder: string;
}

/** 예외 환불 사유의 입력 문제 */
export type PointShopReasonProblem = 'REQUIRED' | 'TOO_SHORT' | 'TOO_LONG';

/** 환불 기한 칸의 입력 문제 */
export type PointShopRefundDaysProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';

/** 종류마다 고를 수 있는 사용 방식(계약 B절 — 변경권은 소모형만, 색·테두리는 영구·기간제) */
const USAGES_BY_KIND: Record<PointShopItemKind, readonly PointShopUsageType[]> = {
    NICKNAME_CHANGE_TICKET: ['CONSUMABLE'],
    NICKNAME_COLOR: ['PERMANENT', 'PERIOD'],
    PROFILE_BORDER: ['PERMANENT', 'PERIOD'],
    PROFILE_AVATAR: ['PERMANENT'],
};

/**
 * 테두리 그라데이션 — ⚠ user-web `util-point-shop.ts` 사본과 같은 값이어야 한다.
 * 고리는 이 값을 바탕으로 칠한 원이고, 안쪽에 바탕색 틈(한 겹)을 두고 이니셜 동그라미를 얹는다(`PointShopBorderPreview`).
 */
const BORDER_GRADIENTS: Readonly<Record<PointShopBorderStyle, string>> = Object.freeze({
    GOLD: 'linear-gradient(135deg,#f6d365,#d4a017,#fff1a8,#b8860b)',
    SILVER: 'linear-gradient(135deg,#f3f4f6,#9ca3af,#ffffff,#6b7280)',
    ROSE: 'linear-gradient(135deg,#fda4af,#e11d48)',
    OCEAN: 'linear-gradient(135deg,#67e8f9,#2563eb)',
    FOREST: 'linear-gradient(135deg,#86efac,#15803d)',
    NIGHT: 'linear-gradient(135deg,#a78bfa,#1e1b4b)',
    RAINBOW: 'conic-gradient(#f43f5e,#f59e0b,#84cc16,#06b6d4,#8b5cf6,#f43f5e)',
});

const CODE_PATTERN = /^[a-z0-9-]{3,40}$/;

export default class UtilPointShop {
    /** 모든 종류(구매 내역 거르기) */
    static readonly KINDS = POINT_SHOP_ITEM_KINDS;
    /** [품목 추가]로 만들 수 있는 종류(아바타 제외 — 「품목」 탭 거르기·종류 고르기) */
    static readonly CREATABLE_KINDS = POINT_SHOP_CREATABLE_KINDS;
    static readonly BORDER_STYLES = POINT_SHOP_BORDER_STYLES;
    static readonly BORDER_GRADIENTS = BORDER_GRADIENTS;
    /** 가격 1~100,000 P */
    static readonly PRICE = { min: 1, max: 100_000 } as const;
    /** 기간제 기간 1~365 일 */
    static readonly DURATION = { min: 1, max: 365 } as const;
    /** 이름(·영문명) 최대 글자 */
    static readonly NAME_MAX = 40;
    /** 설명(·영문 설명) 최대 글자 */
    static readonly DESCRIPTION_MAX = 500;
    /** 코드 글자 수 */
    static readonly CODE = { min: 3, max: 40 } as const;
    /** 예외 환불 사유 5~200자 */
    static readonly REASON = { min: 5, max: 200 } as const;
    /** 셀프 환불 기한 1~30 일 */
    static readonly REFUND_DAYS = { min: 1, max: 30 } as const;

    /** 종류가 고를 수 있는 사용 방식 */
    static usagesOf(kind: PointShopItemKind): readonly PointShopUsageType[] {
        return USAGES_BY_KIND[kind];
    }

    /** 이 종류·사용 방식에서 보이는(보내는) 칸인가 — 색은 닉네임 색만, 테두리는 프로필 테두리만, 기간은 기간제만 */
    static hasColor(kind: PointShopItemKind | null): boolean {
        return kind === 'NICKNAME_COLOR';
    }
    static hasBorder(kind: PointShopItemKind | null): boolean {
        return kind === 'PROFILE_BORDER';
    }
    /** 아바타 품목인가 — 한 줄 편집은 이름·영문명·가격·순서·사용 여부만 */
    static isAvatar(kind: PointShopItemKind | null): boolean {
        return kind === 'PROFILE_AVATAR';
    }

    /**
     * 아바타 그림 주소 — 두 앱의 `public/avatars/roboyam/<id>.svg`(배포 자산). `<img>` 로만 그린다(`v-html`·인라인 SVG 금지).
     * @param {string} avatarId - 카탈로그 아바타 ID.
     * @returns {string} 그림 주소.
     */
    static avatarUrl(avatarId: string): string {
        return `/avatars/roboyam/${encodeURIComponent(avatarId)}.svg`;
    }

    static hasDuration(usage: PointShopUsageType | null): boolean {
        return usage === 'PERIOD';
    }

    /** 테두리 키의 그라데이션 — 모르는 키면 null */
    static borderGradient(key: string | null | undefined): string | null {
        return key && Object.prototype.hasOwnProperty.call(BORDER_GRADIENTS, key) ? BORDER_GRADIENTS[key as PointShopBorderStyle] : null;
    }

    /**
     * 판매 상태 — 시작 전이면 예정, 끝났으면 끝, 남은 재고 0 이면 품절, 아니면 판매 중(사용 여부 `active` 는 따로 보인다).
     * @param {Pick<PointShopAdminItem, 'saleStartAt' | 'saleEndAt' | 'stock'>} item - 품목.
     * @param {number} [now] - 기준 시각(ms).
     * @returns {PointShopSaleState} 판매 상태.
     */
    static saleState(item: Pick<PointShopAdminItem, 'saleStartAt' | 'saleEndAt' | 'stock'>, now: number = Date.now()): PointShopSaleState {
        if (item.saleStartAt && now < Date.parse(item.saleStartAt)) return 'UPCOMING';
        if (item.saleEndAt && now >= Date.parse(item.saleEndAt)) return 'ENDED';
        if (item.stock === 0) return 'SOLD_OUT';
        return 'ON_SALE';
    }

    /** 판매 상태 딱지 색 */
    static saleSeverity(state: PointShopSaleState): 'success' | 'info' | 'secondary' | 'danger' {
        if (state === 'ON_SALE') return 'success';
        if (state === 'UPCOMING') return 'info';
        if (state === 'SOLD_OUT') return 'danger';
        return 'secondary';
    }

    /** 글자 수 — 유니코드 한 글자씩(서버도 코드포인트로 센다) */
    static length(text: string): number {
        return Array.from(text).length;
    }

    /**
     * 0 이상 정수로 읽는다 — 앞뒤 공백·천 단위 쉼표는 허용한다.
     * @returns 정수. 비었거나 정수가 아니면 null.
     */
    static parseInt(text: string): number | null {
        const cleaned = text.trim().replace(/,/g, '');
        return /^\d{1,9}$/.test(cleaned) ? Number(cleaned) : null;
    }

    // ── 품목 폼 ─

    /** 새 품목의 빈 폼 — 종류는 아직 안 골랐다 */
    static emptyForm(): PointShopItemForm {
        return {
            kind: null,
            code: '',
            usageType: null,
            name: '',
            nameEn: '',
            description: '',
            descriptionEn: '',
            color: '',
            borderStyle: null,
            avatarId: '',
            pricePoint: '',
            durationDays: '',
            stock: '',
            saleStartAt: '',
            saleEndAt: '',
            active: true,
            sortOrder: '',
        };
    }

    /** 서버 품목 → 입력값 */
    static formOf(item: PointShopAdminItem): PointShopItemForm {
        return {
            kind: item.kind,
            code: item.code,
            usageType: item.usageType,
            name: item.name,
            nameEn: item.nameEn ?? '',
            description: item.description,
            descriptionEn: item.descriptionEn ?? '',
            color: item.color ?? '',
            borderStyle: item.borderStyle,
            avatarId: item.avatarId ?? '',
            pricePoint: String(item.pricePoint),
            durationDays: item.durationDays === null ? '' : String(item.durationDays),
            stock: item.stock === null ? '' : String(item.stock),
            saleStartAt: item.saleStartAt ?? '',
            saleEndAt: item.saleEndAt ?? '',
            active: item.active,
            sortOrder: String(item.sortOrder),
        };
    }

    /**
     * 종류를 고르면(새 품목) 그 종류에 맞게 사용 방식을 맞춘다 — 한 가지뿐이면 그것으로, 고를 수 없는 값이면 첫 값으로. 맞지 않는 칸은 비운다.
     * @param {PointShopItemForm} form - 지금 입력값.
     * @param {PointShopItemKind} kind - 고른 종류.
     * @returns {PointShopItemForm} 새 입력값.
     */
    static withKind(form: PointShopItemForm, kind: PointShopItemKind): PointShopItemForm {
        const usages = UtilPointShop.usagesOf(kind);
        const usageType = form.usageType && usages.includes(form.usageType) ? form.usageType : usages[0]!;
        return {
            ...form,
            kind,
            usageType,
            color: UtilPointShop.hasColor(kind) ? form.color : '',
            borderStyle: UtilPointShop.hasBorder(kind) ? form.borderStyle : null,
            durationDays: UtilPointShop.hasDuration(usageType) ? form.durationDays : '',
        };
    }

    /**
     * 칸마다의 문제 — 서버와 같은 규칙(종류별 칸 규칙 포함). 보이지 않는 칸(종류에 맞지 않는 칸)은 검사하지 않는다(보낼 때 null 로 보낸다).
     * @param {PointShopItemForm} form - 입력값.
     * @param {boolean} creating - 새 품목인가(코드 형식은 만들 때만 검사 — 고칠 때는 읽기 전용).
     * @returns 칸 이름 → 문제.
     */
    static problems(form: PointShopItemForm, creating: boolean): Partial<Record<PointShopItemField, PointShopItemProblem>> {
        const out: Partial<Record<PointShopItemField, PointShopItemProblem>> = {};
        if (!form.kind) out.kind = 'REQUIRED';
        if (creating) {
            if (!form.code.trim()) out.code = 'REQUIRED';
            else if (!CODE_PATTERN.test(form.code.trim())) out.code = 'CODE_FORMAT';
        }
        if (!form.usageType) out.usageType = 'REQUIRED';
        else if (form.kind && !UtilPointShop.usagesOf(form.kind).includes(form.usageType)) out.usageType = 'NOT_ALLOWED';

        const name = form.name.trim();
        if (!name) out.name = 'REQUIRED';
        else if (UtilPointShop.length(name) > UtilPointShop.NAME_MAX) out.name = 'TOO_LONG';
        if (UtilPointShop.length(form.nameEn.trim()) > UtilPointShop.NAME_MAX) out.nameEn = 'TOO_LONG';
        if (UtilPointShop.length(form.description.trim()) > UtilPointShop.DESCRIPTION_MAX) out.description = 'TOO_LONG';
        if (UtilPointShop.length(form.descriptionEn.trim()) > UtilPointShop.DESCRIPTION_MAX) out.descriptionEn = 'TOO_LONG';

        if (UtilPointShop.hasColor(form.kind)) {
            if (!form.color.trim()) out.color = 'REQUIRED';
            else if (!UtilColor.isHex(form.color.trim())) out.color = 'COLOR_FORMAT';
        }
        if (UtilPointShop.hasBorder(form.kind) && !form.borderStyle) out.borderStyle = 'REQUIRED';

        const price = UtilPointShop.rangeProblem(form.pricePoint, UtilPointShop.PRICE.min, UtilPointShop.PRICE.max);
        if (price) out.pricePoint = price;
        if (UtilPointShop.hasDuration(form.usageType)) {
            const duration = UtilPointShop.rangeProblem(form.durationDays, UtilPointShop.DURATION.min, UtilPointShop.DURATION.max);
            if (duration) out.durationDays = duration;
        }
        if (form.stock.trim() && UtilPointShop.parseInt(form.stock) === null) out.stock = 'NOT_INTEGER';
        if (!form.sortOrder.trim()) out.sortOrder = 'REQUIRED';
        else if (UtilPointShop.parseInt(form.sortOrder) === null) out.sortOrder = 'NOT_INTEGER';

        if (form.saleStartAt && form.saleEndAt && Date.parse(form.saleEndAt) <= Date.parse(form.saleStartAt)) out.saleEndAt = 'END_BEFORE_START';
        return out;
    }

    /** 범위가 있는 정수 칸 하나의 문제 */
    static rangeProblem(text: string, min: number, max: number): 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE' | null {
        if (!text.trim()) return 'REQUIRED';
        const value = UtilPointShop.parseInt(text);
        if (value === null) return 'NOT_INTEGER';
        return value < min || value > max ? 'OUT_OF_RANGE' : null;
    }

    /**
     * 입력값 → 저장 요청. 종류에 맞지 않는 칸은 null 로 보낸다. 문제가 있으면 null.
     * @param {PointShopItemForm} form - 입력값.
     * @param {boolean} creating - 새 품목인가.
     * @returns {PointShopItemSaveRequest | null} 요청 본문.
     */
    static toRequest(form: PointShopItemForm, creating: boolean): PointShopItemSaveRequest | null {
        if (Object.keys(UtilPointShop.problems(form, creating)).length > 0 || !form.kind || !form.usageType) return null;
        const text = (value: string) => value.trim().replace(/[ \t]+/g, ' ');
        const optional = (value: string) => (text(value) ? text(value) : null);
        return {
            code: form.code.trim(),
            kind: form.kind,
            usageType: form.usageType,
            name: text(form.name),
            nameEn: optional(form.nameEn),
            description: form.description.trim(),
            descriptionEn: form.descriptionEn.trim() ? form.descriptionEn.trim() : null,
            color: UtilPointShop.hasColor(form.kind) ? form.color.trim().toLowerCase() : null,
            borderStyle: UtilPointShop.hasBorder(form.kind) ? form.borderStyle : null,
            avatarId: UtilPointShop.isAvatar(form.kind) ? form.avatarId : null,
            pricePoint: UtilPointShop.parseInt(form.pricePoint)!,
            durationDays: UtilPointShop.hasDuration(form.usageType) ? UtilPointShop.parseInt(form.durationDays) : null,
            stock: form.stock.trim() ? UtilPointShop.parseInt(form.stock) : null,
            saleStartAt: form.saleStartAt || null,
            saleEndAt: form.saleEndAt || null,
            active: form.active,
            sortOrder: UtilPointShop.parseInt(form.sortOrder)!,
        };
    }

    /**
     * 입력값이 처음 값과 다른가 — 숫자는 읽어서 비교한다(「05」와 「5」는 같다).
     * @param {PointShopItemForm} form - 지금 입력값.
     * @param {PointShopItemForm} base - 처음 값.
     * @returns {boolean} 바꾼 칸이 있으면 true.
     */
    static isDirty(form: PointShopItemForm, base: PointShopItemForm): boolean {
        const keys = Object.keys(base) as (keyof PointShopItemForm)[];
        const numeric: readonly (keyof PointShopItemForm)[] = ['pricePoint', 'durationDays', 'stock', 'sortOrder'];
        return keys.some((key) => {
            if (numeric.includes(key)) return (UtilPointShop.parseInt(form[key] as string) ?? (form[key] as string).trim()) !== (UtilPointShop.parseInt(base[key] as string) ?? (base[key] as string).trim());
            if (key === 'color') return form.color.trim().toLowerCase() !== base.color.trim().toLowerCase();
            const a = form[key];
            const b = base[key];
            return typeof a === 'string' && typeof b === 'string' ? a.trim() !== b.trim() : a !== b;
        });
    }

    /**
     * 이미 있는 품목의 표시값(닉네임 색·테두리)을 바꾸는가 — 산 사람의 장식도 바로 바뀐다는 경고에 쓴다(가격·이름은 구매 사본이라 그대로).
     * @param {PointShopItemForm} form - 지금 입력값.
     * @param {PointShopAdminItem | null} item - 고치는 품목(새 품목이면 null).
     * @returns {boolean} 바꾸면 true.
     */
    static decorationChanged(form: PointShopItemForm, item: PointShopAdminItem | null): boolean {
        if (!item) return false;
        if (item.kind === 'NICKNAME_COLOR') return form.color.trim().toLowerCase() !== (item.color ?? '').toLowerCase();
        if (item.kind === 'PROFILE_BORDER') return form.borderStyle !== item.borderStyle;
        return false;
    }

    // ── 예외 환불 사유 ─

    /** 앞뒤 공백을 떼고 연속 공백을 한 칸으로 */
    static reasonText(text: string): string {
        return text.trim().replace(/\s+/g, ' ');
    }

    static reasonProblem(text: string): PointShopReasonProblem | null {
        const value = UtilPointShop.reasonText(text);
        if (!value) return 'REQUIRED';
        const length = UtilPointShop.length(value);
        if (length < UtilPointShop.REASON.min) return 'TOO_SHORT';
        return length > UtilPointShop.REASON.max ? 'TOO_LONG' : null;
    }

    // ── 설정 ─

    static refundDaysProblem(text: string): PointShopRefundDaysProblem | null {
        return UtilPointShop.rangeProblem(text, UtilPointShop.REFUND_DAYS.min, UtilPointShop.REFUND_DAYS.max);
    }
}
