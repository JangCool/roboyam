/**
 * 포인트 문구 중 셸(헤더 메뉴·햄버거 메뉴)이 쓰는 것. 키는 `point.무엇` 두 마디. `en/point.ts` 와 같은 키, 같은 차례.
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
};
