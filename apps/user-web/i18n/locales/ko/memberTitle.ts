/**
 * 회원 칭호(member-title-contract) — 작성자 줄 대표 칭호 · 공개 프로필 칭호 모음 · 내 정보 › 칭호 · 축하 창 · 명예의 전당·칭호 도감 · 게시판 스텝 「칭호 신청」.
 * 키는 `memberTitle.무엇` 두 마디(묶음은 세 마디). `en/memberTitle.ts` 와 같은 키, 같은 차례. 칭호 이름·조건 글자·받는 방법 안내는 서버가 요청 언어로 준다.
 */
export default {
    hallOfFame: '명예의 전당',
    myEntry: '칭호',
    myTitle: '내 칭호',
    loading: '칭호를 불러오는 중',
    loadFailed: '칭호를 불러오지 못했어요',

    // 대표 칭호
    repHeading: '대표 칭호',
    repHint: '글 목록·글 보기·댓글의 내 이름 옆에 하나만 보여요.',
    repDefault: '기본',
    repDefaultHint: '가진 작가 칭호 중 가장 높은 것',
    repHidden: '안 보이기',
    repHiddenHint: '이름 옆에 칭호를 보이지 않아요',
    repSaved: '대표 칭호를 바꿨어요.',

    // 가진 칭호
    ownedHeading: '가진 칭호 {count}개',
    ownedEmpty: '아직 받은 칭호가 없어요',
    ownedEmptyHint: '좋은 글을 쓰고 꾸준히 활동하면 칭호를 받을 수 있어요.',

    // 진행
    progressHeading: '다음 칭호까지',
    progressCount: '{current} / {target}',
    progressTier: '{label} {tier}',

    // 신청형 칭호
    applyHeading: '신청해서 받는 칭호',
    applyAction: '신청하기',
    applied: '신청했어요. 심사 결과는 알림으로 알려 드려요.',
    conditionMet: '충족',
    conditionNotMet: '아직 못 채움',
    reapplyAt: '{date}부터 다시 신청할 수 있어요',

    // 신청 상태
    applicationsHeading: '신청 기록',
    rejectReasonLine: '사유: {reason}',

    tier: {
        BRONZE: '동',
        SILVER: '은',
        GOLD: '금',
    },
    method: {
        AUTO: '자동',
        APPLY: '신청 심사',
        AWARD: '운영자 수여',
    },
    status: {
        PENDING: '심사 중',
        APPROVED: '승인',
        REJECTED: '거절',
    },

    ratio: '회원 중 {percent}%',
    ratioUnder: '회원 중 {percent}% 미만',
    periodMonth: '{year}년 {month}월',
    periodYear: '{year}년',
    countLabel: '{label} ×{count}',

    // 공개 프로필
    profileHeading: '칭호',
    periodsOpen: '{label} {count}번 — 받은 달 보기',
    periodsTitle: '{label} 받은 때',

    // 명예의 전당 · 칭호 도감
    hallLead: '이 사이트를 빛낸 작가들과 모든 칭호를 받는 방법이에요.',
    hallHeading: '명예의 전당',
    hallLoadFailed: '명예의 전당을 불러오지 못했어요',
    hallEmpty: '아직 명예의 전당에 오른 회원이 없어요',
    hallEmptyHint: '매달 이달의 작가가 뽑히면 여기에 남아요.',
    honoraryHeading: '명예 작가',
    yearlyHeading: '역대 올해의 작가',
    monthlyHeading: '역대 이달의 작가',
    catalogHeading: '칭호 도감',
    catalogLoadFailed: '칭호 도감을 불러오지 못했어요',
    catalogEmpty: '아직 칭호가 없어요',
    holders: '{count}명이 가졌어요',
    tierLine: '{tier} · {threshold}번 · {count}명',
    hallSeo: '{site}의 명예의 전당과 칭호 도감 — 명예 작가 · 올해의 작가 · 이달의 작가와 칭호를 받는 방법.',

    // 축하 창
    celebrateTitle: '새 칭호를 받았어요',
    celebrateText: '축하해요! 「{label}」 칭호를 받았어요.',
    celebrateRatio: '{ratio}만 가진 칭호예요',
    celebrateMore: '그 밖에 {count}개를 더 받았어요',
    celebrateUse: '대표 칭호로 쓰기',
    celebrateHall: '명예의 전당 보기',

    // 게시판 스텝 「칭호 신청」
    staffTab: '칭호 신청',
    staffTitle: '칭호 신청 심사',
    staffHint: '이 게시판에 연결된 칭호의 신청이에요. 내 신청은 심사할 수 없어요.',
    staffStatusLabel: '신청 상태',
    staffPending: '심사 중',
    staffEmptyPending: '심사할 신청이 없어요',
    staffEmpty: '칭호 신청이 없어요',
    staffLoadFailed: '칭호 신청을 불러오지 못했어요',
    staffOwnApplication: '내 신청이라 다른 매니저나 운영자가 심사해요.',
    reviewReasonLine: '심사 사유: {reason}',
    approve: '승인',
    reject: '거절',
    approveTitle: '칭호 신청 승인',
    rejectTitle: '칭호 신청 거절',
    approveText: '{name} 님에게 「{label}」 칭호를 줘요.',
    rejectText: '{name} 님의 「{label}」 신청을 거절해요.',
    approveReasonLabel: '메모(선택)',
    rejectReasonLabel: '거절 사유',
    rejectNotice: '사유는 신청한 회원에게 보여요. 거절하면 정해진 날 뒤에 다시 신청할 수 있어요.',
    rejectReasonRequired: '거절 사유를 적어 주세요.',
    approvedToast: '{name} 님의 신청을 승인했어요.',
    rejectedToast: '{name} 님의 신청을 거절했어요.',
};
