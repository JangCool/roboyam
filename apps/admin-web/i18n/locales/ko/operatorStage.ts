/**
 * 운영 주체의 문구 — 설정 > 운영·약관 > 운영 주체 화면(`SiteOperatorSettings`)과 약관 화면의 운영 주체 요약 줄. `en/operatorStage.ts` 와 같은 키, 같은 차례.
 * ⚠ 약관 본문에 들어가는 호칭·이름표(「운영자」·「회사」·「상호」 등)는 화면 문구가 아니라 약관 글자라 여기에 없다(`UtilTermsPlaceholder`).
 */
export default {
    // ── 구획 ──
    title: '운영 주체',
    pageSubtitle: '개인·개인사업자·법인 단계와 운영자·사업자 정보 — 약관을 게시할 때 이 값이 들어가요',
    intro: "사이트를 운영하는 사람·사업자예요. 약관 본문의 자리표시(「{'{{'}운영자{'}}'}」 등)는 게시할 때 이 값으로 바뀌어 저장돼요.",
    loadFailed: '운영 주체를 불러오지 못했어요',
    saved: '운영 주체를 저장했어요',

    // ── 단계 ──
    stageLabel: '단계',
    stage: {
        INDIVIDUAL: '개인',
        SOLE_PROPRIETOR: '개인사업자',
        CORPORATION: '법인',
    },
    stageHelp: {
        INDIVIDUAL: '사업자등록 전 — 약관 속 호칭 「운영자」',
        SOLE_PROPRIETOR: '사업자등록 후 — 호칭 「운영자」',
        CORPORATION: '법인 설립 후 — 호칭 「회사」',
    },
    individualNotice: '애드센스 광고는 쓸 수 있어요. 공구 판매·로보티콘 유료 판매·광고/핫딜 유료 신청은 개인사업자 이상 + 통신판매업 신고 뒤에 켤 수 있어요',

    // ── 공통 칸 ──
    commonTitle: '운영자 정보',
    operatorName: '운영자 이름',
    operatorNameHelp: '개인정보처리방침에 개인정보 보호책임자로 공개돼요',
    contactEmail: '연락 이메일',
    phone: '전화번호',
    phoneOptional: '선택',
    phoneRequired: '필수',
    officerSame: '개인정보 보호책임자 — 운영자와 같음',
    officerSameHelp: '끄면 보호책임자의 이름과 이메일을 따로 써요.',
    officerName: '보호책임자 이름',
    officerEmail: '보호책임자 이메일',
    hostingProvider: '호스팅 사업자',
    hostingProviderPlaceholder: '예: Amazon Web Services',
    mailProvider: '메일 발송 사업자',
    mailProviderPlaceholder: '예: Amazon Web Services (Amazon SES)',
    optional: '선택',
    lengthHint: '{max}자까지',

    // ── 사업자 칸 ──
    businessTitle: '사업자 정보',
    businessName: '상호',
    companyName: '회사명',
    representative: '대표자',
    registrationNumber: '사업자등록번호',
    registrationNumberHelp: '숫자만 써도 하이픈을 맞춰 넣어요.',
    corporateNumber: '법인등록번호',
    address: '사업장 주소',
    addressPlaceholder: '한 줄로 써 주세요',
    mailOrderNumber: '통신판매업 신고번호',
    mailOrderAuthority: '신고 기관',
    mailOrderAuthorityPlaceholder: '예: 서울특별시 강남구',
    mailOrderHelp: '비워 두면 공구 판매·로보티콘 유료 판매·광고/핫딜 유료 신청을 받을 수 없어요',

    // ── 푸터 안내 문구 ──
    footerNoticeTitle: '푸터 안내 문구',
    footerNotice: '안내 문구',
    footerNoticeHint: '한 줄에 한 문장 · {lines}줄까지 · 줄마다 {max}자까지',
    footerNoticeHelp:
        "공개 사이트 푸터 아래쪽에 줄마다 한 줄씩 보여요. 비워 두면 안내를 숨겨요. {'{'}사이트{'}'}를 쓰면 사이트 이름으로 바뀌고, 바로 뒤의 은/는·이/가·을/를은 사이트 이름에 맞춰 고쳐져요(예: {'{'}사이트{'}'}은 → 로보얌은, ROBOYAM은).",
    footerNoticeScopeLabel: '보이기',
    footerNoticeScope: {
        INDIVIDUAL_ONLY: '개인 단계에서만 보이기',
        ALWAYS: '늘 보이기',
    },
    footerNoticeProblemLines: '{max}줄까지 쓸 수 있어요.',
    footerNoticeProblemLineLength: '한 줄에 {max}자 이하로 줄여 주세요.',

    // ── 칸 문제 ──
    required: {
        operatorName: '운영자 이름을 입력해 주세요.',
        contactEmail: '연락 이메일을 입력해 주세요.',
        phone: '사업자 단계에서는 전화번호가 필요해요.',
        privacyOfficerName: '보호책임자 이름을 입력해 주세요.',
        privacyOfficerEmail: '보호책임자 이메일을 입력해 주세요.',
        hostingProvider: '호스팅 사업자를 입력해 주세요.',
        mailProvider: '메일 발송 사업자를 입력해 주세요.',
        businessName: '상호를 입력해 주세요.',
        companyName: '회사명을 입력해 주세요.',
        representative: '대표자를 입력해 주세요.',
        registrationNumber: '사업자등록번호를 입력해 주세요.',
        address: '사업장 주소를 입력해 주세요.',
        mailOrderNumber: '통신판매업 신고번호를 입력해 주세요.',
        mailOrderAuthority: '신고 기관을 입력해 주세요.',
        corporateNumber: '법인등록번호를 입력해 주세요.',
    },
    problemLength: '{max}자 이하로 줄여 주세요.',
    problemEmail: '이메일 주소 모양이 아니에요.',
    problemPhone: '숫자와 + - ( ), 띄어쓰기만 쓸 수 있어요.',
    problemBizNumber: '123-45-67890 모양으로 써 주세요.',

    // ── 단계를 바꾸고 저장할 때 ──
    changeConfirmTitle: '단계를 {from}에서 {to} 바꿀까요?',
    /** 확인 창 제목의 「~(으)로」 — 단계 이름마다 받침에 맞춘 조사 */
    stageTo: {
        INDIVIDUAL: '개인으로',
        SOLE_PROPRIETOR: '개인사업자로',
        CORPORATION: '법인으로',
    },
    changeConfirmMessage: '단계를 바꾸면 이용약관·개인정보처리방침·운영정책 새 버전을 게시해야 해요. 지금 게시본은 바뀌지 않아요',
    changeConfirmCorporation: '법인이 되면 회원 정보가 다른 주체로 넘어가요. 바뀐 약관이 시행되기 전에 회원 전체에게 알려야 해요(개인정보보호법 제27조)',
    changeConfirmLabel: '바꾸고 저장',

    // ── 전환 이력 ──
    historyTitle: '전환 이력',
    historyEmpty: '아직 단계를 바꾼 적이 없어요',
    colChangedAt: '날짜',
    colChange: '단계',
    colChangedBy: '처리자',
    changeLine: '{from} → {to}',
    unknownOperator: '알 수 없음',

    // ── 떠날 때 ──
    leaveTitle: '저장하지 않은 운영 주체 변경이 있어요',
    leaveMessage: '저장하지 않은 변경은 사라져요. 계속할까요?',
    leaveConfirm: '버리고 이동',

    // ── 약관 화면의 요약 줄 ──
    summaryLine: '지금 운영 주체: {stage} · 약관 속 호칭 「{alias}」',
    summaryLink: '운영 주체에서 바꾸기',
    summaryLoadFailed: '운영 주체를 불러오지 못했어요',
    alias: {
        INDIVIDUAL: '운영자',
        SOLE_PROPRIETOR: '운영자',
        CORPORATION: '회사',
    },
};
