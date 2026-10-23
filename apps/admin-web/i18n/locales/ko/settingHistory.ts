/**
 * 설정 변경 이력(소셜 로그인 설정 · 메일 서버 설정 화면 제목 옆 「변경 이력」 단추가 여는 서랍 `SettingHistoryDrawer` + 표 `SettingHistoryTable`)의 문구.
 * 칸 이름 `field.<칸>` 은 서버 응답의 `labelKey`(`settingHistory.field.<field>`)가 가리키는 키다 — 서버가 새 칸을 이력에 남기면 여기에 한 줄 더한다.
 * `en/settingHistory.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '설정 이력',
    open: '변경 이력',
    loadFailed: '설정 이력을 불러오지 못했어요',
    emptyTitle: '아직 바뀐 기록이 없어요',
    emptyHint: '설치하거나 이 화면에서 저장하면 여기에 남아요.',
    changedAt: '바꾼 시각',
    sourceHeader: '출처 · 작업',
    provider: '공급자',
    operator: '운영자',
    changes: '바뀐 칸',
    none: '(없음)',
    allProviders: '모든 공급자',

    // 비밀 값 표시 코드
    secretSet: '입력함',
    secretChanged: '바꿈',
    secretCleared: '지움',

    // 출처 · 작업
    sourceInstall: '설치',
    sourceAdmin: '관리자',
    actionCreate: '처음 설정',
    actionUpdate: '변경',
    actionDelete: '삭제',

    // 칸 이름 — 서버 `labelKey`
    field: {
        clientId: '클라이언트 ID',
        clientSecret: '클라이언트 시크릿',
        enabled: '켜기',
        host: '서버 주소',
        port: '포트',
        security: '보안 방식',
        username: '사용자 이름',
        password: '비밀번호',
        fromAddress: '보내는 사람 이메일',
        senderName: '보내는 이름',
        subjectPrefix: '제목 머리말',
    },
};
