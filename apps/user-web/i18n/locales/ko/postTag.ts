/**
 * 글 태그(post-tags-contract) — 글쓰기·고치기의 태그 칸 · 글 보기의 태그 칩 · 게시판 목록 태그 거르기 · 새소식 가젯 태그 탭 문구.
 * `en/postTag.ts` 와 같은 키, 같은 차례. ⚠ 문구 안의 `@`·`|` 는 `{'@'}`·`{'|'}` 로 감싼다.
 */
export default {
    label: '태그',
    optional: '선택 · 최대 {max}개',
    placeholder: '태그를 쓰고 엔터',
    help: '엔터·쉼표·스페이스로 넣어요. 한 태그 {len}자까지, #은 빼고 저장해요.',
    listLabel: '단 태그',
    remove: '{name} 태그 지우기',
    tooMany: '태그는 {max}개까지 달 수 있어요.',
    tooLong: '태그는 {len}자까지 쓸 수 있어요.',
    invalid: '태그에는 #·쉼표·줄바꿈을 쓸 수 없어요.',
    viewLabel: '이 글의 태그',
    viewLink: '{name} 태그 글 보기',
    filterLabel: '태그 거르기',
    filterClear: '{name} 태그 거르기 풀기',
    filterEmptyTitle: '#{name} 태그 글이 아직 없어요',
    filterEmptyText: '태그 거르기를 풀면 모든 글을 볼 수 있어요.',
    filterAll: '모든 글 보기',
    tabs: '태그',
    tabEmpty: '이 태그의 글이 아직 없어요',
};
