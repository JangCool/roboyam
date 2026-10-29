/**
 * 공용 주소 입력(`AddressInput`)·자체 주소 DB 검색(`AddressSearchPanel`)·개발 시연 화면(`/dev/address`)의 문구. `en/address.ts` 와 같은 키, 같은 차례.
 */
export default {
    postcode: '우편번호',
    search: '우편번호 찾기',
    line1: '주소',
    line1Placeholder: '우편번호 찾기로 채워져요',
    line2: '상세 주소',
    line2Placeholder: '동·호수 등',
    manualHelp: '우편번호와 주소를 직접 입력해 주세요.',
    searchUnavailable: '우편번호 찾기를 열지 못했어요. 우편번호와 주소를 직접 입력해 주세요.',
    fallbackNotice: '다음 우편번호 서비스를 열지 못해서 사이트의 주소 목록에서 찾아요.',
    manualEntry: '직접 입력',

    keywordLabel: '주소 검색어',
    keywordPlaceholder: '도로명, 건물 이름, 지번으로 찾기',
    searchIntro: '도로명 주소나 건물 이름을 넣어 찾아 주세요. 예) 테헤란로 152, 시청',
    keywordTooShort: '{min}자 이상 입력해 주세요.',
    noResults: '찾는 주소가 없어요. 검색어를 바꾸거나 직접 입력해 주세요.',
    resultCount: '주소 {count}건',
    jibunPrefix: '지번 {address}',
    searchFailed: '주소를 찾지 못했어요. 잠시 후 다시 시도해 주세요.',

    devBadge: '개발 전용 화면',
    devTitle: '주소 입력 시연',
    devIntro: '공용 주소 입력을 눌러 봐요. 관리자 설정 › 주소 검색의 제공처가 회원 화면에서 어떻게 보이는지 확인하는 화면이에요.',
    devCaseTitle: '1. 지금 설정',
    devCaseIntro: '서버에서 받은 제공처와 다음 스크립트 주소예요. 관리자 화면에서 바꿨으면 다시 받은 뒤 「우편번호 찾기」를 눌러 주세요.',
    devReload: '설정 다시 받기',
    devInputTitle: '2. 새 주소 입력',
    devShowErrors: '칸 오류 보이기 · 감추기',
    devClear: '비우기',
    devValue: '지금 값',
    devManualTitle: '3. 이미 있는 주소 고치기',
    devManualIntro: '저장된 주소를 고칠 때는 처음부터 직접 입력이 열려 있어요(우편번호 찾기도 그대로 쓸 수 있어요).',
};
