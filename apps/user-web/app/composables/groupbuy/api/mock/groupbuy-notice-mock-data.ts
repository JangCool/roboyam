// MOCK: 공구 상세의 상품정보제공고시 양식(결정 87) — `groupbuy-mock-data.ts` 의 시드가 부른다. 서버가 붙으면 목업 폴더째 지운다.
//
// 옆의 `notice-catalog-2023-01-01.json` 은 서버 원본 사본(서버 붙으면 지움 — 원본 `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json`).
// 품목·항목 이름·번호·묶음 줄은 그 파일에서 채운다. 회원 응답은 서버가 카탈로그 이름(품목·항목)까지 붙여 준다고 가정한다(회원 화면은 카탈로그를 따로 받지 않는다).
//
// ── 시드 ─
// 적용 대상은 단품(결정 88 · 서버 2b-2) — `skuIds`·`skuNames`(예전 optionNames), 비면 기본 양식(MAIN 만 덮는다). 추가 구성상품(ADDON)은 단품마다 자기 양식.
//
//   101 (8) 가정용 전기제품 — 에너지소비효율등급 「제공 불가 + 사유」 · 판매자 추가 「호환 기종」
//   102 기본 「본체」 (12) 소형전자 + 추가 구성상품 「여분 배터리」 (12) · 「충전 독」 (12) — KC 면제 사실 · 소비전력 제공 불가
//   103·104 (40) 기타 재화 · 105 (23) 어린이제품(출시 전 — 출시년월 제공 불가) · 106 (23) · 107 (8) · 108 (23)
//   109 (20) 햅쌀 — 날짜 대체 표기 · 축산물·수입 항목 「제공 불가 + 사유」 · 추가 「도정일」
//   110 (20) 사과·감귤 — 종류마다 양식(그 종류의 중량 단품들, 기본 양식 없음) · 추가 「당도(Brix)」 · 111 (20) 고등어(수입 신고 문구)·전복 — 단품마다 양식
//   112 (21) 김치 — 묶음 줄(1. 「식품 등의 표시·광고에 관한 법률」…) · 113 (1) 티셔츠 · 114 (2) 운동화 — 단품 여럿이어도 기본 양식 하나 · 추가 「사이즈 가이드」

import type { GroupBuyNoticeView, GroupBuySku, NoticeRowKind, NoticeRowView } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import catalogJson from '~/composables/groupbuy/api/mock/notice-catalog-2023-01-01.json';

interface CatalogFile {
    version: string;
    noticeNo: string;
    categories: { no: number; code: string; name: string; items: { no: string; key: string; group?: string | null; name: string; description: string | null; kind: string }[] }[];
}

const CATALOG = catalogJson as CatalogFile;
const AS = '로보얌 고객센터 1588-0000(평일 10:00~17:00)';
const WARRANTY = '구입일로부터 1년 무상 보증 — 소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해 드려요.';

/** 항목 값 — 글이면 값, `{ none }` 이면 「제공 불가」 + 사유 */
type Fill = string | { none: string };

/** 고시 양식 한 장 — 카탈로그 품목 항목 차례대로 줄을 만들고 `fill` 로 채운다. `skuNames` = 적용 단품 이름(비면 기본 양식 — ID 는 `mockNotices` 가 채운다), `custom` = 판매자 추가 항목 [이름, 내용] */
function notice(code: string, title: string, fill: Record<string, Fill>, skuNames: string[] = [], custom: [string, string][] = []): GroupBuyNoticeView {
    const category = CATALOG.categories.find((c) => c.code === code);
    if (!category) throw new Error(`MOCK: 고시 품목 ${code} 없음`);
    const rows: NoticeRowView[] = category.items.map((item) => {
        const f = fill[item.key];
        const base = { itemKey: item.key, no: item.no, group: item.group ?? null, name: item.name, description: item.description, kind: item.kind as NoticeRowKind };
        if (f !== undefined && typeof f !== 'string') return { ...base, value: '', unavailable: true, unavailableReason: f.none };
        return { ...base, value: f ?? '', unavailable: false, unavailableReason: '' };
    });
    return {
        categoryCode: code,
        categoryNo: category.no,
        categoryName: category.name,
        title,
        skuIds: [],
        skuNames,
        rows,
        customItems: custom.map(([name, value], sortOrder) => ({ name, value, sortOrder })),
        catalogVersion: CATALOG.version,
        noticeNo: CATALOG.noticeNo,
    };
}

