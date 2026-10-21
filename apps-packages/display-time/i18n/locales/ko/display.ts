/**
 * 표시 값(언어·시간대·시간 형식·날짜 형식) 폼 `DisplayValueForm` 의 문구 — 사용자 개인 설정과 관리자 표시 기본값 화면이 한 폼을 같이 쓴다(hamonica3 `display.ts` 와 같은 생각).
 * 구획 제목·저장 안내·토스트처럼 **화면마다 다른 문구는 쓰는 앱의 문구 파일**이 갖는다 — 여기는 폼 안의 칸 이름·안내·선택지 이름만 안다.
 * **서버 `message` 가 있으면 그것을 먼저 쓴다.** `en/display.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 칸 이름·안내
    language: '언어',
    zoneLabel: '시간대',
    zoneHelp: '그리는 데만 써요. 저장은 UTC 그대로라 바꿔도 이미 쌓인 기록은 그대로예요.',
    zoneFilter: '도시·지역 이름으로 찾기 (예: New_York, 서울)',
    zoneEmpty: '찾는 시간대가 없어요',
    zonePlaceholder: '시간대를 골라 주세요',
    timeFormat: '시간 형식',
    dateFormat: '날짜 형식',
    previewTitle: '이렇게 보여요',
    previewPast: '어제 이전',

    // 시간대 묶음 이름
    zoneGroupMajor: '주요 시간대',
    zoneGroupAll: '모든 시간대',

    // 시간 형식·날짜 형식 이름 — 뒤에 고른 시간대의 지금 견본이 괄호로 붙는다(`24시간 (14:32)`)
    timeFormat_H24: '24시간',
    timeFormat_H12: '12시간',
    dateFormat_YMD_DOT: '연.월.일',
    dateFormat_YMD_DASH: '연-월-일',
    dateFormat_DMY_SLASH: '일/월/연',
    dateFormat_MDY_SLASH: '월/일/연',
};
