/**
 * 포인트 문구 — 셸(헤더 메뉴·햄버거 메뉴)·내 정보의 포인트 요약·포인트 화면(`/me/points`). 내역 한 줄의 사유 이름은 서버가 준다(여기 없다). 키는 `point.무엇` 두 마디. `en/point.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다).
 */
export default {
    // 포인트 화면 입구 이름
    entry: '포인트·출석체크',
    entryWithReward: '포인트·출석체크 · +{amount}P',
    // 출석 기능이 없을 때(`attendanceAvailable=false`)·요약을 받기 전
    entryNoAttendance: '포인트',

    // 보유 포인트 한 줄 — {points} 는 "1,280P" 꼴
    balanceLine: '포인트 {points}',

    // 출석 단추
    attend: '오늘 출석하기',
    attendWithReward: '오늘 출석하기 +{amount}P',

    // 보유 포인트 + 출석 안내
    summaryWithAttend: '{balance} · {attend}',
    // 내 정보 바로가기 줄 오른쪽 — 오늘 출석 전이면 받을 포인트를 붙인다
    entryHint: '{balance} · 출석 +{amount}P',

    // 포인트 화면(`/me/points`)·내 정보의 포인트 요약
    title: '포인트',
    seoDescription: '{site} 보유 포인트와 적립 내역이에요.',
    history: '포인트 내역',
    loading: '포인트를 불러오는 중',
    loadFailed: '포인트를 불러오지 못했어요',
    balance: '보유 포인트',
    // 적립/충전 나눔(충전 기능이 열렸을 때만 — commerce D13)·소멸 예정·미회수 — {paid}·{free}·{points} 는 "1,280P" 꼴
    splitLine: '충전 포인트 {paid} · 적립 포인트 {free}',
    expiringLine: '{date} 소멸 예정 {points}',
    expiryNote: '포인트는 받은 날부터 1년 뒤 소멸돼요. 소멸일이 가까운 포인트부터 먼저 쓰여요.',
    expiryNoteSplit: '적립 포인트는 받은 날부터 1년 뒤 소멸돼요(충전 포인트는 소멸되지 않아요). 소멸일이 가까운 적립 포인트부터 먼저 쓰여요.',
    debtTitle: '미회수 {points} — 다음 적립에서 차감돼요',
    debtText: '지운 글·댓글로 받은 포인트를 돌려받아야 하는데 포인트가 모자라 덜 가져간 양이에요.',
    todayTitle: '오늘 적립',
    todayPost: '글 작성',
    todayComment: '댓글 작성',
    todayLikeBonus: '추천 보너스',
    countOfCap: '{count}/{cap}회',
    capReached: '오늘 다 받았어요',
    todayTotal: '오늘 총 적립',
    totalOfCap: '{amount} / {cap}',
    totalCapReached: '오늘 한도에 닿았어요',
    dayBasis: '하루는 한국 시간 0시에 새로 시작해요.',

    // 적립 규칙
    rulesTitle: '적립 규칙',
    ruleAttendance: '출석',
    rulePost: '글 작성',
    ruleComment: '댓글 작성',
    ruleLikeBonus: '추천 {threshold}개를 받으면',
    rulePerDay: '+{amount}P · 하루 {cap}번',
    ruleOff: '지급하지 않아요',
    ruleSignup: '본인인증 보너스 +{amount}P(한 번)',
    signupReceived: '받았어요',
    signupVerify: '본인인증하기',
    conditionsTitle: '적립 조건',
    condPost: '글은 본문 {n}자 이상(게시판에 따라 다를 수 있어요)',
    condComment: '댓글은 {rules}',
    condCommentMin: '{n}자 이상',
    condCommentOwnPost: '내 글에 단 댓글 제외',
    condCommentOnce: '한 글에 한 번',
    condInterval: '포인트를 받은 글·댓글 뒤 {seconds}초 안에 쓴 것은 적립 없음',
    condDailyTotal: '활동 적립은 하루 {cap}까지 — 넘치는 만큼은 주지 않아요',
    rulesRevokeNote: '글·댓글을 지우면 그 글·댓글로 받은 포인트를 돌려받아요. 포인트는 0P 아래로 내려가지 않고, 모자란 양은 다음 적립에서 차감돼요.',
    ruleAttendanceStreak: '연속 출석 {days}일마다',

    // 출석체크(커머스 1-3) — 달력·오늘 출석
    attendanceTitle: '출석체크',
    todayAttendance: '출석',
    attendDone: '완료',
    attending: '출석하는 중…',
    attended: '오늘 출석 완료',
    attendHelp: '하루 1번만 받을 수 있어요. 날짜는 한국 시간 자정 기준이에요.',
    attendedHelp: '내일 다시 출석할 수 있어요.',
    calPrev: '지난달',
    calNext: '다음 달',
    calMonth: '{year}년 {month}월',
    // 일요일부터 — 쉼표로 나눈다
    calWeekdays: '일,월,화,수,목,금,토',
    calLabel: '출석 달력',
    calLoading: '출석 달력을 불러오는 중',
    calLoadFailed: '출석 달력을 불러오지 못했어요',
    calDay: '{month}월 {day}일',
    calDayAttended: '{date} 출석함',
    calDayToday: '{label} · 오늘',
    calLegend: '파란 칸은 출석한 날, 굵은 테두리는 오늘이에요.',
    calEmptyMonth: '이 달에는 출석 기록이 없어요 · 가입하기 전이거나 출석하지 않은 달이에요.',
    monthCount: '이 달 출석 {count}일',
    streak: '연속 {count}일',
    streakNext: '{days}일 더 출석하면 +{bonus}P',
    streakNextToday: '오늘 출석하면 연속 보너스 +{bonus}P',
    attendedToast: '출석했어요! +{amount}P',
    attendedToastStreak: '출석했어요! +{amount}P · 연속 {streak}일 보너스 +{bonus}P',
    attendedToastNoPoints: '출석했어요',
    alreadyAttendedToast: '오늘은 이미 출석했어요',
    attendFailedTitle: '출석 처리에 실패했어요.',
    attendFailedText: '포인트는 지급되지 않았어요. 잠시 후 다시 시도해 주세요.',
    attendUnknownTitle: '출석 결과를 확인하지 못했어요.',
    attendUnknownText: '달력과 보유 포인트를 다시 불러와 확인할게요. 이미 출석됐다면 다시 누르지 않아도 돼요.',
    attendRateLimitedTitle: '잠시 후 다시 시도해 주세요',
    attendRateLimitedText: '요청이 너무 많아요. 조금 뒤에 다시 눌러 주세요.',
    blockedSuspendedTitle: '정지 중에는 출석할 수 없어요',
    blockedSuspendedText: '정지가 풀리면 다시 출석할 수 있어요. 정지 중에는 포인트가 지급되지 않아요.',
    blockedEmailTitle: '이메일 인증 후 출석할 수 있어요',
    blockedEmailText: '인증 메일의 링크를 누르면 바로 출석할 수 있어요.',
    blockedEmailLink: '이메일 인증 안내 보기',
    blockedSanctionLink: '제재 안내 보기',

    // 내역
    kindFilter: '내역 거르기',
    kindEarn: '적립',
    kindRevoke: '차감',
    kindExpire: '소멸',
    kindCancelUse: '사용 취소',
    restoredUntil: '유효기간이 지난 포인트라 {date}까지 쓸 수 있게 돌려드렸어요',
    historyLoadFailed: '내역을 불러오지 못했어요',
    historyEmpty: '아직 포인트 내역이 없어요',
    historyEmptyFiltered: '이 조건의 내역이 없어요',
    targetUnavailable: '볼 수 없는 글',
    balanceAfter: '잔액 {points}',

    // 사업자 전용 계정
    businessTitle: '사업자 계정은 포인트를 쓰지 않아요',
    businessText: '포인트 적립과 내역은 일반 회원 계정에서 볼 수 있어요.',
};
