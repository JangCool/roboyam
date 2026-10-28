// MOCK: 공구 상품·카테고리 API(결정 89)의 메모리 목업 — `useGroupBuyProductApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   products-fail     상품 목록 읽기 실패        products-empty   상품 없음
//   product-fail      상품 상세 읽기 실패        categories-fail  카테고리 읽기 실패
//   forbidden         쓰기 403                   save-fail        저장 500
//   product-locked    상품 저장이 늘 409 GROUP_BUY_PRODUCT_STRUCTURE_LOCKED(구성 잠금)
//
// ── 목업 규칙(가정 — 2b-2 확정 전) ─
//   · 상품은 공구 목업(`seedGroupBuys`)에서 만든다 — 공구마다 상품 하나(`pd-1xx`, 공구 gb-1xx 와 같은 번호), 공구 gb-102 의 추가 구성상품 둘은 따로 상품(`pd-201`·`pd-202`).
//   · 기본 공급가 = 판매가의 약 62%(100원 단위), gb-105 상품은 비워 둔다(모집 시작 409 를 보려고).
//   · 모집 중 공구가 쓰는 상품은 옵션 축·단품 구성 잠금(설명·이미지·고시는 고친다 — 진행 중 공구에도 바로 보인다는 것은 서버 몫, 목업은 표시만).
//   · 카테고리는 2단. 상품이 있는 카테고리는 지우지 못한다(`400 fields.categories`).

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockGroupBuyUses } from '~/composables/groupbuy/api/mock/groupbuy-mock';
import { PRODUCT_CATEGORY_OF, productIdOf, seedGroupBuys, supplyGuess } from '~/composables/groupbuy/api/mock/groupbuy-mock-data';
import type { GroupBuyDetail, GroupBuyNotice, GroupBuyPage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type {
    GroupBuyAddonSource,
    GroupBuyCategory,
    GroupBuyCategoryInput,
    GroupBuyProductDetail,
    GroupBuyProductQuery,
    GroupBuyProductRow,
    GroupBuyProductSaveRequest,
    GroupBuyProductSku,
} from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Db {
    categories: GroupBuyCategory[];
    products: GroupBuyProductDetail[];
    seq: number;
}

let state: Db | null = null;

function cat(id: string, parentId: string | null, name: string, sortOrder: number, hidden = false): GroupBuyCategory {
    return { id, parentId, name, sortOrder, hidden, productCount: 0 };
}

function seedCategories(): GroupBuyCategory[] {
    return [
        cat('ct-food', null, '식품', 0),
        cat('ct-rice', 'ct-food', '쌀·잡곡', 0),
        cat('ct-fruit', 'ct-food', '과일', 1),
        cat('ct-sea', 'ct-food', '수산', 2),
        cat('ct-kimchi', 'ct-food', '김치·반찬', 3),
        cat('ct-fashion', null, '패션', 1),
        cat('ct-clothes', 'ct-fashion', '의류', 0),
        cat('ct-shoes', 'ct-fashion', '신발', 1),
        cat('ct-digital', null, '디지털·로봇', 2),
        cat('ct-robot', 'ct-digital', '로봇 키트', 0),
        cat('ct-home', 'ct-digital', '생활 가전', 1),
        cat('ct-parts', 'ct-digital', '부품·소모품', 2),
        cat('ct-season', null, '시즌 특가', 3, true),
    ];
}

function pathOf(categories: GroupBuyCategory[], id: string): string {
    const c = categories.find((x) => x.id === id);
    if (!c) return '';
    const parent = c.parentId ? categories.find((x) => x.id === c.parentId) : null;
    return parent ? `${parent.name} › ${c.name}` : c.name;
}

/** 공구 → 상품(본 상품 단품·기본 양식만) */
function productOf(g: GroupBuyDetail, categories: GroupBuyCategory[]): GroupBuyProductDetail {
    const mains = g.skus.filter((s) => s.kind === 'MAIN');
    const mainNames = new Set(mains.map((s) => s.name));
    const id = productIdOf(g.id);
    const skus: GroupBuyProductSku[] = mains.map((s) => ({ skuId: `p${s.skuId}`, valueIds: [...s.valueIds], name: s.name || g.productName, baseSupplyPrice: g.id === 'gb-105' ? null : supplyGuess(s.unitPrice), onSale: s.onSale }));
    const categoryId = PRODUCT_CATEGORY_OF[g.id] ?? 'ct-robot';
    return {
        id,
        name: g.productName,
        categoryId,
        categoryPath: pathOf(categories, categoryId),
        summary: g.summary,
        spec: g.spec,
        description: g.description,
        images: g.images.map((i) => ({ ...i })),
        normalPriceBasis: g.normalPriceBasis,
        normalPriceSource: g.normalPriceSource,
        withdrawalRestriction: g.withdrawalRestriction,
        axes: mockClone(g.axes),
        skus,
        notices: g.notices.filter((n) => n.skuNames.every((name) => mainNames.has(name))).map((n) => mockClone(n)),
        structureLocked: false,
        groupBuys: [],
        version: 0,
        updatedAt: g.updatedAt,
    };
}

/** gb-102 의 추가 구성상품 → 따로 상품(그 상품의 고시 양식은 기본 양식으로) */
function addonProductsOf(g: GroupBuyDetail, categories: GroupBuyCategory[]): GroupBuyProductDetail[] {
    return g.skus
        .filter((s) => s.kind === 'ADDON')
        .map((s, i) => {
            const id = `pd-${201 + i}`;
            const notice = g.notices.find((n) => n.skuNames.includes(s.name));
            return {
                id,
                name: s.name,
                categoryId: 'ct-parts',
                categoryPath: pathOf(categories, 'ct-parts'),
                summary: `${g.productName} 전용 ${s.name}`,
                spec: '',
                description: `<p>${s.name} — ${g.productName} 와 함께 쓰는 별매 상품이에요.</p>`,
                images: s.imageUrl ? [{ mediaId: s.imageKey ?? `md-${id}`, url: s.imageUrl, thumbUrl: s.imageUrl }] : [],
                normalPriceBasis: null,
                normalPriceSource: '',
                withdrawalRestriction: '',
                axes: [],
                skus: [{ skuId: `p${s.skuId}`, valueIds: [], name: s.name, baseSupplyPrice: supplyGuess(s.unitPrice), onSale: true }],
                notices: notice ? [{ ...mockClone(notice), skuIds: [], skuNames: [] } as GroupBuyNotice] : [],
                structureLocked: false,
                groupBuys: [],
                version: 0,
                updatedAt: g.updatedAt,
            };
        });
}

function db(): Db {
    if (state) return state;
    const categories = seedCategories();
    const groupBuys = seedGroupBuys();
    const products = groupBuys.map((g) => productOf(g, categories));
    const g102 = groupBuys.find((g) => g.id === 'gb-102');
    if (g102) products.push(...addonProductsOf(g102, categories));
    state = { categories, products, seq: 500 };
    return state;
}

function nextId(prefix: string): string {
    const d = db();
    d.seq += 1;
    return `${prefix}-${d.seq}`;
}

/** 쓰는 공구·잠금·카테고리 수는 읽을 때마다 다시 센다 */
function present(p: GroupBuyProductDetail): GroupBuyProductDetail {
    const uses = mockGroupBuyUses(p.id, p.skus.map((s) => s.skuId));
    return mockClone({ ...p, categoryPath: pathOf(db().categories, p.categoryId), groupBuys: uses, structureLocked: uses.some((u) => u.status === 'RECRUITING') });
}

function counted(): GroupBuyCategory[] {
    const d = db();
    return d.categories.map((c) => ({ ...c, productCount: d.products.filter((p) => p.categoryId === c.id || d.categories.find((x) => x.id === p.categoryId)?.parentId === c.id).length }));
}

async function guardRead(failFlag: string): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('read-forbidden')) throw MOCK_ERRORS.forbidden();
    if (flags.has(failFlag)) throw MOCK_ERRORS.internal();
    return flags;
}

