// MOCK: 공구 관리자 API(공구·수량·주문·발송·요청)의 메모리 목업 — `useGroupBuyAdminApi`·`useGroupBuyOrderApi`·`useGroupBuyRequestApi` 가 부른다.
// 서버가 붙으면 이 폴더째 지운다. 새로고침하면 처음 데이터(`groupbuy-mock-data.ts`)로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여러 개) ─
//   공통        forbidden          모든 쓰기가 403 PERMISSION_DENIED        read-forbidden    모든 목록·상세가 403
//               save-fail          모든 쓰기가 500
//   공구        gb-list-fail       목록 500                                 gb-list-empty     목록 비어 있음
//               gb-detail-fail     상세 500                                 gb-locked         DRAFT 저장이 409 GROUP_BUY_LOCKED_FIELD(그 사이 모집 시작됨)
//               gb-version         설명·이미지 저장이 409 GROUP_BUY_VERSION_CONFLICT
//               gb-round-exists    추가 차수가 409 GROUP_BUY_ROUND_EXISTS    gb-no-policy      모집 시작이 409 GROUP_BUY_SHIPPING_POLICY_MISSING
//   고시 양식   notice-incomplete  모집 시작이 늘 409 GROUP_BUY_NOTICE_INCOMPLETE(다 채운 공구면 첫 양식의 첫 항목을 빈 것으로 돌려준다)
//               notice-catalog-fail 고시 카탈로그 500
//   수량        stock-fail         수량 화면 500                            stock-floor       줄이기가 늘 409 GROUP_BUY_QTY_BELOW_FLOOR
//               movements-empty    변동 내역 비어 있음
//   주문        orders-fail        목록 500                                 orders-empty      목록 비어 있음
//               order-detail-fail  상세 500                                 ship-partial      발송·준비중·배송완료 일괄이 줄 절반 실패(부분 성공)
//               bulk-fail          일괄 처리 500                            no-large          고액 문턱을 넘는 운영 환불이 승인 대기(고액 권한 없음)
//               refund-unreconciled 운영 환불이 409 GROUP_BUY_PAYMENT_UNRECONCILED   export-not-ready  명단 내보내기 409 GROUP_BUY_EXPORT_NOT_READY
//   요청        requests-fail      목록 500                                 requests-empty    목록 비어 있음
//               request-detail-fail 상세 500                               approve-changed   승인이 409 GROUP_BUY_REFUND_AMOUNT_CHANGED(그 사이 또 바뀜)
//
// ── 목업 가정(계약에 없는 것 — 보고에 적었다) ─
//   · 모든 금액·수량·미리 보기는 서버 계산 값이라 치고 대충 맞춘 숫자다(`GroupBuyRefundCalculator` 를 흉내 내지 않는다).
//   · 「모집 시작」= DRAFT → RECRUITING(공개). 시작 시각이 미래면 회원 화면은 그때까지 「예정」 탭.
//   · 운영자 조기 마감의 `close_reason` 은 `EARLY_CLOSED`(사용자 결정 2026-10-28 — 결정 79 에 더함).
//   · 취소 요청 거절은 `ALREADY_SHIPPED` 하나뿐이고 사진이 필요 없다. 반품 거절은 그 밖의 코드 + 사진 1~3장.
//   · 고시 양식(결정 87 — 2b-2 서버 모양): 저장 `notices[{categoryCode, title, skuNames, values[고시 항목 {itemKey, value, unavailable, unavailableReason} + 추가 항목 {custom: true, itemKey: null, name, value, sortOrder}]}]`,
//     응답 `notices[{categoryCode, title, skuIds, skuNames, values, customItems[{name, value, sortOrder}]}]`. 적용 단품이 빈 양식 = 기본 양식(MAIN 단품들만).
//     축이 있으면 기본 양식은 하나까지, 기본 양식이 없으면 판매하는 MAIN 조합이 모두 어느 양식에 들어야 하고, 추가 구성상품은 늘 자기 양식(그 상품 하나만)이 있어야 한다(`unassignedSkus`).
//     저장(모든 상태) 400 — 양식 제목 빔 · 「제공 불가」인데 사유 빔 · 한 단품이 두 양식에 · 기본 양식 둘 · 추가 구성상품을 섞음(`skuNames`) · 없는 품목·단품 · 추가 항목(반쪽 줄·40자·20개·이름 겹침·고시 항목 이름).
//     모집 시작과 모집 뒤 저장 409 — 양식 없음 · 빈 항목 · 빠진 단품(추가 항목은 대상 아님). 모집 뒤 수정은 `PATCH …/content` 에 같이, 고시만 바뀌면 이력 `NOTICE`. 추가 차수는 양식을 복사(적용 단품은 새 ID).
//   · 옵션 축·단품(결정 88 — 저장은 서버 2b-2 `GroupBuyDraft` 모양): 400 `fields.axes|skus|addons`(칸마다 메시지 하나). 모집 뒤 구성 변경은 409 `GROUP_BUY_LOCKED_FIELD`.
//     판매 끈 조합은 고시 대상·공급 불가·수량 조정 대상이 아니다. 추가 입고·줄이기는 `skuId` — MAIN 은 공구 최대도 같이, ADDON 은 자기 한도만.
//     단품 공급 불가: 다른 MAIN 줄이 남은 주문만 회원 선택을 묻는다(본 상품이 안 남으면 추가 구성상품도 함께 취소 — 2b-3 확정 전 가정).

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { ApiError } from '~/composables/common/useApiClient';
import { carrierName, mockAt, mockDay, mockImage, seedGroupBuys, seedMovements, seedOrders, seedRequests, seedRevisions } from '~/composables/groupbuy/api/mock/groupbuy-mock-data';
import { mockNoticeCatalog } from '~/composables/groupbuy/api/mock/groupbuy-notice-catalog-mock';
import UtilGroupBuy from '~/utils/util-groupbuy';
import type {
    GroupBuyActions,
    GroupBuyCancelPreview,
    GroupBuyContentRequest,
    GroupBuyDelayNoticeRequest,
    GroupBuyDetail,
    GroupBuyFormOptions,
    GroupBuyImageUpload,
    GroupBuyListResponse,
    GroupBuyMovement,
    GroupBuyMovementQuery,
    GroupBuyNotice,
    GroupBuyNoticeInput,
    GroupBuySku,
    GroupBuyPage,
    GroupBuyQuery,
    GroupBuyRevision,
    GroupBuyRoundCreated,
    GroupBuyRow,
    GroupBuySaveRequest,
    GroupBuyStatus,
    GroupBuyStockAdjustRequest,
    GroupBuyStockLine,
    GroupBuyStockView,
    GroupBuySupplyFailurePreview,
    GroupBuySupplyFailureRequest,
    NoticeCatalog,
    NoticeCustomInput,
    NoticeItemValue,
} from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { GROUP_BUY_STATUSES } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type {
    GroupBuyAdminRefundRequest,
    GroupBuyAdminRefundResult,
    GroupBuyBulkResult,
    GroupBuyBulkRowResult,
    GroupBuyCarrierChoice,
    GroupBuyExportRequest,
    GroupBuyExportResult,
    GroupBuyOrderActions,
    GroupBuyOrderDetail,
    GroupBuyOrderListResponse,
    GroupBuyOrderQuery,
    GroupBuyOrderStatus,
    GroupBuyRefundPreview,
    GroupBuyRevealedAddress,
    GroupBuyShipRow,
    GroupBuyTrackingFixRequest,
} from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import { GROUP_BUY_ORDER_STATUSES } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type {
    GroupBuyApproveRequest,
    GroupBuyCompleteRequest,
    GroupBuyEvidencePhoto,
    GroupBuyExchangeShipRequest,
    GroupBuyPickupRequest,
    GroupBuyReceiveRequest,
    GroupBuyRejectRequest,
    GroupBuyRequestActions,
    GroupBuyRequestDetail,
    GroupBuyRequestListResponse,
    GroupBuyRequestQuery,
} from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import { GROUP_BUY_REQUEST_STATUSES } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';

const OPERATOR = '나(목업 운영자)';
const PRE_SHIP: readonly GroupBuyOrderStatus[] = ['JOINED', 'CONFIRMED', 'PREPARING'];
const TRACKING_RE = /^[0-9A-Za-z]{6,30}$/;

interface MockDb {
    groupBuys: GroupBuyDetail[];
    orders: GroupBuyOrderDetail[];
    requests: GroupBuyRequestDetail[];
    revisions: Map<string, GroupBuyRevision[]>;
    movements: Map<string, GroupBuyMovement[]>;
    seq: number;
}

let state: MockDb | null = null;

function db(): MockDb {
    if (state) return state;
    const groupBuys = seedGroupBuys();
    const orders = seedOrders(groupBuys);
    const requests = seedRequests(orders);
    for (const r of requests) {
        if (r.status !== 'REQUESTED' && r.status !== 'APPROVED') continue;
        const o = orders.find((x) => x.id === r.order.id);
        if (o) o.openRequest = { id: r.id, type: r.type };
    }
    // 승인 때 다시 계산한 값이 요청 때와 다른 예(결정 72)
    const changed = requests.find((r) => r.id === 'rq-2');
    if (changed) {
        changed.recalculated = {
            breakdown: { ...changed.breakdown, reshippingFee: 3000, total: 19000 },
            reasons: ['그 사이 같은 주문의 다른 줄이 취소돼 남은 상품이 무료배송 기준(50,000원) 아래가 됐어요 — 배송비 3,000원을 다시 받아요.'],
        };
    }
    state = { groupBuys, orders, requests, revisions: new Map(), movements: new Map(), seq: 1000 };
    return state;
}

function nextId(prefix: string): string {
    const d = db();
    d.seq += 1;
    return `${prefix}-${d.seq}`;
}

async function guardRead(failFlag?: string): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('read-forbidden')) throw MOCK_ERRORS.forbidden();
    if (failFlag && flags.has(failFlag)) throw MOCK_ERRORS.internal();
    return flags;
}

async function guardWrite(): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (flags.has('save-fail')) throw MOCK_ERRORS.internal();
    return flags;
}

function reasonProblem(reason: string): string | null {
    const length = Array.from(reason.trim()).length;
    if (length < 5) return '사유를 5자 이상 적어 주세요.';
    if (length > 200) return '사유는 200자까지 적을 수 있어요.';
    return null;
}

function requireReason(reason: string): void {
    const problem = reasonProblem(reason);
    if (problem) throw MOCK_ERRORS.validation({ reason: problem });
}

// ── 공구 ───────────────────────────────────────────

function findGroupBuy(id: string): GroupBuyDetail {
    const found = db().groupBuys.find((g) => g.id === id);
    if (!found) throw MOCK_ERRORS.notFound('GROUP_BUY_NOT_FOUND', '공구를 찾을 수 없어요.');
    return found;
}

function ordersOf(groupBuyId: string): GroupBuyOrderDetail[] {
    return db().orders.filter((o) => o.groupBuy.id === groupBuyId);
}

function actionsOf(g: GroupBuyDetail): GroupBuyActions {
    const rounds = db().groupBuys.filter((x) => x.roundRootId === g.roundRootId);
    const openRound = rounds.some((x) => x.status === 'DRAFT' || x.status === 'RECRUITING');
    const ended = ['CLOSING', 'CLOSED_SUCCESS', 'ORDERED', 'SHIPPING', 'COMPLETED', 'CLOSED_FAILED'].includes(g.status);
    const roundBlockedReason = !ended ? 'SOURCE_INVALID' : openRound ? 'ROUND_EXISTS' : null;
    const live = !['DRAFT', 'CANCELED', 'CLOSED_FAILED', 'COMPLETED'].includes(g.status);
    return {
        canStart: g.status === 'DRAFT',
        canCloseEarly: g.status === 'RECRUITING',
        canCancel: ['RECRUITING', 'CLOSING', 'CLOSED_SUCCESS', 'ORDERED', 'SHIPPING'].includes(g.status),
        canMarkOrdered: g.status === 'CLOSED_SUCCESS' && g.fulfillment === 'AFTER_CLOSE',
        canCreateRound: roundBlockedReason === null,
        roundBlockedReason: g.status === 'DRAFT' || g.status === 'RECRUITING' || g.status === 'CANCELED' ? 'SOURCE_INVALID' : roundBlockedReason,
        canDelayNotice: live && g.preShipOrderCount > 0,
        canSupplyFailure: live && g.skus.filter((s) => s.onSale).length > 1 && g.preShipOrderCount > 0,
        canAdjustStock: g.status === 'RECRUITING',
    };
}

