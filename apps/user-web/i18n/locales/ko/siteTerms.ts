/**
 * 서비스 이름(용어)의 **기본 이름** — 운영자가 바꾸기 전, 또는 서버 값이 없을 때 쓴다(`utils/util-site-terms.ts`, `useSiteTerms().term(키)`).
 * 운영자가 바꾼 이름은 서버 값(한 문자열)이라 여기에 없고 언어와 무관하게 그대로 쓴다. `en/siteTerms.ts` 와 같은 키, 같은 차례.
 * ⚠ 한국어 값은 `UtilSiteTerms.sanitize` 가 "바꾸지 않은 이름"을 가려내는 기준이기도 하다.
 */
export default {
    groupBuy: '공동구매',
    groups: '소모임',
    itemShop: '아이템샵',
    popular: '인기글',
    latest: '최신글',
    adultLabel: '19금',
    adminBadge: '운영자',
    staffBadge: '운영진',
};
