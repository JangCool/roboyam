/**
 * 사업자 화면 중 사용자 메뉴의 사업자 줄이 쓰는 것만 남긴 단순 버전 — 원본(community)은 사업자 센터·신청 화면 전체의 문구를 담은 큰 파일이다.
 * 글자(`business.centerEntry`·`business.applyEntry`)는 `i18n/locales/{ko,en}/business.ts` 에 있고 컴포넌트가 `t` 로 바로 읽는다 — 여기에는 글자가 아닌 값만 남는다.
 */
export default class UtilBusinessMessages {
    static readonly ENTRY_CENTER_ICON = 'pi pi-briefcase';
}