function present(g: GroupBuyDetail): GroupBuyDetail {
    const rounds = db()
        .groupBuys.filter((x) => x.roundRootId === g.roundRootId)
        .sort((a, b) => a.roundNo - b.roundNo)
        .map((x) => ({ id: x.id, roundNo: x.roundNo, status: x.status, title: x.title }));
    return mockClone({ ...g, rounds, actions: actionsOf(g) });
}

function rowOf(g: GroupBuyDetail): GroupBuyRow {
    return {
        id: g.id,
        title: g.title,
        productName: g.productName,
        roundNo: g.roundNo,
        roundRootId: g.roundRootId,
        status: g.status,
        closeReason: g.closeReason,
        fulfillment: g.fulfillment,
        minQty: g.minQty,
        maxQty: g.maxQty,
        currentQty: g.currentQty,
        reservedQty: g.reservedQty,
        groupPrice: g.groupPrice,
        startAt: g.startAt,
        endAt: g.endAt,
        extendedUntil: g.extendedUntil,
        thumbnailUrl: g.images[0]?.thumbUrl ?? null,
        orderCount: ordersOf(g.id).filter((o) => o.status !== 'PENDING_PAYMENT' && o.status !== 'EXPIRED').length + (g.status === 'DRAFT' ? 0 : g.currentQty > 20 ? 15 : 0),
        updatedAt: g.updatedAt,
    };
}

function addRevision(g: GroupBuyDetail, kind: GroupBuyRevision['kind'], changedFields: string[]): void {
    const list = db().revisions.get(g.id) ?? seedRevisions(g);
    g.revisionNo += 1;
    list.unshift({
        revisionNo: g.revisionNo,
        kind,
        changedFields,
        title: g.title,
        summary: g.summary,
        description: g.description,
        imageCount: g.images.length,
        maxQty: g.maxQty,
        addedQty: g.addedQty,
        skuMax: namedSkus(g).map((s) => ({ name: s.name, kind: s.kind, maxQty: s.maxQty })),
        shipPlannedAt: g.shipPlannedAt,
        notices: mockClone(g.notices),
        operator: OPERATOR,
        createdAt: new Date().toISOString(),
    });
    db().revisions.set(g.id, list);
}

/** 판매하는 단품 중 이름이 있는 것(조합·추가 구성상품) — 축 없는 단품 하나는 공구 전체로 다룬다 */
function namedSkus(g: GroupBuyDetail): GroupBuySku[] {
    return g.skus.filter((s) => s.onSale && (s.kind === 'ADDON' || s.valueIds.length > 0));
}

function addMovement(g: GroupBuyDetail, kind: GroupBuyMovement['kind'], qty: number, skuId: string | null, reason: string | null): void {
    const list = db().movements.get(g.id) ?? seedMovements(g);
    const target = skuId ? (g.skus.find((s) => s.skuId === skuId) ?? null) : null;
    const named = target && (target.kind === 'ADDON' || target.valueIds.length > 0) ? target : null;
    list.unshift({
        id: nextId('mv'),
        kind,
        skuName: named?.name ?? null,
        skuKind: named?.kind ?? null,
        qty,
        currentAfter: g.currentQty,
        reservedAfter: g.reservedQty,
        maxAfter: g.maxQty,
        skuCurrentAfter: named?.currentQty ?? null,
        skuReservedAfter: named?.reservedQty ?? null,
        skuMaxAfter: named?.maxQty ?? null,
        orderNo: null,
        actor: OPERATOR,
        reason,
        createdAt: new Date().toISOString(),
    });
    db().movements.set(g.id, list);
}

export async function mockGroupBuyList(query: GroupBuyQuery): Promise<GroupBuyListResponse> {
    const flags = await guardRead('gb-list-fail');
    const q = query.q?.trim().toLowerCase() ?? '';
    const base = flags.has('gb-list-empty') ? [] : db().groupBuys.filter((g) => !q || g.title.toLowerCase().includes(q) || g.productName.toLowerCase().includes(q) || g.id === q);
    const counts = Object.fromEntries(GROUP_BUY_STATUSES.map((s) => [s, base.filter((g) => g.status === s).length])) as Record<GroupBuyStatus, number>;
    const filtered = base.filter((g) => !query.status || g.status === query.status).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    const start = query.page * query.size;
    return mockClone({ items: filtered.slice(start, start + query.size).map(rowOf), total: filtered.length, counts });
}

export async function mockGroupBuyDetail(id: string): Promise<GroupBuyDetail> {
    await guardRead('gb-detail-fail');
    return present(findGroupBuy(id));
}

export async function mockGroupBuyFormOptions(): Promise<GroupBuyFormOptions> {
    await guardRead();
    return {
        shippingPolicies: [
            {
                code: 'STANDARD',
                name: '기본 택배',
                current: { baseFee: 3000, freeShippingOver: 50000, returnFee: 3000, returnFeeMode: 'ONE_WAY', effectiveFrom: mockAt(-90 * 1440) },
                upcoming: { baseFee: 3500, freeShippingOver: 50000, returnFee: 3500, returnFeeMode: 'ONE_WAY', effectiveFrom: mockAt(30 * 1440) },
            },
            { code: 'BULKY', name: '대형 화물', current: { baseFee: 15000, freeShippingOver: null, returnFee: 15000, returnFeeMode: 'ROUND_TRIP', effectiveFrom: mockAt(-30 * 1440) }, upcoming: null },
            { code: 'FREE', name: '무료배송(예정)', current: null, upcoming: { baseFee: 0, freeShippingOver: 0, returnFee: 3000, returnFeeMode: 'ONE_WAY', effectiveFrom: mockAt(10 * 1440) } },
        ],
        defaultShipWithinBusinessDays: 3,
        periodMaxDays: { threshold: 14, firstCome: 30 },
    };
}

// ── 상품정보제공고시(결정 87) ─

const CATALOG = mockNoticeCatalog();

export async function mockGroupBuyNoticeCatalog(): Promise<NoticeCatalog> {
    await guardRead('notice-catalog-fail');
    return mockClone(CATALOG);
}

/** 요청 `values[]` 를 고시 항목 · 판매자 추가 항목으로 나눈다 */
function splitValues(n: GroupBuyNoticeInput): { items: NoticeItemValue[]; customs: NoticeCustomInput[] } {
    const items: NoticeItemValue[] = [];
    const customs: NoticeCustomInput[] = [];
    for (const v of n.values) {
        if ('custom' in v && v.custom) customs.push(v);
        else items.push(v as NoticeItemValue);
    }
    return { items, customs };
}

/** 고시 대상 단품(판매하는 이름 있는 단품 — 축 없는 단품 하나는 늘 기본 양식) */
type SkuTarget = Pick<GroupBuySku, 'name' | 'kind'>;

/**
 * 양식 저장 검사(모든 상태 — 400 `fields.notices[i].…`). 빈 항목·어느 양식에도 안 든 단품은 여기서 막지 않는다(모집 시작 때 409).
 * `targets` 는 이 저장이 끝난 뒤의 고시 대상 단품(DRAFT 는 요청의 구성, 모집 뒤는 지금 구성).
 * 추가 구성상품 양식에는 그 상품 하나만(MAIN 단품·다른 추가 구성상품과 섞으면 `skuNames` — 서버 2b-2).
 * 판매자 추가 항목 오류 키 `notices[i].customItems[k].name|value` 의 k 는 요청의 추가 항목 차례(빈 줄 포함)라고 가정한다.
 */
function noticeFields(notices: readonly GroupBuyNoticeInput[], targets: readonly SkuTarget[], fields: Record<string, string>): void {
    const owner = new Map<string, number>();
    const kindOf = new Map(targets.map((s) => [s.name, s.kind]));
    const hasMainTargets = targets.some((s) => s.kind === 'MAIN');
    let baseSeen = false;
    notices.forEach((n, i) => {
        const category = CATALOG.categories.find((c) => c.code === n.categoryCode);
        if (!category) fields[`notices[${i}].categoryCode`] = '고시 품목을 골라 주세요.';
        if (!n.title.trim()) fields[`notices[${i}].title`] = '양식 제목을 입력해 주세요.';
        else if (Array.from(n.title.trim()).length > UtilGroupBuy.NOTICE_TITLE_MAX) fields[`notices[${i}].title`] = `양식 제목은 ${UtilGroupBuy.NOTICE_TITLE_MAX}자까지예요.`;
        if (n.skuNames.length === 0) {
            if (baseSeen && hasMainTargets) fields[`notices[${i}].skuNames`] = '옵션 조합이 있는 공구는 기본 양식(적용 단품 비움)을 하나만 둘 수 있어요. 적용할 단품을 골라 주세요.';
            baseSeen = true;
        }
        const addons = n.skuNames.filter((name) => kindOf.get(name) === 'ADDON');
        if (addons.length && n.skuNames.length > 1) fields[`notices[${i}].skuNames`] = `추가 구성상품 「${addons[0]}」 양식에는 그 상품 하나만 넣어요.`;
        for (const name of n.skuNames) {
            if (!kindOf.has(name)) fields[`notices[${i}].skuNames`] = `「${name}」 단품이 없어요. 화면을 새로 불러와 주세요.`;
            else if (owner.has(name)) fields[`notices[${i}].skuNames`] = `「${name}」 단품은 이미 「${notices[owner.get(name)!]!.title}」 양식에 들어 있어요. 한 단품은 양식 하나에만 넣어요.`;
            else owner.set(name, i);
        }
        n.values.forEach((v, j) => {
            if ('custom' in v && v.custom) return;
            const item = v as NoticeItemValue;
            if (item.unavailable && !item.unavailableReason.trim()) fields[`notices[${i}].values[${j}].unavailableReason`] = '제공할 수 없는 사유를 구체적으로 적어 주세요.';
            else if (Array.from(item.unavailableReason).length > UtilGroupBuy.NOTICE_TEXT_MAX) fields[`notices[${i}].values[${j}].unavailableReason`] = `${UtilGroupBuy.NOTICE_TEXT_MAX}자까지 적을 수 있어요.`;
            if (Array.from(item.value).length > UtilGroupBuy.NOTICE_TEXT_MAX) fields[`notices[${i}].values[${j}].value`] = `${UtilGroupBuy.NOTICE_TEXT_MAX}자까지 적을 수 있어요.`;
        });
        // 판매자 추가 항목 — 이름·내용 둘 다 빈 줄은 버린다(검사 안 함), 반쪽 줄은 400
        const { customs } = splitValues(n);
        let kept = 0;
        customs.forEach((c, k) => {
            const key = (field: string) => `notices[${i}].customItems[${k}].${field}`;
            if (!c.name.trim() && !c.value.trim()) return;
            kept += 1;
            if (kept > UtilGroupBuy.NOTICE_CUSTOM_MAX) fields[key('name')] = `판매자 추가 항목은 양식마다 ${UtilGroupBuy.NOTICE_CUSTOM_MAX}개까지예요.`;
            else if (!c.name.trim()) fields[key('name')] = '항목 이름을 입력해 주세요.';
            else if (Array.from(c.name).length > UtilGroupBuy.NOTICE_CUSTOM_LABEL_MAX) fields[key('name')] = `항목 이름은 ${UtilGroupBuy.NOTICE_CUSTOM_LABEL_MAX}자까지예요.`;
            else if (category && UtilGroupBuy.noticeCustomNameTaken(c.name, category.items)) fields[key('name')] = `「${c.name.trim()}」은(는) 고시 항목 이름이에요. 고시 항목 칸에 적어 주세요.`;
            else if (UtilGroupBuy.noticeCustomNameRepeated(customs, k)) fields[key('name')] = `「${c.name.trim()}」 항목이 이 양식에 이미 있어요.`;
            if (!c.value.trim()) fields[key('value')] = '내용을 입력해 주세요.';
            else if (Array.from(c.value).length > UtilGroupBuy.NOTICE_TEXT_MAX) fields[key('value')] = `${UtilGroupBuy.NOTICE_TEXT_MAX}자까지 적을 수 있어요.`;
        });
    });
}

