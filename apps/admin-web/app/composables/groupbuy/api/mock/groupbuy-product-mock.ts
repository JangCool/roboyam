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
//   · 옵션 종류(사용자 결정 2026-10-28): 색상·사이즈·중량·용량·종류·과일·수량 + 숨긴 소재. 공구 목업의 축 이름을 종류 코드로 바꿔 상품을 만든다(「과일」→ FRUIT 등).
//   · 상품 ID · 단품 ID 는 따로 채번한다(단품 ID 에 상품 ID 를 붙이지 않는다) — 목업 단품 ID 는 5001 부터(공구 목업 `productSkuId` 와 같은 번호).

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockGroupBuyUses } from '~/composables/groupbuy/api/mock/groupbuy-mock';
import { PRODUCT_CATEGORY_OF, productIdOf, seedGroupBuys, supplyGuess } from '~/composables/groupbuy/api/mock/groupbuy-mock-data';
import type { GroupBuyDetail, GroupBuyNotice, GroupBuyPage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type {
    GroupBuyAddonSource,
    GroupBuyCategory,
    GroupBuyCategoryInput,
    GroupBuyOptionType,
    GroupBuyOptionTypeInput,
    GroupBuyProductDetail,
    GroupBuyProductQuery,
    GroupBuyProductRow,
    GroupBuyProductSaveRequest,
    GroupBuyProductSku,
} from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Db {
    categories: GroupBuyCategory[];
    optionTypes: { code: string; name: string; hidden: boolean }[];
    products: GroupBuyProductDetail[];
    seq: number;
}

let state: Db | null = null;
/** 새 단품 ID 채번(목업 — 서버는 시퀀스) */
let skuSeq = 0;

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

/** 옵션 종류 시드 — 이름 → 코드(공구 목업의 축 이름을 옮길 때도 쓴다) */
const OPTION_TYPES: { code: string; name: string; hidden: boolean }[] = [
    { code: 'COLOR', name: '색상', hidden: false },
    { code: 'SIZE', name: '사이즈', hidden: false },
    { code: 'WEIGHT', name: '중량', hidden: false },
    { code: 'VOLUME', name: '용량', hidden: false },
    { code: 'KIND', name: '종류', hidden: false },
    { code: 'FRUIT', name: '과일', hidden: false },
    { code: 'QUANTITY', name: '수량', hidden: false },
    { code: 'MATERIAL', name: '소재', hidden: true },
];

function typeCodeOf(name: string): string {
    return OPTION_TYPES.find((x) => x.name === name)?.code ?? 'KIND';
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
    const valueName = new Map(g.axes.flatMap((a) => a.values.map((v) => [v.id, v.name] as const)));
    const skus: GroupBuyProductSku[] = mains.map((s) => ({ skuId: s.productSkuId!, values: s.valueIds.map((id) => valueName.get(id) ?? ''), valueIds: [...s.valueIds], name: s.name || g.productName, defaultSupplyPrice: g.id === 'gb-105' ? null : supplyGuess(s.unitPrice), onSale: s.onSale }));
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
        normalPrice: g.normalPrice,
        normalPriceBasis: g.normalPriceBasis,
        normalPriceSource: g.normalPriceSource,
        withdrawalRestriction: g.withdrawalRestriction,
        optionTypeCodes: g.axes.map((a) => typeCodeOf(a.name)),
        optionNames: g.axes.map((a) => OPTION_TYPES.find((x) => x.code === typeCodeOf(a.name))!.name),
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
                normalPrice: s.unitPrice,
                normalPriceBasis: null,
                normalPriceSource: '',
                withdrawalRestriction: '',
                optionTypeCodes: [],
                optionNames: [],
                axes: [],
                skus: [{ skuId: s.productSkuId!, values: [], valueIds: [], name: s.name, defaultSupplyPrice: supplyGuess(s.unitPrice), onSale: true }],
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
    state = { categories, optionTypes: OPTION_TYPES.map((x) => ({ ...x })), products, seq: 500 };
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

function validate(body: GroupBuyProductSaveRequest, selfId: string | null): void {
    const fields: Record<string, string> = {};
    if (!body.name.trim() || body.name.trim().length > 100) fields.name = '상품 이름을 1~100자로 적어 주세요.';
    const c = db().categories.find((x) => x.id === body.categoryId);
    if (!c) fields.categoryId = '카테고리를 골라 주세요.';
    else if (db().categories.some((x) => x.parentId === c.id)) fields.categoryId = '아래 단 카테고리를 골라 주세요.';
    const S = UtilGroupBuy.SKU;
    const d = db();
    const types = new Map(d.optionTypes.map((x) => [x.code, x]));
    if (body.optionTypeCodes.length > S.axisMax) fields.optionTypeCodes = `옵션은 ${S.axisMax}개까지예요.`;
    else if (body.optionTypeCodes.some((c) => !types.has(c)) || new Set(body.optionTypeCodes).size !== body.optionTypeCodes.length) fields.optionTypeCodes = '옵션 종류를 다시 골라 주세요.';
    if (!body.skus.length) fields.skus = '단품을 하나 이상 넣어 주세요.';
    else if (body.skus.length > S.comboMax) fields.skus = `단품은 ${S.comboMax}개까지예요.`;
    else if (body.skus.some((x) => !x.name.trim())) fields.skus = '단품 이름을 넣어 주세요.';
    else if (UtilGroupBuy.repeatedNames(body.skus.map((x) => x.name)).length) fields.skus = '같은 이름의 단품이 있어요.';
    else if (body.optionTypeCodes.length && body.skus.some((x) => x.values.length !== body.optionTypeCodes.length || x.values.some((v) => !v.trim()))) fields.skus = '단품마다 옵션 값을 모두 넣어 주세요.';
    else if (body.optionTypeCodes.length && UtilGroupBuy.repeatedNames(body.skus.map((x) => x.values.map((v) => v.trim()).join('\u0001'))).length) fields.skus = '옵션 값이 같은 단품이 두 줄 있어요.';
    else if (!body.skus.some((x) => x.onSale)) fields.skus = '판매하는 단품이 하나 이상 있어야 해요.';
    if (body.skus.some((s) => s.defaultSupplyPrice !== null && s.defaultSupplyPrice < 0)) fields.skus = '공급가는 0 이상이에요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
}

function apply(p: GroupBuyProductDetail, body: GroupBuyProductSaveRequest): void {
    // 옵션 이름 · 단품의 옵션 값 → 축(값은 처음 나온 차례). ID 는 같은 이름이면 살린다
    const typeName = (code: string) => db().optionTypes.find((x) => x.code === code)?.name ?? code;
    const axes = body.optionTypeCodes.map((code, i) => {
        const name = typeName(code);
        const before = p.axes.find((x) => x.name === name);
        const values = [...new Set(body.skus.map((x) => (x.values[i] ?? '').trim()))];
        return { id: before?.id ?? nextId('pax'), name: name.trim(), sortOrder: i, values: values.map((v, j) => ({ id: before?.values.find((x) => x.name === v)?.id ?? nextId('pav'), name: v, sortOrder: j })) };
    });
    const skus: GroupBuyProductSku[] = body.skus.map((x) => {
        const values = x.values.map((v) => v.trim());
        return { skuId: x.skuId ?? String(5900 + ++skuSeq), values, valueIds: values.map((v, i) => axes[i]!.values.find((y) => y.name === v)!.id), name: x.name.trim(), defaultSupplyPrice: x.defaultSupplyPrice, onSale: x.onSale };
    });
    p.name = body.name.trim();
    p.categoryId = body.categoryId!;
    p.summary = body.summary;
    p.spec = body.spec;
    p.description = body.description;
    p.images = body.images.map((mediaId) => p.images.find((i) => i.mediaId === mediaId) ?? { mediaId, url: '', thumbUrl: '' });
    p.normalPrice = body.normalPrice;
    p.normalPriceBasis = body.normalPriceBasis;
    p.normalPriceSource = body.normalPriceSource;
    p.withdrawalRestriction = body.withdrawalRestriction;
    p.optionTypeCodes = [...body.optionTypeCodes];
    p.optionNames = body.optionTypeCodes.map(typeName);
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
    validate(body, null);
    const p: GroupBuyProductDetail = { id: nextId('pd'), name: '', categoryId: '', categoryPath: '', summary: '', spec: '', description: '', images: [], normalPrice: 0, normalPriceBasis: null, normalPriceSource: '', withdrawalRestriction: '', optionTypeCodes: [], optionNames: [], axes: [], skus: [], notices: [], structureLocked: false, groupBuys: [], version: -1, updatedAt: '' };
    apply(p, body);
    db().products.unshift(p);
    return present(p);
}

export async function mockProductUpdate(id: string, body: GroupBuyProductSaveRequest): Promise<GroupBuyProductDetail> {
    const flags = await guardWrite();
    const p = db().products.find((x) => x.id === id);
    if (!p) throw MOCK_ERRORS.notFound('GROUP_BUY_PRODUCT_NOT_FOUND', '상품을 찾을 수 없어요.');
    if (body.version !== p.version) throw MOCK_ERRORS.conflict('GROUP_BUY_VERSION_CONFLICT', '다른 운영자가 먼저 고쳤어요. 새로 불러온 뒤 다시 고쳐 주세요.');
    validate(body, p.id);
    // 모집 중 공구가 쓰면 이름·정상가·청약철회 제한·옵션 구성 잠금(서버 2b-2 — 설명·이미지·카테고리·고시는 언제든)
    const shape = (names: string[], list: { skuId: string | null; name: string; values: string[] }[]) => JSON.stringify([names, list.map((x) => [x.skuId, x.name.trim(), x.values])]);
    const locked = present(p).structureLocked;
    const lockedField =
        body.name.trim() !== p.name ? 'name' : body.normalPrice !== p.normalPrice ? 'normalPrice' : body.withdrawalRestriction.trim() !== p.withdrawalRestriction ? 'withdrawalRestriction' : shape(body.optionTypeCodes, body.skus) !== shape(p.optionTypeCodes, p.skus) ? 'options' : null;
    if (flags.has('product-locked') || (locked && lockedField)) {
        throw MOCK_ERRORS.conflict('GROUP_BUY_LOCKED_FIELD', '이 상품을 쓰는 공구가 모집 중이라 이름·정상가·청약철회 제한·옵션 구성은 바꿀 수 없어요.', { field: lockedField ?? 'options' });
    }
    const used = new Set(mockGroupBuyUses(p.id, p.skus.map((x) => x.skuId)).length ? p.skus.map((x) => x.skuId) : []);
    const dropped = p.skus.filter((x) => used.has(x.skuId) && !body.skus.some((y) => y.skuId === x.skuId));
    if (flags.has('product-sku-in-use') || dropped.length) throw MOCK_ERRORS.conflict('GROUP_BUY_PRODUCT_SKU_IN_USE', '공구가 쓰는 단품은 뺄 수 없어요. 판매를 끄거나 다음 공구부터 빼 주세요.', { skuNames: (dropped.length ? dropped : p.skus.slice(0, 1)).map((x) => x.name) });
    apply(p, body);
    return present(p);
}

/** 공구의 추가 구성상품으로 고를 단품 — 본 상품(`exceptProductId`)을 뺀 모든 상품의 판매 단품 */
export async function mockAddonSources(exceptProductId: string | null): Promise<GroupBuyAddonSource[]> {
    await mockDelay();
    return mockClone(
        db()
            .products.filter((p) => p.id !== exceptProductId)
            .flatMap((p) => p.skus.filter((s) => s.onSale).map((s): GroupBuyAddonSource => ({ productSkuId: s.skuId, productId: p.id, productName: p.name, name: p.skus.length > 1 ? `${p.name} (${s.name})` : p.name, imageUrl: p.images[0]?.thumbUrl ?? null, defaultSupplyPrice: s.defaultSupplyPrice }))),
    );
}

/** 공구 목업이 저장 때 읽는 상품(사본) — 없으면 null */
export function mockProductSnapshot(id: string): GroupBuyProductDetail | null {
    const p = db().products.find((x) => x.id === id);
    return p ? present(p) : null;
}

/** 상품 단품 ID → 그 상품·단품(추가 구성상품 이름·이미지·고시) */
export function mockProductOfSku(productSkuId: string): { product: GroupBuyProductDetail; sku: GroupBuyProductSku } | null {
    for (const product of db().products) {
        const sku = product.skus.find((x) => x.skuId === productSkuId);
        if (sku) return { product: mockClone(product), sku: mockClone(sku) };
    }
    return null;
}

/** 옵션 종류 — 쓰는 상품 수 · 이미 쓴 값(자동완성)을 붙인다 */
function presentTypes(): GroupBuyOptionType[] {
    const d = db();
    return d.optionTypes.map((x, i) => {
        const using = d.products.filter((p) => p.optionTypeCodes.includes(x.code));
        const used = using.flatMap((p) => p.skus.map((k) => k.values[p.optionTypeCodes.indexOf(x.code)] ?? '')).filter(Boolean);
        return { ...x, sortOrder: i, productCount: using.length, usedValues: [...new Set(used)] };
    });
}

export async function mockOptionTypeList(): Promise<GroupBuyOptionType[]> {
    await guardRead('option-types-fail');
    return mockClone(presentTypes());
}

export async function mockOptionTypeSave(items: GroupBuyOptionTypeInput[]): Promise<GroupBuyOptionType[]> {
    await guardWrite();
    const d = db();
    const counted = presentTypes();
    const fields: Record<string, string> = {};
    if (items.some((x) => !/^[A-Z][A-Z0-9_]{1,19}$/.test(x.code))) fields.optionTypes = '코드는 영문 대문자로 시작하는 영문 대문자·숫자·_ 2~20자예요.';
    else if (new Set(items.map((x) => x.code)).size !== items.length) fields.optionTypes = '같은 코드가 두 줄 있어요.';
    else if (items.some((x) => !x.name.trim() || x.name.trim().length > 20)) fields.optionTypes = '이름은 1~20자로 적어 주세요.';
    const removed = counted.filter((x) => !items.some((y) => y.code === x.code) && x.productCount > 0);
    if (removed.length) fields.optionTypes = `상품이 쓰는 옵션 종류는 지울 수 없어요: ${removed.map((x) => x.name).join(', ')}`;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    d.optionTypes = items.map((x) => ({ code: x.code, name: x.name.trim(), hidden: x.hidden }));
    // 이름이 바뀌면 그 종류를 쓰는 상품의 옵션 이름·축 이름도 따라간다
    for (const p of d.products) {
        p.optionNames = p.optionTypeCodes.map((c) => d.optionTypes.find((x) => x.code === c)?.name ?? c);
        p.axes = p.axes.map((a, i) => ({ ...a, name: p.optionNames[i] ?? a.name }));
    }
    return mockClone(presentTypes());
}
