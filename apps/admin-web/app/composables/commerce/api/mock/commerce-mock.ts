// MOCK: 커머스 설정의 2b 구획(배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정)과 대시보드 커머스 칸의 메모리 목업 — `useCommerceSettingsApi`·`useCommerceDashboardApi` 가 부른다.
// 「요청 제한」은 이미 실제 서버라 여기 없다. 서버가 붙으면 이 폴더째 지운다. 새로고침하면 처음 값으로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   policies-fail   배송비 정책 읽기 500        carriers-fail   택배사 읽기 500          business-fail   사업자 정보 읽기 500
//   sales-fail      판매 스위치 읽기 500        gbsettings-fail 공구 설정 읽기 500       settings-save-fail 위 구획의 모든 저장 500
//   forbidden       위 구획의 모든 저장 403     sales-off       판매 꺼짐 + 체크리스트 일부만 체크
//   dashboard-fail  대시보드 500               dashboard-empty 대시보드 숫자 모두 0      dashboard-partial 결제 이상·요청 칸 권한 없음(null)
//
// ── 목업 가정 ─
//   · 공구 설정의 범위(최소~최대)는 계약에 선점 시간(3~30)만 있어 나머지는 목업이 정했다 — 서버가 `ranges` 로 준다.
//   · 판매 켜기 자동 조건 넷(사업자 정보·PG 키·택배사 1개 이상·본인인증)은 서버가 판정한다.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { CommerceDashboardView } from '~/composables/commerce/api/useCommerceDashboardApi.types';
import type {
    BusinessInfoValues,
    BusinessInfoView,
    CarrierList,
    CarrierSaveRequest,
    GroupBuySettingsNumberKey,
    GroupBuySettingsSaveRequest,
    GroupBuySettingsView,
    SalesChecklistKey,
    SalesSwitchView,
    SalesToggleRequest,
    ShippingPolicyCreateRequest,
    ShippingPolicyLine,
    ShippingPolicyList,
} from '~/composables/commerce/api/useCommerceSettingsApi.types';

const OPERATOR = '나(목업 운영자)';
const DAY = 86_400_000;
const at = (days: number) => new Date(Date.now() + days * DAY).toISOString();

async function read(flag: string): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has(flag)) throw MOCK_ERRORS.internal();
    return flags;
}

async function write(): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (flags.has('settings-save-fail')) throw MOCK_ERRORS.internal();
    return flags;
}

// ── 배송비 정책 ──
let policies: ShippingPolicyLine[] | null = null;
function policyDb(): ShippingPolicyLine[] {
    policies ??= [
        { id: 'sp-1', code: 'STANDARD', name: '기본 택배', baseFee: 3000, freeShippingOver: 50000, returnFee: 3000, returnFeeMode: 'ONE_WAY', effectiveFrom: at(-90), effectiveTo: at(30), started: true, current: true, createdBy: '최고 관리자', createdAt: at(-91) },
        { id: 'sp-2', code: 'STANDARD', name: '기본 택배', baseFee: 3500, freeShippingOver: 50000, returnFee: 3500, returnFeeMode: 'ONE_WAY', effectiveFrom: at(30), effectiveTo: null, started: false, current: false, createdBy: '운영자 박', createdAt: at(-2) },
        { id: 'sp-3', code: 'BULKY', name: '대형 화물', baseFee: 15000, freeShippingOver: null, returnFee: 15000, returnFeeMode: 'ROUND_TRIP', effectiveFrom: at(-30), effectiveTo: null, started: true, current: true, createdBy: '최고 관리자', createdAt: at(-31) },
    ];
    return policies;
}

export async function mockShippingPolicies(): Promise<ShippingPolicyList> {
    await read('policies-fail');
    return mockClone({ items: policyDb() });
}

