/**
 * 검색 엔진 등록(설정 > 일반 「검색 엔진 등록」 — `components/site/SiteSearchVerificationSettings.vue`) 문구.
 * 키는 `searchVerification.무엇` 두 마디. `en/searchVerification.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '검색 엔진 등록',
    intro: '검색 엔진에 사이트 소유를 확인받는 코드예요. 저장하면 공개 사이트 첫 화면 HTML 머리(head)에 소유 확인 메타 태그가 들어가요. 비워 둔 칸은 태그를 넣지 않아요.',
    loadFailed: '검색 엔진 등록 정보를 불러오지 못했어요',
    hint: '선택 · 영문·숫자·-·_ {max}자 이내',
    placeholder: 'content 값 또는 복사한 메타 태그 전체',
    openSite: '등록 화면',
    newTab: '새 창',
    naverName: '네이버',
    googleName: '구글',
    bingName: '빙',
    naverLabel: '네이버 서치어드바이저',
    naverHelp: '서치어드바이저 › 사이트 등록 › HTML 태그에서 content 값을 붙여 넣으세요. 저장한 뒤 네이버에서 소유 확인을 누르세요.',
    googleLabel: '구글 Search Console',
    googleHelp: 'Search Console › 속성 추가 › URL 접두어 › HTML 태그에서 content 값을 붙여 넣으세요. 도메인(DNS) 방식으로 확인했다면 비워 둬도 돼요.',
    bingLabel: '빙 웹마스터 도구',
    bingHelp: '빙 웹마스터 도구 › 사이트 추가 › HTML 메타 태그에서 content 값을 붙여 넣으세요. 저장한 뒤 빙에서 확인을 누르세요.',
    problemLength: '{max}자 이내로 넣어 주세요.',
    problemChars: '영문·숫자·-·_ 만 넣을 수 있어요.',
    problemTag: '이 칸에 맞는 메타 태그가 아니에요. content 값만 붙여 넣어 주세요.',
    problemOtherTag: '{service} 태그예요. {service} 칸에 붙여 넣어 주세요.',
    previewLabel: '공개 사이트에 들어가는 태그(저장된 값)',
    previewEmpty: '넣는 태그가 없어요',
    saved: '검색 엔진 등록 정보를 저장했어요',
};
