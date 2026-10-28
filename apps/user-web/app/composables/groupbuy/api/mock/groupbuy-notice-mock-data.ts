// MOCK: 공구 상세의 상품정보제공고시 양식(결정 87) — `groupbuy-mock-data.ts` 의 시드가 부른다. 서버가 붙으면 목업 폴더째 지운다.
//
// ⚠ 임시 항목: 실제 카탈로그 파일(서버 `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json` — 공정위고시 제2022-15호 원문 표를 옮긴 것)은
//   아직 없다. 아래 품목별 항목 키·이름·종류는 상식 수준으로 적은 **임시 값**이고, 카탈로그가 생기면 그 키·이름으로 바뀐다.
//   회원 응답은 서버가 카탈로그 이름(품목 이름·항목 이름)까지 붙여 준다고 가정한다(회원 화면은 카탈로그를 따로 받지 않는다).
//
// ── 시드 ─
//   101 (8) 가정용 전기제품 — 에너지소비효율등급 「제공 불가 + 사유」 · 출시년월 「가장 빠른 날짜」
//   102 기본 「본체 세트」 (12) 소형전자(본체 세트 · 예비 배터리 — 예비 배터리는 부수 상품이라 기본 양식) + 「확장 센서 팩」 (12) 옵션 1023 용 — KC 면제 사실 · 소비전력 제공 불가
//   103·104 (40) 기타 재화 · 105 (23) 어린이제품(출시 전 — 출시년월 제공 불가) · 106 (23) 제조연월 「범위」 · 107 (8) 「발송일 기준 기간」 · 108 (23)

import type { GroupBuyNoticeView, NoticeRowView } from '~/composables/groupbuy/api/useGroupBuyApi.types';

const CATALOG = { catalogVersion: '2023-01-01', catalogSource: '공정거래위원회고시 제2022-15호' };
const CATEGORY_NAMES: Record<number, string> = { 8: '가정용 전기제품', 12: '소형전자', 23: '어린이제품', 40: '기타 재화' };
const AS = '로보얌 고객센터 1588-0000(평일 10:00~17:00)';
const WARRANTY = '구입일로부터 1년 무상 보증 — 소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해 드려요.';

const text = (key: string, name: string, value: string): NoticeRowView => ({ key, name, kind: 'TEXT', value, unavailable: false, reason: '' });
const none = (key: string, name: string, reason: string, kind: NoticeRowView['kind'] = 'TEXT'): NoticeRowView => ({ key, name, kind, value: '', unavailable: true, reason });
const date = (key: string, name: string, value: string, dateMode: NonNullable<NoticeRowView['dateMode']>): NoticeRowView => ({ key, name, kind: 'DATE', value, unavailable: false, reason: '', dateMode });
const kc = (key: string, name: string, value: string, kcMode: NonNullable<NoticeRowView['kcMode']>): NoticeRowView => ({ key, name, kind: 'KC', value, unavailable: false, reason: '', kcMode });
const warranty = (value = WARRANTY): NoticeRowView => ({ key: 'warranty', name: '품질보증기준', kind: 'WARRANTY', value, unavailable: false, reason: '' });

function notice(gbId: number, order: number, categoryNo: number, title: string, rows: NoticeRowView[], optionIds: number[] = []): GroupBuyNoticeView {
    return { id: `n-${gbId}-${order}`, order, categoryNo, categoryName: CATEGORY_NAMES[categoryNo] ?? '', title, optionIds, ...CATALOG, rows };
}

/** (8) 가정용 전기제품 */
function homeAppliance(model: string, opts: { kcNo: string; power: string; release: NoticeRowView; size: string; spec: string; energy?: NoticeRowView }): NoticeRowView[] {
    return [
        text('modelName', '품명 및 모델명', model),
        kc('kcCert', 'KC 인증정보', opts.kcNo, 'CERT'),
        text('ratedPower', '정격전압 · 소비전력', opts.power),
        opts.energy ?? none('energyGrade', '에너지소비효율등급', '에너지소비효율등급 표시 대상 품목이 아니에요.'),
        opts.release,
        text('maker', '제조자(수입자)', '로보얌테크(주) / 수입자 동일'),
        text('origin', '제조국', '중국'),
        text('size', '크기', opts.size),
        text('spec', '주요 사양', opts.spec),
        warranty(),
        text('asContact', 'A/S 책임자와 전화번호', AS),
    ];
}

