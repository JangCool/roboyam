/**
 * 등급 안내(`/me/grade`)·등급 진행도(`components/grade/GradeProgress.vue`)·내 프로필의 등급 줄 문구. 등급 **이름**은 운영자가 정하는 값이라 서버가 준다(여기 없다).
 * 키는 `grade.무엇` 두 마디. `en/grade.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '등급 안내',
    seoDescription: '{site} 등급의 승급 조건과 내 진행도를 확인해요.',
    mineLabel: '내 등급',
    rulesHeading: '등급 조건',
    myRuleTag: '내 등급',

    // 조건 글자
    ruleInstant: '가입 즉시',
    ruleDays: '가입 {n}일',
    ruleActivity: '글·댓글 합 {n}개',
    nextHeading: '다음 등급 {label}까지',
    reqDays: '가입 {n}일',
    reqActivity: '글·댓글 {n}개',
    reqOther: '조건 {n}',
    progressDays: '{n}일째',
    progressDaysLeft: '{n}일째 ({left}일 남음)',
    progressCount: '{n}개',
    progressCountLeft: '{n}개 ({left}개 남음)',
    highestTitle: '가장 높은 등급이에요',
    highestText: '모든 승급 조건을 채웠어요.',

    // 이렇게 계산해요
    howHeading: '이렇게 계산해요',
    how1: '삭제·숨김 처리된 글·댓글은 활동 수에서 빠져요.',
    how2: '받은 추천 수는 승급에 쓰이지 않아요.',
    how3: '정지 중에는 승급이 보류돼요.',
    how4: '조건을 채우면 자동으로 올라가요. 활동이 줄어도 자동으로 내려가지는 않아요.',

    // 상태
    authLoading: '로그인 상태를 확인하는 중',
    progressLoading: '진행도를 불러오는 중',
    rulesLoading: '등급 조건을 불러오는 중',
    rulesErrorTitle: '등급 조건표를 불러오지 못했어요',
    progressErrorTitle: '진행도를 불러오지 못했어요',
    progressErrorText: '조건표는 그대로 볼 수 있어요. 잠시 후 다시 시도해 주세요.',
    guestNote: '로그인하면 내 진행도를 볼 수 있어요.',
    pausedSanctionTitle: '정지 중이라 승급이 보류돼요.',
    pausedSanctionText: '정지가 풀리면 다시 계산돼요.',
    pausedAdminTitle: '자동 승급이 멈춰 있어요.',
    pausedAdminText: '운영팀이 등급을 직접 조정해서, 지금은 활동을 채워도 등급이 자동으로 오르지 않아요.',
    sanctionLink: '제재 안내 보기',
};
