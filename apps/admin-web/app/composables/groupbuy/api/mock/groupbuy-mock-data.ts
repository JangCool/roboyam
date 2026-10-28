// MOCK: 공구 관리자 목업의 처음 데이터 — `groupbuy-mock.ts` 가 메모리에 올려 고친다(새로고침하면 처음으로). 서버가 붙으면 이 폴더째 지운다.
// 공구는 계약 결정 11 의 local 시드 넷(성사형 모집 중 · 선착순 즉시 발송(옵션 셋) · 마감 임박 · 다 팔려 끝난 1차) + DRAFT 하나 + 불발 하나(로봇 101~106)
// + 로봇 밖 예시 품목(결정 87 — 107 햅쌀 성사형 모집 중 · 108 사과·감귤 선착순(옵션마다 양식) · 109 고등어·전복 DRAFT(양식 다 채움) · 110 김치 다 팔림 · 111 티셔츠 선착순(옵션 넷, 양식 하나) · 112 러닝화 중단).
// 고시 양식 값은 서버 카탈로그(`notice-catalog-2023-01-01.json`)의 진짜 항목 키로 채웠다.
// 금액·수량은 서버가 계산했다고 치는 값이다(계산 규칙을 흉내 내지 않는다 — 화면은 보여 주기만).

import { mockNoticeCatalog } from '~/composables/groupbuy/api/mock/groupbuy-notice-catalog-mock';
import type { GroupBuyDetail, GroupBuyMember, GroupBuyMovement, GroupBuyNotice, GroupBuyRevision, NoticeItemValue } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type { GroupBuyOrderDetail, GroupBuyOrderItem } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type { GroupBuyRequestDetail } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const NOW = Date.now();
const MIN = 60_000;
const DAY = 86_400_000;

/** 지금부터 분 단위로 옮긴 순간 */
export function mockAt(minutes: number): string {
    return new Date(NOW + minutes * MIN).toISOString();
}

/** 지금부터 날 단위로 옮긴 한국 날짜 `YYYY-MM-DD` */
export function mockDay(days: number): string {
    const d = new Date(NOW + days * DAY + 9 * 60 * MIN);
    return d.toISOString().slice(0, 10);
}