/** (8) 가정용 전기제품 */
function homeAppliance(model: string, o: { kc: string; power: string; release: string; size: string; energy?: Fill }): Record<string, Fill> {
    return {
        product_model_name: model,
        kc_certification: o.kc,
        rated_voltage_power: o.power,
        energy_efficiency_grade: o.energy ?? { none: '에너지소비효율등급 표시대상 기자재가 아니에요 — 해당 없음.' },
        model_release_month: o.release,
        manufacturer_importer: '로보얌테크(주) / 수입자 동일',
        country_of_manufacture: '중국',
        dimensions_capacity_form: o.size,
        additional_installation_cost: '없음',
        quality_warranty: WARRANTY,
        after_service_contact: AS,
    };
}

/** (12) 소형전자 */
function smallElectronics(model: string, o: { kc: Fill; power: Fill; release: string; size: string; spec: string }): Record<string, Fill> {
    return {
        product_model_name: model,
        kc_certification: o.kc,
        rated_voltage_power: o.power,
        model_release_month: o.release,
        manufacturer_importer: '로보얌테크(주)',
        country_of_manufacture: '대한민국',
        dimensions_weight: o.size,
        main_spec: o.spec,
        quality_warranty: '구입일로부터 6개월 무상 보증 — 소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해 드려요. 소모품(배터리)은 3개월이에요.',
        after_service_contact: AS,
    };
}

/** (23) 어린이제품 */
function kids(model: string, o: { kc: string; size: string; color: string; material: string; age: string; release: Fill; caution: string }): Record<string, Fill> {
    return {
        product_model_name: model,
        kc_certification: o.kc,
        dimensions_weight: o.size,
        color: o.color,
        material: o.material,
        age_range: o.age,
        size_weight_limit: { none: '착용·탑승용 제품이 아니라 크기·체중 제한이 없어요 — 해당 없음.' },
        model_release_month: o.release,
        manufacturer_importer: '로보얌에듀(주) / 수입자 동일',
        country_of_manufacture: '중국',
        handling_safety: o.caution,
        quality_warranty: WARRANTY,
        after_service_contact: AS,
    };
}

/** (40) 기타 재화 */
function etc(model: string, cert: Fill): Record<string, Fill> {
    return {
        product_model_name: model,
        legal_certification: cert,
        country_or_origin: '중국',
        manufacturer_importer: '로보얌테크(주) / 수입자 동일',
        contact: AS,
    };
}

/** (20) 농수축산물 — 국내산 농산물 기본값 */
function agri(o: { name: string; pack: string; producer: string; origin: string; date: string; composition: string; storage: string; caution: string; labeling?: string; imported?: Fill }): Record<string, Fill> {
    return {
        item_name: o.name,
        package_contents: o.pack,
        producer_importer: o.producer,
        origin: o.origin,
        manufactured_date_expiry: o.date,
        agri_fishery_labeling: o.labeling ?? '유전자변형농수산물 아님 · 지리적 표시 등록 품목 아님',
        livestock_labeling: { none: '축산물이 아니에요 — 해당 없음.' },
        imported_food_declaration: o.imported ?? { none: '국내산이라 수입 신고 대상이 아니에요 — 해당 없음.' },
        composition: o.composition,
        storage_handling: o.storage,
        safety_cautions: o.caution,
        consumer_contact: AS,
    };
}

/** 공구 ID 로 고시 양식 목업 — 적용 단품 이름을 그 공구 단품 ID 로 바꿔 넣는다(서버는 `skuIds`·`skuNames` 를 함께 준다) */
export function mockNotices(gbId: number, title: string, skus: GroupBuySku[]): GroupBuyNoticeView[] {
    return forms(gbId, title).map((form) => ({ ...form, skuIds: form.skuNames.map((name) => skus.find((k) => k.name === name)?.skuId ?? 0) }));
}

