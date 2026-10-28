// MOCK: 상품정보제공고시 카탈로그(결정 87) — `GET /api/admin/group-buys/notice-catalog` 의 목업 응답.
// 정본은 서버 `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json`(2b-2 가 고시 원문에서 옮기는 중)이다. 그 파일이 생기면 이 파일만 지우고 실제 API 로 바꾼다.
//
// ── 목업 가정 ─
//   · 품목 40개의 번호·이름은 원문(국가법령정보센터, 2026-10-28 확인)대로다. 다만 (33)~(38) 은 원문 이름을 아직 옮기지 못해 기억에 기댄 **임시 이름**이다(`provisional`).
//   · 항목은 (8) 가정용 전기제품 · (12) 소형전자 · (23) 어린이제품 · (40) 기타 재화 넷만 상식 수준으로 채운 **임시 항목**이다(`provisional`). 나머지 품목은 항목이 비어 「항목 준비 중」으로 보인다.
//   · 항목 이름·안내 문구는 서버 데이터라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import type { NoticeCatalog, NoticeCategory, NoticeItemDef } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

const WARRANTY_GUIDE = '구체적인 피해보상기준을 적거나 「○○○ 보상규정에 따라 보상」과 그 규정을 볼 수 있는 방법을 적어 주세요. 소비자분쟁해결기준보다 불리하면 그 사실과 내용도 적어요.';
const KC_GUIDE = 'KC 인증번호(식별부호)를 적어요. 식별부호가 없으면 인증 받은 사실을, 면제 품목이면 면제 사실을 적어요. 인증 대상인지는 운영자가 확인해요.';
const DATE_GUIDE = '정확한 날짜 대신 가장 빠른 날짜·범위·주문일(발송일) 기준 기간으로 적어도 돼요.';

function item(key: string, name: string, kind: NoticeItemDef['kind'] = 'TEXT', required = true, guide?: string): NoticeItemDef {
    return guide ? { key, name, required, kind, guide } : { key, name, required, kind };
}

const ITEMS: Record<number, NoticeItemDef[]> = {
    8: [
        item('modelName', '품명 및 모델명'),
        item('kcCert', 'KC 인증정보(전기용품 및 생활용품 안전관리법에 따른 안전인증·안전확인·공급자적합성확인 대상 여부)', 'KC', true, KC_GUIDE),
        item('ratedPower', '정격전압·소비전력'),
        item('energyGrade', '에너지소비효율등급(해당하는 경우)', 'TEXT', false),
        item('releaseDate', '동일모델의 출시년월', 'DATE', true, DATE_GUIDE),
        item('manufacturer', '제조자(수입품은 수입자를 함께)'),
        item('origin', '제조국'),
        item('size', '크기(형태 포함)'),
        item('warranty', '품질보증기준', 'WARRANTY', true, WARRANTY_GUIDE),
        item('asContact', 'A/S 책임자와 전화번호'),
    ],
    12: [
        item('modelName', '품명 및 모델명'),
        item('kcCert', 'KC 인증정보(전기용품 및 생활용품 안전관리법·전파법 대상 여부)', 'KC', true, KC_GUIDE),
        item('ratedPower', '정격전압·소비전력'),
        item('releaseDate', '동일모델의 출시년월', 'DATE', true, DATE_GUIDE),
        item('manufacturer', '제조자(수입품은 수입자를 함께)'),
        item('origin', '제조국'),
        item('sizeWeight', '크기·무게'),
        item('mainSpec', '주요 사양'),
        item('warranty', '품질보증기준', 'WARRANTY', true, WARRANTY_GUIDE),
        item('asContact', 'A/S 책임자와 전화번호'),
    ],
    23: [
        item('modelName', '품명 및 모델명'),
        item('kcCert', 'KC 인증정보(어린이제품 안전 특별법에 따른 안전인증·안전확인·공급자적합성확인)', 'KC', true, KC_GUIDE),
        item('sizeWeight', '크기·중량'),
        item('color', '색상'),
        item('material', '재질(섬유는 혼용률)'),
        item('ageRange', '사용연령 또는 체중 범위'),
        item('releaseDate', '동일모델의 출시년월', 'DATE', true, DATE_GUIDE),
        item('manufacturer', '제조자(수입품은 수입자를 함께)'),
        item('origin', '제조국'),
        item('caution', '취급방법 및 취급 때 주의사항·안전표시(주의·경고 등)'),
        item('warranty', '품질보증기준', 'WARRANTY', true, WARRANTY_GUIDE),
        item('asContact', 'A/S 책임자와 전화번호'),
    ],
    40: [
        item('modelName', '품명 및 모델명'),
        item('certification', '법에 의한 인증·허가 등을 받았음을 확인할 수 있는 경우 그에 대한 사항', 'KC', true, KC_GUIDE),
        item('origin', '제조국 또는 원산지'),
        item('manufacturer', '제조자(수입품은 수입자를 함께)'),
        item('asContact', 'A/S 책임자와 전화번호 또는 소비자상담 관련 전화번호'),
    ],
};

