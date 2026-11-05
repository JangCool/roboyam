/**
 * 운영자 회원 계정(관리자 앱 운영자에게 연결된 회원 계정) 문구 — 계정 화면(`/settings/account`)의 읽기 전용 카드, 탈퇴 화면(`/settings/account/withdraw`)의 막힘 안내.
 * 게시판 운영 줄·처리 대화창의 「하루 한도 없음」은 `boardStaffAction`·`boardSanction` 에 있다. 「운영자」 뱃지 글자는 사이트 용어 `ADMIN_BADGE`(`useSiteTerms`).
 * 키는 `operatorAccount.무엇` 두 마디. `en/operatorAccount.ts` 와 같은 키, 같은 차례.
 */
export default {
    /* 계정 화면 카드 */
    sectionTitle: '운영자 계정 연결',
    cardTitle: '운영자 계정에 연결된 회원 계정이에요',
    cardOperator: '운영자 {name}',
    powersLabel: '이 계정으로 할 수 있는 일',
    powerRobotikon: '로보티콘 모든 팩을 쓸 수 있어요',
    powerBadge: '닉네임 옆에 「{badge}」 표시가 붙어요',
    powerBoards: '모든 게시판에서 매니저처럼 관리할 수 있어요 · 하루 한도 없음',
    unlinkNote: '연결 해제는 관리자 앱에서 해요.',

    /* 탈퇴 화면 */
    withdrawBlockedTitle: '운영자 계정에 연결된 동안은 탈퇴할 수 없어요.',
    withdrawBlockedText: '관리자에게 연결 해제를 요청해 주세요.',
};
