/**
 * 관리자 메뉴 항목의 이름 — 항목 정의는 `utils/util-admin-menu.ts` 한 곳이고, 새 기능 화면이 생기면 거기에 한 줄 + 여기에 한 줄을 더한다.
 * 키는 `menu.<항목 key>`. `en/menu.ts` 와 같은 키, 같은 차례.
 * `batchAndLogs` 는 묶음 이름이고(화면 없음), 나머지 이름은 hamonica3 시스템 설정 메뉴의 이름을 그대로 쓴다.
 */
export default {
    dashboard: '대시보드',
    system: '시스템',
    general: '일반',
    notifications: '알림',
    member: '회원',
    members: '회원 관리',
    grades: '등급 설정',
    operatorsAndPermissions: '운영자와 권한',
    operators: '운영자 관리',
    teams: '조직 구성',
    roles: '역할 관리',
    rolesMember: '회원 역할',
    rolesOperator: '운영자 역할',
    rolesGrade: '등급 역할',
    batchAndLogs: '배치와 로그',
    batches: '배치 관리',
    batchLogs: '배치 로그',
    audit: '감사 로그',
};
