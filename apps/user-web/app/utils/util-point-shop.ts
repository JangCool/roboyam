import type { AuthorDecoration, PointShopItemKind, PointShopItemView, ProfileBorderStyle } from '~/composables/pointshop/api/usePointShopApi.types';
import type { EquipSlot, MyEquipped, MyItemRefundOutcome, MyItemRefundRequested, MyItemView } from '~/composables/pointshop/api/useMyItemsApi.types';

/**
 * 포인트 상점 규칙 — 입구 주소(셸의 헤더 프로필 메뉴·햄버거), 프로필 테두리 팔레트, 판매·보유·환불 판정을 미리 보이는 순수 함수. 상태도 요청도 없다.
 * 판정은 서버다(`PointShopPurchaseService`·`PointShopRefundService`·`UserItemService` 후보 — `docs/features/commerce-stage1-contract.md` B절). 화면은 미리 알리고 단추를 끈다.
 * 화면 이름은 「포인트 상점」(서비스 용어 `POINT_SHOP`) — 공동구매(주문 내역)와 같은 메뉴로 묶지 않는다.
 */

/** 프로필 테두리 하나 — 그라데이션 고리(바탕색 틈 하나 + 이 그라데이션, 무지개는 conic)와 이름 문구 키 */
export interface ProfileBorderDef {
    /** CSS `background` 값 */
    gradient: string;
    /** 이름 문구 키(`pointShop.border_*`) */
    nameKey: string;
}

/**
 * 프로필 테두리 팔레트 — 키는 서버 CHECK(`point_shop_items.border_style`)와 같은 목록이다.
 * ⚠ admin-web 사본(`apps/admin-web/app/utils/util-point-shop.ts`)과 **값이 같아야 한다** — 관리자 미리보기와 회원 화면의 고리가 어긋나지 않게. 두 앱이 같이 쓰게 되면 `apps-packages` 로 옮길지 정한다(계약 「미확정」).
 * 테두리 색을 적는 곳은 이 표 한 곳이다(그리는 곳은 `ProfileBorderAvatar`). 두 테마에서 읽히도록 고리와 동그라미 사이에 바탕색 틈을 둔다.
 */
const BORDERS: Readonly<Record<ProfileBorderStyle, ProfileBorderDef>> = Object.freeze({
    GOLD: { gradient: 'linear-gradient(135deg,#f6d365,#d4a017,#fff1a8,#b8860b)', nameKey: 'pointShop.border_GOLD' },
    SILVER: { gradient: 'linear-gradient(135deg,#f3f4f6,#9ca3af,#ffffff,#6b7280)', nameKey: 'pointShop.border_SILVER' },
    ROSE: { gradient: 'linear-gradient(135deg,#fda4af,#e11d48)', nameKey: 'pointShop.border_ROSE' },
    OCEAN: { gradient: 'linear-gradient(135deg,#67e8f9,#2563eb)', nameKey: 'pointShop.border_OCEAN' },
    FOREST: { gradient: 'linear-gradient(135deg,#86efac,#15803d)', nameKey: 'pointShop.border_FOREST' },
    NIGHT: { gradient: 'linear-gradient(135deg,#a78bfa,#1e1b4b)', nameKey: 'pointShop.border_NIGHT' },
    RAINBOW: { gradient: 'conic-gradient(#f43f5e,#f59e0b,#84cc16,#06b6d4,#8b5cf6,#f43f5e)', nameKey: 'pointShop.border_RAINBOW' },
});

/** 목록 거르기 값 — 전체(`ALL`)는 `kind` 를 보내지 않는다 */
export type PointShopKindFilter = 'ALL' | PointShopItemKind;

/** 상세 화면에서 살 수 없는 까닭 — 판매 예정 · 판매 끝 · 품절 · 이미 가짐(영구). 포인트 모자람(`INSUFFICIENT`)은 구매 확인 대화상자가 알린다 */
export type PointShopBlockReason = 'UPCOMING' | 'ENDED' | 'SOLD_OUT' | 'OWNED' | 'INSUFFICIENT';

const DAY_MS = 86_400_000;

/** 거르기 순서 — 아바타(`PROFILE_AVATAR`)는 품목 목록이 아니라 `GET /api/avatars` 로 그린다(목록 화면이 가른다) */
const KINDS: readonly PointShopItemKind[] = ['NICKNAME_COLOR', 'PROFILE_BORDER', 'PROFILE_AVATAR', 'NICKNAME_CHANGE_TICKET'];

export default class UtilPointShop {
    /** 포인트 상점 목록 */
    static readonly PATH = '/point-shop';

    /** 내 아이템 */
    static readonly MY_ITEMS_PATH = '/me/items';

    /** 프로필 테두리 팔레트(키 → 그라데이션·이름 키) */
    static readonly BORDERS = BORDERS;

    /** 팔레트 키 — 보여 주는 순서 */
    static readonly BORDER_KEYS: readonly ProfileBorderStyle[] = Object.freeze(Object.keys(BORDERS) as ProfileBorderStyle[]);

