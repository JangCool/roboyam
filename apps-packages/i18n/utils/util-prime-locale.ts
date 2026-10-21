/**
 * PrimeVue 컴포넌트가 스스로 쓰는 말 — DatePicker(날짜·시간 고르개)의 요일·달 이름과 단추 글자, 파일 고르기 안내.
 * 우리 문구(vue-i18n)와는 별개의 사전이다 — 둘을 함께 바꿔야 "달력은 영어인데 단추는 한국어"가 되지 않는다(`useLanguage`). 필요한 키만 덮는다(나머지는 PrimeVue 기본).
 * 영어는 PrimeVue 기본 사전이 영어라 따로 적지 않는다 — 시작할 때 기본 사전을 복사해 두었다가(`defaults`) 영어로 바꿀 때 그것을 되돌려 덮는다(`useLanguage`).
 * 날짜 패턴(`dateFormat`)은 표시 값의 날짜 형식을 따르므로 언어와 별개로 `@roboyam/display-time` 이 덮는다.
 * 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다.
 */

/** PrimeVue 로캘에 덮는 키들 */
export type PrimeLocaleOverrides = {
    firstDayOfWeek: number;
    dayNames: string[];
    dayNamesShort: string[];
    dayNamesMin: string[];
    monthNames: string[];
    monthNamesShort: string[];
    today: string;
    clear: string;
    weekHeader: string;
    dateFormat: string;
    am: string;
    pm: string;
    chooseYear: string;
    chooseMonth: string;
    chooseDate: string;
    prevDecade: string;
    nextDecade: string;
    prevYear: string;
    nextYear: string;
    prevMonth: string;
    nextMonth: string;
    prevHour: string;
    nextHour: string;
    prevMinute: string;
    nextMinute: string;
    prevSecond: string;
    nextSecond: string;
    choose: string;
    noFileChosenMessage: string;
    fileChosenMessage: string;
    fileSizeTypes: string[];
};

const KO: PrimeLocaleOverrides = {
    firstDayOfWeek: 0,
    dayNames: ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'],
    dayNamesShort: ['일', '월', '화', '수', '목', '금', '토'],
    dayNamesMin: ['일', '월', '화', '수', '목', '금', '토'],
    monthNames: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
    monthNamesShort: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
    today: '오늘',
    clear: '지우기',
    weekHeader: '주',
    dateFormat: 'yy-mm-dd',
    am: '오전',
    pm: '오후',
    chooseYear: '연도 고르기',
    chooseMonth: '달 고르기',
    chooseDate: '날짜 고르기',
    prevDecade: '이전 10년',
    nextDecade: '다음 10년',
    prevYear: '이전 해',
    nextYear: '다음 해',
    prevMonth: '이전 달',
    nextMonth: '다음 달',
    prevHour: '이전 시',
    nextHour: '다음 시',
    prevMinute: '이전 분',
    nextMinute: '다음 분',
    prevSecond: '이전 초',
    nextSecond: '다음 초',
    // 파일 고르기(FileUpload mode="basic") — 고른 파일이 없을 때 영문 안내("No file chosen")를 보이지 않는다(화면이 자기 안내 글을 둔다)
    choose: '파일 선택',
    noFileChosenMessage: '',
    fileChosenMessage: '파일 {0}개',
    fileSizeTypes: ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'],
};

export default class UtilPrimeLocale {
    /**
     * 언어에 맞는 PrimeVue 로캘 — 한국어면 한국어 사전, 그 밖(영어)은 null(PrimeVue 기본 = 시작할 때 복사해 둔 사전으로 되돌린다).
     * @param {string} language - 언어(`ko`·`en`).
     * @returns {PrimeLocaleOverrides | null} 덮을 값(새 객체). 영어면 null.
     */
    static overridesFor(language: string): PrimeLocaleOverrides | null {
        return language === 'ko' ? { ...KO } : null;
    }
}
