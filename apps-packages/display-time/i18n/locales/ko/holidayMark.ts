/**
 * 달력 날짜 칸의 쉬는 날 표시 문구(`UtilHolidayMessages`) — 모든 달력이 같이 쓴다. `en/holidayMark.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 접근성 이름 — 「2026년 9월 25일, 추석」
    cellLabel: '{year}년 {month}월 {day}일, {names}',
    // 회사 휴무일 이름 뒤에 붙는 말(관리자 앱만)
    company: '{name}(회사 휴무일)',
    // 이름이 비어 있을 때
    companyOnly: '회사 휴무일',
    holidayOnly: '공휴일',
};