async function guardWrite(): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (flags.has('save-fail')) throw MOCK_ERRORS.internal();
    return flags;
}

export async function mockCategoryList(): Promise<GroupBuyCategory[]> {
    await guardRead('categories-fail');
    return mockClone(counted());
}

export async function mockCategorySave(items: GroupBuyCategoryInput[]): Promise<GroupBuyCategory[]> {
    await guardWrite();
    const d = db();
    const fields: Record<string, string> = {};
    if (items.some((c) => !c.name.trim() || c.name.trim().length > 30)) fields.categories = '카테고리 이름은 1~30자로 적어 주세요.';
    const removed = d.categories.filter((c) => !items.some((x) => x.id === c.id));
    const inUse = counted().filter((c) => removed.some((r) => r.id === c.id) && c.productCount > 0);
    if (inUse.length) fields.categories = `상품이 있는 카테고리는 지울 수 없어요: ${inUse.map((c) => c.name).join(', ')}`;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const idOf = new Map<string, string>();
    for (const c of items) idOf.set(c.tempKey, c.id ?? nextId('ct'));
    const order = new Map<string | null, number>();
    d.categories = items.map((c) => {
        const parentId = c.parent ? (idOf.get(c.parent) ?? c.parent) : null;
        const n = order.get(parentId) ?? 0;
        order.set(parentId, n + 1);
        return cat(idOf.get(c.tempKey)!, parentId, c.name.trim(), n, c.hidden);
    });
    return mockClone(counted());
}