export async function mockShippingPolicyCreate(body: ShippingPolicyCreateRequest): Promise<ShippingPolicyList> {
    await write();
    const fields: Record<string, string> = {};
    if (!/^[A-Z][A-Z0-9_]{1,29}$/.test(body.code)) fields.code = '코드는 영문 대문자·숫자·밑줄 2~30자예요(첫 글자는 영문).';
    if (!body.name.trim()) fields.name = '정책 이름을 입력해 주세요.';
    if (!Number.isInteger(body.baseFee) || body.baseFee < 0) fields.baseFee = '0 이상 정수로 입력해 주세요.';
    if (!Number.isInteger(body.returnFee) || body.returnFee < 0) fields.returnFee = '0 이상 정수로 입력해 주세요.';
    if (body.freeShippingOver !== null && (!Number.isInteger(body.freeShippingOver) || body.freeShippingOver < 0)) fields.freeShippingOver = '0 이상 정수로 입력해 주세요.';
    if (!body.effectiveFrom) fields.effectiveFrom = '적용 시작을 골라 주세요.';
    else if (body.effectiveFrom <= new Date().toISOString()) fields.effectiveFrom = '적용 시작은 지금보다 뒤여야 해요(이미 시작된 기간은 바꿀 수 없어요).';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const same = policyDb().filter((p) => p.code === body.code);
    if (same.some((p) => p.effectiveFrom === body.effectiveFrom)) throw MOCK_ERRORS.conflict('COMMERCE_SHIPPING_POLICY_OVERLAP', '같은 코드에 같은 시작 시각의 줄이 이미 있어요.');
    // 앞 줄의 끝을 새 줄의 시작으로 닫는다(겹치지 않게)
    const before = same.filter((p) => p.effectiveFrom < body.effectiveFrom).sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0];
    const after = same.filter((p) => p.effectiveFrom > body.effectiveFrom).sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom))[0];
    if (before) before.effectiveTo = body.effectiveFrom;
    policyDb().push({ id: `sp-${Date.now()}`, ...body, name: body.name.trim(), effectiveTo: after?.effectiveFrom ?? null, started: false, current: false, createdBy: OPERATOR, createdAt: new Date().toISOString() });
    policyDb().sort((a, b) => a.code.localeCompare(b.code) || a.effectiveFrom.localeCompare(b.effectiveFrom));
    return mockClone({ items: policyDb() });
}

export async function mockShippingPolicyDelete(id: string): Promise<ShippingPolicyList> {
    await write();
    const line = policyDb().find((p) => p.id === id);
    if (!line) throw MOCK_ERRORS.notFound('COMMERCE_SHIPPING_POLICY_NOT_FOUND', '배송비 정책 줄을 찾을 수 없어요.');
    if (line.started) throw MOCK_ERRORS.conflict('COMMERCE_SHIPPING_POLICY_STARTED', '적용이 시작된 줄은 지울 수 없어요. 새 줄을 더해 바꿔 주세요.');
    policies = policyDb().filter((p) => p.id !== id);
    const before = policies.filter((p) => p.code === line.code && p.effectiveTo === line.effectiveFrom)[0];
    if (before) before.effectiveTo = line.effectiveTo;
    return mockClone({ items: policies });
}

// ── 택배사 ──
let carriers: CarrierList | null = null;
function carrierDb(): CarrierList {
    carriers ??= {
        items: [
            { code: 'CJ', name: 'CJ대한통운', trackingUrlTemplate: 'https://trace.cjlogistics.com/next/tracking.html?wblNo={no}', active: true, used: true },
            { code: 'HANJIN', name: '한진택배', trackingUrlTemplate: 'https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?wblnum={no}', active: true, used: true },
            { code: 'LOTTE', name: '롯데택배', trackingUrlTemplate: 'https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={no}', active: true, used: true },
            { code: 'EPOST', name: '우체국택배', trackingUrlTemplate: 'https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1={no}', active: true, used: true },
            { code: 'LOGEN', name: '로젠택배', trackingUrlTemplate: 'https://www.ilogen.com/web/personal/trace/{no}', active: true, used: false },
            { code: 'KDEXP', name: '경동택배', trackingUrlTemplate: 'https://kdexp.com/service/delivery/etc/delivery.do?barcode={no}', active: false, used: false },
        ],
        updatedAt: at(-40),
        updatedBy: '최고 관리자',
    };
    return carriers;
}

export async function mockCarriers(): Promise<CarrierList> {
    await read('carriers-fail');
    return mockClone(carrierDb());
}

export async function mockCarriersSave(body: CarrierSaveRequest): Promise<CarrierList> {
    await write();
    const fields: Record<string, string> = {};
    const codes = new Set<string>();
    body.items.forEach((c, i) => {
        if (!/^[A-Z][A-Z0-9_]{1,19}$/.test(c.code)) fields[`items[${i}].code`] = '코드는 영문 대문자·숫자·밑줄 2~20자예요.';
        else if (codes.has(c.code)) fields[`items[${i}].code`] = '같은 코드가 있어요.';
        codes.add(c.code);
        if (!c.name.trim()) fields[`items[${i}].name`] = '이름을 입력해 주세요.';
        const count = c.trackingUrlTemplate.split('{no}').length - 1;
        if (!c.trackingUrlTemplate.startsWith('https://') || count !== 1) fields[`items[${i}].trackingUrlTemplate`] = '`https://` 로 시작하고 `{no}` 가 한 번 들어가야 해요.';
    });
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const removedUsed = carrierDb().items.filter((c) => c.used && !codes.has(c.code));
    if (removedUsed.length) throw MOCK_ERRORS.conflict('COMMERCE_CARRIER_IN_USE', `발송 이력이 있는 택배사(${removedUsed.map((c) => c.code).join(', ')})는 지울 수 없어요. 끄기만 할 수 있어요.`);
    if (!body.items.some((c) => c.active)) throw MOCK_ERRORS.validation({ items: '켜진 택배사가 하나는 있어야 해요(판매 켜기 조건).' });
    const used = new Set(carrierDb().items.filter((c) => c.used).map((c) => c.code));
    carriers = { items: body.items.map((c) => ({ ...c, name: c.name.trim(), used: used.has(c.code) })), updatedAt: new Date().toISOString(), updatedBy: OPERATOR };
    return mockClone(carriers);
}