/** 공구 ID 로 고시 양식 — 모르는 ID 면 (40) 기타 재화 한 장 */
function forms(gbId: number, title: string): GroupBuyNoticeView[] {
    switch (gbId) {
        case 101:
            return [
                notice('HOME_APPLIANCES', '로봇 청소기 X1', homeAppliance('로봇 청소기 X1 (RV-X1)', { kc: '안전인증 HU071234-24001 · 전파 R-R-RBY-RVX1', power: 'AC 220V 60Hz(충전 스테이션) · 소비전력 40W', release: '2026년 3월', size: '350 × 350 × 95mm · 먼지통 0.45L · 원형' }), [], [['호환 기종', '로보얌 홈 앱 — 안드로이드 10 · iOS 15 이상']]),
            ];
        case 102:
            // 본 상품(축 없음)은 기본 양식, 추가 구성상품은 단품마다 따로(결정 88 — 고시 원칙 7 의 「별개 상품」)
            return [
                notice('SMALL_ELECTRONICS', '본체', smallElectronics('코딩 로봇 키트 R1 (RK-R1)', {
                    kc: 'R-R-RBY-RKR1001 (전파법 적합성평가)',
                    power: 'DC 3.7V(리튬폴리머 1,200mAh) · 최대 5W',
                    release: '2025년 11월',
                    size: '160 × 120 × 70mm · 420g(배터리 포함)',
                    spec: '메인보드 · 모터 2개 · 바퀴 · 배터리 1개 · 블록 코딩 앱 지원',
                }), [], [['호환 기종', '블록 코딩 앱 — 안드로이드 9 · iPadOS 15 이상 · 크롬북']]),
                notice('SMALL_ELECTRONICS', '여분 배터리', smallElectronics('R1 교체용 배터리 (RK-B12)', {
                    kc: '안전확인 YU07123-26001 (리튬이온·리튬폴리머 2차전지)',
                    power: '정격 DC 3.7V · 1,200mAh(4.44Wh)',
                    release: '2025년 11월',
                    size: '50 × 34 × 10mm · 24g',
                    spec: 'R1 본체 전용 · 완충 약 90분 · 사용 약 120분',
                }), ['여분 배터리'], [['보관 방법', '오래 쓰지 않을 때는 50% 정도 충전해 서늘한 곳에 두세요.']]),
                notice('SMALL_ELECTRONICS', '충전 독', smallElectronics('R1 충전 독 (RK-D1)', {
                    kc: '「전기용품 및 생활용품 안전관리법」상 안전확인 대상이 아닌 직류 5V 이하 기기라 KC 인증이 면제돼요.',
                    power: { none: 'USB 어댑터에서 전원을 받는 기기라 단독 소비전력을 재지 않았어요 — 입력 DC 5V 2A.' },
                    release: '2026년 1월',
                    size: '180 × 140 × 30mm · 210g',
                    spec: 'R1 본체를 올려 두면 충전 · USB-C 케이블 포함(어댑터 별도)',
                }), ['충전 독']),
            ];
        case 103:
        case 104:
            return [notice('OTHER_GOODS', '데스크 로봇 팔 미니', etc('데스크 로봇 팔 미니 (DA-M4)', 'R-R-RBY-DAM4002 (전파법 적합성평가)'))];
        case 105:
            return [
                notice('CHILDREN_PRODUCTS', '휴머노이드 교육 로봇 H5', kids('휴머노이드 교육 로봇 H5 (HE-H5)', {
                    kc: '어린이제품 안전인증 CB065R123-6001',
                    size: '높이 420mm · 2.1kg',
                    color: '화이트 · 블루',
                    material: 'ABS 수지 · 알루미늄 프레임',
                    age: '만 8세 이상',
                    release: { none: '신제품이라 아직 출시 전이에요 — 2026년 12월 출시 예정이에요.' },
                    caution: '작은 부품이 있어 만 3세 미만 어린이가 삼키지 않게 해 주세요. 관절 사이에 손가락이 끼지 않게 주의해 주세요.',
                })),
            ];
        case 106:
            return [
                notice('CHILDREN_PRODUCTS', '자율주행 RC카 키트', kids('자율주행 RC카 키트 (RC-A1)', {
                    kc: '어린이제품 안전확인 CB063R456-5002',
                    size: '280 × 190 × 110mm · 1.2kg',
                    color: '블랙 · 오렌지',
                    material: 'ABS 수지 · 고무 타이어',
                    age: '만 10세 이상',
                    release: '2025년 9월',
                    caution: '충전 중에는 자리를 비우지 말아 주세요. 젖은 손으로 만지지 마세요.',
                })),
            ];
        case 107:
            return [
                notice('HOME_APPLIANCES', '스마트 화분 로봇 P2', homeAppliance('스마트 화분 로봇 P2 (SP-P2)', { kc: '안전확인 XU100567-25003', power: 'DC 5V(USB-C 어댑터 포함, AC 220V) · 소비전력 6W', release: '2025년 8월', size: '지름 160mm × 높이 210mm · 물통 500mL · 원통형' })),
            ];
        case 108:
            return [
                notice('CHILDREN_PRODUCTS', '로봇 강아지 펫봇 S', kids('로봇 강아지 펫봇 S (PB-S1)', {
                    kc: '어린이제품 공급자적합성확인 CB061R789-4003',
                    size: '300 × 150 × 220mm · 1.5kg',
                    color: '그레이',
                    material: 'ABS 수지 · 실리콘',
                    age: '만 6세 이상',
                    release: '2025년 6월',
                    caution: '물에 닿지 않게 해 주세요. 다리 관절에 손가락이 끼지 않게 주의해 주세요.',
                })),
            ];
        case 109:
            return [
                notice('AGRI_LIVESTOCK_FISHERY', '햅쌀', agri({
                    name: '쌀(멥쌀) · 추청 단일 품종',
                    pack: '10kg 또는 20kg × 1포(옵션)',
                    producer: '햇살들영농조합법인(경기 이천)',
                    origin: '국내산(경기도 이천시)',
                    date: '도정일로부터 30일 안에 드시기를 권해요 — 발송 3일 전 도정(도정일은 포장에 표시)',
                    composition: '쌀 10kg 또는 20kg 1포(지퍼 포장)',
                    storage: '직사광선을 피해 서늘한 곳(15℃ 이하)에 두고, 개봉 뒤에는 밀폐해 냉장 보관을 권해요.',
                    caution: '습기가 차면 곰팡이·쌀벌레가 생길 수 있어요.',
                }), [], [['도정일', '발송 3일 전 도정 — 포장 앞면에 표시'], ['등급', '특 등급(품위 검사 결과)']]),
            ];
        case 110:
            return [
                notice('AGRI_LIVESTOCK_FISHERY', '사과', agri({
                    name: '사과(부사)',
                    pack: '3kg(8~10과) 또는 5kg(14~16과) 1상자',
                    producer: '청송골과수농원(경북 청송)',
                    origin: '국내산(경상북도 청송군)',
                    date: '수확 후 3일 이내 발송 · 받은 날부터 냉장 2주 안에 드시기를 권해요',
                    composition: '사과 8~16과 · 완충재',
                    storage: '0~4℃ 냉장 보관 · 다른 과일과 따로 두세요.',
                    caution: '껍질째 드실 때는 흐르는 물에 씻어 주세요.',
                }), ['사과 / 3kg', '사과 / 5kg'], [['당도(Brix)', '13Brix 이상 비파괴 선별']]),
                notice('AGRI_LIVESTOCK_FISHERY', '감귤', agri({
                    name: '감귤(노지 온주밀감)',
                    pack: '3kg 또는 5kg(S~M 크기) 1상자',
                    producer: '한라바람농장(제주 서귀포)',
                    origin: '국내산(제주특별자치도 서귀포시)',
                    date: '수확 후 3일 이내 발송 · 받은 날부터 서늘한 곳 7일',
                    composition: '감귤 3kg 또는 5kg · 완충재',
                    storage: '서늘하고 바람이 통하는 곳에 두고, 무른 것은 골라 먼저 드세요.',
                    caution: '왁스 처리를 하지 않았어요.',
                }), ['감귤 / 3kg', '감귤 / 5kg'], [['당도(Brix)', '11Brix 이상 선별']]),
            ];
        case 111:
            return [
                notice('AGRI_LIVESTOCK_FISHERY', '손질 고등어', agri({
                    name: '고등어(순살 손질 · 냉동)',
                    pack: '1kg(6~8팩, 팩당 130~170g)',
                    producer: '바다마루수산(부산) / 수입자 동일',
                    origin: '노르웨이산',
                    date: '냉동(-18℃ 이하) 제조일로부터 12개월 — 발송일 기준 남은 기간 9개월 이상인 제품만 보내요',
                    labeling: '유전자변형수산물 아님',
                    imported: '수입식품안전관리 특별법에 따른 수입신고를 필함',
                    composition: '고등어 순살 6~8팩 · 아이스팩',
                    storage: '-18℃ 이하 냉동 보관 · 해동한 것은 다시 얼리지 마세요.',
                    caution: '가시가 남아 있을 수 있어요. 고등어 알레르기가 있으면 드시지 마세요.',
                }), ['손질 고등어 1kg (냉동)'], [['손질 방법', '머리·내장·가운데 뼈 제거, 소금 간 없음']]),
                notice('AGRI_LIVESTOCK_FISHERY', '활전복', agri({
                    name: '전복(활 · 양식)',
                    pack: '1kg(10~12미)',
                    producer: '완도청해전복(전남 완도)',
                    origin: '국내산(전라남도 완도군)',
                    date: '출하 당일 발송 · 받은 뒤 냉장 3일 안에 드시기를 권해요',
                    labeling: '유전자변형수산물 아님',
                    composition: '활전복 10~12미 · 해초 · 아이스팩',
                    storage: '0~5℃ 냉장 · 젖은 해초로 덮어 두세요.',
                    caution: '익혀 드시기를 권해요. 패류 알레르기가 있으면 드시지 마세요.',
                }), ['활전복 1kg (냉장)']),
            ];
        case 112:
            return [
                notice('PROCESSED_FOOD', '포기김치', {
                    product_name: '전라도식 포기김치',
                    food_type: '배추김치',
                    producer_location: '남도손맛식품(주) · 전라남도 해남군',
                    manufactured_date_expiry: '소비기한 제조일로부터 냉장 30일 — 주문일 기준 3일 안에 만든 제품을 보내요',
                    package_contents: '3kg 또는 5kg(옵션)',
                    ingredients: '배추 75%(국내산), 무(국내산), 고춧가루 5%(국내산), 마늘, 멸치액젓, 새우젓, 찹쌀풀',
                    nutrition: { none: '영양성분 표시대상 식품이 아니라 따로 분석하지 않았어요 — 해당 없음.' },
                    gmo_labeling: { none: '유전자변형 원료를 쓰지 않아 표시 대상이 아니에요.' },
                    safety_cautions: '새우·멸치·대두가 들어 있어요. 숙성 중 가스가 생겨 포장이 부풀 수 있어요.',
                    imported_food_declaration: { none: '국내 제조 식품이라 해당 없어요.' },
                    consumer_contact: AS,
                }, [], [['보관 방법', '0~4℃ 냉장 · 꺼낸 뒤에는 꾹 눌러 공기를 빼 두세요.']]),
            ];
        case 113:
            return [
                notice('CLOTHING', '오버핏 티셔츠', {
                    material: '면 100%',
                    color: '화이트 · 블랙(옵션)',
                    size: 'S(가슴단면 53cm · 총장 69cm) · M(가슴단면 56cm · 총장 72cm) · L(가슴단면 59cm · 총장 75cm)',
                    manufacturer_importer: '로보얌어패럴(주)',
                    country_of_manufacture: '대한민국',
                    washing_handling_cautions: '30℃ 이하 손세탁 또는 세탁망 사용 · 표백제 금지 · 그늘에 뉘어 말려 주세요.',
                    manufactured_month: '2026년 8월 ~ 9월 생산분',
                    quality_warranty: '소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해요 — 봉제 불량은 받은 날부터 30일 안에 교환·환불.',
                    after_service_contact: AS,
                }, [], [['사이즈 표', 'S 어깨 49 · 소매 21 / M 어깨 52 · 소매 22 / L 어깨 55 · 소매 23 (cm)']]),
            ];
        case 114:
            return [
                notice('SHOES', '러닝화', {
                    main_material: '겉감 폴리에스터 메시 · 안감 폴리에스터 · 밑창 EVA·고무',
                    color: '라이트 그레이',
                    size: '발길이 240~280mm(10mm 간격 — 옵션)',
                    manufacturer_importer: '런앤런(주) / 수입자 동일',
                    country_of_manufacture: '베트남',
                    handling_cautions: '세탁기·건조기 사용 금지 · 물에 젖으면 그늘에서 말려 주세요.',
                    quality_warranty: '소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해요 — 접착 불량은 구입일부터 6개월.',
                    after_service_contact: AS,
                }, [], [['사이즈 가이드', '정사이즈 · 발볼이 넓으면 10mm 크게 고르세요.']]),
            ];
        default:
            return [notice('OTHER_GOODS', title, etc(title, { none: '인증·허가 대상이 아니에요 — 해당 없음.' }))];
    }
}
