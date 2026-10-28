import type {
    BusinessInfoValues,
    BusinessInfoView,
    CarrierList,
    CarrierSaveRequest,
    GroupBuySettingsSaveRequest,
    GroupBuySettingsView,
    RequestLimitsSaveRequest,
    RequestLimitsView,
    SalesChecklistKey,
    SalesSwitchView,
    SalesToggleRequest,
    ShippingPolicyCreateRequest,
    ShippingPolicyList,
} from '~/composables/commerce/api/useCommerceSettingsApi.types';

/**
 * 커머스 설정 API — 서버 주소 문자열(`/api/admin/commerce/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 정책: `docs/domain/commerce.md` 12-4·10-2(배송비 정책)·20절(판매 스위치), 계약 `docs/features/commerce-foundation-contract.md` 「0-5」·`commerce-groupbuy-contract.md` 결정 8·10·11·18.
 * 권한 `commerce.settings`(없으면 403 `PERMISSION_DENIED`) — 판매 **켜기**만 최고 관리자(서비스가 막는다). 쓰기는 CSRF, 바꾼 값은 서버가 감사(전→후)에 남긴다. 응답은 `Cache-Control: no-store`.
 *
 *  - `GET`·`PUT /api/admin/commerce/request-limits` → `RequestLimitsView`(실제 서버)
 *  - `GET  /api/admin/commerce/shipping-policies` · `POST`(새 줄) · `DELETE /{id}`(시작 전 줄만) → `ShippingPolicyList` · `409 SHIPPING_POLICY_CONFLICT`·`COMMERCE_SHIPPING_POLICY_STARTED` · 감사 `COMMERCE_SHIPPING_POLICY_*`
 *  - `GET`·`PUT /api/admin/commerce/carriers` → `CarrierList`(통째로 저장) · `409 COMMERCE_CARRIER_IN_USE`
 *  - `GET`·`PUT /api/admin/commerce/business-info` → `BusinessInfoView` · `400 fields.businessNo`(체크섬)
 *  - `GET  /api/admin/commerce/sales` · `POST /sales/checklist/{key} {checked}` · `POST /sales {enabled, reason}` → `SalesSwitchView` · `409 COMMERCE_SALES_NOT_READY` · 감사 `COMMERCE_SALES_TOGGLE`
 *  - `GET`·`PUT /api/admin/commerce/group-buy-settings` → `GroupBuySettingsView` · `400 fields.values.<칸>`
 *
 */
const PATH = {
    root: '/api/admin/commerce',
    requestLimits: () => `${PATH.root}/request-limits`,
    shippingPolicies: () => `${PATH.root}/shipping-policies`,
    shippingPolicy: (id: string) => `${PATH.root}/shipping-policies/${encodeURIComponent(id)}`,
    carriers: () => `${PATH.root}/carriers`,
    businessInfo: () => `${PATH.root}/business-info`,
    sales: () => `${PATH.root}/sales`,
    salesChecklist: (key: SalesChecklistKey) => `${PATH.root}/sales/checklist/${encodeURIComponent(key)}`,
    groupBuySettings: () => `${PATH.root}/group-buy-settings`,
};

export function useCommerceSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 요청 제한(종류별 1분 한도) */
        requestLimits: (): Promise<RequestLimitsView> => client.get<RequestLimitsView>(PATH.requestLimits()),

        /** 요청 제한 저장(네 종류를 한 번에) */
        saveRequestLimits: (body: RequestLimitsSaveRequest): Promise<RequestLimitsView> => client.put<RequestLimitsView>(PATH.requestLimits(), body),

        /** 배송비 정책 줄 목록 */
        shippingPolicies: (): Promise<ShippingPolicyList> =>
            client.get<ShippingPolicyList>(PATH.shippingPolicies()),

        /** 배송비 정책 새 줄(적용 시작이 미래) */
        addShippingPolicy: (body: ShippingPolicyCreateRequest): Promise<ShippingPolicyList> =>
            client.post<ShippingPolicyList>(PATH.shippingPolicies(), body),

        /** 시작 전 줄 지우기 */
        deleteShippingPolicy: (id: string): Promise<ShippingPolicyList> =>
            client.del<ShippingPolicyList>(PATH.shippingPolicy(id)),

        /** 택배사 목록 */
        carriers: (): Promise<CarrierList> =>
            client.get<CarrierList>(PATH.carriers()),

        /** 택배사 목록 통째로 저장 */
        saveCarriers: (body: CarrierSaveRequest): Promise<CarrierList> =>
            client.put<CarrierList>(PATH.carriers(), body),

        /** 사업자 정보 */
        businessInfo: (): Promise<BusinessInfoView> =>
            client.get<BusinessInfoView>(PATH.businessInfo()),

        saveBusinessInfo: (values: BusinessInfoValues): Promise<BusinessInfoView> =>
            client.put<BusinessInfoView>(PATH.businessInfo(), { values }),

        /** 판매 스위치·체크리스트 */
        salesSwitch: (): Promise<SalesSwitchView> =>
            client.get<SalesSwitchView>(PATH.sales()),

        setChecklist: (key: SalesChecklistKey, checked: boolean): Promise<SalesSwitchView> =>
            client.post<SalesSwitchView>(PATH.salesChecklist(key), { checked }),

        toggleSales: (body: SalesToggleRequest): Promise<SalesSwitchView> =>
            client.post<SalesSwitchView>(PATH.sales(), body),

        /** 공구 설정 */
        groupBuySettings: (): Promise<GroupBuySettingsView> =>
            client.get<GroupBuySettingsView>(PATH.groupBuySettings()),

        saveGroupBuySettings: (body: GroupBuySettingsSaveRequest): Promise<GroupBuySettingsView> =>
            client.put<GroupBuySettingsView>(PATH.groupBuySettings(), body),
    };
}