export async function mockProductList(query: GroupBuyProductQuery): Promise<GroupBuyPage<GroupBuyProductRow>> {
    const flags = await guardRead('products-fail');
    if (flags.has('products-empty')) return { items: [], total: 0 };
    const d = db();
    const q = (query.q ?? '').trim().toLowerCase();
    const rows = d.products
        .filter((p) => !q || p.name.toLowerCase().includes(q))
        .filter((p) => !query.categoryId || p.categoryId === query.categoryId || d.categories.find((c) => c.id === p.categoryId)?.parentId === query.categoryId)
        .map((p): GroupBuyProductRow => {
            const v = present(p);
            return { id: v.id, name: v.name, categoryId: v.categoryId, categoryPath: v.categoryPath, thumbUrl: v.images[0]?.thumbUrl ?? null, skuCount: v.skus.length, groupBuyCount: v.groupBuys.length, recruitingCount: v.groupBuys.filter((u) => u.status === 'RECRUITING').length, updatedAt: v.updatedAt };
        });
    return { items: rows.slice(query.page * query.size, query.page * query.size + query.size), total: rows.length };
}

export async function mockProductDetail(id: string): Promise<GroupBuyProductDetail> {
    await guardRead('product-fail');
    const p = db().products.find((x) => x.id === id);
    if (!p) throw MOCK_ERRORS.notFound('GROUP_BUY_PRODUCT_NOT_FOUND', '상품을 찾을 수 없어요.');
    return present(p);
}

function validate(body: GroupBuyProductSaveRequest): void {
    const fields: Record<string, string> = {};
    if (!body.name.trim() || body.name.trim().length > 100) fields.name = '상품 이름을 1~100자로 적어 주세요.';
    const c = db().categories.find((x) => x.id === body.categoryId);
    if (!c) fields.categoryId = '카테고리를 골라 주세요.';
    else if (db().categories.some((x) => x.parentId === c.id)) fields.categoryId = '아래 단 카테고리를 골라 주세요.';
    if (body.axes.length > UtilGroupBuy.SKU.axisMax) fields.axes = `옵션 축은 ${UtilGroupBuy.SKU.axisMax}개까지예요.`;
    if (body.skus.some((s) => s.baseSupplyPrice !== null && s.baseSupplyPrice < 0)) fields.skus = '공급가는 0 이상이에요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
}