    /** 종류 거르기 순서(전체 다음) */
    static readonly KINDS = KINDS;

    /** 판매 끝이 이만큼(일) 안으로 다가오면 「~M월 D일까지」를 보인다 */
    static readonly ENDING_SOON_DAYS = 7;

    /**
     * 품목 상세 주소
     * @param {string} id - 품목 ID.
     * @returns {string} `/point-shop/{id}`.
     */
    static itemPath(id: string): string {
        return `${UtilPointShop.PATH}/${encodeURIComponent(id)}`;
    }

    /**
     * 목록 주소 — 종류를 주면 그 거르기로 연다(프로필 설정의 「변경권 사기」 링크)
     * @param {PointShopItemKind} [kind] - 거를 종류.
     * @returns {string} `/point-shop` 또는 `/point-shop?kind=…`.
     */
    static listPath(kind?: PointShopItemKind): string {
        return kind ? `${UtilPointShop.PATH}?kind=${kind}` : UtilPointShop.PATH;
    }

    /**
     * 주소의 `?kind=` 를 거르기 값으로 — 모르는 값이면 전체
     * @param {unknown} value - `route.query.kind`.
     * @returns {PointShopKindFilter} 거르기 값.
     */
    static parseKind(value: unknown): PointShopKindFilter {
        return typeof value === 'string' && (KINDS as readonly string[]).includes(value) ? (value as PointShopItemKind) : 'ALL';
    }

    /**
     * 테두리 팔레트 키인가
     * @param {unknown} value - 서버가 준 값.
     * @returns {boolean} 아는 키이면 true(모르는 키는 테두리 없이 그린다).
     */
    static isBorder(value: unknown): value is ProfileBorderStyle {
        return typeof value === 'string' && Object.prototype.hasOwnProperty.call(BORDERS, value);
    }

    /**
     * 테두리 하나의 정의
     * @param {unknown} value - 팔레트 키.
     * @returns {ProfileBorderDef | null} 모르는 키·빈 값이면 null.
     */
    static border(value: unknown): ProfileBorderDef | null {
        return UtilPointShop.isBorder(value) ? BORDERS[value] : null;
    }

    /**
     * 품목 종류가 장착하는 자리
     * @param {string} kind - 품목 종류.
     * @returns {EquipSlot | null} 닉네임 색·프로필 테두리. 장착하지 않는 종류(변경권)는 null.
     */
    static slotOf(kind: string): EquipSlot | null {
        if (kind === 'NICKNAME_COLOR') return 'NICKNAME_COLOR';
        if (kind === 'PROFILE_BORDER') return 'PROFILE_BORDER';
        return null;
    }

    /**
     * 장착 응답을 작성자 장식 모양으로
     * @param {MyEquipped} equipped - 두 자리의 지금 장착.
     * @returns {AuthorDecoration} 닉네임 색·테두리 키(없으면 null).
     */
    static decorationOf(equipped: MyEquipped): AuthorDecoration {
        return { nicknameColor: equipped.nicknameColor?.color ?? null, profileBorder: equipped.profileBorder?.borderStyle ?? null };
    }

    /**
     * 환불 요청의 응답이 「환불 신청」(정지 중 202)인가 — `useApiClient` 는 상태 코드를 돌려주지 않아 모양(`requestId`)으로 가른다
     * @param {MyItemRefundOutcome} outcome - 응답.
     * @returns {boolean} 신청이면 true(바로 환불이면 false).
     */
    static isRefundRequested(outcome: MyItemRefundOutcome): outcome is MyItemRefundRequested {
        return typeof (outcome as Partial<MyItemRefundRequested>).requestId === 'string';
    }

    /**
     * 환불 신청을 기다리는 중인가 — 그동안 장착·아바타 고르기·환불이 막힌다(서버 `409 POINT_SHOP_REFUND_PENDING`)
     * @param {MyItemView} row - 보유 줄.
     * @returns {boolean} 대기 중이면 true.
     */
    static refundPending(row: MyItemView): boolean {
        return row.refundRequest?.status === 'PENDING';
    }

    /**
     * 이름의 첫 글자 — 프로필 그림 기능이 없어 이니셜 동그라미에 쓴다
     * @param {string | null | undefined} name - 닉네임.
     * @returns {string} 첫 글자(없으면 빈 글자).
     */
    static initial(name: string | null | undefined): string {
        return Array.from(name ?? '')[0] ?? '';
    }

    /**
     * 지금부터 그 시각까지 남은 날 수(올림) — 기간제 「N일 남음」
     * @param {string | null | undefined} iso - 끝 시각.
     * @param {number} [now] - 지금(ms).
     * @returns {number | null} 남은 날(지났으면 0). 시각이 없으면 null.
     */
    static daysLeft(iso: string | null | undefined, now: number = Date.now()): number | null {
        if (!iso) return null;
        const end = Date.parse(iso);
        if (Number.isNaN(end)) return null;
        return Math.max(0, Math.ceil((end - now) / DAY_MS));
    }