// ── 사업자 정보 ──
let business: BusinessInfoView | null = null;
function businessDb(): BusinessInfoView {
    business ??= {
        values: {
            companyName: '주식회사 로보얌(시험)',
            representative: '홍길동',
            businessNo: '220-81-62517',
            mailOrderNo: '2026-서울강남-01234',
            mailOrderAuthority: '서울특별시 강남구청',
            address: '서울특별시 강남구 테헤란로 123, 45층',
            phone: '02-1234-5678',
            email: 'help@roboyam.test',
            businessHours: '평일 10:00~17:00(점심 12:00~13:00, 주말·공휴일 휴무)',
            privacyOfficer: '김보안(privacy@roboyam.test)',
            hostingProvider: 'Amazon Web Services, Inc.',
            shippingRefundPolicy: '공구 상품은 발송 예정일에 맞춰 보내 드려요. 단순 변심 반품은 배송완료 뒤 7일 안에 신청할 수 있고, 반품 배송비는 공구 상세에 적힌 금액이에요.',
        },
        ready: true,
        updatedAt: at(-20),
        updatedBy: '최고 관리자',
    };
    return business;
}

const REQUIRED: (keyof BusinessInfoValues)[] = ['companyName', 'representative', 'businessNo', 'mailOrderNo', 'mailOrderAuthority', 'address', 'phone', 'email'];

export async function mockBusinessInfo(): Promise<BusinessInfoView> {
    await read('business-fail');
    return mockClone(businessDb());
}

export async function mockBusinessInfoSave(values: BusinessInfoValues): Promise<BusinessInfoView> {
    await write();
    const fields: Record<string, string> = {};
    if (values.businessNo && !/^\d{3}-\d{2}-\d{5}$/.test(values.businessNo)) fields.businessNo = '사업자등록번호는 000-00-00000 모양이에요.';
    if (values.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) fields.email = '이메일 주소를 확인해 주세요.';
    if (Array.from(values.shippingRefundPolicy).length > 3000) fields.shippingRefundPolicy = '3,000자까지 적을 수 있어요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    business = { values: { ...values }, ready: REQUIRED.every((k) => values[k].trim() !== ''), updatedAt: new Date().toISOString(), updatedBy: OPERATOR };
    return mockClone(business);
}

// ── 판매 스위치 ──
let sales: SalesSwitchView | null = null;
function salesDb(flags: Set<string>): SalesSwitchView {
    if (sales) return sales;
    const off = flags.has('sales-off');
    const keys: SalesChecklistKey[] = ['BUSINESS_REG', 'MAIL_ORDER_REPORT', 'TERMS', 'PRIVACY_POLICY', 'PG_CONTRACT', 'RETURN_ADDRESS'];
    sales = {
        enabled: !off,
        autoChecks: [
            { key: 'BUSINESS_INFO', ok: true },
            { key: 'PG_KEYS', ok: true },
            { key: 'CARRIER', ok: true },
            { key: 'IDENTITY', ok: true },
        ],
        checklist: keys.map((key, i) => ({ key, checked: !off || i < 3, checkedBy: !off || i < 3 ? '최고 관리자' : null, checkedAt: !off || i < 3 ? at(-25 + i) : null })),
        canEnable: !off,
        changedAt: off ? null : at(-18),
        changedBy: off ? null : '최고 관리자',
    };
    return sales;
}

function refreshCanEnable(view: SalesSwitchView): void {
    view.canEnable = view.autoChecks.every((c) => c.ok) && view.checklist.every((c) => c.checked);
}

export async function mockSalesSwitch(): Promise<SalesSwitchView> {
    const flags = await read('sales-fail');
    const view = salesDb(flags);
    view.autoChecks = view.autoChecks.map((c) => (c.key === 'BUSINESS_INFO' ? { ...c, ok: businessDb().ready } : c.key === 'CARRIER' ? { ...c, ok: carrierDb().items.some((x) => x.active) } : c));
    refreshCanEnable(view);
    return mockClone(view);
}

export async function mockSalesChecklist(key: SalesChecklistKey, checked: boolean): Promise<SalesSwitchView> {
    const flags = await write();
    const view = salesDb(flags);
    view.checklist = view.checklist.map((c) => (c.key === key ? { key, checked, checkedBy: checked ? OPERATOR : null, checkedAt: checked ? new Date().toISOString() : null } : c));
    refreshCanEnable(view);
    return mockClone(view);
}