/** 저장할 양식(응답 모양) — 적용 단품 이름을 지금 단품의 ID·이름으로(없는 이름은 뺀다), 빈 추가 줄을 버리고 `sortOrder` 를 0부터 다시 매긴다 */
function noticesOf(notices: readonly GroupBuyNoticeInput[], skus: readonly GroupBuySku[]): GroupBuyNotice[] {
    const targets = skus.filter((s) => s.onSale && (s.kind === 'ADDON' || s.valueIds.length > 0));
    return notices.map((n) => {
        const { items, customs } = splitValues(n);
        const picked = n.skuNames.flatMap((name) => targets.filter((s) => s.name === name));
        return {
            categoryCode: n.categoryCode,
            title: n.title.trim(),
            skuIds: picked.map((s) => s.skuId),
            skuNames: picked.map((s) => s.name),
            values: items.map((v) => ({ itemKey: v.itemKey, value: v.value, unavailable: v.unavailable, unavailableReason: v.unavailable ? v.unavailableReason : '' })),
            customItems: customs.filter((c) => c.name.trim() || c.value.trim()).map((c, k) => ({ name: c.name, value: c.value, sortOrder: k })),
        };
    });
}

/** 비교용 양식 내용 */
function noticeShape(notices: readonly GroupBuyNotice[]): string {
    return JSON.stringify(notices.map((n) => ({ ...n, skuIds: [...n.skuIds].sort(), skuNames: [...n.skuNames].sort() })));
}

function noticeIncompleteError(notices: readonly GroupBuyNotice[], targets: readonly SkuTarget[], force: boolean): ApiError | null {
    const problems = UtilGroupBuy.noticeProblems(notices, CATALOG, targets);
    if (force && UtilGroupBuy.noticeComplete(problems)) {
        const first = notices[0];
        const key = first ? CATALOG.categories.find((c) => c.code === first.categoryCode)?.items[0]?.key : undefined;
        if (first && key) problems.notices.push({ index: 0, title: first.title, categoryCode: first.categoryCode, missingKeys: [key] });
        else problems.noNotice = true;
    }
    if (UtilGroupBuy.noticeComplete(problems)) return null;
    return MOCK_ERRORS.conflict('GROUP_BUY_NOTICE_INCOMPLETE', '상품정보제공고시 양식을 다 채우지 않았어요. 빈 항목은 값을 적거나 「제공 불가」와 사유를 적고, 추가 구성상품마다 양식을 두고 모든 단품이 어느 양식에 들게 해 주세요.', { ...problems });
}

/** 저장 요청의 고시 대상 단품(조합 이름 — 판매하는 것만 · 추가 구성상품) */
function draftTargets(body: GroupBuySaveRequest): SkuTarget[] {
    const off = new Set(body.skus.filter((s) => !s.onSale).map((s) => UtilGroupBuy.skuMainName(s.values)));
    const combos = body.axes.length ? UtilGroupBuy.skuCombinations(body.axes.map((a) => a.values)).map((v) => UtilGroupBuy.skuMainName(v)) : [];
    return [...combos.filter((name) => !off.has(name)).map((name): SkuTarget => ({ name, kind: 'MAIN' })), ...body.addons.map((a): SkuTarget => ({ name: a.name.trim(), kind: 'ADDON' }))];
}

/**
 * 옵션 구성 검사(서버 2b-2 `GroupBuyDraft` 규칙 — 오류 칸은 `axes`·`skus`·`addons` 하나씩):
 * 축 0~3 · 축 이름 빔/겹침 · 축마다 값 1~30 · 값 빔/겹침 · 조합 ≤ 200 · 조합이 축 값과 안 맞음 · 판매 조합 1개 이상 · 추가 구성상품 ≤ 20 · 이름 빔/겹침/조합 이름과 겹침 · 가격.
 */
function skuFields(body: GroupBuySaveRequest, fields: Record<string, string>): void {
    const S = UtilGroupBuy.SKU;
    const axes = body.axes;
    if (axes.length > S.axisMax) fields.axes = `옵션 축은 ${S.axisMax}개까지예요.`;
    else if (axes.some((a) => !a.name.trim())) fields.axes = '옵션 이름을 입력해 주세요.';
    else if (UtilGroupBuy.repeatedNames(axes.map((a) => a.name)).length) fields.axes = '같은 이름의 옵션이 있어요.';
    else if (axes.some((a) => a.values.length < 1 || a.values.length > S.valueMax)) fields.axes = `옵션마다 값을 1~${S.valueMax}개 적어 주세요.`;
    else if (axes.some((a) => a.values.some((v) => !v.trim()))) fields.axes = '빈 옵션 값이 있어요.';
    else if (axes.some((a) => UtilGroupBuy.repeatedNames(a.values).length)) fields.axes = '한 옵션 안에 같은 값이 있어요.';
    const comboCount = UtilGroupBuy.skuComboCount(axes.map((a) => a.values.length));
    if (!fields.axes && axes.length && comboCount > S.comboMax) fields.axes = `옵션 조합은 ${S.comboMax}개까지예요(지금 ${comboCount}개).`;
    if (!fields.axes) {
        const valid = (values: string[]) => values.length === axes.length && values.every((v, i) => axes[i]!.values.includes(v));
        if (body.skus.some((s) => !valid(s.values))) fields.skus = '옵션 값에 없는 조합이 있어요. 화면을 새로 불러와 주세요.';
        else if (body.skus.some((s) => s.extraPrice < 0 || (s.maxQty !== null && s.maxQty < 1))) fields.skus = '추가금은 0 이상, 한도는 1 이상(비우면 한도 없음)으로 적어 주세요.';
        else if (axes.length && body.skus.filter((s) => !s.onSale).length >= comboCount) fields.skus = '판매하는 조합이 하나 이상 있어야 해요.';
    }
    const comboNames = new Set(axes.length ? UtilGroupBuy.skuCombinations(axes.map((a) => a.values)).map((v) => UtilGroupBuy.noticeNameKey(UtilGroupBuy.skuMainName(v))) : []);
    const addons = body.addons;
    if (addons.length > S.addonMax) fields.addons = `추가 구성상품은 ${S.addonMax}개까지예요.`;
    else if (addons.some((a) => !a.name.trim())) fields.addons = '추가 구성상품 이름을 입력해 주세요.';
    else if (UtilGroupBuy.repeatedNames(addons.map((a) => a.name)).length) fields.addons = '같은 이름의 추가 구성상품이 있어요.';
    else if (addons.some((a) => comboNames.has(UtilGroupBuy.noticeNameKey(a.name)))) fields.addons = '추가 구성상품 이름이 옵션 조합 이름과 같아요.';
    else if (addons.some((a) => !(a.price > 0))) fields.addons = '추가 구성상품 가격을 입력해 주세요.';
    else if (addons.some((a) => a.maxQty === null || a.maxQty < 1)) fields.addons = '추가 구성상품 한도 수량을 1 이상으로 적어 주세요.';
}

/** 저장 칸 검사 — 서버 `GroupBuyAdminService` 의 규칙 후보(필드 키 = 요청 칸 이름) */
function validateSave(body: GroupBuySaveRequest): void {
    const fields: Record<string, string> = {};
    noticeFields(body.notices, draftTargets(body), fields);
    skuFields(body, fields);
    if (!body.title.trim()) fields.title = '제목을 입력해 주세요.';
    if (!body.productName.trim()) fields.productName = '상품명을 입력해 주세요.';
    if (!(body.groupPrice > 0)) fields.groupPrice = '공구가를 입력해 주세요.';
    if (body.normalPrice > 0 && body.normalPriceBasis === null) fields.normalPriceBasis = '정상가 기준을 골라 주세요.';
    if (body.minQty > body.maxQty) fields.minQty = '성사 수량은 최대 수량보다 클 수 없어요.';
    if (body.fulfillment === 'IMMEDIATE' && body.minQty !== 0) fields.fulfillment = '즉시 발송은 선착순(성사 수량 0) 공구만 고를 수 있어요.';
    if (body.fulfillment === 'AFTER_CLOSE' && !body.shipPlannedAt) fields.shipPlannedAt = '마감 후 발주 공구는 발송 예정일이 필요해요.';
    if (body.fulfillment === 'IMMEDIATE' && !body.shipWithinBusinessDays) fields.shipWithinBusinessDays = '결제 후 몇 영업일 안에 보낼지 입력해 주세요.';
    if (body.startAt && body.endAt && body.endAt <= body.startAt) fields.endAt = '마감은 시작보다 뒤여야 해요.';
    if (body.startAt && body.endAt) {
        const days = (Date.parse(body.endAt) - Date.parse(body.startAt)) / 86_400_000;
        const cap = body.minQty > 0 ? 14 : 30;
        if (days > cap) fields.endAt = `${body.minQty > 0 ? '성사형' : '선착순'} 공구는 기간이 ${cap}일을 넘을 수 없어요.`;
    }
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
}

/**
 * 저장 요청 → 축·단품(응답 모양). DRAFT 를 다시 저장해도 고시 양식의 `skuIds` 가 이어지게 같은 이름(축·값)·같은 조합·같은 이름(추가 구성상품)은 ID 를 살린다.
 * 적지 않은 조합은 추가금 0 · 한도 없음 · 판매(서버 2b-2). 축이 없으면 단품 하나.
 */
function buildSkus(g: GroupBuyDetail, body: GroupBuySaveRequest): Pick<GroupBuyDetail, 'axes' | 'skus'> {
    const axes = body.axes.map((a, i) => {
        const before = g.axes.find((x) => x.name === a.name.trim());
        const axisId = before?.id ?? nextId('ax');
        return { id: axisId, name: a.name.trim(), sortOrder: i, values: a.values.map((v, j) => ({ id: before?.values.find((x) => x.name === v.trim())?.id ?? nextId('av'), name: v.trim(), sortOrder: j })) };
    });
    const mainsBefore = g.skus.filter((s) => s.kind === 'MAIN');
    const mains: GroupBuySku[] = axes.length
        ? UtilGroupBuy.skuCombinations(axes.map((a) => a.values)).map((values) => {
              const name = UtilGroupBuy.skuMainName(values.map((v) => v.name));
              const input = body.skus.find((s) => UtilGroupBuy.skuMainName(s.values) === name);
              const extra = input?.extraPrice ?? 0;
              return { skuId: mainsBefore.find((s) => s.name === name)?.skuId ?? nextId('sku'), kind: 'MAIN', valueIds: values.map((v) => v.id), name, extraPrice: extra, unitPrice: body.groupPrice + extra, maxQty: input?.maxQty ?? null, initialMaxQty: null, currentQty: 0, reservedQty: 0, onSale: input?.onSale ?? true, imageKey: null, imageUrl: null, sortOrder: 0 };
          })
        : [{ skuId: mainsBefore.find((s) => s.valueIds.length === 0)?.skuId ?? nextId('sku'), kind: 'MAIN', valueIds: [], name: '', extraPrice: 0, unitPrice: body.groupPrice, maxQty: null, initialMaxQty: null, currentQty: 0, reservedQty: 0, onSale: true, imageKey: null, imageUrl: null, sortOrder: 0 }];
    const addons: GroupBuySku[] = body.addons.map((a) => ({
        skuId: g.skus.find((s) => s.kind === 'ADDON' && s.name === a.name.trim())?.skuId ?? nextId('sku'),
        kind: 'ADDON',
        valueIds: [],
        name: a.name.trim(),
        extraPrice: 0,
        unitPrice: a.price,
        maxQty: a.maxQty,
        initialMaxQty: null,
        currentQty: 0,
        reservedQty: 0,
        onSale: true,
        imageKey: a.imageKey,
        imageUrl: a.imageKey ? imageOf(a.imageKey).thumbUrl : null,
        sortOrder: 0,
    }));
    return { axes, skus: [...mains, ...addons].map((s, i) => ({ ...s, sortOrder: i })) };
}

