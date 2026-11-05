/**
 * 휴일 관리(`/settings/holidays`) 문구 — 사이트 휴일 표 하나(공공데이터 공휴일 · 기본값 · 회사 휴무일). `en/holiday.ts` 와 같은 키, 같은 차례.
 * 달력 칸의 휴일 표시 문구는 레이어(`holidayMark.*`)가 갖는다. **서버 `message` 가 있으면 그것을 먼저 쓴다.**
 */
export default {
    title: '휴일 관리',
    subtitle: '사이트의 쉬는 날을 이 표 하나로 정해요. 모든 달력의 휴일 표시와 공구 영업일 계산이 이 표를 써요. 공휴일은 공공데이터에서 매월 받아요.',
    readOnly: '보기만 할 수 있어요. 회사 휴무일을 바꾸거나 지금 받으려면 「사이트 설정 변경」 권한이 필요해요.',
    loadFailed: '휴일을 불러오지 못했어요.',

    // 공공데이터 받기
    syncSection: '공공데이터 받기',
    syncLead: '한국천문연구원 특일 정보에서 공휴일을 받아요. 매월 저절로 받고, 여기서 바로 받을 수도 있어요.',
    noKeyTitle: '공공데이터 인증키가 없어요',
    noKeyText: '공공데이터 설정에서 인증키를 넣어 주세요. 그 전에는 기본값 공휴일만 써요.',
    noKeyLink: '공공데이터 설정으로 가기',
    syncNever: '아직 받은 적이 없어요.',
    syncRanAt: '마지막으로 받은 때',
    syncRanBy: '{at} · {name}',
    syncAuto: '{at} · 정기 받기',
    syncResult: '결과',
    syncApplied: '받은 해',
    syncChanged: '바뀐 수',
    syncChangedCount: '{count}건',
    syncFailed: '받지 못한 해',
    syncNone: '없음',
    syncNow: '지금 받기',
    syncDone: '공휴일을 받았어요.',
    syncDoneNoKey: '인증키가 없어 받지 않았어요.',
    syncDoneFailed: '공휴일을 받지 못했어요.',
    outcome: {
        OK: '모두 받음',
        PARTIAL: '일부만 받음',
        NO_KEY: '인증키 없음',
        FAILED: '받지 못함',
    },

    // 해별 목록
    listSection: '{year}년 휴일',
    prevYear: '앞 해',
    nextYear: '다음 해',
    thisYear: '올해',
    colDate: '날짜',
    colName: '이름',
    colSource: '출처',
    source: {
        PUBLIC_DATA: '공공데이터',
        SEED: '기본값',
        OPERATOR: '회사 휴무일',
    },
    substitute: '대체공휴일',
    addedBy: '{name}님이 넣음',
    empty: '이 해에는 휴일이 없어요.',
    emptyHint: '공공데이터를 받거나 회사 휴무일을 더해 보세요.',
    remove: '지우기',
    removeAria: '{date} {name} 지우기',
    removeTitle: '회사 휴무일을 지울까요?',
    removeMessage: '{date} 「{name}」을(를) 지워요.',
    removed: '회사 휴무일을 지웠어요.',
    readOnlyRow: '공공데이터·기본값 휴일은 바꿀 수 없어요.',

    // 회사 휴무일 더하기
    addSection: '회사 휴무일 더하기',
    addLead: '공휴일이 아닌 회사 쉬는 날이에요(창립기념일 등). 관리자 달력에만 보여요(회원 화면 달력에는 공휴일만).',
    addDate: '날짜',
    addName: '이름',
    addNameHint: '1~30자',
    addNamePlaceholder: '예: 창립기념일',
    addSubmit: '더하기',
    added: '회사 휴무일을 더했어요.',
    dateRequired: '날짜를 골라 주세요.',
    nameRequired: '이름을 넣어 주세요.',
    nameTooLong: '이름은 30자까지예요.',
};