function apply(p: GroupBuyProductDetail, body: GroupBuyProductSaveRequest): void {
    const axes = body.axes.map((a, i) => {
        const before = p.axes.find((x) => x.name === a.name.trim());
        return { id: before?.id ?? nextId('pax'), name: a.name.trim(), sortOrder: i, values: a.values.map((v, j) => ({ id: before?.values.find((x) => x.name === v.trim())?.id ?? nextId('pav'), name: v.trim(), sortOrder: j })) };
    });
    const skus: GroupBuyProductSku[] = axes.length
        ? UtilGroupBuy.skuCombinations(axes.map((a) => a.values)).map((values) => {
              const name = UtilGroupBuy.skuMainName(values.map((v) => v.name));
              const input = body.skus.find((s) => UtilGroupBuy.skuMainName(s.values) === name);
              return { skuId: p.skus.find((s) => s.name === name)?.skuId ?? nextId('psku'), valueIds: values.map((v) => v.id), name, baseSupplyPrice: input?.baseSupplyPrice ?? null, onSale: input?.onSale ?? true };
          })
        : [{ skuId: p.skus.find((s) => s.valueIds.length === 0)?.skuId ?? nextId('psku'), valueIds: [], name: body.name.trim(), baseSupplyPrice: body.skus[0]?.baseSupplyPrice ?? null, onSale: true }];
    p.name = body.name.trim();
    p.categoryId = body.categoryId!;
    p.summary = body.summary;
    p.spec = body.spec;
    p.description = body.description;
    p.images = body.imageIds.map((mediaId) => p.images.find((i) => i.mediaId === mediaId) ?? { mediaId, url: '', thumbUrl: '' });
    p.normalPriceBasis = body.normalPriceBasis;
    p.normalPriceSource = body.normalPriceSource;
    p.withdrawalRestriction = body.withdrawalRestriction;
    p.axes = axes;
    p.skus = skus;
    p.notices = body.notices.map((n) => ({
        categoryCode: n.categoryCode,
        title: n.title,
        skuIds: [],
        skuNames: [...n.skuNames],
        values: n.values.filter((v): v is Extract<typeof v, { itemKey: string }> => 'itemKey' in v && v.itemKey !== null).map((v) => ({ itemKey: v.itemKey, value: v.value, unavailable: v.unavailable, unavailableReason: v.unavailableReason })),
        customItems: n.values.filter((v) => 'custom' in v && v.custom).map((v, i) => ({ name: (v as { name: string }).name, value: v.value, sortOrder: i })),
    })) as GroupBuyNotice[];
    p.version += 1;
    p.updatedAt = new Date().toISOString();
}

export async function mockProductCreate(body: GroupBuyProductSaveRequest): Promise<GroupBuyProductDetail> {
    await guardWrite();
    validate(body);
    const p: GroupBuyProductDetail = { id: nextId('pd'), name: '', categoryId: '', categoryPath: '', summary: '', spec: '', description: '', images: [], normalPriceBasis: null, normalPriceSource: '', withdrawalRestriction: '', axes: [], skus: [], notices: [], structureLocked: false, groupBuys: [], version: -1, updatedAt: '' };
    apply(p, body);
    db().products.unshift(p);
    return present(p);
}

export async function mockProductUpdate(id: string, body: GroupBuyProductSaveRequest): Promise<GroupBuyProductDetail> {
    const flags = await guardWrite();
    const p = db().products.find((x) => x.id === id);
    if (!p) throw MOCK_ERRORS.notFound('GROUP_BUY_PRODUCT_NOT_FOUND', '상품을 찾을 수 없어요.');
    if (body.version !== p.version) throw MOCK_ERRORS.conflict('GROUP_BUY_VERSION_CONFLICT', '다른 운영자가 먼저 고쳤어요. 새로 불러온 뒤 다시 고쳐 주세요.');
    validate(body);
    const shape = (axes: { name: string; values: string[] }[]) => JSON.stringify(axes);
    const structureChanged = shape(body.axes) !== shape(p.axes.map((a) => ({ name: a.name, values: a.values.map((v) => v.name) })));
    if (flags.has('product-locked') || (structureChanged && present(p).structureLocked)) {
        throw MOCK_ERRORS.conflict('GROUP_BUY_PRODUCT_STRUCTURE_LOCKED', '이 상품을 쓰는 공구가 모집 중이라 옵션 축·단품 구성은 바꿀 수 없어요. 새 단품은 다음 공구부터 넣어 주세요.');
    }
    apply(p, body);
    return present(p);
}

/** 공구의 추가 구성상품으로 고를 단품 — 본 상품(`exceptProductId`)을 뺀 모든 상품의 판매 단품 */
export async function mockAddonSources(exceptProductId: string | null): Promise<GroupBuyAddonSource[]> {
    await mockDelay();
    return mockClone(
        db()
            .products.filter((p) => p.id !== exceptProductId)
            .flatMap((p) => p.skus.filter((s) => s.onSale).map((s): GroupBuyAddonSource => ({ productSkuId: s.skuId, productId: p.id, productName: p.name, name: p.axes.length ? `${p.name} · ${s.name}` : p.name, imageUrl: p.images[0]?.thumbUrl ?? null, baseSupplyPrice: s.baseSupplyPrice }))),
    );
}