function applySave(g: GroupBuyDetail, body: GroupBuySaveRequest): void {
    Object.assign(g, {
        title: body.title.trim(),
        productName: body.productName.trim(),
        summary: body.summary,
        spec: body.spec,
        description: body.description,
        normalPrice: body.normalPrice,
        groupPrice: body.groupPrice,
        normalPriceBasis: body.normalPriceBasis,
        normalPriceSource: body.normalPriceSource,
        minQty: body.minQty,
        fulfillment: body.fulfillment,
        maxQty: body.maxQty,
        maxQtyPerUser: body.maxQtyPerUser,
        roundLimitShared: body.roundLimitShared,
        startAt: body.startAt,
        endAt: body.endAt,
        extensionMode: body.extensionMode,
        shippingPolicyCode: body.shippingPolicyCode,
        shipPlannedAt: body.shipPlannedAt,
        shipWithinBusinessDays: body.shipWithinBusinessDays,
        withdrawalRestriction: body.withdrawalRestriction,
        updatedAt: new Date().toISOString(),
    });
    g.images = body.imageIds.map((mediaId) => imageOf(mediaId));
    Object.assign(g, buildSkus(g, body));
    g.notices = noticesOf(body.notices, g.skus);
    g.version += 1;
}

/** 올린 이미지 기억(올리기 응답의 주소를 저장 때 다시 찾는다) */
const uploaded = new Map<string, GroupBuyImageUpload>();

function imageOf(mediaId: string): GroupBuyDetail['images'][number] {
    const fromUpload = uploaded.get(mediaId);
    if (fromUpload) return { mediaId, url: fromUpload.url, thumbUrl: fromUpload.thumbUrl };
    for (const g of db().groupBuys) {
        const found = g.images.find((i) => i.mediaId === mediaId);
        if (found) return { ...found };
    }
    const url = mockImage(mediaId, 180);
    return { mediaId, url, thumbUrl: url };
}

export async function mockGroupBuyCreate(body: GroupBuySaveRequest): Promise<GroupBuyDetail> {
    await guardWrite();
    validateSave(body);
    const id = nextId('gb');
    const now = new Date().toISOString();
    const g: GroupBuyDetail = {
        ...seedGroupBuys().find((x) => x.status === 'DRAFT')!,
        id,
        roundNo: 1,
        roundRootId: id,
        version: 0,
        revisionNo: 0,
        createdBy: OPERATOR,
        createdAt: now,
        updatedAt: now,
    };
    applySave(g, body);
    db().groupBuys.unshift(g);
    return present(g);
}

export async function mockGroupBuyUpdate(id: string, body: GroupBuySaveRequest): Promise<GroupBuyDetail> {
    const flags = await guardWrite();
    const g = findGroupBuy(id);
    if (flags.has('gb-locked') || g.status !== 'DRAFT') throw MOCK_ERRORS.conflict('GROUP_BUY_LOCKED_FIELD', '모집이 시작된 공구라 가격·기간·옵션·배송비는 바꿀 수 없어요. 화면을 새로 불러와 주세요.');
    if (body.version !== g.version) throw MOCK_ERRORS.conflict('GROUP_BUY_VERSION_CONFLICT', '다른 운영자가 먼저 고쳤어요. 새로 불러온 뒤 다시 고쳐 주세요.');
    validateSave(body);
    applySave(g, body);
    return present(g);
}

export async function mockGroupBuyContent(id: string, body: GroupBuyContentRequest): Promise<GroupBuyDetail> {
    const flags = await guardWrite();
    const g = findGroupBuy(id);
    if (flags.has('gb-version') || body.version !== g.version) throw MOCK_ERRORS.conflict('GROUP_BUY_VERSION_CONFLICT', '다른 운영자가 먼저 고쳤어요. 새로 불러온 뒤 다시 고쳐 주세요.');
    const fields: Record<string, string> = {};
    if (body.imageIds.length > 8) fields.imageIds = '대표 이미지는 8장까지예요.';
    const targets = namedSkus(g);
    noticeFields(body.notices, targets, fields);
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const nextNotices = noticesOf(body.notices, g.skus);
    const noticesChanged = noticeShape(nextNotices) !== noticeShape(g.notices);
    // 모집 뒤라 공개된 양식이 덜 찬 채로 남으면 안 된다
    if (noticesChanged) {
        const incomplete = noticeIncompleteError(nextNotices, targets, false);
        if (incomplete) throw incomplete;
    }
    const changed: string[] = [];
    if (body.description !== g.description) changed.push('description');
    if (body.summary !== g.summary) changed.push('summary');
    if (body.spec !== g.spec) changed.push('spec');
    if (body.imageIds.join() !== g.images.map((i) => i.mediaId).join()) changed.push('images');
    g.summary = body.summary;
    g.spec = body.spec;
    g.description = body.description;
    g.images = body.imageIds.map((mediaId) => imageOf(mediaId));
    if (noticesChanged) g.notices = nextNotices;
    g.version += 1;
    g.updatedAt = new Date().toISOString();
    if (noticesChanged && !changed.length) addRevision(g, 'NOTICE', ['notices']);
    else addRevision(g, 'CONTENT', noticesChanged ? [...changed, 'notices'] : changed);
    return present(g);
}

export async function mockGroupBuyImageUpload(file: File): Promise<GroupBuyImageUpload> {
    await guardWrite();
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw MOCK_ERRORS.validation({ file: 'JPEG·PNG·WebP 이미지만 올릴 수 있어요.' });
    if (file.size > 5 * 1024 * 1024) throw MOCK_ERRORS.validation({ file: '이미지는 5MB 까지 올릴 수 있어요.' });
    const url = URL.createObjectURL(file);
    const result: GroupBuyImageUpload = { mediaId: nextId('md'), url, thumbUrl: url, width: 1600, height: 1600 };
    uploaded.set(result.mediaId, result);
    return result;
}

export async function mockGroupBuyRevisions(id: string, page: number, size: number): Promise<GroupBuyPage<GroupBuyRevision>> {
    await guardRead();
    const g = findGroupBuy(id);
    const list = db().revisions.get(id) ?? seedRevisions(g);
    db().revisions.set(id, list);
    return mockClone({ items: list.slice(page * size, page * size + size), total: list.length });
}

