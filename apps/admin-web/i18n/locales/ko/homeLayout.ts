/**
 * homeLayout 업무 문구 — 홈 배치 편집기(`/settings/home-layout`). `en/homeLayout.ts` 와 같은 키, 같은 차례.
 * 테마 이름은 `site.themeName_<id>` 를 쓴다(여기 없다). 가젯 제목은 운영자가 정한 값이라 서버가 준다.
 */
export default {
    title: '홈 배치',
    subtitle: '공개 홈의 가젯 위치와 크기를 테마마다 정해요. 끌어서 옮기고 오른쪽 아래 모서리를 끌어 크기를 바꿔요.',
    mockNote: '목업이에요 — 저장은 이 관리자 화면의 메모리에만 남고 공개 사이트에는 아직 전해지지 않아요(새로고침하면 처음으로 돌아가요).',
    readOnly: '보기 권한만 있어 배치를 바꿀 수 없어요.',
    loadFailed: '홈 배치를 불러오지 못했어요.',
    saved: '홈 배치를 저장했어요.',
    saveFailed: '홈 배치를 저장하지 못했어요.',
    lastSaved: '마지막 저장 {at} · {by}',
    neverSaved: '아직 저장한 적이 없어 테마 기본 배치예요.',

    // ── 테마·칸 수 ─
    themeTabs: '배치를 고칠 테마',
    currentTheme: '지금 테마',
    columns: '격자 칸 수',
    columnsOption: '{count}칸',
    columnsHint: '칸 수를 바꾸면 위치와 폭을 2배 또는 반으로 바꿔요(반으로 줄일 때는 반올림하고 가젯마다 최소 폭을 지켜요). 화면에서의 자리는 그대로예요.',
    liveEditHint: '아래 실제 화면에서 가젯을 끌어 옮기고, 오른쪽 아래 모서리를 끌어 크기를 바꾸고, 오른쪽 위 ×로 빼요.',

    // ── 편집기 ─
    editorSection: '배치',
    editorLabel: '{theme} 홈 배치 편집기',
    keyboardHint: '가젯을 고르고 화살표로 옮겨요(위·아래는 이웃 가젯과 자리 바꾸기). Shift + 화살표는 크기, Delete 는 빼기예요.',
    empty: '놓인 가젯이 없어요. 「가젯 넣기」로 넣어 보세요.',
    itemLabel: '{title}({kind}) — {x}칸, {y}행, 폭 {w}칸, 높이 {h}행',
    announce: '{title}: {x}칸, {y}행, 폭 {w}칸, 높이 {h}행',
    size: '{w}×{h}',
    desktopOnly: 'PC만',
    mobileOnly: '모바일만',
    hiddenBoth: '숨김',
    newBadge: '새 가젯',
    notesTitle: '공개 홈에서는',
    noteHeight: '높이(h)는 최소 높이예요. 내용이 더 길면 칸이 늘어나고 같은 열 아래 가젯만 밀려요(겹치지 않아요).',
    noteResponsive: '태블릿이나 사이드 메뉴 때문에 좁을 때는 칸 수를 반으로 줄여 보여요(반 폭 이하 가젯 둘은 나란히, 나머지는 전체 폭). 모바일은 위에서 아래, 같은 줄이면 왼쪽부터 한 줄로 쌓여요.',
    noteHidden: 'PC만·모바일만 가젯은 보이지 않는 화면에서 자리를 비우지 않고 아래 가젯이 올라와요.',
    noteFixed: '행 높이 15px · 간격 16px 은 고정이에요. 가젯 내용(게시판·개수·꾸미기) 편집은 아직 없어요.',
    notePreview: '칸 안의 내용은 모양을 보이기 위한 견본이에요(실제 글이 아니에요). 칸이 낮으면 내용이 잘려 보여요 — 공개 홈에서는 내용 길이만큼 늘어나요.',

    // ── 칸 미리보기(견본) ─
    previewPeriod: {
        REALTIME: '실시간',
        YESTERDAY: '어제',
        WEEK: '이번 주',
    },
    previewMembers: '멤버 {n}명',
    previewPrice: '{amount}원',
    previewDiscount: '{n}%',
    previewSold: '{n}% 판매',

    // ── 고른 가젯 ─
    selectedSection: '고른 가젯',
    noneSelected: '가젯을 누르면 위치와 크기를 숫자로 바꿀 수 있어요.',
    fieldX: '왼쪽 칸(x)',
    fieldY: '위 행(y)',
    fieldW: '폭(w)',
    fieldH: '최소 높이(h)',
    fieldRange: '{min}~{max}',
    remove: '빼기',
    removeLabel: '{title} 빼기',
    removed: '「{title}」을(를) 뺐어요. 저장하기 전에는 되돌리기로 살릴 수 있어요.',

    // ── 넣기·초기화 ─
    add: '가젯 넣기',
    addTitle: '가젯 넣기',
    addLead: '넣을 종류를 고르세요. 맨 아래에 기본 크기로 들어가요. 내용(게시판·개수 등)은 저장할 때 종류별 기본값으로 만들어져요.',
    added: '「{title}」을(를) 맨 아래에 넣었어요.',
    resetDefault: '테마 기본 배치로',
    resetTitle: '테마 기본 배치로 바꿀까요?',
    resetMessage: '지금 편집기의 가젯과 자리가 테마 기본 배치로 바뀌어요. 저장해야 적용돼요.',
    resetConfirm: '기본 배치로',
    resetDone: '테마 기본 배치를 불러왔어요. 저장해야 적용돼요.',
    switchTitle: '저장하지 않은 배치가 있어요',
    switchMessage: '다른 테마로 가면 지금 바꾼 배치가 사라져요.',
    switchConfirm: '버리고 이동',
    leaveTitle: '저장하지 않은 배치가 있어요',
    leaveMessage: '이 화면을 나가면 바꾼 배치가 사라져요.',
    leaveConfirm: '나가기',
    stay: '머무르기',

    // ── 가젯 종류 ─
    kind: {
        HIGHLIGHT: '하이라이트',
        POST_LIST: '글 목록',
        BOARD_CARDS: '게시판 카드',
        GALLERY: '갤러리',
        GROUPS: '인기 소모임',
        GROUP_BUY: '공동구매',
        QUICK_LINKS: '바로가기',
        NOTICE_TEXT: '안내 글',
        UNKNOWN: '알 수 없는 종류',
    },
    kindDesc: {
        HIGHLIGHT: '운영자가 고른 글을 크게 보여요.',
        POST_LIST: '인기글·최신글·게시판 글을 줄로 보여요.',
        BOARD_CARDS: '게시판마다 대표 글과 제목들을 카드로 보여요.',
        GALLERY: '사진·영상 글을 칸으로 보여요.',
        GROUPS: '요즘 활발한 소모임을 보여요.',
        GROUP_BUY: '진행 중인 공동구매를 보여요.',
        QUICK_LINKS: '자주 가는 곳의 단추 줄이에요.',
        NOTICE_TEXT: '운영자가 적은 짧은 안내 글이에요.',
    },
};
