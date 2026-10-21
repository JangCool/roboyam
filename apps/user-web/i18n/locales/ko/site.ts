/**
 * 사이트 이름·한 줄 소개·공개 주소 입력의 문구(설치 마법사 3단계 · 운영자 설정 화면) — `components/common/SiteFields.vue`·`components/install/InstallSiteStep.vue`·`utils/util-site-messages.ts` 가 쓴다.
 * 키는 `site.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/site.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다). `{max}` 같은 자리표시자는 값으로 채워진다.
 */
export default {
    // 칸 이름·힌트
    nameLabel: '사이트 이름',
    nameHint: '1~{max}자',
    taglineLabel: '한 줄 소개(선택)',
    taglineHint: '0~{max}자',
    publicUrlLabel: '공개 주소',

    // 안내
    taglineHelp: '로고 아래 한 줄·검색 제목 뒤에 보여요. 비워 두면 보이지 않아요(나중에 관리자 설정에서 바꿀 수 있어요).',
    publicUrlHelp: '브라우저로 이 사이트에 접속하는 주소예요. 예: https://example.com',
    publicUrlWhatTitle: '공개 주소란?',
    publicUrlWhat:
        '사용자가 브라우저로 이 사이트에 들어오는 주소예요. 가입 인증·비밀번호 재설정 메일에 들어가는 링크, 소셜 로그인 콜백 주소, 검색엔진용 정보의 기준이 돼요. 끝에 /를 붙이지 말고 https://example.com 처럼 적어 주세요.',
    publicUrlPrefilled: '지금 접속한 주소로 미리 채웠어요. 사용자가 접속할 주소와 다르면 고쳐 주세요.',
    publicUrlLocal: 'localhost 같은 주소는 이 컴퓨터에서만 열려요. 메일 링크와 소셜 로그인은 다른 사람도 열 수 있는 실제 주소여야 해요.',
    // 확인 화면에서 한 줄 소개를 비워 두었을 때 — 고른 기본 게시판 구성이 슬로건을 갖고 있으면 설치 때 서버가 그것을 넣으므로 "없음"이라고 단정하지 않는다
    taglineNone: '입력하지 않음',

    // 화면이 미리 검사한 오류
    nameRequired: '사이트 이름을 입력해 주세요.',
    nameLength: '사이트 이름은 {max}자 이하로 입력해 주세요.',
    taglineChars: '한 줄 소개에 < 와 > 를 넣을 수 없어요.',
    taglineLength: '한 줄 소개는 {max}자 이하로 입력해 주세요.',
    publicUrlRequired: '공개 주소를 입력해 주세요.',
    publicUrlScheme: '주소는 http:// 또는 https:// 로 시작해야 해요.',
    publicUrlPath: '끝의 /와 경로·쿼리는 빼고 https://example.com 처럼 입력해 주세요.',
    publicUrlFormat: '주소 형식을 확인해 주세요. 예: https://example.com',
};