export async function mockGroupBuyStart(id: string): Promise<GroupBuyDetail> {
    const flags = await guardWrite();
    const g = findGroupBuy(id);
    if (g.status !== 'DRAFT') throw MOCK_ERRORS.conflict('GROUP_BUY_NOT_DRAFT', '이미 모집을 시작한 공구예요.');
    if (flags.has('gb-no-policy') || g.shippingPolicyCode === 'FREE') throw MOCK_ERRORS.conflict('GROUP_BUY_SHIPPING_POLICY_MISSING', '고른 배송비 정책에 지금 유효한 줄이 없어요. 설정 › 커머스 설정에서 확인해 주세요.');
    if (g.fulfillment === 'AFTER_CLOSE' && !g.shipPlannedAt) throw MOCK_ERRORS.validation({ shipPlannedAt: '마감 후 발주 공구는 발송 예정일이 필요해요.' });
    const incomplete = noticeIncompleteError(g.notices, namedSkus(g), flags.has('notice-incomplete'));
    if (incomplete) throw incomplete;
    g.status = 'RECRUITING';
    g.shippingSnapshot = g.shippingPolicyCode === 'BULKY' ? { shippingFee: 15000, freeShippingOver: null, returnShippingFee: 15000, returnFeeMode: 'ROUND_TRIP' } : { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' };
    g.initialMaxQty = g.maxQty;
    g.skus = g.skus.map((s) => ({ ...s, initialMaxQty: s.maxQty }));
    if (!g.startAt || g.startAt < new Date().toISOString()) g.startAt = new Date().toISOString();
    g.updatedAt = new Date().toISOString();
    addRevision(g, 'START', []);
    return present(g);
}

export async function mockGroupBuyCloseEarly(id: string, reason: string): Promise<GroupBuyDetail> {
    await guardWrite();
    requireReason(reason);
    const g = findGroupBuy(id);
    if (g.status !== 'RECRUITING') throw MOCK_ERRORS.conflict('GROUP_BUY_NOT_RECRUITING', '모집 중인 공구가 아니에요.');
    g.status = 'CLOSING';
    g.closeReason = 'EARLY_CLOSED';
    g.closingAt = new Date().toISOString();
    g.updatedAt = g.closingAt;
    return present(g);
}

export async function mockGroupBuyCancelPreview(id: string): Promise<GroupBuyCancelPreview> {
    await guardRead();
    const g = findGroupBuy(id);
    const shipped = ordersOf(id).filter((o) => o.status === 'SHIPPED' || o.status === 'DELIVERED').length;
    return { refundOrders: g.preShipOrderCount, refundAmount: g.preShipOrderCount * g.groupPrice, expireOrders: g.reservedQty > 0 ? 1 : 0, shippedOrders: shipped };
}

export async function mockGroupBuyCancel(id: string, reason: string): Promise<GroupBuyDetail> {
    await guardWrite();
    requireReason(reason);
    const g = findGroupBuy(id);
    if (!actionsOf(g).canCancel) throw MOCK_ERRORS.conflict('GROUP_BUY_NOT_CANCELABLE', '이 상태에서는 중단할 수 없어요.');
    g.status = 'CANCELED';
    g.closeReason = 'CANCELED';
    g.reservedQty = 0;
    g.preShipOrderCount = 0;
    g.updatedAt = new Date().toISOString();
    for (const o of ordersOf(id)) {
        if (PRE_SHIP.includes(o.status)) {
            o.refunds.push({ id: nextId('rf'), cause: 'CANCELED', method: 'PG_CANCEL', state: 'PENDING', itemRefund: o.amounts.remaining - o.amounts.shippingFee, shippingRefund: o.amounts.shippingFee, returnFee: 0, reshippingFee: 0, reshippingRefund: 0, compensation: 0, total: o.amounts.remaining, businessFault: true, reason, operator: OPERATOR, requestId: null, requestedAt: new Date().toISOString(), completedAt: null });
            o.refundedAmount = o.amount;
            o.amounts.refundedAmount = o.amount;
            o.amounts.remaining = 0;
            o.history.push({ from: o.status, to: 'REFUNDED', actor: 'SYSTEM', note: '공구 중단', at: new Date().toISOString() });
            o.status = 'REFUNDED';
        } else if (o.status === 'PENDING_PAYMENT') {
            o.history.push({ from: o.status, to: 'EXPIRED', actor: 'SYSTEM', note: '공구 중단(GROUP_BUY_CANCELED)', at: new Date().toISOString() });
            o.status = 'EXPIRED';
        }
        o.groupBuy.status = 'CANCELED';
    }
    return present(g);
}

export async function mockGroupBuyMarkOrdered(id: string): Promise<GroupBuyDetail> {
    await guardWrite();
    const g = findGroupBuy(id);
    if (g.status !== 'CLOSED_SUCCESS' || g.fulfillment !== 'AFTER_CLOSE') throw MOCK_ERRORS.conflict('GROUP_BUY_NOT_ORDERABLE', '성사된 마감 후 발주 공구만 발주 처리할 수 있어요.');
    g.status = 'ORDERED';
    g.updatedAt = new Date().toISOString();
    for (const o of ordersOf(id)) o.groupBuy.status = 'ORDERED';
    return present(g);
}

export async function mockGroupBuyCreateRound(id: string): Promise<GroupBuyRoundCreated> {
    const flags = await guardWrite();
    const source = findGroupBuy(id);
    const blocked = actionsOf(source).roundBlockedReason;
    if (flags.has('gb-round-exists') || blocked === 'ROUND_EXISTS') throw MOCK_ERRORS.conflict('GROUP_BUY_ROUND_EXISTS', '이 공구의 다음 차수(작성 중 또는 모집 중)가 이미 있어요.');
    if (blocked === 'SOURCE_INVALID') throw MOCK_ERRORS.conflict('GROUP_BUY_ROUND_SOURCE_INVALID', '모집이 끝난 공구에서만 추가 차수를 열 수 있어요.');
    const roundNo = Math.max(...db().groupBuys.filter((g) => g.roundRootId === source.roundRootId).map((g) => g.roundNo)) + 1;
    const newId = nextId('gb');
    const now = new Date().toISOString();
    // 축·단품은 새 ID 로(구성·추가금·한도·판매 여부는 그대로), 고시 양식(판매자 추가 항목 포함)은 적용 단품을 새 ID 로 바꿔 복사한다(결정 87·88)
    const idMap = new Map(source.skus.map((s) => [s.skuId, nextId('sku')]));
    const skus: GroupBuySku[] = source.skus.map((s) => ({ ...s, skuId: idMap.get(s.skuId)!, initialMaxQty: null, currentQty: 0, reservedQty: 0 }));
    const notices: GroupBuyNotice[] = mockClone(source.notices).map((n) => ({ ...n, skuIds: n.skuIds.map((x) => idMap.get(x) ?? x) }));
    const copy: GroupBuyDetail = {
        ...mockClone(source),
        id: newId,
        status: 'DRAFT',
        closeReason: null,
        roundNo,
        title: source.title.replace(/\s*\d+차$/, '') + ` ${roundNo}차`,
        normalPriceBasis: 'PREVIOUS_ROUND',
        normalPriceSource: `${source.roundNo}차 공구가 ${source.groupPrice.toLocaleString('ko-KR')}원`,
        skus,
        notices,
        initialMaxQty: null,
        addedQty: 0,
        currentQty: 0,
        reservedQty: 0,
        startAt: null,
        endAt: null,
        extendedUntil: null,
        closingAt: null,
        shippingSnapshot: null,
        shipPlannedAt: null,
        preShipOrderCount: 0,
        version: 0,
        revisionNo: 0,
        delayNotices: [],
        supplyFailures: [],
        createdBy: OPERATOR,
        createdAt: now,
        updatedAt: now,
    };
    db().groupBuys.unshift(copy);
    return { id: newId, roundNo };
}

export async function mockGroupBuyDelayNotice(id: string, body: GroupBuyDelayNoticeRequest): Promise<GroupBuyDetail> {
    await guardWrite();
    const g = findGroupBuy(id);
    const fields: Record<string, string> = {};
    if (!body.newPlannedAt) fields.newPlannedAt = '새 발송 예정일을 골라 주세요.';
    else if (g.shipPlannedAt && body.newPlannedAt <= g.shipPlannedAt) fields.newPlannedAt = '지금 예정일보다 뒤인 날을 골라 주세요.';
    const problem = reasonProblem(body.reason);
    if (problem) fields.reason = problem;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    g.delayNotices.unshift({ id: nextId('dn'), previousPlannedAt: g.shipPlannedAt, newPlannedAt: body.newPlannedAt, reason: body.reason.trim(), notifiedOrders: g.preShipOrderCount, operator: OPERATOR, createdAt: new Date().toISOString() });
    g.shipPlannedAt = body.newPlannedAt;
    for (const o of ordersOf(id)) if (PRE_SHIP.includes(o.status)) o.delayCancelable = true;
    addRevision(g, 'SHIP_DATE', ['shipPlannedAt']);
    return present(g);
}

export async function mockGroupBuySupplyPreview(id: string, skuId: string): Promise<GroupBuySupplyFailurePreview> {
    await guardRead();
    const g = findGroupBuy(id);
    const target = g.skus.find((s) => s.skuId === skuId && s.onSale);
    if (!target) throw MOCK_ERRORS.validation({ skuId: '단품을 골라 주세요.' });
    const lines = Math.max(1, Math.round(target.currentQty / 3));
    return { skuId, skuName: target.name, skuKind: target.kind, cancelLines: lines, refundAmount: lines * target.unitPrice, fullRefundOrders: Math.ceil(lines / 2), choiceOrders: Math.floor(lines / 2), choiceDeadline: mockAt(3 * 1440) };
}

export async function mockGroupBuySupplyFailure(id: string, body: GroupBuySupplyFailureRequest): Promise<GroupBuyDetail> {
    await guardWrite();
    const g = findGroupBuy(id);
    const fields: Record<string, string> = {};
    const target = g.skus.find((s) => s.skuId === body.skuId && s.onSale);
    if (!target) fields.skuId = '단품을 골라 주세요.';
    const problem = reasonProblem(body.reason);
    if (problem) fields.reason = problem;
    if (Object.keys(fields).length || !target) throw MOCK_ERRORS.validation(fields);
    const preview = await mockGroupBuySupplyPreview(id, body.skuId);
    g.supplyFailures.unshift({ id: nextId('sf'), skuId: target.skuId, skuName: target.name, skuKind: target.kind, reason: body.reason.trim(), refundedLines: preview.cancelLines, refundAmount: preview.refundAmount, choiceOrders: preview.choiceOrders, choicePending: preview.choiceOrders, choiceDeadline: preview.choiceDeadline, operator: OPERATOR, createdAt: new Date().toISOString() });
    target.maxQty = target.currentQty;
    addMovement(g, 'REDUCE', 0, target.skuId, `공급 불가 — ${body.reason.trim()}`);
    for (const o of ordersOf(id)) {
        if (!PRE_SHIP.includes(o.status) || !o.items.some((i) => i.skuId === target.skuId)) continue;
        // 다른 줄이 남으면 회원 선택(본 상품이 하나도 안 남으면 추가 구성상품도 함께 취소 — 2b-3 확정 전 가정)
        const rest = o.items.filter((i) => i.skuId !== target.skuId);
        if (rest.some((i) => i.kind === 'MAIN')) o.supplyChoicePending = true;
    }
    return present(g);
}

// ── 수량(결정 88 — 공구 합계는 MAIN 단품만, 추가 구성상품은 자기 한도) ─────────────

function stockLine(g: GroupBuyDetail, target: GroupBuySku | null): GroupBuyStockLine {
    const gbRemaining = Math.max(0, g.maxQty - g.currentQty - g.reservedQty);
    const returned = g.id === 'gb-102' ? 1 : 0;
    if (!target) {
        return {
            skuId: null,
            kind: null,
            name: g.title,
            onSale: true,
            maxQty: g.maxQty,
            initialMaxQty: g.initialMaxQty,
            addedQty: g.initialMaxQty === null ? 0 : Math.max(0, g.maxQty - g.initialMaxQty),
            currentQty: g.currentQty,
            reservedQty: g.reservedQty,
            remainingQty: gbRemaining,
            returnedQty: returned,
            restockedQty: 0,
            soldOut: g.currentQty >= g.maxQty,
            tempSoldOut: g.currentQty < g.maxQty && g.currentQty + g.reservedQty >= g.maxQty,
            reduceFloor: Math.max(g.currentQty + g.reservedQty, g.minQty),
        };
    }
    const own = target.maxQty === null ? null : Math.max(0, target.maxQty - target.currentQty - target.reservedQty);
    // MAIN 은 공구 남은 수량도 본다 — ADDON 은 자기 한도만
    const remaining = !target.onSale ? 0 : target.kind === 'ADDON' ? (own ?? 0) : own === null ? gbRemaining : Math.min(own, gbRemaining);
    const soldOut = target.onSale && target.maxQty !== null && target.currentQty >= target.maxQty;
    return {
        skuId: target.skuId,
        kind: target.kind,
        name: target.name,
        onSale: target.onSale,
        maxQty: target.maxQty,
        initialMaxQty: target.initialMaxQty,
        addedQty: target.initialMaxQty === null || target.maxQty === null ? 0 : Math.max(0, target.maxQty - target.initialMaxQty),
        currentQty: target.currentQty,
        reservedQty: target.reservedQty,
        remainingQty: remaining,
        returnedQty: 0,
        restockedQty: 0,
        soldOut,
        tempSoldOut: target.onSale && remaining === 0 && !soldOut,
        reduceFloor: target.currentQty + target.reservedQty,
    };
}

function stockView(g: GroupBuyDetail): GroupBuyStockView {
    const named = namedSkus(g);
    const holdName = (i: number) => (named[i] ? named[i]!.name : g.productName);
    const holds = g.reservedQty > 0
        ? [
              { orderNo: 'G2026102800044', member: { id: '10311', nickname: '테크러버', withdrawn: false }, lines: `${holdName(0)} × ${Math.min(2, g.reservedQty)}`, qty: Math.min(2, g.reservedQty), reservedUntil: mockAt(3) },
              ...(g.reservedQty > 2 ? [{ orderNo: 'G2026102800047', member: { id: '10102', nickname: '메이커준', withdrawn: false }, lines: `${holdName(1)} × ${g.reservedQty - 2}`, qty: g.reservedQty - 2, reservedUntil: mockAt(1) }] : []),
          ]
        : [];
    // 축이 있는 MAIN 조합(판매 끈 것 포함) 다음 추가 구성상품 — 축 없는 단품 하나는 공구 전체 줄로 본다
    const lines = g.skus.filter((s) => s.kind === 'ADDON' || s.valueIds.length > 0);
    return mockClone({
        groupBuy: { id: g.id, title: g.title, roundNo: g.roundNo, status: g.status, minQty: g.minQty, fulfillment: g.fulfillment },
        total: stockLine(g, null),
        skus: lines.map((s) => stockLine(g, s)),
        holds,
        canAdjust: g.status === 'RECRUITING',
    });
}

export async function mockGroupBuyStock(id: string): Promise<GroupBuyStockView> {
    await guardRead('stock-fail');
    return stockView(findGroupBuy(id));
}

export async function mockGroupBuyMovements(id: string, query: GroupBuyMovementQuery): Promise<GroupBuyPage<GroupBuyMovement>> {
    const flags = await guardRead('stock-fail');
    const g = findGroupBuy(id);
    const list = flags.has('movements-empty') ? [] : (db().movements.get(id) ?? seedMovements(g));
    if (!flags.has('movements-empty')) db().movements.set(id, list);
    const filtered = list.filter((m) => !query.kind || m.kind === query.kind);
    return mockClone({ items: filtered.slice(query.page * query.size, query.page * query.size + query.size), total: filtered.length });
}

/**
 * 추가 입고·줄이기 — `skuId` 단위(서버 2b-2). MAIN 단품을 움직이면 공구 최대도 같은 수만큼(결정 80), ADDON 은 자기 한도만.
 * 한도 없는 단품은 `409 GROUP_BUY_SKU_UNLIMITED`, 판매 끈 조합은 `409 GROUP_BUY_SKU_NOT_ON_SALE`(구성은 모집 뒤 잠금 — 화면 가정).
 */
export async function mockGroupBuyStockAdjust(id: string, body: GroupBuyStockAdjustRequest): Promise<GroupBuyStockView> {
    const flags = await guardWrite();
    const g = findGroupBuy(id);
    const fields: Record<string, string> = {};
    if (!Number.isInteger(body.qty) || body.qty < 1) fields.qty = '1 이상 정수로 입력해 주세요.';
    const problem = reasonProblem(body.reason);
    if (problem) fields.reason = problem;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (g.status !== 'RECRUITING') throw MOCK_ERRORS.conflict('GROUP_BUY_NOT_RECRUITING', '모집 중인 공구만 수량을 바꿀 수 있어요.');
    const target = body.skuId ? g.skus.find((s) => s.skuId === body.skuId) : null;
    if (body.skuId && !target) throw MOCK_ERRORS.validation({ skuId: '단품을 골라 주세요.' });
    if (target && !target.onSale) throw MOCK_ERRORS.conflict('GROUP_BUY_SKU_NOT_ON_SALE', '판매하지 않는 조합이에요.');
    if (target && target.maxQty === null) throw MOCK_ERRORS.conflict('GROUP_BUY_SKU_UNLIMITED', '한도가 없는 단품은 공구 전체 수량으로 조정해 주세요.');
    const addon = target?.kind === 'ADDON';
    if (body.direction === 'ADD') {
        if (!addon && g.currentQty >= g.maxQty) throw MOCK_ERRORS.conflict('GROUP_BUY_SOLD_OUT', '이미 다 팔려 마감 중이에요. 추가 차수로 열어 주세요.');
        if (target && target.maxQty !== null) target.maxQty += body.qty;
        if (!addon) {
            g.maxQty += body.qty;
            g.addedQty += body.qty;
        }
    } else {
        const line = stockLine(g, target ?? null);
        const target2 = (target ? target.maxQty! : g.maxQty) - body.qty;
        if (flags.has('stock-floor') || target2 < line.reduceFloor) throw MOCK_ERRORS.conflict('GROUP_BUY_QTY_BELOW_FLOOR', `줄일 수 있는 하한(${line.reduceFloor}개) 아래로는 줄일 수 없어요.`, { floor: line.reduceFloor });
        if (target && target.maxQty !== null) target.maxQty -= body.qty;
        if (!addon) g.maxQty -= body.qty;
        if (g.currentQty >= g.maxQty) {
            g.status = 'CLOSING';
            g.closeReason = 'SOLD_OUT';
            g.closingAt = new Date().toISOString();
        }
    }
    g.updatedAt = new Date().toISOString();
    addMovement(g, body.direction, body.qty, target?.skuId ?? null, body.reason.trim());
    addRevision(g, 'STOCK', [addon ? 'skus' : 'maxQty']);
    return stockView(g);
}

// ── 주문 ─────────────────────────────────────────

function findOrder(id: string): GroupBuyOrderDetail {
    const found = db().orders.find((o) => o.id === id);
    if (!found) throw MOCK_ERRORS.notFound('GROUP_BUY_ORDER_NOT_FOUND', '주문을 찾을 수 없어요.');
    return found;
}

function goodsReady(o: GroupBuyOrderDetail): boolean {
    return o.groupBuy.fulfillment === 'IMMEDIATE' || o.groupBuy.status === 'ORDERED' || o.groupBuy.status === 'SHIPPING' || o.groupBuy.status === 'CANCELED';
}

function orderActions(o: GroupBuyOrderDetail): GroupBuyOrderActions {
    const ready = goodsReady(o) && !o.openRequest && !o.supplyChoicePending;
    let refundMode: GroupBuyOrderActions['refundMode'] = null;
    let refundBlockedReason: GroupBuyOrderActions['refundBlockedReason'] = null;
    if (o.openRequest) refundBlockedReason = 'OPEN_REQUEST';
    else if (PRE_SHIP.includes(o.status)) refundMode = 'LINES';
    else if (o.status === 'DELIVERED') refundMode = 'AMOUNT';
    else if (o.status === 'SHIPPED') refundBlockedReason = 'SHIPPED';
    else refundBlockedReason = 'CLOSED';
    return {
        canPrepare: o.status === 'CONFIRMED' && ready,
        canShip: (o.status === 'CONFIRMED' || o.status === 'PREPARING') && ready,
        canFixTracking: o.status === 'SHIPPED',
        canDeliver: o.status === 'SHIPPED',
        canRevealAddress: o.address.erasedAt === null,
        refundMode,
        refundBlockedReason,
    };
}

function presentOrder(o: GroupBuyOrderDetail): GroupBuyOrderDetail {
    return mockClone({ ...o, actions: orderActions(o) });
}

function matchesOrder(o: GroupBuyOrderDetail, query: GroupBuyOrderQuery): boolean {
    if (query.groupBuyId && o.groupBuy.id !== query.groupBuyId) return false;
    const q = query.q?.trim().toLowerCase();
    if (q && !(o.orderNo.toLowerCase().includes(q) || o.member.nickname.toLowerCase().includes(q) || o.member.id === q)) return false;
    if (query.from && o.createdAt < query.from) return false;
    if (query.to && o.createdAt >= query.to) return false;
    if (query.alert === 'SHIP_OVERDUE' && !(PRE_SHIP.includes(o.status) && o.shipDueAt !== null && o.shipDueAt < new Date().toISOString())) return false;
    if (query.alert === 'DELIVERY_STALE' && !(o.status === 'SHIPPED' && o.shippedAt !== null && Date.parse(o.shippedAt) < Date.now() - 5 * 86_400_000)) return false;
    return true;
}

export async function mockOrderList(query: GroupBuyOrderQuery): Promise<GroupBuyOrderListResponse> {
    const flags = await guardRead('orders-fail');
    const base = flags.has('orders-empty') ? [] : db().orders.filter((o) => matchesOrder(o, query));
    const counts = Object.fromEntries(GROUP_BUY_ORDER_STATUSES.map((s) => [s, base.filter((o) => o.status === s).length])) as Record<GroupBuyOrderStatus, number>;
    const filtered = base.filter((o) => !query.status || o.status === query.status).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    const start = query.page * query.size;
    return mockClone({ items: filtered.slice(start, start + query.size).map(presentOrder), total: filtered.length, counts });
}

export async function mockOrderDetail(id: string): Promise<GroupBuyOrderDetail> {
    await guardRead('order-detail-fail');
    return presentOrder(findOrder(id));
}

export async function mockOrderReveal(id: string, reason: string): Promise<GroupBuyRevealedAddress> {
    await guardWrite();
    requireReason(reason);
    const o = findOrder(id);
    if (o.address.erasedAt) throw MOCK_ERRORS.conflict('GROUP_BUY_ADDRESS_ERASED', '보관 기간이 지나 배송지를 지웠어요.');
    o.addressViews.unshift({ operator: OPERATOR, reason: reason.trim(), viewedAt: new Date().toISOString() });
    return { recipient: `${o.member.nickname}(목업 이름)`, phone: '010-1234-5678', postcode: o.address.postcode, address1: '서울 강남구 테헤란로 123', address2: '45층 4501호', memo: '문 앞에 두고 문자 주세요', expiresAt: mockAt(10) };
}

export async function mockOrderMemo(id: string, memo: string): Promise<GroupBuyOrderDetail> {
    await guardWrite();
    if (Array.from(memo).length > 1000) throw MOCK_ERRORS.validation({ memo: '메모는 1,000자까지예요.' });
    const o = findOrder(id);
    o.adminMemo = memo;
    return presentOrder(o);
}

function bulk(ids: string[], flags: Set<string>, check: (o: GroupBuyOrderDetail) => { code: string; message: string } | null, apply: (o: GroupBuyOrderDetail) => void): GroupBuyBulkResult {
    if (flags.has('bulk-fail')) throw MOCK_ERRORS.internal();
    if (ids.length > 200) throw MOCK_ERRORS.validation({ rows: '한 번에 200건까지 처리할 수 있어요.' });
    const results: GroupBuyBulkRowResult[] = ids.map((id, index) => {
        const o = db().orders.find((x) => x.id === id);
        if (!o) return { orderId: id, orderNo: id, ok: false, code: 'GROUP_BUY_ORDER_NOT_FOUND', message: '주문을 찾을 수 없어요.' };
        const problem = flags.has('ship-partial') && index % 2 === 1 ? { code: 'GROUP_BUY_ORDER_STATE_CHANGED', message: '그 사이 주문 상태가 바뀌었어요(취소 요청 접수).' } : check(o);
        if (problem) return { orderId: id, orderNo: o.orderNo, ok: false, ...problem };
        apply(o);
        return { orderId: id, orderNo: o.orderNo, ok: true, code: null, message: null };
    });
    return { succeeded: results.filter((r) => r.ok).length, failed: results.filter((r) => !r.ok).length, results };
}

export async function mockOrderPrepare(orderIds: string[]): Promise<GroupBuyBulkResult> {
    const flags = await guardWrite();
    return bulk(
        orderIds,
        flags,
        (o) => (orderActions(o).canPrepare ? null : { code: 'GROUP_BUY_ORDER_NOT_PREPARABLE', message: '결제 확정(CONFIRMED) 주문만 준비중으로 바꿀 수 있어요(마감 후 발주 공구는 발주 처리 뒤).' }),
        (o) => {
            o.history.push({ from: o.status, to: 'PREPARING', actor: OPERATOR, note: null, at: new Date().toISOString() });
            o.status = 'PREPARING';
        },
    );
}

export async function mockOrderShip(rows: GroupBuyShipRow[]): Promise<GroupBuyBulkResult> {
    const flags = await guardWrite();
    const byId = new Map(rows.map((r) => [r.orderId, r]));
    return bulk(
        rows.map((r) => r.orderId),
        flags,
        (o) => {
            const row = byId.get(o.id)!;
            if (!orderActions(o).canShip) return { code: 'GROUP_BUY_ORDER_NOT_SHIPPABLE', message: o.openRequest ? '열린 취소·반품 요청이 있어 발송할 수 없어요.' : o.supplyChoicePending ? '공급 불가 회원 선택 기한 전이라 발송할 수 없어요.' : '발송할 수 있는 상태가 아니에요.' };
            if (!row.carrierCode) return { code: 'VALIDATION', message: '택배사를 골라 주세요.' };
            const cleaned = row.trackingNo.replace(/[\s-]/g, '');
            if (!TRACKING_RE.test(cleaned)) return { code: 'GROUP_BUY_TRACKING_INVALID', message: '송장번호는 숫자·영문 6~30자예요.' };
            return null;
        },
        (o) => {
            const row = byId.get(o.id)!;
            const no = row.trackingNo.replace(/[\s-]/g, '');
            const now = new Date().toISOString();
            o.shipments.push({ kind: 'ORIGINAL', carrierCode: row.carrierCode, carrierName: carrierName(row.carrierCode), trackingNo: no, trackingUrl: `https://trace.example.com/${row.carrierCode}?no=${no}`, shippedAt: now, deliveredAt: null });
            o.carrierCode = row.carrierCode;
            o.trackingNo = no;
            o.shippedAt = now;
            o.history.push({ from: o.status, to: 'SHIPPED', actor: OPERATOR, note: null, at: now });
            o.status = 'SHIPPED';
            const g = db().groupBuys.find((x) => x.id === o.groupBuy.id);
            if (g && g.status === 'ORDERED') {
                g.status = 'SHIPPING';
                for (const other of ordersOf(g.id)) other.groupBuy.status = 'SHIPPING';
            }
        },
    );
}

export async function mockOrderDeliver(orderIds: string[]): Promise<GroupBuyBulkResult> {
    const flags = await guardWrite();
    return bulk(
        orderIds,
        flags,
        (o) => (o.status === 'SHIPPED' ? null : { code: 'GROUP_BUY_ORDER_NOT_SHIPPED', message: '발송(SHIPPED) 주문만 배송완료로 바꿀 수 있어요.' }),
        (o) => {
            const now = new Date().toISOString();
            o.deliveredAt = now;
            const last = o.shipments[o.shipments.length - 1];
            if (last) last.deliveredAt = now;
            o.history.push({ from: o.status, to: 'DELIVERED', actor: OPERATOR, note: null, at: now });
            o.status = 'DELIVERED';
        },
    );
}

export async function mockOrderFixTracking(id: string, body: GroupBuyTrackingFixRequest): Promise<GroupBuyOrderDetail> {
    await guardWrite();
    const o = findOrder(id);
    const fields: Record<string, string> = {};
    if (!body.carrierCode) fields.carrierCode = '택배사를 골라 주세요.';
    const cleaned = body.trackingNo.replace(/[\s-]/g, '');
    if (!TRACKING_RE.test(cleaned)) fields.trackingNo = '송장번호는 숫자·영문 6~30자예요.';
    const problem = reasonProblem(body.reason);
    if (problem) fields.reason = problem;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (o.status !== 'SHIPPED') throw MOCK_ERRORS.conflict('GROUP_BUY_ORDER_NOT_SHIPPED', '발송 상태의 주문만 송장을 고칠 수 있어요.');
    const last = o.shipments[o.shipments.length - 1];
    if (last) Object.assign(last, { carrierCode: body.carrierCode, carrierName: carrierName(body.carrierCode), trackingNo: cleaned, trackingUrl: `https://trace.example.com/${body.carrierCode}?no=${cleaned}` });
    o.carrierCode = body.carrierCode;
    o.trackingNo = cleaned;
    o.history.push({ from: 'SHIPPED', to: 'SHIPPED', actor: OPERATOR, note: `송장 정정 — ${body.reason.trim()}`, at: new Date().toISOString() });
    return presentOrder(o);
}

function refundPreviewOf(o: GroupBuyOrderDetail, body: GroupBuyAdminRefundRequest, flags: Set<string>): GroupBuyRefundPreview {
    let itemRefund = 0;
    let shippingRefund = 0;
    let reshippingFee = 0;
    if (body.lines) {
        let remainingGoods = 0;
        for (const item of o.items) {
            const pick = body.lines.find((l) => l.itemId === item.id)?.qty ?? 0;
            itemRefund += pick * item.unitPrice;
            remainingGoods += (item.qty - item.canceledQty - item.returnedQty - pick) * item.unitPrice;
        }
        if (remainingGoods === 0) shippingRefund = o.amounts.shippingFee;
        else if (!body.businessFault && o.amounts.shippingFee === 0 && remainingGoods < 50000) reshippingFee = 3000;
    } else {
        itemRefund = body.amount ?? 0;
    }
    const total = Math.max(0, itemRefund + shippingRefund - reshippingFee);
    const after = o.adminRefundTotal + total;
    const threshold = o.largeRefundThreshold;
    return { itemRefund, shippingRefund, reshippingFee, total, remainingAfter: o.amounts.remaining - total, adminRefundTotalAfter: after, largeRefundThreshold: threshold, needsLarge: flags.has('refund-large') || (threshold > 0 && after >= threshold) };
}

function validateRefund(o: GroupBuyOrderDetail, body: GroupBuyAdminRefundRequest, needReason: boolean): void {
    const fields: Record<string, string> = {};
    const mode = orderActions(o).refundMode;
    if (mode === 'LINES') {
        const total = (body.lines ?? []).reduce((s, l) => s + l.qty, 0);
        if (total <= 0) fields.lines = '돌려줄 줄과 수량을 골라 주세요.';
        for (const l of body.lines ?? []) {
            const item = o.items.find((i) => i.id === l.itemId);
            if (item && l.qty > item.qty - item.canceledQty - item.returnedQty) fields.lines = '남은 수량보다 많이 고를 수 없어요.';
        }
    } else if (mode === 'AMOUNT') {
        if (!body.amount || body.amount < 1) fields.amount = '돌려줄 금액을 입력해 주세요.';
        else if (body.amount > o.amounts.remaining) fields.amount = `남은 결제액(${o.amounts.remaining.toLocaleString('ko-KR')}원)보다 많이 돌려줄 수 없어요.`;
    }
    if (needReason) {
        const problem = reasonProblem(body.reason);
        if (problem) fields.reason = problem;
    }
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
}

export async function mockOrderRefundPreview(id: string, body: GroupBuyAdminRefundRequest): Promise<GroupBuyRefundPreview> {
    const flags = await guardRead();
    const o = findOrder(id);
    validateRefund(o, body, false);
    return refundPreviewOf(o, body, flags);
}

export async function mockOrderRefund(id: string, body: GroupBuyAdminRefundRequest): Promise<GroupBuyAdminRefundResult> {
    const flags = await guardWrite();
    const o = findOrder(id);
    const actions = orderActions(o);
    if (flags.has('refund-unreconciled')) throw MOCK_ERRORS.conflict('GROUP_BUY_PAYMENT_UNRECONCILED', '이 결제에 열린 대사 불일치(콘솔 직접 취소)가 있어요. 결제 › 대사 불일치에서 먼저 처리해 주세요.');
    if (!actions.refundMode) throw MOCK_ERRORS.conflict(actions.refundBlockedReason === 'SHIPPED' ? 'GROUP_BUY_REFUND_SHIPPED' : 'GROUP_BUY_REFUND_NOT_ALLOWED', actions.refundBlockedReason === 'SHIPPED' ? '배송 중인 주문은 운영 환불을 할 수 없어요. 배송완료 뒤 또는 반품 요청으로 처리해 주세요.' : '이 주문은 운영 환불을 할 수 없어요.');
    validateRefund(o, body, true);
    const preview = refundPreviewOf(o, body, flags);
    const waiting = preview.needsLarge && flags.has('no-large');
    const now = new Date().toISOString();
    o.refunds.push({ id: nextId('rf'), cause: 'ADMIN', method: 'PG_CANCEL', state: waiting ? 'AWAITING_APPROVAL' : 'PENDING', itemRefund: preview.itemRefund, shippingRefund: preview.shippingRefund, returnFee: 0, reshippingFee: preview.reshippingFee, reshippingRefund: 0, compensation: 0, total: preview.total, businessFault: body.businessFault, reason: body.reason.trim(), operator: OPERATOR, requestId: null, requestedAt: now, completedAt: null });
    if (!waiting) {
        o.adminRefundTotal = preview.adminRefundTotalAfter;
        o.refundedAmount += preview.total;
        o.amounts.refundedAmount = o.refundedAmount;
        o.amounts.remaining -= preview.total;
        o.amounts.reshippingFeeCharged += preview.reshippingFee;
        if (body.lines) {
            for (const l of body.lines) {
                const item = o.items.find((i) => i.id === l.itemId);
                if (item) item.canceledQty += l.qty;
            }
            if (o.items.every((i) => i.qty - i.canceledQty === 0)) {
                o.history.push({ from: o.status, to: 'CANCELED', actor: OPERATOR, note: '운영 환불', at: now });
                o.status = 'CANCELED';
            }
        } else if (o.amounts.remaining <= 0) {
            o.history.push({ from: o.status, to: 'REFUNDED', actor: OPERATOR, note: '운영 환불', at: now });
            o.status = 'REFUNDED';
        }
        o.totalQty = o.items.reduce((s, i) => s + i.qty - i.canceledQty - i.returnedQty, 0);
    }
    return { state: waiting ? 'AWAITING_APPROVAL' : 'QUEUED', order: presentOrder(o) };
}

function csvCell(value: string): string {
    // 수식 주입 막기(결정 20) — 서버가 하는 일을 목업도 흉내 낸다
    const safe = /^[=+\-@]/.test(value) ? `'${value}` : value;
    return `"${safe.replaceAll('"', '""')}"`;
}

export async function mockOrderExport(body: GroupBuyExportRequest): Promise<GroupBuyExportResult> {
    const flags = await guardWrite();
    requireReason(body.reason);
    const g = findGroupBuy(body.groupBuyId);
    if (flags.has('export-not-ready') || (g.fulfillment === 'AFTER_CLOSE' && !['ORDERED', 'SHIPPING'].includes(g.status))) throw MOCK_ERRORS.conflict('GROUP_BUY_EXPORT_NOT_READY', '마감 후 발주 공구는 발주 처리(ORDERED) 뒤에 명단을 내보낼 수 있어요.');
    const rows = ordersOf(g.id).filter((o) => o.status === 'CONFIRMED' || o.status === 'PREPARING');
    const header = ['주문번호', '받는 분', '연락처', '우편번호', '주소', '상세주소', '배송 메모', '품목'];
    const lines = rows.map((o) => [o.orderNo, `${o.member.nickname}(목업)`, '010-1234-5678', o.address.postcode, '서울 강남구 테헤란로 123', '45층', '=문 앞', o.itemSummary].map(csvCell).join(','));
    const blob = new Blob([`﻿${[header.map(csvCell).join(','), ...lines].join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
    return { fileName: `shipping-${g.id}-${mockDay(0)}.csv`, rowCount: rows.length, downloadUrl: URL.createObjectURL(blob) };
}

// ── 요청 ─────────────────────────────────────────

function findRequest(id: string): GroupBuyRequestDetail {
    const found = db().requests.find((r) => r.id === id);
    if (!found) throw MOCK_ERRORS.notFound('GROUP_BUY_REQUEST_NOT_FOUND', '요청을 찾을 수 없어요.');
    return found;
}

function requestActions(r: GroupBuyRequestDetail): GroupBuyRequestActions {
    const approved = r.status === 'APPROVED' && r.type === 'RETURN';
    return {
        canApprove: r.status === 'REQUESTED',
        canReject: r.status === 'REQUESTED',
        canSetPickup: approved && r.collectionMethod === 'COMPANY_PICKUP' && !r.receivedAt,
        canReceive: approved && !r.receivedAt,
        canComplete: approved && !!r.receivedAt && r.resolution === 'REFUND',
        canExchangeShip: approved && !!r.receivedAt && r.resolution === 'EXCHANGE',
        canConvertToRefund: approved && r.resolution === 'EXCHANGE',
        resellAllowed: r.groupBuy.status === 'RECRUITING',
    };
}

function presentRequest(r: GroupBuyRequestDetail): GroupBuyRequestDetail {
    const g = db().groupBuys.find((x) => x.id === r.groupBuy.id);
    if (g) r.groupBuy.status = g.status;
    return mockClone({ ...r, actions: requestActions(r) });
}

function closeOpenRequest(r: GroupBuyRequestDetail): void {
    const o = db().orders.find((x) => x.id === r.order.id);
    if (o && o.openRequest?.id === r.id) o.openRequest = null;
}

export async function mockRequestList(query: GroupBuyRequestQuery): Promise<GroupBuyRequestListResponse> {
    const flags = await guardRead('requests-fail');
    const q = query.q?.trim().toLowerCase() ?? '';
    const base = flags.has('requests-empty')
        ? []
        : db().requests.filter((r) => (!query.type || r.type === query.type) && (!query.level || r.deadlineLevel !== 'OK') && (!q || r.order.orderNo.toLowerCase().includes(q) || r.member.nickname.toLowerCase().includes(q) || r.member.id === q));
    const counts = Object.fromEntries(GROUP_BUY_REQUEST_STATUSES.map((s) => [s, base.filter((r) => r.status === s).length])) as GroupBuyRequestListResponse['counts'];
    // 오래 기다린 것이 위(기한이 급한 차례)
    const filtered = base.filter((r) => !query.status || r.status === query.status).sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    const start = query.page * query.size;
    return mockClone({ items: filtered.slice(start, start + query.size).map(presentRequest), total: filtered.length, counts });
}

export async function mockRequestDetail(id: string): Promise<GroupBuyRequestDetail> {
    await guardRead('request-detail-fail');
    return presentRequest(findRequest(id));
}

export async function mockRequestApprove(id: string, body: GroupBuyApproveRequest): Promise<GroupBuyRequestDetail> {
    const flags = await guardWrite();
    const r = findRequest(id);
    if (r.status !== 'REQUESTED') throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_NOT_OPEN', '이미 처리된 요청이에요.');
    const now = r.recalculated?.breakdown.total ?? r.previewAmount;
    if (flags.has('approve-changed')) throw MOCK_ERRORS.conflict('GROUP_BUY_REFUND_AMOUNT_CHANGED', '그 사이 환불 금액이 다시 바뀌었어요. 새로 불러와 확인해 주세요.', { previewAmount: r.previewAmount, recalculatedAmount: now + 1000 });
    if (body.expectedAmount !== now) throw MOCK_ERRORS.conflict('GROUP_BUY_REFUND_AMOUNT_CHANGED', '환불 금액이 화면과 달라요. 새로 불러와 확인해 주세요.', { previewAmount: r.previewAmount, recalculatedAmount: now });
    const at = new Date().toISOString();
    if (r.recalculated) {
        r.breakdown = r.recalculated.breakdown;
        r.recalculated = null;
    }
    r.handledBy = OPERATOR;
    r.handledAt = at;
    if (r.type === 'CANCEL') {
        r.status = 'COMPLETED';
        r.history.push({ status: 'COMPLETED', actor: OPERATOR, note: '발송 전 취소 승인 — 환불 요청을 PG 에 보냈어요', at });
        const o = db().orders.find((x) => x.id === r.order.id);
        if (o) {
            for (const ri of r.items) {
                const item = o.items.find((i) => i.id === ri.orderItemId);
                if (item) item.canceledQty += ri.qty;
            }
            o.refunds.push({ id: nextId('rf'), cause: 'REQUEST', method: 'PG_CANCEL', state: 'PENDING', itemRefund: r.breakdown.itemRefund, shippingRefund: r.breakdown.shippingRefund, returnFee: 0, reshippingFee: r.breakdown.reshippingFee, reshippingRefund: 0, compensation: 0, total: r.breakdown.total, businessFault: false, reason: null, operator: OPERATOR, requestId: r.id, requestedAt: at, completedAt: null });
            o.refundedAmount += r.breakdown.total;
            o.amounts.refundedAmount = o.refundedAmount;
            o.amounts.remaining -= r.breakdown.total;
        }
        closeOpenRequest(r);
    } else {
        r.status = 'APPROVED';
        r.history.push({ status: 'APPROVED', actor: OPERATOR, note: r.collectionMethod === 'COMPANY_PICKUP' ? '업체회수 — 택배 회수를 접수해 주세요' : '고객발송 — 반품 주소를 안내했어요', at });
    }
    return presentRequest(r);
}

export async function mockRequestReject(id: string, body: GroupBuyRejectRequest): Promise<GroupBuyRequestDetail> {
    await guardWrite();
    const r = findRequest(id);
    const fields: Record<string, string> = {};
    if (r.type === 'CANCEL' && body.reasonCode !== 'ALREADY_SHIPPED') fields.reasonCode = '발송 전 취소 요청은 「이미 발송함」으로만 거절할 수 있어요.';
    if (r.type === 'RETURN' && body.reasonCode === 'ALREADY_SHIPPED') fields.reasonCode = '반품 요청에는 쓸 수 없는 사유예요.';
    if (r.type === 'RETURN' && body.photoIds.length === 0) fields.photoIds = '거절 근거 사진을 1장 이상 올려 주세요.';
    if (body.photoIds.length > 3) fields.photoIds = '사진은 3장까지예요.';
    const problem = reasonProblem(body.reason);
    if (problem) fields.reason = problem;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (r.status !== 'REQUESTED') throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_NOT_OPEN', '이미 처리된 요청이에요.');
    if (body.reasonCode === 'USED_VALUE_LOSS' && !r.withdrawalRestricted) throw MOCK_ERRORS.conflict('GROUP_BUY_REJECT_REASON_NOT_ALLOWED', '이 공구에는 청약철회 제한 안내가 없어 「사용으로 가치 감소」로 거절할 수 없어요.');
    const at = new Date().toISOString();
    r.status = 'REJECTED';
    r.rejectReasonCode = body.reasonCode;
    r.rejectReason = body.reason.trim();
    r.rejectPhotos = body.photoIds.map((pid) => ({ id: pid, url: photos.get(pid) ?? mockImage('evidence', 90) }));
    r.handledBy = OPERATOR;
    r.handledAt = at;
    r.history.push({ status: 'REJECTED', actor: OPERATOR, note: body.reason.trim(), at });
    if (r.extraPayment && r.extraPayment.status === 'PAID') r.extraPayment = { ...r.extraPayment, status: 'REFUNDED', refundedAmount: r.extraPayment.amount };
    closeOpenRequest(r);
    return presentRequest(r);
}

const photos = new Map<string, string>();

export async function mockRequestPhotoUpload(file: File): Promise<GroupBuyEvidencePhoto> {
    await guardWrite();
    if (!file.type.startsWith('image/')) throw MOCK_ERRORS.validation({ file: '이미지만 올릴 수 있어요.' });
    if (file.size > 5 * 1024 * 1024) throw MOCK_ERRORS.validation({ file: '사진은 5MB 까지 올릴 수 있어요.' });
    const photo = { id: nextId('ph'), url: URL.createObjectURL(file) };
    photos.set(photo.id, photo.url);
    return photo;
}

export async function mockRequestPickup(id: string, body: GroupBuyPickupRequest): Promise<GroupBuyRequestDetail> {
    await guardWrite();
    const r = findRequest(id);
    const fields: Record<string, string> = {};
    if (!body.carrierCode) fields.carrierCode = '택배사를 골라 주세요.';
    if (!TRACKING_RE.test(body.trackingNo.replace(/[\s-]/g, ''))) fields.trackingNo = '송장번호는 숫자·영문 6~30자예요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (!requestActions(r).canSetPickup) throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_STATE', '지금은 회수 송장을 넣을 수 없어요.');
    r.pickup = { carrierCode: body.carrierCode, trackingNo: body.trackingNo.replace(/[\s-]/g, '') };
    r.history.push({ status: r.status, actor: OPERATOR, note: `회수 송장 ${carrierName(body.carrierCode)} ${r.pickup.trackingNo}`, at: new Date().toISOString() });
    return presentRequest(r);
}

export async function mockRequestReceive(id: string, body: GroupBuyReceiveRequest): Promise<GroupBuyRequestDetail> {
    await guardWrite();
    const r = findRequest(id);
    if (!body.receivedAt) throw MOCK_ERRORS.validation({ receivedAt: '반환받은 날을 골라 주세요.' });
    if (body.receivedAt > mockDay(0)) throw MOCK_ERRORS.validation({ receivedAt: '오늘 뒤의 날은 고를 수 없어요.' });
    if (!requestActions(r).canReceive) throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_STATE', '지금은 회수 확인을 할 수 없어요.');
    r.receivedAt = `${body.receivedAt}T03:00:00.000Z`;
    r.elapsedBusinessDays = 0;
    r.deadlineLevel = 'OK';
    r.history.push({ status: r.status, actor: OPERATOR, note: `회수 확인(반환받은 날 ${body.receivedAt}) — 3영업일 안에 환불해야 해요`, at: new Date().toISOString() });
    return presentRequest(r);
}

export async function mockRequestComplete(id: string, body: GroupBuyCompleteRequest): Promise<GroupBuyRequestDetail> {
    await guardWrite();
    const r = findRequest(id);
    const fields: Record<string, string> = {};
    for (const item of r.items) {
        const picked = body.dispositions.filter((d) => d.requestItemId === item.id);
        const sum = picked.reduce((s, d) => s + d.qty, 0);
        if (sum !== item.qty) fields[`dispositions.${item.id}`] = `처리 수량의 합이 반품 수량(${item.qty}개)과 같아야 해요.`;
    }
    if (body.compensationAmount < 0 || body.compensationAmount > r.compensationCap) fields.compensationAmount = `보상액은 0~${r.compensationCap.toLocaleString('ko-KR')}원이에요.`;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    for (const d of body.dispositions) {
        const item = r.items.find((i) => i.id === d.requestItemId);
        if (d.disposition === 'RESELL' && d.qty > 0 && (!requestActions(r).resellAllowed || item?.reasonCode !== 'CHANGE_OF_MIND')) throw MOCK_ERRORS.conflict('GROUP_BUY_NOT_RESELLABLE', '다시 판매는 모집 중인 공구의 단순 변심 반품만 고를 수 있어요.');
    }
    if (!requestActions(r).canComplete) throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_STATE', '회수 확인 뒤에 완료할 수 있어요.');
    const total = r.breakdown.total + body.compensationAmount;
    if (body.expectedAmount !== total) throw MOCK_ERRORS.conflict('GROUP_BUY_REFUND_AMOUNT_CHANGED', '환불 금액이 화면과 달라요. 새로 불러와 확인해 주세요.');
    const at = new Date().toISOString();
    for (const item of r.items) item.dispositions = body.dispositions.filter((d) => d.requestItemId === item.id && d.qty > 0).map((d) => ({ disposition: d.disposition, qty: d.qty }));
    r.compensationAmount = body.compensationAmount;
    r.breakdown = { ...r.breakdown, compensation: body.compensationAmount, total };
    r.status = 'COMPLETED';
    r.handledBy = OPERATOR;
    r.handledAt = at;
    r.history.push({ status: 'COMPLETED', actor: OPERATOR, note: '반품 완료 — 환불 요청을 PG 에 보냈어요', at });
    const o = db().orders.find((x) => x.id === r.order.id);
    if (o) {
        for (const ri of r.items) {
            const item = o.items.find((i) => i.id === ri.orderItemId);
            if (item) item.returnedQty += ri.qty;
        }
        o.refunds.push({ id: nextId('rf'), cause: 'REQUEST', method: 'PG_CANCEL', state: 'PENDING', itemRefund: r.breakdown.itemRefund, shippingRefund: r.breakdown.shippingRefund, returnFee: r.breakdown.returnFee, reshippingFee: r.breakdown.reshippingFee, reshippingRefund: r.breakdown.reshippingRefund, compensation: body.compensationAmount, total, businessFault: r.breakdown.operatorPaysReturnFee, reason: null, operator: OPERATOR, requestId: r.id, requestedAt: at, completedAt: null });
        o.refundedAmount += r.breakdown.total;
        o.amounts.refundedAmount = o.refundedAmount;
        o.amounts.compensationAmount += body.compensationAmount;
        o.amounts.remaining -= total;
        o.totalQty = o.items.reduce((s, i) => s + i.qty - i.canceledQty - i.returnedQty, 0);
        if (o.totalQty === 0) {
            o.history.push({ from: o.status, to: 'REFUNDED', actor: OPERATOR, note: '전체 반품', at });
            o.status = 'REFUNDED';
        }
    }
    closeOpenRequest(r);
    return presentRequest(r);
}

export async function mockRequestExchangeShip(id: string, body: GroupBuyExchangeShipRequest): Promise<GroupBuyRequestDetail> {
    await guardWrite();
    const r = findRequest(id);
    const fields: Record<string, string> = {};
    if (!body.carrierCode) fields.carrierCode = '택배사를 골라 주세요.';
    if (!TRACKING_RE.test(body.trackingNo.replace(/[\s-]/g, ''))) fields.trackingNo = '송장번호는 숫자·영문 6~30자예요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (!requestActions(r).canExchangeShip) throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_STATE', '회수 확인 뒤에 교환 재발송을 할 수 있어요.');
    const at = new Date().toISOString();
    r.exchangeShipment = { carrierCode: body.carrierCode, trackingNo: body.trackingNo.replace(/[\s-]/g, ''), shippedAt: at };
    r.status = 'COMPLETED';
    r.history.push({ status: 'COMPLETED', actor: OPERATOR, note: '교환 재발송(교환 출고 EXCHANGE_OUT 기록)', at });
    closeOpenRequest(r);
    return presentRequest(r);
}

export async function mockRequestConvertToRefund(id: string, reason: string): Promise<GroupBuyRequestDetail> {
    await guardWrite();
    requireReason(reason);
    const r = findRequest(id);
    if (!requestActions(r).canConvertToRefund) throw MOCK_ERRORS.conflict('GROUP_BUY_REQUEST_STATE', '교환 요청만 환불로 바꿀 수 있어요.');
    r.resolution = 'REFUND';
    const amount = r.items.reduce((s, i) => s + i.qty * 27000, 0);
    r.breakdown = { ...r.breakdown, itemRefund: amount, total: amount };
    r.items = r.items.map((i) => ({ ...i, refundAmount: i.qty * 27000 }));
    r.history.push({ status: r.status, actor: OPERATOR, note: `교환할 물건 없음 → 환불로 바꿈(회원 알림) — ${reason.trim()}`, at: new Date().toISOString() });
    return presentRequest(r);
}

export async function mockOrderCarriers(): Promise<GroupBuyCarrierChoice[]> {
    await guardRead();
    return ['CJ', 'HANJIN', 'LOTTE', 'EPOST', 'LOGEN'].map((code) => ({ code, name: carrierName(code) }));
}