/** 목업 이미지 — 바깥 주소 없이 SVG 데이터 주소 */
export function mockImage(label: string, hue: number): string {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="hsl(${hue},55%,82%)"/><text x="200" y="215" font-size="44" text-anchor="middle" fill="hsl(${hue},45%,30%)" font-family="sans-serif">${label}</text></svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

export const MEMBERS: Record<string, GroupBuyMember> = {
    m1: { id: '10021', nickname: '로봇덕후', withdrawn: false },
    m2: { id: '10044', nickname: '코딩맘', withdrawn: false },
    m3: { id: '10102', nickname: '메이커준', withdrawn: false },
    m4: { id: null, nickname: '탈퇴한 회원', withdrawn: true },
    m5: { id: '10230', nickname: '공구왕', withdrawn: false },
    m6: { id: '10311', nickname: '테크러버', withdrawn: false },
};

const NO_ACTIONS = { canStart: false, canCloseEarly: false, canCancel: false, canMarkOrdered: false, canCreateRound: false, roundBlockedReason: null, canDelayNotice: false, canSupplyFailure: false, canAdjustStock: false };

const CATALOG = mockNoticeCatalog();
const AS = '로보얌 고객센터 02-000-0000(평일 10~17시)';
const WARRANTY = '소비자분쟁해결기준(공정거래위원회 고시)에 따라 구입일로부터 1년 무상 수리 — 기준은 공정거래위원회 누리집에서 볼 수 있어요.';

/** 고시 양식 한 장 — 카탈로그 항목에 맞춘 빈 줄에 `filled` 를 채운다(글이면 값, 객체면 그 칸들). `customs` = 판매자 추가 항목 [이름, 내용] */
function notice(categoryCode: string, title: string, optionNames: string[], filled: Record<string, string | Partial<NoticeItemValue>>, customs: [string, string][] = []): GroupBuyNotice {
    const items = CATALOG.categories.find((c) => c.code === categoryCode)?.items ?? [];
    const values = UtilGroupBuy.noticeValuesFor(items).map((v) => {
        const f = filled[v.itemKey];
        return f === undefined ? v : typeof f === 'string' ? { ...v, value: f } : { ...v, ...f };
    });
    return { categoryCode, title, optionNames, values, customItems: customs.map(([name, value], sortOrder) => ({ name, value, sortOrder })) };
}

const STANDARD_SNAPSHOT = { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' } as const;

/** 예시 공구 한 건 — 자주 같은 칸은 기본값, 필요한 칸만 덮어쓴다 */
function seed(over: Partial<GroupBuyDetail> & Pick<GroupBuyDetail, 'id' | 'status' | 'title' | 'productName'>): GroupBuyDetail {
    return {
        closeReason: null,
        roundNo: 1,
        roundRootId: over.id,
        rounds: [],
        summary: '',
        spec: '',
        description: '',
        images: [],
        normalPrice: 0,
        groupPrice: 0,
        normalPriceBasis: null,
        normalPriceSource: '',
        options: [],
        minQty: 0,
        fulfillment: 'IMMEDIATE',
        maxQty: 100,
        initialMaxQty: 100,
        addedQty: 0,
        currentQty: 0,
        reservedQty: 0,
        maxQtyPerUser: null,
        roundLimitShared: false,
        startAt: mockAt(-2 * 1440),
        endAt: mockAt(5 * 1440),
        extensionMode: 'DEMAND',
        extendedUntil: null,
        closingAt: null,
        shippingPolicyCode: 'STANDARD',
        shippingSnapshot: { ...STANDARD_SNAPSHOT },
        shipPlannedAt: null,
        shipWithinBusinessDays: 3,
        withdrawalRestriction: '',
        preShipOrderCount: 0,
        version: 1,
        revisionNo: 1,
        notices: [],
        actions: NO_ACTIONS,
        delayNotices: [],
        supplyFailures: [],
        createdBy: '운영자 김',
        createdAt: mockAt(-5 * 1440),
        updatedAt: mockAt(-1 * 1440),
        ...over,
    };
}

function img(id: string, label: string, hue: number) {
    const url = mockImage(label, hue);
    return { mediaId: id, url, thumbUrl: url };
}

export function seedGroupBuys(): GroupBuyDetail[] {
    return [
        {
            id: 'gb-101',
            status: 'RECRUITING',
            closeReason: null,
            roundNo: 1,
            roundRootId: 'gb-101',
            rounds: [],
            title: '로봇 청소기 R7 공동구매',
            productName: '로보클린 R7 물걸레 로봇 청소기',
            summary: '물걸레 자동 세척·건조, 라이다 매핑. 30대 모이면 성사돼요.',
            spec: '크기 350×350×97mm\n흡입력 6,000Pa\n배터리 5,200mAh\n제조국 대한민국',
            description: '<p>물걸레 자동 세척·건조까지 되는 R7 을 공동구매가로 모아요.</p><p>성사되면 마감 뒤 한 번에 발주해 보내 드려요.</p>',
            images: [img('md-1011', 'R7', 210), img('md-1012', 'R7 dock', 200)],
            normalPrice: 459000,
            groupPrice: 389000,
            normalPriceBasis: 'OFFICIAL_STORE',
            normalPriceSource: '제조사 공식몰 2026-10-20 판매가 캡처(사내 드라이브)',
            options: [],
            minQty: 30,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 100,
            initialMaxQty: 100,
            addedQty: 0,
            currentQty: 18,
            reservedQty: 2,
            maxQtyPerUser: 2,
            roundLimitShared: false,
            startAt: mockAt(-3 * 1440),
            endAt: mockAt(4 * 1440),
            extensionMode: 'DEMAND',
            extendedUntil: null,
            closingAt: null,
            shippingPolicyCode: 'STANDARD',
            shippingSnapshot: { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' },
            shipPlannedAt: mockDay(21),
            shipWithinBusinessDays: null,
            withdrawalRestriction: '',
            preShipOrderCount: 18,
            version: 3,
            revisionNo: 2,
            notices: [
                notice('HOME_APPLIANCES', '로봇 청소기', [], {
                    product_model_name: '로보클린 R7 / RC-R7W',
                    kc_certification: '안전인증 HU071234-25001 · 전파 R-R-RBC-R7W',
                    rated_voltage_power: 'DC 14.4V · 55W(도크 AC 220V 60Hz · 30W)',
                    energy_efficiency_grade: { unavailable: true, unavailableReason: '에너지소비효율등급 표시대상 기자재가 아니에요(효율관리기자재 아님) — 해당 없음.' },
                    model_release_month: '2026년 3월',
                    manufacturer_importer: '로보클린(주)',
                    country_of_manufacture: '대한민국',
                    dimensions_capacity_form: '본체 350×350×97mm · 먼지통 0.4L · 원형',
                    additional_installation_cost: '없음(도크를 콘센트에 꽂기만 하면 돼요)',
                    quality_warranty: WARRANTY,
                    after_service_contact: AS,
                }, [['호환 기종', '로보클린 앱 — 안드로이드 10 · iOS 15 이상']]),
            ],
            actions: NO_ACTIONS,
            delayNotices: [],
            supplyFailures: [],
            createdBy: '운영자 김',
            createdAt: mockAt(-6 * 1440),
            updatedAt: mockAt(-2 * 1440),
        },
        {
            id: 'gb-102',
            status: 'RECRUITING',
            closeReason: null,
            roundNo: 1,
            roundRootId: 'gb-102',
            rounds: [],
            title: '교육용 코딩 로봇 키트 선착순',
            productName: '코딩봇 미니 v2 (3색)',
            summary: '재고 보유 · 결제 후 3영업일 안에 보내요. 색상 셋 중 골라 담으세요.',
            spec: '대상 8세 이상\n블록 코딩 앱 지원\n충전식 배터리',
            description: '<p>재고를 확보해 둔 선착순 공구예요. 결제하시면 바로 준비해 보내 드려요.</p><p>한정 수량으로 준비했어요.</p>',
            images: [img('md-1021', 'Codingbot', 140), img('md-1022', 'Black', 0), img('md-1023', 'White', 60)],
            normalPrice: 89000,
            groupPrice: 25000,
            normalPriceBasis: 'PREVIOUS_SALE',
            normalPriceSource: '자사몰 2026-08 정가 판매 기록',
            options: [
                { id: 'op-1', name: '블랙', extraPrice: 0, maxQty: 40, initialMaxQty: 40, currentQty: 33, reservedQty: 2 },
                { id: 'op-2', name: '화이트', extraPrice: 0, maxQty: 40, initialMaxQty: 30, currentQty: 40, reservedQty: 0 },
                { id: 'op-3', name: '레드(한정)', extraPrice: 2000, maxQty: 40, initialMaxQty: 40, currentQty: 38, reservedQty: 2 },
                { id: 'op-4', name: '전용 충전 도크(별매)', extraPrice: 19000, maxQty: 20, initialMaxQty: 20, currentQty: 0, reservedQty: 0 },
            ],
            minQty: 0,
            fulfillment: 'IMMEDIATE',
            maxQty: 120,
            initialMaxQty: 110,
            addedQty: 10,
            currentQty: 111,
            reservedQty: 4,
            maxQtyPerUser: 5,
            roundLimitShared: false,
            startAt: mockAt(-5 * 1440),
            endAt: mockAt(9 * 1440),
            extensionMode: 'DEMAND',
            extendedUntil: null,
            closingAt: null,
            shippingPolicyCode: 'STANDARD',
            shippingSnapshot: { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' },
            shipPlannedAt: null,
            shipWithinBusinessDays: 3,
            withdrawalRestriction: '포장을 뜯어 블록을 조립한 경우 상품 가치가 크게 떨어져 단순 변심 반품이 제한될 수 있어요(하자·오배송은 제외).',
            preShipOrderCount: 6,
            version: 7,
            revisionNo: 4,
            notices: [
                notice('SMALL_ELECTRONICS', '본체', [], {
                    product_model_name: '코딩봇 미니 v2 / CBM-200',
                    kc_certification: '어린이제품 안전확인 CB061R123-2501 · 전파 R-C-CBT-CBM200',
                    rated_voltage_power: 'DC 3.7V 리튬이온 1,200mAh · 충전 5V 1A',
                    model_release_month: '2026년 5월',
                    manufacturer_importer: '코딩봇(주) / 수입자 로보얌상사',
                    country_of_manufacture: '중국',
                    dimensions_weight: '120×95×80mm · 310g',
                    main_spec: '상세페이지 참조',
                    quality_warranty: WARRANTY,
                    after_service_contact: AS,
                }, [['호환 기종', '블록 코딩 앱 — 안드로이드 9 · iPadOS 15 이상 · 크롬북']]),
                notice('SMALL_ELECTRONICS', '전용 충전 도크', ['전용 충전 도크(별매)'], {
                    product_model_name: '코딩봇 충전 도크 / CBD-10',
                    kc_certification: '안전확인 YU07123-26002',
                    rated_voltage_power: 'AC 100~240V 50/60Hz · 출력 5V 2A',
                    model_release_month: '2026년 4월',
                    manufacturer_importer: '코딩봇(주) / 수입자 로보얌상사',
                    country_of_manufacture: '중국',
                    dimensions_weight: '150×110×40mm · 180g',
                    main_spec: '본체 2대 동시 충전 · 충전 표시등',
                    quality_warranty: WARRANTY,
                    after_service_contact: AS,
                }),
            ],
            actions: NO_ACTIONS,
            delayNotices: [],
            supplyFailures: [],
            createdBy: '운영자 박',
            createdAt: mockAt(-8 * 1440),
            updatedAt: mockAt(-1 * 1440),
        },
        {
            id: 'gb-103',
            status: 'RECRUITING',
            closeReason: null,
            roundNo: 1,
            roundRootId: 'gb-103',
            rounds: [],
            title: '휴머노이드 로봇 팔 키트 (마감 임박)',
            productName: '6축 로봇 팔 교육 키트',
            summary: '10대 모이면 성사 · 마감 뒤 발주해요.',
            spec: '서보모터 6개\n아두이노 호환\n조립 설명서 포함',
            description: '<p>6축 로봇 팔을 직접 조립해 보는 키트예요.</p>',
            images: [img('md-1031', 'Arm', 30)],
            normalPrice: 129000,
            groupPrice: 99000,
            normalPriceBasis: 'MARKET',
            normalPriceSource: '오픈마켓 3곳 평균(2026-10-25)',
            options: [],
            minQty: 10,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 50,
            initialMaxQty: 50,
            addedQty: 0,
            currentQty: 47,
            reservedQty: 1,
            maxQtyPerUser: 3,
            roundLimitShared: false,
            startAt: mockAt(-7 * 1440),
            endAt: mockAt(40),
            extensionMode: 'DEMAND',
            extendedUntil: null,
            closingAt: null,
            shippingPolicyCode: 'STANDARD',
            shippingSnapshot: { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' },
            shipPlannedAt: mockDay(14),
            shipWithinBusinessDays: null,
            withdrawalRestriction: '',
            preShipOrderCount: 21,
            version: 2,
            revisionNo: 1,
            notices: [
                notice('CHILDREN_PRODUCTS', '로봇 팔 키트', [], {
                    product_model_name: '6축 로봇 팔 교육 키트 / ARM-6E',
                    kc_certification: '어린이제품 공급자적합성확인 CB113A045-2601',
                    dimensions_weight: '조립 때 높이 380mm · 1.2kg',
                    color: '화이트 · 블루',
                    material: 'ABS 수지 · 알루미늄',
                    age_range: '14세 이상(14세 미만은 보호자와 함께)',
                    size_weight_limit: { unavailable: true, unavailableReason: '착용·탑승용 제품이 아니라 크기·체중 제한이 없어요 — 해당 없음.' },
                    model_release_month: '2025년 12월',
                    manufacturer_importer: '메이커랩(주)',
                    country_of_manufacture: '대한민국',
                    handling_safety: '작은 부품을 삼키지 않게 하세요. 서보모터가 움직일 때 손가락을 넣지 마세요.',
                    quality_warranty: WARRANTY,
                    after_service_contact: AS,
                }, [['호환 기종', '아두이노 우노 R3 호환 보드 · 스크래치 3.0']]),
            ],
            actions: NO_ACTIONS,
            delayNotices: [],
            supplyFailures: [],
            createdBy: '운영자 김',
            createdAt: mockAt(-9 * 1440),
            updatedAt: mockAt(-7 * 1440),
        },
        {
            id: 'gb-104',
            status: 'CLOSED_SUCCESS',
            closeReason: 'SOLD_OUT',
            roundNo: 1,
            roundRootId: 'gb-104',
            rounds: [],
            title: 'AI 반려 로봇 미니 1차',
            productName: '펫봇 미니 (그레이·핑크)',
            summary: '20대 모이면 성사 · 다 팔려 마감됐어요.',
            spec: '음성 인식\n표정 디스플레이\nUSB-C 충전',
            description: '<p>1차 공구는 다 팔렸어요. 다음 차수 알림을 받아 보세요.</p><p>※ 1차 발송 예정일은 11월 둘째 주예요.</p>',
            images: [img('md-1041', 'Petbot', 300), img('md-1042', 'Pink', 330)],
            normalPrice: 199000,
            groupPrice: 149000,
            normalPriceBasis: 'OFFICIAL_STORE',
            normalPriceSource: '수입사 공식 판매가',
            options: [
                { id: 'op-41', name: '그레이', extraPrice: 0, maxQty: 30, initialMaxQty: 30, currentQty: 30, reservedQty: 0 },
                { id: 'op-42', name: '핑크', extraPrice: 5000, maxQty: 30, initialMaxQty: 30, currentQty: 30, reservedQty: 0 },
            ],
            minQty: 20,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 60,
            initialMaxQty: 60,
            addedQty: 0,
            currentQty: 60,
            reservedQty: 0,
            maxQtyPerUser: 2,
            roundLimitShared: true,
            startAt: mockAt(-12 * 1440),
            endAt: mockAt(-2 * 1440),
            extensionMode: 'DEMAND',
            extendedUntil: null,
            closingAt: mockAt(-3 * 1440),
            shippingPolicyCode: 'STANDARD',
            shippingSnapshot: { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' },
            shipPlannedAt: mockDay(-1),
            shipWithinBusinessDays: null,
            withdrawalRestriction: '',
            preShipOrderCount: 34,
            version: 4,
            revisionNo: 2,
            notices: [
                notice('SMALL_ELECTRONICS', '펫봇 미니', [], {
                    product_model_name: '펫봇 미니 / PB-M1',
                    kc_certification: '전파 R-R-PBT-PBM1 · 안전확인 YU07999-25010',
                    rated_voltage_power: 'DC 3.7V · 충전 5V 2A(USB-C)',
                    model_release_month: '2025년 11월',
                    manufacturer_importer: '펫봇 테크 / 수입자 로보얌상사',
                    country_of_manufacture: '대만',
                    dimensions_weight: '110×90×105mm · 420g',
                    main_spec: '음성 인식 · 2.4인치 표정 디스플레이 · 배터리 6시간',
                    quality_warranty: WARRANTY,
                    after_service_contact: AS,
                }),
            ],
            actions: NO_ACTIONS,
            delayNotices: [],
            supplyFailures: [],
            createdBy: '운영자 박',
            createdAt: mockAt(-15 * 1440),
            updatedAt: mockAt(-3 * 1440),
        },
        {
            id: 'gb-105',
            status: 'DRAFT',
            closeReason: null,
            roundNo: 1,
            roundRootId: 'gb-105',
            rounds: [],
            title: '자율주행 RC카 키트',
            productName: '오토RC 스타터',
            summary: '',
            spec: '',
            description: '<p>작성 중이에요.</p>',
            images: [],
            normalPrice: 159000,
            groupPrice: 119000,
            normalPriceBasis: null,
            normalPriceSource: '',
            options: [],
            minQty: 15,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 80,
            initialMaxQty: null,
            addedQty: 0,
            currentQty: 0,
            reservedQty: 0,
            maxQtyPerUser: 2,
            roundLimitShared: false,
            startAt: mockAt(2 * 1440),
            endAt: mockAt(12 * 1440),
            extensionMode: 'DEMAND',
            extendedUntil: null,
            closingAt: null,
            shippingPolicyCode: 'STANDARD',
            shippingSnapshot: null,
            shipPlannedAt: null,
            shipWithinBusinessDays: null,
            withdrawalRestriction: '',
            preShipOrderCount: 0,
            version: 1,
            revisionNo: 0,
            // 일부 항목이 비어 모집 시작이 409 GROUP_BUY_NOTICE_INCOMPLETE 로 막힌다(「제공 불가」인데 사유가 빈 칸 하나 포함)
            notices: [
                notice('CHILDREN_PRODUCTS', '오토RC 스타터', [], {
                    product_model_name: '오토RC 스타터 / ARC-S1',
                    dimensions_weight: '260×180×120mm · 900g',
                    color: '옐로',
                    age_range: '10세 이상',
                    manufacturer_importer: '오토RC(주)',
                    country_of_manufacture: { unavailable: true, unavailableReason: '' },
                    quality_warranty: '상세페이지 참고',
                }),
            ],
            actions: NO_ACTIONS,
            delayNotices: [],
            supplyFailures: [],
            createdBy: '운영자 김',
            createdAt: mockAt(-1 * 1440),
            updatedAt: mockAt(-1 * 1440),
        },
        {
            id: 'gb-106',
            status: 'CLOSED_FAILED',
            closeReason: 'TIME_UP',
            roundNo: 1,
            roundRootId: 'gb-106',
            rounds: [],
            title: '로봇 강아지 프로',
            productName: '독봇 프로',
            summary: '50대 모이면 성사 — 모이지 않아 불발됐어요.',
            spec: '',
            description: '<p>불발된 공구예요. 결제하신 분께 전액 환불했어요.</p>',
            images: [img('md-1061', 'Dogbot', 260)],
            normalPrice: 990000,
            groupPrice: 790000,
            normalPriceBasis: 'OFFICIAL_STORE',
            normalPriceSource: '공식몰',
            options: [],
            minQty: 50,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 100,
            initialMaxQty: 100,
            addedQty: 0,
            currentQty: 12,
            reservedQty: 0,
            maxQtyPerUser: 1,
            roundLimitShared: false,
            startAt: mockAt(-20 * 1440),
            endAt: mockAt(-6 * 1440),
            extensionMode: 'DEMAND',
            extendedUntil: null,
            closingAt: mockAt(-6 * 1440),
            shippingPolicyCode: 'STANDARD',
            shippingSnapshot: { shippingFee: 3000, freeShippingOver: 50000, returnShippingFee: 3000, returnFeeMode: 'ONE_WAY' },
            shipPlannedAt: mockDay(-2),
            shipWithinBusinessDays: null,
            withdrawalRestriction: '',
            preShipOrderCount: 0,
            version: 2,
            revisionNo: 1,
            notices: [
                notice('SMALL_ELECTRONICS', '독봇 프로', [], {
                    product_model_name: '독봇 프로 / DB-P3',
                    kc_certification: '전파 R-R-DBT-DBP3',
                    rated_voltage_power: 'DC 25.2V · 충전 AC 220V',
                    model_release_month: '2026년 6월',
                    manufacturer_importer: '독봇 로보틱스 / 수입자 로보얌상사',
                    country_of_manufacture: '중국',
                    dimensions_weight: '700×310×400mm · 15kg',
                    main_spec: '12자유도 보행 · 라이다',
                    quality_warranty: WARRANTY,
                    after_service_contact: AS,
                }),
            ],
            actions: NO_ACTIONS,
            delayNotices: [],
            supplyFailures: [],
            createdBy: '운영자 박',
            createdAt: mockAt(-22 * 1440),
            updatedAt: mockAt(-6 * 1440),
        },
        // ── 로봇 밖 예시 품목(사용자 지시 2026-10-28 — 쌀·과일·생선·김치·옷·신발) ──
        seed({
            id: 'gb-107',
            status: 'RECRUITING',
            title: '이천 햅쌀 10kg 산지 공동구매',
            productName: '2026년 햅쌀 추청 10kg',
            summary: '50포가 모이면 성사 · 마감 뒤 도정해 보내요.',
            spec: '품종 추청(아키바레)\n중량 10kg\n도정 발송 3일 전',
            description: '<p>올가을 수확한 햅쌀을 산지에서 바로 모아요.</p><p>성사되면 마감 뒤 도정해 보내 드려요.</p>',
            images: [img('md-1071', '햅쌀 10kg', 45)],
            normalPrice: 52000,
            groupPrice: 42900,
            normalPriceBasis: 'MARKET',
            normalPriceSource: '농산물 유통 정보 2026-10 산지 시세(사내 메모)',
            minQty: 50,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 200,
            initialMaxQty: 200,
            currentQty: 37,
            reservedQty: 1,
            maxQtyPerUser: 4,
            startAt: mockAt(-2 * 1440),
            endAt: mockAt(6 * 1440),
            shipPlannedAt: mockDay(10),
            shipWithinBusinessDays: null,
            notices: [
                notice('AGRI_LIVESTOCK_FISHERY', '햅쌀 10kg', [], {
                    item_name: '쌀(멥쌀) · 추청 단일 품종',
                    package_contents: '10kg × 1포',
                    producer_importer: '햇살들영농조합법인(경기 이천)',
                    origin: '국내산(경기도 이천시)',
                    manufactured_date_expiry: '도정일로부터 30일 안에 드시기를 권해요 — 발송 3일 전 도정(도정일은 포장에 표시)',
                    agri_fishery_labeling: '유전자변형농산물 아님 · 지리적 표시 등록 품목 아님',
                    livestock_labeling: { unavailable: true, unavailableReason: '축산물이 아니에요 — 해당 없음.' },
                    imported_food_declaration: { unavailable: true, unavailableReason: '국내산이라 수입 신고 대상이 아니에요 — 해당 없음.' },
                    composition: '쌀 10kg 1포(지퍼 포장)',
                    storage_handling: '직사광선을 피해 서늘한 곳(15℃ 이하)에 두고, 개봉 뒤에는 밀폐해 냉장 보관을 권해요.',
                    safety_cautions: '습기가 차면 곰팡이·쌀벌레가 생길 수 있어요.',
                    consumer_contact: AS,
                }, [['도정일', '발송 3일 전 도정 — 포장 앞면에 표시'], ['등급', '특 등급(품위 검사 결과)']]),
            ],
        }),
        seed({
            id: 'gb-108',
            status: 'RECRUITING',
            title: '제철 사과·감귤 선착순',
            productName: '부사 사과 5kg · 노지 감귤 5kg',
            summary: '산지 창고 재고 · 결제 다음 날 수확·선별해 보내요.',
            spec: '사과 5kg(14~16과) · 감귤 5kg(S~M)',
            description: '<p>제철 과일을 산지에서 바로 보내요. 사과와 감귤은 다른 농가 상품이라 정보를 따로 적었어요.</p>',
            images: [img('md-1081', '사과', 0), img('md-1082', '감귤', 30)],
            normalPrice: 39000,
            groupPrice: 23900,
            normalPriceBasis: 'PREVIOUS_SALE',
            normalPriceSource: '자사몰 2025-11 판매 기록',
            options: [
                { id: 'op-81', name: '사과 5kg (부사)', extraPrice: 6000, maxQty: 80, initialMaxQty: 80, currentQty: 52, reservedQty: 2 },
                { id: 'op-82', name: '감귤 5kg (노지)', extraPrice: 0, maxQty: 80, initialMaxQty: 80, currentQty: 31, reservedQty: 0 },
            ],
            minQty: 0,
            fulfillment: 'IMMEDIATE',
            maxQty: 160,
            initialMaxQty: 160,
            currentQty: 83,
            reservedQty: 2,
            maxQtyPerUser: 3,
            shipWithinBusinessDays: 2,
            withdrawalRestriction: '신선식품이라 받은 뒤 시간이 지나 가치가 크게 떨어지면 단순 변심 반품이 제한될 수 있어요(상한 것·오배송은 제외).',
            // 별개 상품 둘 — 기본 양식 없이 옵션마다 양식(모든 옵션이 어느 양식에 든다)
            notices: [
                notice('AGRI_LIVESTOCK_FISHERY', '사과', ['사과 5kg (부사)'], {
                    item_name: '사과(부사)',
                    package_contents: '5kg(14~16과) 1상자',
                    producer_importer: '청송골과수농원(경북 청송)',
                    origin: '국내산(경상북도 청송군)',
                    manufactured_date_expiry: '수확 후 3일 이내 발송 · 받은 날부터 냉장 2주 안에 드시기를 권해요',
                    agri_fishery_labeling: '유전자변형농산물 아님',
                    livestock_labeling: { unavailable: true, unavailableReason: '축산물이 아니에요 — 해당 없음.' },
                    imported_food_declaration: { unavailable: true, unavailableReason: '국내산이라 해당 없어요.' },
                    composition: '사과 14~16과 · 완충재',
                    storage_handling: '0~4℃ 냉장 보관 · 다른 과일과 따로 두세요(에틸렌).',
                    safety_cautions: '껍질째 드실 때는 흐르는 물에 씻어 주세요.',
                    consumer_contact: AS,
                }, [['당도(Brix)', '13Brix 이상 비파괴 선별']]),
                notice('AGRI_LIVESTOCK_FISHERY', '감귤', ['감귤 5kg (노지)'], {
                    item_name: '감귤(노지 온주밀감)',
                    package_contents: '5kg(S~M 크기, 약 50~60과) 1상자',
                    producer_importer: '한라바람농장(제주 서귀포)',
                    origin: '국내산(제주특별자치도 서귀포시)',
                    manufactured_date_expiry: '수확 후 3일 이내 발송 · 받은 날부터 서늘한 곳 7일',
                    agri_fishery_labeling: '유전자변형농산물 아님',
                    livestock_labeling: { unavailable: true, unavailableReason: '축산물이 아니에요 — 해당 없음.' },
                    imported_food_declaration: { unavailable: true, unavailableReason: '국내산이라 해당 없어요.' },
                    composition: '감귤 5kg · 완충재',
                    storage_handling: '서늘하고 바람이 통하는 곳에 두고, 무른 것은 골라 먼저 드세요.',
                    safety_cautions: '왁스 처리를 하지 않았어요.',
                    consumer_contact: AS,
                }, [['당도(Brix)', '11Brix 이상 선별']]),
            ],
        }),
        seed({
            id: 'gb-109',
            status: 'DRAFT',
            title: '손질 고등어·활전복 산지 직송',
            productName: '손질 고등어 1kg(냉동) · 활전복 1kg(냉장)',
            summary: '30세트가 모이면 성사 · 마감 뒤 출하해요.',
            spec: '고등어 순살 1kg(6~8팩) · 활전복 1kg(10~12미)',
            description: '<p>작성 중 — 고시 양식은 다 채웠어요. 모집 시작을 눌러 보세요.</p>',
            images: [img('md-1091', '고등어', 200), img('md-1092', '전복', 170)],
            normalPrice: 45000,
            groupPrice: 24900,
            normalPriceBasis: 'MARKET',
            normalPriceSource: '수산물 산지 시세 2026-10',
            options: [
                { id: 'op-91', name: '손질 고등어 1kg (냉동)', extraPrice: 0, maxQty: 60, initialMaxQty: null, currentQty: 0, reservedQty: 0 },
                { id: 'op-92', name: '활전복 1kg (냉장)', extraPrice: 12000, maxQty: 40, initialMaxQty: null, currentQty: 0, reservedQty: 0 },
            ],
            minQty: 30,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 100,
            initialMaxQty: null,
            startAt: mockAt(3 * 1440),
            endAt: mockAt(10 * 1440),
            shippingSnapshot: null,
            shipPlannedAt: mockDay(14),
            shipWithinBusinessDays: null,
            revisionNo: 0,
            notices: [
                notice('AGRI_LIVESTOCK_FISHERY', '손질 고등어', ['손질 고등어 1kg (냉동)'], {
                    item_name: '고등어(순살 손질 · 냉동)',
                    package_contents: '1kg(6~8팩, 팩당 130~170g)',
                    producer_importer: '바다마루수산(부산) / 수입자 동일',
                    origin: '노르웨이산',
                    manufactured_date_expiry: '냉동(-18℃ 이하) 제조일로부터 12개월 — 발송일 기준 남은 기간 9개월 이상인 제품만 보내요',
                    agri_fishery_labeling: '유전자변형수산물 아님',
                    livestock_labeling: { unavailable: true, unavailableReason: '축산물이 아니에요 — 해당 없음.' },
                    imported_food_declaration: '수입식품안전관리 특별법에 따른 수입신고를 필함',
                    composition: '고등어 순살 6~8팩 · 아이스팩',
                    storage_handling: '-18℃ 이하 냉동 보관 · 해동한 것은 다시 얼리지 마세요.',
                    safety_cautions: '가시가 남아 있을 수 있어요. 고등어 알레르기가 있으면 드시지 마세요.',
                    consumer_contact: AS,
                }, [['손질 방법', '머리·내장·가운데 뼈 제거, 소금 간 없음']]),
                notice('AGRI_LIVESTOCK_FISHERY', '활전복', ['활전복 1kg (냉장)'], {
                    item_name: '전복(활 · 양식)',
                    package_contents: '1kg(10~12미)',
                    producer_importer: '완도청해전복(전남 완도)',
                    origin: '국내산(전라남도 완도군)',
                    manufactured_date_expiry: '출하 당일 발송 · 받은 뒤 냉장 3일 안에 드시기를 권해요',
                    agri_fishery_labeling: '유전자변형수산물 아님 · 지리적 표시 등록 품목 아님',
                    livestock_labeling: { unavailable: true, unavailableReason: '축산물이 아니에요 — 해당 없음.' },
                    imported_food_declaration: { unavailable: true, unavailableReason: '국내산이라 해당 없어요.' },
                    composition: '활전복 10~12미 · 해초 · 아이스팩',
                    storage_handling: '0~5℃ 냉장 · 젖은 해초로 덮어 두세요.',
                    safety_cautions: '익혀 드시기를 권해요. 패류 알레르기가 있으면 드시지 마세요.',
                    consumer_contact: AS,
                }),
            ],
        }),
        seed({
            id: 'gb-110',
            status: 'CLOSED_SUCCESS',
            closeReason: 'SOLD_OUT',
            title: '김장 포기김치 선착순',
            productName: '전라도식 포기김치 3kg · 5kg',
            summary: '다 팔려 마감됐어요.',
            spec: '배추 국내산 · 고춧가루 국내산',
            description: '<p>준비한 수량이 다 팔렸어요. 다음 차수 알림을 받아 보세요.</p>',
            images: [img('md-1101', '포기김치', 5)],
            normalPrice: 32000,
            groupPrice: 25900,
            normalPriceBasis: 'OFFICIAL_STORE',
            normalPriceSource: '제조사 공식몰 판매가',
            // 같은 상품의 무게만 다른 옵션 — 기본 양식 하나로 충분하다
            options: [
                { id: 'op-101', name: '포기김치 3kg', extraPrice: 0, maxQty: 60, initialMaxQty: 60, currentQty: 60, reservedQty: 0 },
                { id: 'op-102', name: '포기김치 5kg', extraPrice: 14000, maxQty: 40, initialMaxQty: 40, currentQty: 40, reservedQty: 0 },
            ],
            minQty: 0,
            fulfillment: 'IMMEDIATE',
            maxQty: 100,
            initialMaxQty: 100,
            currentQty: 100,
            maxQtyPerUser: 2,
            startAt: mockAt(-9 * 1440),
            endAt: mockAt(-3 * 1440),
            closingAt: mockAt(-4 * 1440),
            shipWithinBusinessDays: 2,
            notices: [
                notice('PROCESSED_FOOD', '포기김치', [], {
                    product_name: '전라도식 포기김치',
                    food_type: '배추김치',
                    producer_location: '남도손맛식품(주) · 전라남도 해남군',
                    manufactured_date_expiry: '소비기한 제조일로부터 냉장 30일 — 주문일 기준 3일 안에 만든 제품을 보내요',
                    package_contents: '3kg 또는 5kg(옵션)',
                    ingredients: '배추 75%(국내산), 무(국내산), 고춧가루 5%(국내산), 마늘, 멸치액젓, 새우젓, 찹쌀풀',
                    nutrition: { unavailable: true, unavailableReason: '영양성분 표시대상 식품이 아니라 따로 분석하지 않았어요 — 해당 없음.' },
                    gmo_labeling: { unavailable: true, unavailableReason: '유전자변형 원료를 쓰지 않아 표시 대상이 아니에요.' },
                    safety_cautions: '새우·멸치·대두가 들어 있어요. 숙성 중 가스가 생겨 포장이 부풀 수 있어요.',
                    imported_food_declaration: { unavailable: true, unavailableReason: '국내 제조 식품이라 해당 없어요.' },
                    consumer_contact: AS,
                }, [['보관 방법', '0~4℃ 냉장 · 꺼낸 뒤에는 꾹 눌러 공기를 빼 두세요.']]),
            ],
        }),
        seed({
            id: 'gb-111',
            status: 'RECRUITING',
            title: '오버핏 코튼 티셔츠 선착순',
            productName: '20수 코튼 오버핏 티셔츠',
            summary: '색상 2 · 사이즈 2 — 결제 후 3영업일 안에 보내요.',
            spec: '면 100% · 20수 싱글 저지',
            description: '<p>기본 반팔 티셔츠예요. 색상·사이즈만 다른 같은 상품이라 고시 양식은 하나예요.</p>',
            images: [img('md-1111', 'T-shirt', 220), img('md-1112', 'Black', 0)],
            normalPrice: 25000,
            groupPrice: 15900,
            normalPriceBasis: 'PREVIOUS_SALE',
            normalPriceSource: '자사몰 2026-06 정가 판매 기록',
            options: [
                { id: 'op-111', name: '화이트 M', extraPrice: 0, maxQty: 50, initialMaxQty: 50, currentQty: 41, reservedQty: 1 },
                { id: 'op-112', name: '화이트 L', extraPrice: 0, maxQty: 50, initialMaxQty: 50, currentQty: 28, reservedQty: 0 },
                { id: 'op-113', name: '블랙 M', extraPrice: 0, maxQty: 50, initialMaxQty: 50, currentQty: 50, reservedQty: 0 },
                { id: 'op-114', name: '블랙 L', extraPrice: 0, maxQty: 50, initialMaxQty: 50, currentQty: 17, reservedQty: 0 },
            ],
            minQty: 0,
            fulfillment: 'IMMEDIATE',
            maxQty: 200,
            initialMaxQty: 200,
            currentQty: 136,
            reservedQty: 1,
            maxQtyPerUser: 5,
            notices: [
                notice('CLOTHING', '오버핏 티셔츠', [], {
                    material: '면 100%',
                    color: '화이트 · 블랙(옵션)',
                    size: 'M(가슴단면 56cm · 총장 72cm) · L(가슴단면 59cm · 총장 75cm)',
                    manufacturer_importer: '로보얌어패럴(주)',
                    country_of_manufacture: '대한민국',
                    washing_handling_cautions: '30℃ 이하 손세탁 또는 세탁망 사용 · 표백제 금지 · 그늘에 뉘어 말려 주세요.',
                    manufactured_month: '2026년 8월 ~ 9월 생산분',
                    quality_warranty: '소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해요 — 봉제 불량은 받은 날부터 30일 안에 교환·환불.',
                    after_service_contact: AS,
                }, [['사이즈 표', 'M 어깨 52 · 소매 22 / L 어깨 55 · 소매 23 (cm)']]),
            ],
        }),
        seed({
            id: 'gb-112',
            status: 'CANCELED',
            closeReason: 'CANCELED',
            title: '러닝화 성사형 공구',
            productName: '쿠션 러닝화 RX-1',
            summary: '공급처 사정으로 운영자가 중단했어요.',
            spec: '겉감 메시 · 밑창 EVA',
            description: '<p>중단된 공구예요. 결제하신 분께 전액 환불했어요.</p>',
            images: [img('md-1121', 'Running', 260)],
            normalPrice: 89000,
            groupPrice: 64000,
            normalPriceBasis: 'OFFICIAL_STORE',
            normalPriceSource: '제조사 공식몰',
            options: [
                { id: 'op-121', name: '240', extraPrice: 0, maxQty: 20, initialMaxQty: 20, currentQty: 4, reservedQty: 0 },
                { id: 'op-122', name: '250', extraPrice: 0, maxQty: 20, initialMaxQty: 20, currentQty: 6, reservedQty: 0 },
                { id: 'op-123', name: '260', extraPrice: 0, maxQty: 20, initialMaxQty: 20, currentQty: 9, reservedQty: 0 },
                { id: 'op-124', name: '270', extraPrice: 0, maxQty: 20, initialMaxQty: 20, currentQty: 5, reservedQty: 0 },
                { id: 'op-125', name: '280', extraPrice: 0, maxQty: 20, initialMaxQty: 20, currentQty: 2, reservedQty: 0 },
            ],
            minQty: 40,
            fulfillment: 'AFTER_CLOSE',
            maxQty: 100,
            initialMaxQty: 100,
            currentQty: 26,
            maxQtyPerUser: 2,
            startAt: mockAt(-10 * 1440),
            endAt: mockAt(-1 * 1440),
            closingAt: mockAt(-2 * 1440),
            shipPlannedAt: mockDay(7),
            shipWithinBusinessDays: null,
            notices: [
                notice('SHOES', '러닝화', [], {
                    main_material: '겉감 폴리에스터 메시 · 안감 폴리에스터 · 밑창 EVA·고무',
                    color: '라이트 그레이',
                    size: '발길이 240~280mm(10mm 간격 — 옵션)',
                    manufacturer_importer: '런앤런(주) / 수입자 동일',
                    country_of_manufacture: '베트남',
                    handling_cautions: '세탁기·건조기 사용 금지 · 물에 젖으면 그늘에서 말려 주세요.',
                    quality_warranty: '소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해요 — 접착 불량은 구입일부터 6개월.',
                    after_service_contact: AS,
                }, [['사이즈 가이드', '정사이즈 · 발볼이 넓으면 10mm 크게 고르세요.']]),
            ],
        }),
    ];
}

function line(id: string, optionName: string | null, qty: number, unitPrice: number, extra: Partial<GroupBuyOrderItem> = {}): GroupBuyOrderItem {
    return { id, optionName, qty, unitPrice, itemAmount: unitPrice * qty, canceledQty: 0, returnedQty: 0, restockedQty: 0, ...extra };
}

interface OrderSeed {
    id: string;
    no: number;
    gb: string;
    member: GroupBuyMember;
    status: GroupBuyOrderDetail['status'];
    items: GroupBuyOrderItem[];
    shippingFee: number;
    createdMin: number;
    refunded?: number;
    carrier?: string;
    tracking?: string;
    shippedMin?: number;
    deliveredMin?: number;
    memo?: string;
}

const ORDER_SEEDS: OrderSeed[] = [
    { id: 'od-1', no: 21, gb: 'gb-102', member: MEMBERS.m1!, status: 'CONFIRMED', items: [line('it-1', '블랙', 2, 25000), line('it-2', '레드(한정)', 1, 27000)], shippingFee: 0, createdMin: -300 },
    { id: 'od-2', no: 22, gb: 'gb-102', member: MEMBERS.m2!, status: 'CONFIRMED', items: [line('it-3', '화이트', 1, 25000)], shippingFee: 3000, createdMin: -240 },
    { id: 'od-3', no: 18, gb: 'gb-102', member: MEMBERS.m3!, status: 'PREPARING', items: [line('it-4', '블랙', 1, 25000), line('it-5', '화이트', 1, 25000)], shippingFee: 0, createdMin: -1500, memo: '선물 포장 문의 — 포장 없이 보내기로 통화함' },
    { id: 'od-4', no: 12, gb: 'gb-102', member: MEMBERS.m5!, status: 'SHIPPED', items: [line('it-6', '레드(한정)', 2, 27000)], shippingFee: 0, createdMin: -5 * 1440, carrier: 'CJ', tracking: '684512345678', shippedMin: -4 * 1440 },
    { id: 'od-5', no: 7, gb: 'gb-102', member: MEMBERS.m6!, status: 'DELIVERED', items: [line('it-7', '블랙', 3, 25000)], shippingFee: 0, createdMin: -7 * 1440, carrier: 'HANJIN', tracking: '512300998877', shippedMin: -6 * 1440, deliveredMin: -4 * 1440 },
    { id: 'od-6', no: 5, gb: 'gb-102', member: MEMBERS.m2!, status: 'DELIVERED', items: [line('it-8', '화이트', 2, 25000, { returnedQty: 0 })], shippingFee: 0, createdMin: -8 * 1440, carrier: 'CJ', tracking: '684500011122', shippedMin: -7 * 1440, deliveredMin: -5 * 1440 },
    { id: 'od-7', no: 3, gb: 'gb-102', member: MEMBERS.m4!, status: 'REFUNDED', items: [line('it-9', '블랙', 1, 25000, { returnedQty: 1 })], shippingFee: 3000, createdMin: -9 * 1440, refunded: 25000, carrier: 'LOTTE', tracking: '230011112222', shippedMin: -8 * 1440, deliveredMin: -7 * 1440 },
    { id: 'od-8', no: 9, gb: 'gb-102', member: MEMBERS.m3!, status: 'SHIPPED', items: [line('it-10', '화이트', 1, 25000)], shippingFee: 3000, createdMin: -12 * 1440, carrier: 'EPOST', tracking: '6070123456789', shippedMin: -10 * 1440 },
    { id: 'od-9', no: 31, gb: 'gb-101', member: MEMBERS.m1!, status: 'JOINED', items: [line('it-11', null, 1, 389000)], shippingFee: 0, createdMin: -2 * 1440 },
    { id: 'od-10', no: 44, gb: 'gb-101', member: MEMBERS.m6!, status: 'PENDING_PAYMENT', items: [line('it-12', null, 2, 389000)], shippingFee: 0, createdMin: -3 },
    { id: 'od-11', no: 41, gb: 'gb-104', member: MEMBERS.m5!, status: 'CONFIRMED', items: [line('it-13', '그레이', 2, 149000)], shippingFee: 0, createdMin: -6 * 1440 },
    { id: 'od-12', no: 42, gb: 'gb-104', member: MEMBERS.m2!, status: 'CONFIRMED', items: [line('it-14', '핑크', 1, 154000), line('it-15', '그레이', 1, 149000)], shippingFee: 0, createdMin: -5 * 1440 },
    { id: 'od-13', no: 43, gb: 'gb-104', member: MEMBERS.m3!, status: 'CONFIRMED', items: [line('it-16', '핑크', 2, 154000)], shippingFee: 0, createdMin: -5 * 1440 },
    { id: 'od-14', no: 15, gb: 'gb-106', member: MEMBERS.m1!, status: 'REFUNDED', items: [line('it-17', null, 1, 790000, { canceledQty: 1 })], shippingFee: 0, createdMin: -15 * 1440, refunded: 790000 },
    { id: 'od-15', no: 38, gb: 'gb-103', member: MEMBERS.m2!, status: 'JOINED', items: [line('it-18', null, 1, 99000)], shippingFee: 3000, createdMin: -3 * 1440 },
    { id: 'od-16', no: 2, gb: 'gb-102', member: MEMBERS.m1!, status: 'CANCELED', items: [line('it-19', '블랙', 1, 25000, { canceledQty: 1 })], shippingFee: 3000, createdMin: -10 * 1440, refunded: 28000 },
];

const CARRIER_NAMES: Record<string, string> = { CJ: 'CJ대한통운', HANJIN: '한진택배', LOTTE: '롯데택배', EPOST: '우체국택배', LOGEN: '로젠택배', KDEXP: '경동택배' };
export function carrierName(code: string): string {
    return CARRIER_NAMES[code] ?? code;
}

/** 주문 번호 `G` + 한국 날짜 + 그날 일련 5자리 */
export function orderNoOf(createdIso: string, serial: number): string {
    const kst = new Date(Date.parse(createdIso) + 9 * 60 * MIN).toISOString().slice(0, 10).replaceAll('-', '');
    return `G${kst}${String(serial).padStart(5, '0')}`;
}

export function seedOrders(groupBuys: GroupBuyDetail[]): GroupBuyOrderDetail[] {
    return ORDER_SEEDS.map((s) => {
        const gb = groupBuys.find((g) => g.id === s.gb)!;
        const itemsAmount = s.items.reduce((sum, i) => sum + i.itemAmount, 0);
        const amount = itemsAmount + s.shippingFee;
        const createdAt = mockAt(s.createdMin);
        const paid = s.status !== 'PENDING_PAYMENT';
        const refunded = s.refunded ?? 0;
        const totalQty = s.items.reduce((sum, i) => sum + i.qty - i.canceledQty - i.returnedQty, 0);
        const first = s.items[0]!;
        const itemSummary = s.items.length > 1 ? `${first.optionName ?? gb.productName} × ${first.qty} 외 ${s.items.length - 1}줄` : `${first.optionName ?? gb.productName} × ${first.qty}`;
        const order: GroupBuyOrderDetail = {
            id: s.id,
            orderNo: orderNoOf(createdAt, s.no),
            groupBuy: { id: gb.id, title: gb.title, roundNo: gb.roundNo, status: gb.status, fulfillment: gb.fulfillment },
            member: s.member,
            itemSummary,
            totalQty,
            amount,
            refundedAmount: refunded,
            status: s.status,
            openRequest: null,
            supplyChoicePending: false,
            delayCancelable: false,
            carrierCode: s.carrier ?? null,
            trackingNo: s.tracking ?? null,
            createdAt,
            paidAt: paid ? mockAt(s.createdMin + 2) : null,
            shippedAt: s.shippedMin !== undefined ? mockAt(s.shippedMin) : null,
            deliveredAt: s.deliveredMin !== undefined ? mockAt(s.deliveredMin) : null,
            items: s.items,
            amounts: { itemsAmount, shippingFee: s.shippingFee, amount, cashAmount: amount, pointAmount: 0, refundedAmount: refunded, compensationAmount: 0, reshippingFeeCharged: 0, remaining: amount - refunded },
            payment: paid ? { paymentOrderId: `po-${s.id}`, paymentId: `GB-${s.id.toUpperCase()}-${s.no}`, status: refunded >= amount ? 'CANCELED' : refunded > 0 ? 'PARTIAL_CANCELED' : 'PAID', payMethod: '카드(신한)', escrow: s.no % 4 === 0, paidAt: mockAt(s.createdMin + 2) } : null,
            address: { recipient: `${s.member.nickname.slice(0, 1)}*${s.member.nickname.slice(-1)}`, phone: '010-****-5678', postcode: '06236', address: '서울 강남구 ******', memo: '문 앞 ***', erasedAt: null },
            addressViews: s.id === 'od-3' ? [{ operator: '운영자 박', reason: '택배 접수 주소 확인(전화 요청)', viewedAt: mockAt(-600) }] : [],
            adminMemo: s.memo ?? '',
            history: historyOf(s.status, createdAt, s),
            refunds: refunded > 0 ? [{ id: `rf-${s.id}`, cause: s.status === 'CANCELED' ? 'USER_CANCEL' : s.gb === 'gb-106' ? 'FAILED' : 'REQUEST', method: 'PG_CANCEL', state: 'DONE', itemRefund: refunded - (s.status === 'CANCELED' ? 3000 : 0), shippingRefund: s.status === 'CANCELED' ? 3000 : 0, returnFee: 0, reshippingFee: 0, reshippingRefund: 0, compensation: 0, total: refunded, businessFault: false, reason: null, operator: null, requestId: null, requestedAt: mockAt(s.createdMin + 600), completedAt: mockAt(s.createdMin + 2000) }] : [],
            shipments: s.carrier && s.tracking && s.shippedMin !== undefined ? [{ kind: 'ORIGINAL', carrierCode: s.carrier, carrierName: carrierName(s.carrier), trackingNo: s.tracking, trackingUrl: `https://trace.example.com/${s.carrier}?no=${s.tracking}`, shippedAt: mockAt(s.shippedMin), deliveredAt: s.deliveredMin !== undefined ? mockAt(s.deliveredMin) : null }] : [],
            requests: [],
            supplyChoice: null,
            shipDueAt: gb.fulfillment === 'IMMEDIATE' ? mockAt(s.createdMin + 3 * 1440) : gb.shipPlannedAt ? `${gb.shipPlannedAt}T00:00:00.000Z` : null,
            adminRefundTotal: 0,
            largeRefundThreshold: 100000,
            actions: { canPrepare: false, canShip: false, canFixTracking: false, canDeliver: false, canRevealAddress: true, refundMode: null, refundBlockedReason: null },
        };
        return order;
    });
}

function historyOf(status: GroupBuyOrderDetail['status'], createdAt: string, s: OrderSeed): GroupBuyOrderDetail['history'] {
    const out: GroupBuyOrderDetail['history'] = [{ from: null, to: 'PENDING_PAYMENT', actor: s.member.nickname, note: null, at: createdAt }];
    const chain: GroupBuyOrderDetail['status'][] = ['JOINED', 'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED'];
    const end = chain.indexOf(status);
    let prev: GroupBuyOrderDetail['status'] = 'PENDING_PAYMENT';
    const stop = end >= 0 ? end : status === 'REFUNDED' && s.deliveredMin !== undefined ? 5 : 1;
    for (let i = 0; i <= stop && i < chain.length; i++) {
        const to = chain[i]!;
        out.push({ from: prev, to, actor: i < 2 ? 'SYSTEM' : '운영자 박', note: null, at: mockAt(s.createdMin + 2 + i * 60) });
        prev = to;
    }
    if (status === 'REFUNDED' || status === 'CANCELED') out.push({ from: prev, to: status, actor: status === 'CANCELED' ? s.member.nickname : 'SYSTEM', note: null, at: mockAt(s.createdMin + 2000) });
    return out;
}

export function seedRequests(orders: GroupBuyOrderDetail[]): GroupBuyRequestDetail[] {
    const od = (id: string) => orders.find((o) => o.id === id)!;
    const zero = { itemRefund: 0, shippingRefund: 0, returnFee: 0, reshippingFee: 0, reshippingRefund: 0, compensation: 0, total: 0, operatorPaysReturnFee: false };
    const base = (o: GroupBuyOrderDetail) => ({
        order: { id: o.id, orderNo: o.orderNo },
        groupBuy: o.groupBuy,
        member: o.member,
        photos: [] as { id: string; url: string }[],
        pickup: null,
        recalculated: null,
        compensationAmount: 0,
        compensationCap: 10000,
        withdrawalRestricted: o.groupBuy.id === 'gb-102',
        rejectReasonCode: null,
        rejectReason: null,
        rejectPhotos: [] as { id: string; url: string }[],
        exchangeShipment: null,
        adminRefundTotal: 0,
        largeRefundThreshold: 100000,
        handledBy: null,
        handledAt: null,
        extraPayment: null,
        receivedAt: null,
        actions: { canApprove: false, canReject: false, canSetPickup: false, canReceive: false, canComplete: false, canExchangeShip: false, canConvertToRefund: false, resellAllowed: false },
    });
    return [
        {
            ...base(od('od-12')),
            id: 'rq-1',
            type: 'CANCEL',
            status: 'REQUESTED',
            resolution: 'REFUND',
            lineCount: 1,
            totalQty: 1,
            mainReason: 'CHANGE_OF_MIND',
            collectionMethod: null,
            previewAmount: 154000,
            createdAt: mockAt(-1 * 1440),
            elapsedBusinessDays: 1,
            deadlineLevel: 'OK',
            items: [{ id: 'ri-1', orderItemId: 'it-14', optionName: '핑크', qty: 1, reasonCode: 'CHANGE_OF_MIND', reasonText: '다른 색으로 사고 싶어요', refundAmount: 154000, dispositions: [] }],
            breakdown: { ...zero, itemRefund: 154000, total: 154000 },
            history: [{ status: 'REQUESTED', actor: '코딩맘', note: null, at: mockAt(-1 * 1440) }],
        },
        {
            ...base(od('od-5')),
            id: 'rq-2',
            type: 'RETURN',
            status: 'REQUESTED',
            resolution: 'REFUND',
            lineCount: 1,
            totalQty: 1,
            mainReason: 'CHANGE_OF_MIND',
            collectionMethod: 'COMPANY_PICKUP',
            previewAmount: 22000,
            createdAt: mockAt(-3 * 1440 - 120),
            elapsedBusinessDays: 3,
            deadlineLevel: 'CRITICAL',
            items: [{ id: 'ri-2', orderItemId: 'it-7', optionName: '블랙', qty: 1, reasonCode: 'CHANGE_OF_MIND', reasonText: '생각보다 작아요', refundAmount: 22000, dispositions: [] }],
            breakdown: { ...zero, itemRefund: 25000, returnFee: 3000, total: 22000 },
            history: [{ status: 'REQUESTED', actor: '테크러버', note: null, at: mockAt(-3 * 1440 - 120) }],
        },
        {
            ...base(od('od-6')),
            id: 'rq-3',
            type: 'RETURN',
            status: 'APPROVED',
            resolution: 'REFUND',
            lineCount: 2,
            totalQty: 2,
            mainReason: 'DEFECT',
            collectionMethod: 'CUSTOMER_SEND',
            previewAmount: 50000,
            createdAt: mockAt(-4 * 1440),
            receivedAt: mockAt(-2 * 1440),
            elapsedBusinessDays: 2,
            deadlineLevel: 'WARN',
            photos: [{ id: 'ph-1', url: mockImage('defect', 0) }],
            compensationAmount: 4000,
            items: [
                { id: 'ri-3', orderItemId: 'it-8', optionName: '화이트', qty: 1, reasonCode: 'DEFECT', reasonText: '전원이 켜지지 않아요', refundAmount: 25000, dispositions: [] },
                { id: 'ri-4', orderItemId: 'it-8', optionName: '화이트', qty: 1, reasonCode: 'CHANGE_OF_MIND', reasonText: '하나만 쓰려고요', refundAmount: 25000, dispositions: [] },
            ],
            breakdown: { ...zero, itemRefund: 50000, total: 50000, operatorPaysReturnFee: true },
            handledBy: '운영자 박',
            handledAt: mockAt(-3 * 1440),
            history: [
                { status: 'REQUESTED', actor: '코딩맘', note: null, at: mockAt(-4 * 1440) },
                { status: 'APPROVED', actor: '운영자 박', note: '고객발송 — 반품 주소 안내함', at: mockAt(-3 * 1440) },
            ],
        },
        {
            ...base(od('od-4')),
            id: 'rq-4',
            type: 'RETURN',
            status: 'REQUESTED',
            resolution: 'EXCHANGE',
            lineCount: 1,
            totalQty: 1,
            mainReason: 'WRONG_ITEM',
            collectionMethod: 'COMPANY_PICKUP',
            previewAmount: 0,
            createdAt: mockAt(-200),
            elapsedBusinessDays: 0,
            deadlineLevel: 'OK',
            photos: [{ id: 'ph-2', url: mockImage('wrong', 30) }],
            items: [{ id: 'ri-5', orderItemId: 'it-6', optionName: '레드(한정)', qty: 1, reasonCode: 'WRONG_ITEM', reasonText: '레드를 시켰는데 블랙이 왔어요', refundAmount: 0, dispositions: [] }],
            breakdown: { ...zero, operatorPaysReturnFee: true },
            history: [{ status: 'REQUESTED', actor: '공구왕', note: null, at: mockAt(-200) }],
        },
        {
            ...base(od('od-8')),
            id: 'rq-5',
            type: 'RETURN',
            status: 'REQUESTED',
            resolution: 'REFUND',
            lineCount: 1,
            totalQty: 1,
            mainReason: 'CHANGE_OF_MIND',
            collectionMethod: 'COMPANY_PICKUP',
            previewAmount: 19000,
            createdAt: mockAt(-2 * 1440),
            elapsedBusinessDays: 2,
            deadlineLevel: 'WARN',
            items: [{ id: 'ri-6', orderItemId: 'it-10', optionName: '화이트', qty: 1, reasonCode: 'CHANGE_OF_MIND', reasonText: '선물하려 했는데 필요 없어졌어요', refundAmount: 19000, dispositions: [] }],
            breakdown: { ...zero, itemRefund: 25000, returnFee: 3000, reshippingFee: 3000, total: 19000 },
            extraPayment: { status: 'PAID', amount: 1000, paymentId: 'GBRF-RQ5-1', paidAt: mockAt(-2 * 1440 + 5), refundedAmount: 0 },
            history: [{ status: 'REQUESTED', actor: '메이커준', note: '반품비 부족분 1,000원 추가 결제 뒤 접수', at: mockAt(-2 * 1440) }],
        },
        {
            ...base(od('od-7')),
            id: 'rq-6',
            type: 'RETURN',
            status: 'COMPLETED',
            resolution: 'REFUND',
            lineCount: 1,
            totalQty: 1,
            mainReason: 'DEFECT',
            collectionMethod: 'COMPANY_PICKUP',
            previewAmount: 25000,
            createdAt: mockAt(-8 * 1440),
            receivedAt: mockAt(-7 * 1440),
            elapsedBusinessDays: 0,
            deadlineLevel: 'OK',
            items: [{ id: 'ri-7', orderItemId: 'it-9', optionName: '블랙', qty: 1, reasonCode: 'DEFECT', reasonText: '바퀴가 돌지 않아요', refundAmount: 25000, dispositions: [{ disposition: 'RETURN_TO_SUPPLIER', qty: 1 }] }],
            breakdown: { ...zero, itemRefund: 25000, total: 25000, operatorPaysReturnFee: true },
            pickup: { carrierCode: 'LOTTE', trackingNo: '230099990000' },
            handledBy: '운영자 박',
            handledAt: mockAt(-7 * 1440 + 30),
            history: [
                { status: 'REQUESTED', actor: '탈퇴한 회원', note: null, at: mockAt(-8 * 1440) },
                { status: 'APPROVED', actor: '운영자 박', note: null, at: mockAt(-8 * 1440 + 100) },
                { status: 'COMPLETED', actor: '운영자 박', note: null, at: mockAt(-7 * 1440 + 30) },
            ],
        },
    ];
}

export function seedRevisions(gb: GroupBuyDetail): GroupBuyRevision[] {
    const out: GroupBuyRevision[] = [];
    for (let n = gb.revisionNo; n >= 1; n--) {
        out.push({
            revisionNo: n,
            kind: n === 1 ? 'START' : n % 2 === 0 ? 'CONTENT' : 'STOCK',
            changedFields: n === 1 ? [] : n % 2 === 0 ? ['description', 'images'] : ['maxQty'],
            title: gb.title,
            summary: gb.summary,
            description: n === gb.revisionNo ? gb.description : `<p>(이전 판 ${n}) ${gb.productName} 소개</p>`,
            imageCount: Math.max(0, gb.images.length - (gb.revisionNo - n)),
            maxQty: gb.maxQty - (n === gb.revisionNo ? 0 : gb.addedQty),
            addedQty: n === gb.revisionNo ? gb.addedQty : 0,
            optionMax: gb.options.map((o) => ({ name: o.name, maxQty: o.maxQty })),
            shipPlannedAt: gb.shipPlannedAt,
            notices: gb.notices.map((x) => ({ ...x, optionNames: [...x.optionNames], values: x.values.map((v) => ({ ...v })), customItems: x.customItems.map((c) => ({ ...c })) })),
            operator: n === 1 ? gb.createdBy : '운영자 박',
            createdAt: mockAt(-(gb.revisionNo - n + 1) * 1440),
        });
    }
    return out;
}

export function seedMovements(gb: GroupBuyDetail): GroupBuyMovement[] {
    const out: GroupBuyMovement[] = [];
    if (gb.status === 'DRAFT') return out;
    const kinds: GroupBuyMovement['kind'][] = ['ADD', 'RESERVE', 'CONFIRM', 'RESERVE', 'RELEASE', 'RESERVE', 'CONFIRM', 'CANCEL', 'RETURN', 'RESTOCK', 'RESERVE', 'CONFIRM'];
    let cur = 0;
    let res = 0;
    let max = gb.initialMaxQty ?? gb.maxQty;
    kinds.forEach((kind, i) => {
        const qty = kind === 'ADD' ? Math.max(gb.addedQty, 5) : 1 + (i % 2);
        if (kind === 'RESERVE') res += qty;
        if (kind === 'RELEASE') res = Math.max(0, res - qty);
        if (kind === 'CONFIRM') {
            res = Math.max(0, res - qty);
            cur += qty;
        }
        if (kind === 'CANCEL' || kind === 'RESTOCK') cur = Math.max(0, cur - qty);
        if (kind === 'ADD') max += qty;
        const option = gb.options[i % Math.max(1, gb.options.length)] ?? null;
        out.push({
            id: `mv-${gb.id}-${i}`,
            kind,
            optionName: option?.name ?? null,
            qty,
            currentAfter: cur,
            reservedAfter: res,
            maxAfter: max,
            optionCurrentAfter: option ? Math.min(cur, option.currentQty) : null,
            optionReservedAfter: option ? Math.min(res, option.reservedQty) : null,
            optionMaxAfter: option ? option.maxQty : null,
            orderNo: kind === 'ADD' ? null : `G20261027000${10 + i}`,
            actor: kind === 'ADD' ? '운영자 박' : kind === 'RELEASE' ? 'SYSTEM' : kind === 'RETURN' || kind === 'RESTOCK' ? '운영자 박' : MEMBERS.m1!.nickname,
            reason: kind === 'ADD' ? '추가 입고 — 공급처 10개 추가 확보' : kind === 'RELEASE' ? '선점 시간 초과' : null,
            createdAt: mockAt(-(kinds.length - i) * 300),
        });
    });
    return out.reverse();
}