    /**
     * 판매 끝이 다가왔는가 — 판매 중이고 끝이 `ENDING_SOON_DAYS` 안
     * @param {PointShopItemView} item - 품목.
     * @param {number} [now] - 지금(ms).
     * @returns {boolean} 「~M월 D일까지」를 보일 때 true.
     */
    static endingSoon(item: PointShopItemView, now: number = Date.now()): boolean {
        if (item.saleState !== 'ON_SALE' || !item.saleEndAt) return false;
        const left = UtilPointShop.daysLeft(item.saleEndAt, now);
        return left !== null && left <= UtilPointShop.ENDING_SOON_DAYS;
    }

    /**
     * 상세 화면에서 지금 살 수 없는 까닭(미리 알림 — 판정은 서버)
     * @param {PointShopItemView} item - 품목.
     * @param {number | null} balance - 내 포인트(모르면 null — 모자람은 보지 않는다).
     * @returns {PointShopBlockReason | null} 까닭. 살 수 있으면 null.
     */
    static blockReason(item: PointShopItemView, balance: number | null): PointShopBlockReason | null {
        if (item.saleState === 'UPCOMING') return 'UPCOMING';
        if (item.saleState === 'ENDED') return 'ENDED';
        if (item.saleState === 'SOLD_OUT') return 'SOLD_OUT';
        if (item.usageType === 'PERMANENT' && item.mine.owned) return 'OWNED';
        if (balance !== null && balance < item.price) return 'INSUFFICIENT';
        return null;
    }

    /**
     * 기간제를 지금 사면 기간이 어디서 시작하는가 — 가진 기간이 남아 있으면 그 끝에서 이어 붙는다
     * @param {PointShopItemView} item - 품목.
     * @param {number} [now] - 지금(ms).
     * @returns {string | null} 이어 붙을 끝(ISO). 지금부터 시작이면 null.
     */
    static periodContinuesFrom(item: PointShopItemView, now: number = Date.now()): string | null {
        if (item.usageType !== 'PERIOD' || !item.mine.periodEndsAt) return null;
        const end = Date.parse(item.mine.periodEndsAt);
        return !Number.isNaN(end) && end > now ? item.mine.periodEndsAt : null;
    }

    /**
     * 보유 목록을 화면 구획으로 나눈다 — 장착하는 자리별(닉네임 색·프로필 테두리) · 프로필 아바타(쓰는 중이 위) · 소모형(품목별 카드) · 회수됨.
     * 자리별 목록은 장착 중 → 기간이 남은 것 → 기간 끝 차례, 같은 차례면 최근에 산 것이 위다.
     * @param {MyItemView[]} items - `GET /api/me/items` 의 줄.
     * @returns 구획별 줄.
     */
    static group(items: MyItemView[]): {
        colors: MyItemView[];
        borders: MyItemView[];
        avatars: MyItemView[];
        tickets: { itemId: string; name: string; nameEn: string | null; rows: MyItemView[]; ownedCount: number }[];
        revoked: MyItemView[];
        others: MyItemView[];
    } {
        const rank = (row: MyItemView) => (row.equipped ? 0 : row.expired ? 2 : 1);
        const order = (a: MyItemView, b: MyItemView) => rank(a) - rank(b) || Date.parse(b.purchasedAt) - Date.parse(a.purchasedAt);
        const live = items.filter((row) => row.status !== 'REVOKED');
        const ticketMap = new Map<string, { itemId: string; name: string; nameEn: string | null; rows: MyItemView[]; ownedCount: number }>();
        for (const row of live.filter((r) => r.usageType === 'CONSUMABLE')) {
            const group = ticketMap.get(row.itemId) ?? { itemId: row.itemId, name: row.name, nameEn: row.nameEn, rows: [], ownedCount: 0 };
            group.rows.push(row);
            // 환불 신청을 기다리는 장은 쓸 수 없어 장수에서 뺀다(서버의 변경권 수와 같다)
            if (row.status === 'OWNED' && !UtilPointShop.refundPending(row)) group.ownedCount++;
            ticketMap.set(row.itemId, group);
        }
        for (const group of ticketMap.values()) group.rows.sort((a, b) => Date.parse(b.purchasedAt) - Date.parse(a.purchasedAt));
        const wearable = live.filter((r) => r.usageType !== 'CONSUMABLE');
        return {
            colors: wearable.filter((r) => r.kind === 'NICKNAME_COLOR').sort(order),
            borders: wearable.filter((r) => r.kind === 'PROFILE_BORDER').sort(order),
            avatars: wearable.filter((r) => r.kind === 'PROFILE_AVATAR').sort((a, b) => Number(b.inUse) - Number(a.inUse) || Date.parse(b.purchasedAt) - Date.parse(a.purchasedAt)),
            tickets: [...ticketMap.values()],
            revoked: items.filter((row) => row.status === 'REVOKED').sort((a, b) => Date.parse(b.purchasedAt) - Date.parse(a.purchasedAt)),
            others: wearable.filter((r) => r.kind !== 'NICKNAME_COLOR' && r.kind !== 'PROFILE_BORDER' && r.kind !== 'PROFILE_AVATAR'),
        };
    }
}
