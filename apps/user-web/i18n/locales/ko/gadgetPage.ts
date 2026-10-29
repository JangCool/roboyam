/**
 * gadgetPage 업무 문구 — 가젯 페이지 `/p/{key}`(홈처럼 가젯 격자로 꾸미는 페이지). `en/gadgetPage.ts` 와 같은 키, 같은 차례.
 * 페이지 이름·설명·가젯 제목은 운영자가 정한 값이라 서버가 준다. 가젯 안의 문구는 `home`, 글쓰기 단추는 `shell.write`.
 */
export default {
    loading: '페이지를 불러오는 중이에요',
    loadFailed: '페이지를 불러오지 못했어요',
    notFoundTitle: '페이지를 찾을 수 없어요',
    notFoundText: '주소가 바뀌었거나 없어진 페이지예요.',
    goHome: '홈으로',
};
