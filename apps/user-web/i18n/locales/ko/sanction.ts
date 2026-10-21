/**
 * 정지 중 전역 배너 문구 — `components/sanction/RestrictionBanner.vue`·`utils/util-sanction-messages.ts` 가 쓴다.
 * 키는 `sanction.무엇` 두 마디. `en/sanction.ts` 와 같은 키, 같은 차례. 해제 예정일 `{date}` 는 표시 값(`useDisplayTime`)으로 그린 글자다.
 */
export default {
    bannerLabel: '이용 정지 안내',
    bannerLink: '자세히 보기',
    suspendedTitle: '이용이 정지된 계정이에요.',
    bannedTitle: '계정이 영구 정지됐어요.',
    readOnly: '읽기만 할 수 있어요.',
    release: '해제 예정 {date}',
};
