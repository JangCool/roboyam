import { mockAddonSources, mockCategoryList, mockCategorySave, mockProductCreate, mockProductDetail, mockProductList, mockProductUpdate } from '~/composables/groupbuy/api/mock/groupbuy-product-mock';
import type { GroupBuyPage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type {
    GroupBuyAddonSource,
    GroupBuyCategory,
    GroupBuyCategoryInput,
    GroupBuyProductDetail,
    GroupBuyProductQuery,
    GroupBuyProductRow,
    GroupBuyProductSaveRequest,
} from '~/composables/groupbuy/api/useGroupBuyProductApi.types';

/**
 * 공구 상품·카테고리 관리자 API(결정 89) — 서버 주소 문자열(`/api/admin/group-buy-products…`·`/api/admin/group-buys/categories`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 모양은 2b-1 화면 목업의 제안이고 서버(2b-2)가 정한다. 권한: 조회 `commerce.product.read` · 등록·수정·카테고리 `commerce.product.manage`
 * · 기본 공급가는 `commerce.product.price` 또는 `commerce.settlement.read` 가 있을 때만 응답에 있다(없으면 null).
 *   - `GET  /api/admin/group-buys/categories` → `GroupBuyCategory[]` · `PUT` 통째로(`GroupBuyCategoryInput[]`) — 상품이 있는 카테고리 지우기는 `400 fields.categories`
 *   - `GET  /api/admin/group-buy-products?q=&categoryId=&page=&size=` → 목록 · `GET /{id}` → `GroupBuyProductDetail`
 *   - `POST /api/admin/group-buy-products` · `PUT /{id}` → 상세 · `400 VALIDATION fields.name|categoryId|axes|skus|notices[i]…` · `409 GROUP_BUY_PRODUCT_STRUCTURE_LOCKED`(모집 중 공구가 쓰는 상품의 옵션 구성) · `GROUP_BUY_VERSION_CONFLICT`
 *   - `GET  /api/admin/group-buy-products/addon-sources?except={productId}` → 공구의 추가 구성상품으로 고를 다른 상품의 단품
 *   (이미지 올리기는 공구 API 의 `POST /api/admin/group-buys/images` 를 같이 쓴다)
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/groupbuy-product-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    categories: '/api/admin/group-buys/categories',
    products: '/api/admin/group-buy-products',
    one: (id: string) => `/api/admin/group-buy-products/${encodeURIComponent(id)}`,
    addonSources: '/api/admin/group-buy-products/addon-sources',
};

export function useGroupBuyProductApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        categories: (): Promise<GroupBuyCategory[]> =>
            // MOCK:
            mockCategoryList(),
        // REAL: client.get<GroupBuyCategory[]>(PATH.categories),

        saveCategories: (items: GroupBuyCategoryInput[]): Promise<GroupBuyCategory[]> =>
            // MOCK:
            mockCategorySave(items),
        // REAL: client.put<GroupBuyCategory[]>(PATH.categories, items),

        list: (query: GroupBuyProductQuery): Promise<GroupBuyPage<GroupBuyProductRow>> =>
            // MOCK:
            mockProductList(query),
        // REAL: client.get<GroupBuyPage<GroupBuyProductRow>>(PATH.products, { query: { ...query } }),

        detail: (id: string): Promise<GroupBuyProductDetail> =>
            // MOCK:
            mockProductDetail(id),
        // REAL: client.get<GroupBuyProductDetail>(PATH.one(id)),

        create: (body: GroupBuyProductSaveRequest): Promise<GroupBuyProductDetail> =>
            // MOCK:
            mockProductCreate(body),
        // REAL: client.post<GroupBuyProductDetail>(PATH.products, body),

        update: (id: string, body: GroupBuyProductSaveRequest): Promise<GroupBuyProductDetail> =>
            // MOCK:
            mockProductUpdate(id, body),
        // REAL: client.put<GroupBuyProductDetail>(PATH.one(id), body),

        /** 공구의 추가 구성상품으로 고를 단품(본 상품은 뺀다) */
        addonSources: (exceptProductId: string | null): Promise<GroupBuyAddonSource[]> =>
            // MOCK:
            mockAddonSources(exceptProductId),
        // REAL: client.get<GroupBuyAddonSource[]>(PATH.addonSources, { query: { except: exceptProductId ?? undefined } }),
    };
}