export async function mockSalesToggle(body: SalesToggleRequest): Promise<SalesSwitchView> {
    const flags = await write();
    const view = salesDb(flags);
    const length = Array.from(body.reason.trim()).length;
    if (length < 5 || length > 200) throw MOCK_ERRORS.validation({ reason: '사유를 5~200자로 적어 주세요.' });
    if (body.enabled && !view.canEnable) throw MOCK_ERRORS.conflict('COMMERCE_SALES_NOT_READY', '자동 조건과 운영 체크리스트가 모두 차야 판매를 켤 수 있어요.');
    view.enabled = body.enabled;
    view.changedAt = new Date().toISOString();
    view.changedBy = OPERATOR;
    return mockClone(view);
}

// ── 공구 설정 ──
const GB_DEFAULTS: Record<GroupBuySettingsNumberKey, number> = {
    holdMinutes: 5,
    thresholdPeriodMaxDays: 14,
    firstComePeriodMaxDays: 30,
    extensionMaxMinutes: 30,
    extensionWindowMinutes: 5,
    judgeGraceMinutes: 2,
    compensationCap: 10000,
    largeRefundThreshold: 100000,
    addressRevealMinutes: 10,
    expiredAddressRetentionDays: 30,
    shipWithinBusinessDays: 3,
    supplyChoiceBusinessDays: 3,
};
const GB_RANGES: GroupBuySettingsView['ranges'] = {
    holdMinutes: { min: 3, max: 30 },
    thresholdPeriodMaxDays: { min: 1, max: 30 },
    firstComePeriodMaxDays: { min: 1, max: 90 },
    extensionMaxMinutes: { min: 0, max: 120 },
    extensionWindowMinutes: { min: 1, max: 30 },
    judgeGraceMinutes: { min: 1, max: 10 },
    compensationCap: { min: 0, max: 100000 },
    largeRefundThreshold: { min: 0, max: 10000000 },
    addressRevealMinutes: { min: 1, max: 60 },
    expiredAddressRetentionDays: { min: 1, max: 180 },
    shipWithinBusinessDays: { min: 1, max: 10 },
    supplyChoiceBusinessDays: { min: 1, max: 10 },
};

let gbSettings: GroupBuySettingsView | null = null;
function gbDb(): GroupBuySettingsView {
    const year = new Date().getFullYear();
    gbSettings ??= {
        values: { ...GB_DEFAULTS, holidays: [`${year}-12-25`, `${year + 1}-01-01`, `${year + 1}-02-16`, `${year + 1}-02-17`, `${year + 1}-02-18`] },
        defaults: { ...GB_DEFAULTS },
        ranges: GB_RANGES,
        updatedAt: at(-10),
        updatedBy: '최고 관리자',
    };
    return gbSettings;
}

export async function mockGroupBuySettings(): Promise<GroupBuySettingsView> {
    await read('gbsettings-fail');
    return mockClone(gbDb());
}

export async function mockGroupBuySettingsSave(body: GroupBuySettingsSaveRequest): Promise<GroupBuySettingsView> {
    await write();
    const fields: Record<string, string> = {};
    for (const key of Object.keys(GB_RANGES) as GroupBuySettingsNumberKey[]) {
        const v = body.values[key];
        const r = GB_RANGES[key];
        if (!Number.isInteger(v) || v < r.min || v > r.max) fields[`values.${key}`] = `${r.min}부터 ${r.max} 사이로 입력해 주세요.`;
    }
    body.values.holidays.forEach((d, i) => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(d)) fields[`values.holidays[${i}]`] = '날짜 모양이 아니에요.';
    });
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    gbSettings = { ...gbDb(), values: { ...body.values, holidays: [...new Set(body.values.holidays)].sort() }, updatedAt: new Date().toISOString(), updatedBy: OPERATOR };
    return mockClone(gbSettings);
}

// ── 대시보드 ──
export async function mockCommerceDashboard(): Promise<CommerceDashboardView> {
    const flags = await read('dashboard-fail');
    if (flags.has('dashboard-empty')) {
        return { preparingWaiting: 0, openRequests: { total: 0, warn: 0, critical: 0 }, paymentAnomalies: 0, expiringHolds: 0, lowStockGroupBuys: 0, shipOverdue: 0, deliveryStale: { count: 0, days: 5 }, generatedAt: new Date().toISOString() };
    }
    const partial = flags.has('dashboard-partial');
    return {
        preparingWaiting: 5,
        openRequests: partial ? null : { total: 4, warn: 2, critical: 1 },
        paymentAnomalies: partial ? null : 2,
        expiringHolds: 3,
        lowStockGroupBuys: 2,
        shipOverdue: 3,
        deliveryStale: { count: 1, days: 5 },
        generatedAt: new Date().toISOString(),
    };
}