/** (12) 소형전자 */
function smallElectronics(model: string, opts: { kc: NoticeRowView; power: NoticeRowView; release: NoticeRowView; size: string; spec: string }): NoticeRowView[] {
    return [
        text('modelName', '품명 및 모델명', model),
        opts.kc,
        opts.power,
        opts.release,
        text('maker', '제조자(수입자)', '로보얌테크(주)'),
        text('origin', '제조국', '대한민국'),
        text('sizeWeight', '크기 · 무게', opts.size),
        text('spec', '주요 사양', opts.spec),
        warranty('구입일로부터 6개월 무상 보증 — 소비자분쟁해결기준(공정거래위원회 고시)에 따라 보상해 드려요. 소모품(배터리)은 3개월이에요.'),
        text('asContact', 'A/S 책임자와 전화번호', AS),
    ];
}

/** (23) 어린이제품 */
function kids(model: string, opts: { kcNo: string; size: string; color: string; material: string; age: string; release: NoticeRowView; caution: string }): NoticeRowView[] {
    return [
        text('modelName', '품명 및 모델명', model),
        kc('kcCert', 'KC 인증정보', opts.kcNo, 'CERT'),
        text('sizeWeight', '크기 · 중량', opts.size),
        text('color', '색상', opts.color),
        text('material', '재질', opts.material),
        text('ageRange', '사용연령 또는 체중범위', opts.age),
        opts.release,
        text('maker', '제조자(수입자)', '로보얌에듀(주) / 수입자 동일'),
        text('origin', '제조국', '중국'),
        text('caution', '취급방법 · 취급 시 주의사항 · 안전표시', opts.caution),
        warranty(),
        text('asContact', 'A/S 책임자와 전화번호', AS),
    ];
}

/** (40) 기타 재화 */
function etc(model: string, cert: NoticeRowView): NoticeRowView[] {
    return [
        text('modelName', '품명 및 모델명', model),
        cert,
        text('origin', '제조국 또는 원산지', '중국'),
        text('maker', '제조자(수입자)', '로보얌테크(주) / 수입자 동일'),
        text('asContact', 'A/S 책임자와 전화번호(소비자상담 관련 전화번호)', AS),
    ];
}

