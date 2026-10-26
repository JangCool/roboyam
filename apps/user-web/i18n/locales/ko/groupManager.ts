/**
 * 소모임 매니저 문구(3단계 — community `docs/api-groups.md` 5-1·「화면 (SPA) — 매니저」) — 관리 메인의 매니저 줄·잠금 줄·물러나기, 멤버 관리의 매니저 거르기·임명·맡은 일 바꾸기·해임·인원 설정,
 * 상세 머리·멤버 탭의 매니저 표시. 계산이 낀 조합은 `utils/util-group-manager-messages.ts`.
 * `en/groupManager.ts` 와 같은 키, 같은 차례. 회원 문구에 「운영진·운영자」를 쓰지 않는다(소모임 안 역할은 「매니저」). 맡길 일의 이름은 고정이다(서버 설정으로 바뀌지 않는다).
 */
export default {
    // ── 맡길 일 5개(고정 순서 — 서버 `GroupManagerPermission`) ──
    perm: {
        APPLICATIONS: '가입 신청 처리',
        KICK: '멤버 강퇴',
        RECRUITING: '모집 마감·다시 열기',
        EVENTS: '일정 관리',
        UNPUBLISH: '공개 글 되돌리기',
    },
    permHint: {
        APPLICATIONS: '신청 목록을 보고 승인·거절해요',
        KICK: '일반 멤버를 내보내요',
        RECRUITING: '모집을 닫고 다시 열어요',
        EVENTS: '소모임의 일정을 만들고 고치고 취소해요',
        UNPUBLISH: '멤버가 공개한 글을 비공개로 되돌려요',
    },
    permNote: {
        APPLICATIONS: '가입을 신청한 사람이 남긴 한마디와 예전에 강퇴된 적이 있는지를 볼 수 있어요. 믿을 수 있는 멤버에게 맡겨 주세요.',
        KICK: '일반 멤버만 내보낼 수 있고 모임장에게 알림이 가요.',
    },
    summarySeparator: ' · ',
    toolsPaused: '지금은 도구를 쓸 수 없어요',

    // ── 거르기·머리 줄 ──
    filterLabel: '멤버 보기',
    filterAll: '전체 {n}',
    filterManagers: '매니저 {count}/{limit}',
    heading: '매니저 {count}/{limit}명',
    appointed: '{y}.{mo} 임명',
    listError: '매니저 목록을 불러오지 못했어요',
    emptyTitle: '아직 매니저가 없어요',
    emptyText: '멤버를 매니저로 임명하면 가입 신청 처리 같은 일을 나눠 맡길 수 있어요.',
    appointOff: '지금은 새로 임명할 수 없어요',
    limitButton: '인원 바꾸기',

    // ── ⋯ 시트 ──
    menuAppoint: '매니저로 임명',
    menuEdit: '맡은 일 바꾸기',
    menuRemove: '매니저 해임',
    kickNotice: '강퇴 사실은 모임장에게도 알림으로 전달돼요.',

    // ── 체크 시트(임명·맡은 일 바꾸기 — 같은 시트) ──
    appointTitle: '{name}님을 매니저로 임명',
    editTitle: '{name}님이 맡은 일',
    appointDescription: '맡길 일을 골라 주세요. 체크한 일만 그 매니저가 할 수 있어요.',
    editDescription: '체크한 일만 그 매니저가 할 수 있어요. 바꾸면 그 매니저에게 알림이 가요.',
    pickOne: '하나 이상 골라 주세요',
    clearedText: '하나도 맡기지 않으려면 매니저에서 해임해 주세요',
    clearedAction: '매니저 해임',
    appointConfirm: '임명하기',
    fixedTitle: '매니저가 맡지 못하는 일',
    fixedInfo: '소모임 정보·채팅 링크 수정',
    fixedManagers: '매니저 임명·맡은 일 바꾸기·인원 설정',
    fixedLeader: '모임장 넘기기·해산',
    slots: '매니저 {count}/{limit}명 → 임명하면 {next}/{limit}명',
    slotsFull: '매니저는 최대 {limit}명이에요. 먼저 해임하거나 인원을 늘려 주세요',

    // ── 결과 ──
    appointedToast: '{name}님을 매니저로 임명했어요',
    changedToast: '{name}님이 맡은 일을 바꿨어요',
    removedToast: '{name}님을 매니저에서 해임했어요',
    limitSavedToast: '매니저 인원을 {n}명으로 바꿨어요',
    notManagerAnymore: '이미 매니저가 아니에요',
    resignedToast: '매니저에서 물러났어요',
    removeFailed: '해임하지 못했어요',
    removeTitle: '{name}님을 매니저에서 해임할까요?',
    removeMessage: '일반 멤버가 되고 맡은 일을 할 수 없어요. {name}님에게 알림이 가요.',
    removeConfirm: '해임하기',

    // ── 인원 설정 시트 ──
    limitTitle: '매니저 인원',
    limitDescription: '모임장이 정한 만큼 소모임에 매니저를 둘 수 있어요(1~{max}명).',
    limitValue: '{n}명',
    limitNow: '지금 {n}명',
    limitFloor: '지금 매니저가 {n}명이라 {n}명 아래로는 줄일 수 없어요',
    limitCeiling: '매니저는 최대 {max}명까지 둘 수 있어요',
    limitUp: '매니저 인원 늘리기',
    limitDown: '매니저 인원 줄이기',

    // ── 관리 메인 ──
    rowManagers: '매니저',
    rowManagersHint: '맡길 일과 인원을 정해요',
    rowValue: '{count}/{limit}명',
    headRole: '나는 매니저예요',
    headGranted: '맡은 일: {list}',
    lockedHint: '모임장이 맡기지 않았어요',
    notDelegatedTitle: '모임장이 맡기지 않은 일이에요',
    lockedText: '{name}는 모임장이 아직 맡기지 않았어요. 필요하면 모임장에게 요청해 주세요.',
    notDelegatedText: '{name}는 모임장이 아직 맡기지 않았어요.',
    lockedOk: '확인',
    leaderOnlyBox: '소모임 정보·채팅 링크 수정, 매니저 임명·맡은 일 변경, 해산은 모임장만 할 수 있어요.',
    roleSection: '내 역할',
    resignRow: '매니저에서 물러나기',
    resignHint: '일반 멤버가 돼요',
    backToManage: '관리로 돌아가기',
    revoked: '모임장이 이 일을 맡기지 않았어요.',
    demoted: '이제 매니저가 아니에요.',

    // ── 사퇴 시트 ──
    resignTitle: '매니저에서 물러날까요?',
    resignDescription: '일반 멤버가 되고 맡은 일을 할 수 없어요. 모임장에게 알림이 가요. 다시 매니저가 되려면 모임장이 임명해야 해요.',
    resignConfirm: '물러나기',

    // ── 쓰기가 멈춘 단추의 짧은 이유 ──
    blockedEmail: '이메일 인증을 마치면 쓸 수 있어요',
    blockedRestricted: '이용이 제한되어 있어 지금은 쓸 수 없어요',
    blockedBusiness: '사업자 계정은 쓸 수 없어요',
};