/** [번호, 코드, 이름, 이름이 임시인가] */
const CATEGORIES: [number, string, string, boolean][] = [
    [1, 'CLOTHING', '의류', false],
    [2, 'SHOES', '구두/신발', false],
    [3, 'BAGS', '가방', false],
    [4, 'FASHION_ACCESSORIES', '패션잡화(모자/벨트/액세서리 등)', false],
    [5, 'BEDDING', '침구류/커튼', false],
    [6, 'FURNITURE', '가구', false],
    [7, 'VIDEO_APPLIANCE', '영상가전', false],
    [8, 'HOME_APPLIANCE', '가정용 전기제품', false],
    [9, 'SEASONAL_APPLIANCE', '계절가전', false],
    [10, 'OFFICE_EQUIPMENT', '사무용기기', false],
    [11, 'OPTICAL', '광학기기', false],
    [12, 'SMALL_ELECTRONICS', '소형전자', false],
    [13, 'MOBILE_DEVICE', '휴대형 통신기기', false],
    [14, 'NAVIGATION', '내비게이션', false],
    [15, 'CAR_ACCESSORIES', '자동차용품', false],
    [16, 'MEDICAL_DEVICE', '의료기기', false],
    [17, 'KITCHENWARE', '주방용품', false],
    [18, 'COSMETICS', '화장품', false],
    [19, 'JEWELRY_WATCH', '귀금속/보석/시계류', false],
    [20, 'AGRI_FOOD', '농수축산물', false],
    [21, 'PROCESSED_FOOD', '가공식품', false],
    [22, 'HEALTH_FOOD', '건강기능식품', false],
    [23, 'KIDS_PRODUCT', '어린이제품', false],
    [24, 'MUSICAL_INSTRUMENT', '악기', false],
    [25, 'SPORTS', '스포츠용품', false],
    [26, 'BOOKS', '서적', false],
    [27, 'LODGING_RESERVATION', '호텔/펜션 예약', false],
    [28, 'TRAVEL_PACKAGE', '여행패키지', false],
    [29, 'AIR_TICKET', '항공권', false],
    [30, 'CAR_RENTAL', '자동차 대여 서비스(렌터카)', false],
    [31, 'RENTAL_APPLIANCE', '물품대여 서비스(정수기·비데·공기청정기 등)', false],
    [32, 'RENTAL_GOODS', '물품대여 서비스(서적·유아용품·행사용품 등)', false],
    [33, 'DIGITAL_CONTENT', '디지털 콘텐츠 등', true],
    [34, 'GIFT_CARD', '상품권/쿠폰 등', true],
    [35, 'MOBILE_COUPON', '모바일 쿠폰 등', true],
    [36, 'MOVIE_SHOW', '영화/공연 등', true],
    [37, 'HOUSEHOLD_CHEMICAL', '생활화학제품 등', true],
    [38, 'BIOCIDE', '살생물제품 등', true],
    [39, 'OTHER_SERVICE', '기타 용역', false],
    [40, 'OTHER_GOODS', '기타 재화', false],
];

export function mockNoticeCatalog(): NoticeCatalog {
    const categories: NoticeCategory[] = CATEGORIES.map(([no, code, name, provisionalName]) => {
        const items = ITEMS[no] ?? [];
        // 이름이 임시이거나 항목을 임시로 채운 품목
        const provisional = provisionalName || items.length > 0;
        return provisional ? { no, code, name, provisional, items: items.map((i) => ({ ...i })) } : { no, code, name, items: [] };
    });
    return { version: '2023-01-01', source: '공정거래위원회고시 제2022-15호', categories };
}
