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
    dayBasis: '하루는 한국 시간 0시에 새로 시작해요.',

    // 적립 규칙
    rulesTitle: '적립 규칙',
    ruleAttendance: '출석',
    rulePost: '글 작성',
    ruleComment: '댓글 작성',
    ruleLikeBonus: '추천 {threshold}개를 받으면',
    rulePerDay: '+{amount}P · 하루 {cap}번',
    ruleOff: '지급하지 않아요',
    rulesRevokeNote: '글·댓글을 지우면 그 글·댓글로 받은 포인트를 돌려받아요. 포인트는 0P 아래로 내려가지 않고, 모자란 양은 다음 적립에서 차감돼요.',

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