/** 공구 ID 로 고시 양식 목업 — 모르는 ID 면 (40) 기타 재화 한 장 */
export function mockNotices(gbId: number, title: string): GroupBuyNoticeView[] {
    switch (gbId) {
        case 101:
            return [
                notice(101, 1, 8, '로봇 청소기 X1', homeAppliance('로봇 청소기 X1 (RV-X1)', {
                    kcNo: 'HU071234-24001',
                    power: 'AC 220V 60Hz(충전 스테이션) · 소비전력 40W',
                    release: date('releaseDate', '출시년월', '2026년 3월 이후 생산분', 'EARLIEST'),
                    size: '350 × 350 × 95mm',
                    spec: '배터리 5,200mAh(약 150분) · 흡입력 4,000Pa · 앱 연동',
                })),
            ];
        case 102:
            return [
                notice(102, 1, 12, '본체 세트', smallElectronics('코딩 로봇 키트 R1 (RK-R1)', {
                    kc: kc('kcCert', 'KC 인증정보', 'R-R-RBY-RKR1001 (전파법 적합성평가)', 'CERT'),
                    power: text('ratedPower', '정격전압 · 소비전력', 'DC 3.7V(리튬폴리머 1,200mAh) · 최대 5W'),
                    release: date('releaseDate', '출시년월', '2025년 11월', 'EXACT'),
                    size: '160 × 120 × 70mm · 420g(배터리 포함)',
                    spec: '메인보드 · 모터 2개 · 바퀴 · 배터리 1개 · 블록 코딩 앱 지원(예비 배터리 옵션은 이 양식을 따라요)',
                })),
                notice(102, 2, 12, '확장 센서 팩', smallElectronics('R1 확장 센서 팩 (RK-S3)', {
                    kc: kc('kcCert', 'KC 인증정보', '전기용품 및 생활용품 안전관리법상 안전확인 대상이 아닌 직류 5V 이하 부품이라 KC 인증이 면제돼요.', 'EXEMPT'),
                    power: none('ratedPower', '정격전압 · 소비전력', '본체에서 전원을 받는 부품이라 단독 소비전력을 재지 않았어요 — 정격전압 DC 5V, 본체 연결 때 0.5W 미만.'),
                    release: date('releaseDate', '출시년월', '2026년 1월', 'EXACT'),
                    size: '센서 3개 각 40 × 20 × 15mm · 합 60g',
                    spec: '거리(초음파) · 색상 · 소리 센서, 본체 확장 포트 연결',
                }), [1023]),
            ];
        case 103:
        case 104:
            return [
                notice(gbId, 1, 40, '데스크 로봇 팔 미니', etc('데스크 로봇 팔 미니 (DA-M4)', kc('kcCert', '법에 의한 인증 · 허가 등을 받았음을 확인할 수 있는 경우 그에 대한 사항', 'R-R-RBY-DAM4002 (전파법 적합성평가)', 'CERT'))),
            ];
        case 105:
            return [
                notice(105, 1, 23, '휴머노이드 교육 로봇 H5', kids('휴머노이드 교육 로봇 H5 (HE-H5)', {
                    kcNo: 'CB065R123-6001',
                    size: '높이 420mm · 2.1kg',
                    color: '화이트 · 블루',
                    material: 'ABS 수지 · 알루미늄 프레임',
                    age: '만 8세 이상',
                    release: none('releaseDate', '동일모델 출시년월', '신제품이라 아직 출시 전이에요 — 2026년 12월 출시 예정이에요.', 'DATE'),
                    caution: '작은 부품이 있어 만 3세 미만 어린이가 삼키지 않게 해 주세요. 관절 사이에 손가락이 끼지 않게 주의해 주세요.',
                })),
            ];
        case 106:
            return [
                notice(106, 1, 23, '자율주행 RC카 키트', kids('자율주행 RC카 키트 (RC-A1)', {
                    kcNo: 'CB063R456-5002',
                    size: '280 × 190 × 110mm · 1.2kg',
                    color: '블랙 · 오렌지',
                    material: 'ABS 수지 · 고무 타이어',
                    age: '만 10세 이상',
                    release: date('releaseDate', '동일모델 출시년월(제조연월)', '2025년 9월 ~ 2025년 12월 생산분', 'RANGE'),
                    caution: '충전 중에는 자리를 비우지 말아 주세요. 젖은 손으로 만지지 마세요.',
                })),
            ];
        case 107:
            return [
                notice(107, 1, 8, '스마트 화분 로봇 P2', homeAppliance('스마트 화분 로봇 P2 (SP-P2)', {
                    kcNo: 'XU100567-25003',
                    power: 'DC 5V(USB-C 어댑터 포함, AC 220V) · 소비전력 6W',
                    release: date('releaseDate', '제조연월', '발송일 기준 6개월 안에 만든 제품', 'ORDER_BASED'),
                    size: '지름 160mm × 높이 210mm',
                    spec: '토양 수분 · 조도 센서 · 자동 급수(물통 500mL)',
                })),
            ];
        case 108:
            return [
                notice(108, 1, 23, '로봇 강아지 펫봇 S', kids('로봇 강아지 펫봇 S (PB-S1)', {
                    kcNo: 'CB061R789-4003',
                    size: '300 × 150 × 220mm · 1.5kg',
                    color: '그레이',
                    material: 'ABS 수지 · 실리콘',
                    age: '만 6세 이상',
                    release: date('releaseDate', '동일모델 출시년월', '2025년 6월', 'EXACT'),
                    caution: '물에 닿지 않게 해 주세요. 다리 관절에 손가락이 끼지 않게 주의해 주세요.',
                })),
            ];
        default:
            return [notice(gbId, 1, 40, title, etc(title, none('kcCert', '법에 의한 인증 · 허가 등을 받았음을 확인할 수 있는 경우 그에 대한 사항', '인증 · 허가 대상이 아니에요.', 'KC')))];
    }
}
