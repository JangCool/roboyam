// MOCK: 공개 홈 가젯 API(`GET /api/home/gadgets`)의 메모리 목업 — `useHomeGadgetApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   home-fail      불러오기 실패(500)
//   home-empty     모든 가젯의 내용이 비었다(영역 유지 `keep` 이 켜진 가젯만 머리·안내가 남는다)
//   home-locked    로그인한 사람만 보는 가젯(인기 소모임)을 로그인하지 않은 방문자에게 잠긴 뼈대로 준다
//   home-unknown   화면이 모르는 종류의 가젯을 하나 섞는다(건너뛰는지 확인)
//
// ── 목업 가정(서버 후보 — 보고 「목업 가정」) ─
//   · 템플릿 = 사이트 테마. 서버는 자기 테마로 배치를 고르지만 목업은 화면의 테마(개발 미리보기 `?theme=` 포함)를 받는다.
//   · 배치는 격자 좌표(`layout: { columns, items: [{ gadgetId, x, y, w, h }] }`) — 테마별 기본 배치는 옛 고정 칸 화면과 최대한 같게 옮겼다(아래 테마 함수마다). 관리자 편집기의 저장은 여기에 오지 않는다.
//   · 테마별 기본 배치(시드)는 community 시드(`docs/api-home-gadgets.md` 3절)를 로보얌 기능으로 옮긴 것 — 핫딜·직접홍보·광고 가젯은 없다.
//   · 게시판 이름·slug·표시색은 ROBOYAM 설치 프리셋(`install-presets/roboyam.json`)과 같다. 글·소모임·공구는 지어낸 예시다.
//   · 썸네일은 바깥 주소 대신 이 파일이 만드는 그림(SVG data URI — 사진 대신 쓰는 색 블록)이다. 서버는 실제 이미지 주소를 준다.
//   · 노출 기간·보는 사람은 서버가 이미 걸렀다고 본다(응답에 그 칸이 없다).

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { PostAuthor, PostBoardRef, PostFeedRow, PostPrefixRef } from '~/composables/board/api/usePostApi.types';
import type { GroupRow } from '~/composables/group/api/useGroupApi.types';
import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import type { BoardCard, GadgetStyle, GalleryItem, HomeGadget, HomeGadgetsResponse, HomeGroupBuyItem, HomeHighlight } from '~/composables/home/api/useHomeGadgetApi.types';

/* ───────────── 게시판(설치 프리셋과 같다) ───────────── */

interface MockBoard extends PostBoardRef {
    investmentNotice: boolean;
}

const BOARDS = {
    ai: { slug: 'ai', name: 'AI·테크', nameEn: 'AI & Tech', color: 'BLUE', investmentNotice: false },
    robot: { slug: 'robot', name: '로봇', nameEn: 'Robots', color: 'PURPLE', investmentNotice: false },
    stock: { slug: 'stock', name: '산업·주식', nameEn: 'Industry & Stocks', color: 'GREEN', investmentNotice: true },
    game: { slug: 'game', name: '게임', nameEn: 'Games', color: 'AMBER', investmentNotice: false },
    sports: { slug: 'sports', name: '스포츠', nameEn: 'Sports', color: 'TEAL', investmentNotice: false },
    video: { slug: 'video', name: '영상·짤', nameEn: 'Videos & Memes', color: null, investmentNotice: false },
    free: { slug: 'free', name: '자유게시판', nameEn: 'Free board', color: null, investmentNotice: false },
    notice: { slug: 'notice', name: '공지', nameEn: 'Notice', color: null, investmentNotice: false },
} satisfies Record<string, MockBoard>;

type BoardKey = keyof typeof BOARDS;

/* ───────────── 작은 도구 ───────────── */

const MINUTE = 60_000;

function ago(minutes: number): string {
    return new Date(Date.now() - minutes * MINUTE).toISOString();
}

/**
 * 사진 대신 쓰는 색 블록 그림 — 두 색 그라디언트 + 산·해 모양. 바깥 주소를 부르지 않는다.
 * (그림 내용이라 UI 색 토큰과 무관하다 — 실제 서버는 올린 이미지 주소를 준다)
 */
function thumb(hue: number): string {
    const svg =
        `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 160 100' preserveAspectRatio='xMidYMid slice'>` +
        `<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='hsl(${hue} 68% 64%)'/><stop offset='1' stop-color='hsl(${(hue + 38) % 360} 62% 40%)'/></linearGradient></defs>` +
        `<rect width='160' height='100' fill='url(#g)'/><circle cx='120' cy='32' r='15' fill='hsl(0 0% 100% / .38)'/>` +
        `<path d='M0 100 L50 54 L82 80 L112 60 L160 100Z' fill='hsl(0 0% 100% / .26)'/></svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function author(name: string, memberId: string): PostAuthor {
    return { kind: 'MEMBER', memberId, name, grade: null, gradeLabel: null, role: 'USER', ipMasked: null, avatarUrl: null };
}

function prefix(id: string, name: string): PostPrefixRef {
    return { id, name, staffOnly: false, color: null };
}

interface RowSeed {
    id: string;
    board: BoardKey;
    title: string;
    by: [string, string];
    minutes: number;
    likes: number;
    comments: number;
    views: number;
    prefix?: [string, string];
    hue?: number;
}

function row(s: RowSeed): PostFeedRow {
    const { investmentNotice: _notice, ...board } = BOARDS[s.board];
    void _notice;
    return {
        id: s.id,
        prefix: s.prefix ? prefix(s.prefix[0], s.prefix[1]) : null,
        title: s.title,
        commentCount: s.comments,
        hasImage: s.hue !== undefined,
        thumbnailUrl: s.hue !== undefined ? thumb(s.hue) : null,
        author: author(s.by[0], s.by[1]),
        createdAt: ago(s.minutes),
        viewCount: s.views,
        likeCount: s.likes,
        pinned: false,
        adult: false,
        hiddenReason: null,
        hasPoll: false,
        blocked: null,
        group: null,
        board,
    };
}

/* ───────────── 글 묶음(ROBOYAM 운영 예시 톤) ───────────── */

const SEEDS: RowSeed[] = [
    { id: '7300000000000000101', board: 'robot', title: '휴머노이드 로봇이 공장에서 일하는 법 — 센서·모터·소프트웨어, 세 병목을 나눠 봤어요', by: ['로봇정비공', '9101'], minutes: 42, likes: 128, comments: 48, views: 3210, prefix: ['p-robot-3', '해설'], hue: 212 },
    { id: '7300000000000000102', board: 'ai', title: '작은 모델로 사진 속 글자 읽기, 내 PC 에서 돌려 본 후기', by: ['토큰절약러', '9102'], minutes: 95, likes: 96, comments: 31, views: 2480, prefix: ['p-ai-4', '팁'], hue: 168 },
    { id: '7300000000000000103', board: 'stock', title: '협동로봇 시장, 3분기 출하량이 말해 주는 것', by: ['공급망읽기', '9103'], minutes: 180, likes: 74, comments: 22, views: 1980, prefix: ['p-stock-1', '산업해설'], hue: 140 },
    { id: '7300000000000000104', board: 'video', title: '로봇 격투 결승 하이라이트, 2분으로 보기', by: ['직관러', '9104'], minutes: 260, likes: 151, comments: 64, views: 5120, prefix: ['p-video-1', '영상'], hue: 18 },
    { id: '7300000000000000105', board: 'game', title: '로봇렌즈로 읽는 이번 주 게임 업데이트', by: ['패치노트', '9105'], minutes: 33, likes: 41, comments: 15, views: 990, prefix: ['p-game-3', '로봇렌즈'], hue: 42 },
    { id: '7300000000000000106', board: 'sports', title: '드론 레이싱 입문 한 달, 장비 고르는 순서', by: ['프로펠러', '9106'], minutes: 410, likes: 38, comments: 12, views: 860, prefix: ['p-sports-4', '직관'], hue: 186 },
    { id: '7300000000000000107', board: 'robot', title: '로봇 팔 하나로 커피를 내리는 데 성공한 후기', by: ['바리스타봇', '9107'], minutes: 70, likes: 212, comments: 86, views: 6400, prefix: ['p-robot-6', '후기'], hue: 30 },
    { id: '7300000000000000108', board: 'ai', title: '오픈소스 음성 모델 비교 — 한국어 억양은 누가 낫나', by: ['음성합성', '9108'], minutes: 150, likes: 133, comments: 54, views: 4100, prefix: ['p-ai-2', '해설'] },
    { id: '7300000000000000109', board: 'robot', title: '[질문] 라즈베리 파이로 바퀴 로봇 만들 때 모터 드라이버는?', by: ['입문자', '9109'], minutes: 22, likes: 64, comments: 41, views: 1720, prefix: ['p-robot-5', '질문'] },
    { id: '7300000000000000110', board: 'robot', title: '3D 프린터로 만든 그리퍼, 설계 파일까지 공개', by: ['메이커', '9110'], minutes: 520, likes: 58, comments: 12, views: 1500, prefix: ['p-robot-4', '제품'], hue: 260 },
    { id: '7300000000000000111', board: 'stock', title: '서비스 로봇 구독 모델, 숫자로 따져 보면', by: ['재무제표', '9111'], minutes: 610, likes: 52, comments: 33, views: 1390, prefix: ['p-stock-2', '실적'] },
    { id: '7300000000000000112', board: 'ai', title: '자율주행 배송 로봇이 횡단보도에서 멈추는 이유', by: ['길찾기', '9112'], minutes: 300, likes: 47, comments: 27, views: 1650, prefix: ['p-ai-1', '뉴스'], hue: 200 },
    { id: '7300000000000000113', board: 'video', title: '직접 만든 4족 보행 로봇 걸음마 영상', by: ['네발', '9113'], minutes: 340, likes: 88, comments: 19, views: 2300, prefix: ['p-video-1', '영상'], hue: 95 },
    { id: '7300000000000000114', board: 'game', title: '게임 속 NPC 에 작은 언어 모델을 붙여 본 실험', by: ['모드제작', '9114'], minutes: 720, likes: 44, comments: 15, views: 1210, prefix: ['p-game-1', '소식'], hue: 300 },
    { id: '7300000000000000115', board: 'free', title: '올가을 로봇 박람회 같이 가실 분 있나요', by: ['박람회러', '9115'], minutes: 12, likes: 9, comments: 8, views: 240 },
    { id: '7300000000000000116', board: 'free', title: '오늘 처음 납땜해 봤는데 이거 중독이네요', by: ['인두초보', '9116'], minutes: 6, likes: 14, comments: 6, views: 180, hue: 12 },
    { id: '7300000000000000117', board: 'robot', title: '서보 모터가 떨리는 건 전원 문제일까요?', by: ['떨림방지', '9117'], minutes: 9, likes: 5, comments: 3, views: 120, prefix: ['p-robot-5', '질문'] },
    { id: '7300000000000000118', board: 'ai', title: '이번 달 공개된 오픈소스 로봇 제어 라이브러리 정리', by: ['깃허브탐험', '9118'], minutes: 18, likes: 27, comments: 11, views: 610, prefix: ['p-ai-1', '뉴스'], hue: 226 },
    { id: '7300000000000000119', board: 'sports', title: '로봇 축구 대회 예선, 오늘 저녁 8시 중계 정리', by: ['로봇축구', '9119'], minutes: 27, likes: 19, comments: 7, views: 430, prefix: ['p-sports-2', '경기'], hue: 150 },
    { id: '7300000000000000120', board: 'stock', title: '감속기 공급망, 올해 하반기 이슈 세 가지', by: ['공급망읽기', '9103'], minutes: 48, likes: 23, comments: 9, views: 720, prefix: ['p-stock-3', '공급망'] },
    { id: '7300000000000000121', board: 'game', title: '파티 구해요 — 로봇 조립 시뮬레이터 협동 모드', by: ['조립왕', '9121'], minutes: 55, likes: 4, comments: 5, views: 150, prefix: ['p-game-4', '파티모집'] },
    { id: '7300000000000000122', board: 'video', title: '책상 위 로봇 팔 조립 타임랩스', by: ['타임랩스', '9122'], minutes: 64, likes: 31, comments: 4, views: 830, prefix: ['p-video-1', '영상'], hue: 330 },
    { id: '7300000000000000123', board: 'video', title: '라인트레이서 주행 영상 — 곡선 구간 개선 전후', by: ['라인트레이서', '9123'], minutes: 130, likes: 36, comments: 6, views: 940, prefix: ['p-video-1', '영상'], hue: 52 },
    { id: '7300000000000000124', board: 'video', title: '3D 프린터 그리퍼 시연 짤', by: ['메이커', '9110'], minutes: 220, likes: 42, comments: 9, views: 1100, prefix: ['p-video-2', '짤'], hue: 276 },
    { id: '7300000000000000125', board: 'ai', title: '프롬프트 하나로 로봇 동작 계획 짜기, 어디까지 되나', by: ['계획세우기', '9125'], minutes: 88, likes: 61, comments: 25, views: 1840, prefix: ['p-ai-2', '해설'], hue: 240 },
    { id: '7300000000000000126', board: 'sports', title: '로봇 심판 도입 경기, 판정 논란 정리', by: ['비디오판독', '9126'], minutes: 200, likes: 33, comments: 29, views: 1300, prefix: ['p-sports-1', '소식'] },
    { id: '7300000000000000127', board: 'free', title: '로봇청소기 이름 뭐라고 지으셨어요?', by: ['먼지제로', '9127'], minutes: 140, likes: 21, comments: 37, views: 900 },
    { id: '7300000000000000128', board: 'game', title: '레트로 게임기를 로봇 팔로 플레이시키기', by: ['레트로', '9128'], minutes: 480, likes: 39, comments: 10, views: 1020, prefix: ['p-game-2', '공략'], hue: 8 },
];

const POSTS: PostFeedRow[] = SEEDS.map(row);

const byId = new Map(POSTS.map((p) => [p.id, p]));

function post(id: string): PostFeedRow {
    return byId.get(id)!;
}

function popular(count: number, boards?: BoardKey[]): PostFeedRow[] {
    return POSTS.filter((p) => !boards || boards.includes(p.board.slug as BoardKey))
        .sort((a, b) => b.likeCount + b.commentCount * 2 - (a.likeCount + a.commentCount * 2))
        .slice(0, count);
}

/**
 * 인기글 기간 탭 목업 — 실시간(점수순)·어제(추천순)·이번 주(댓글순)로 순서를 달리한 세 목록. `exclude` 글은 뺀다(한 화면에 같은 글 두 번 안 나오게).
 * 실제 서버는 기간별 순위 스냅샷(`GET /api/home/popular?period=`)과 같은 줄을 준다.
 */
function popularPeriods(count: number, exclude: ReadonlySet<string> = new Set()) {
    const pool = POSTS.filter((p) => !exclude.has(p.id));
    const realtime = [...pool].sort((a, b) => b.likeCount + b.commentCount * 2 - (a.likeCount + a.commentCount * 2)).slice(0, count);
    return {
        items: realtime,
        periods: {
            REALTIME: realtime,
            YESTERDAY: [...pool].sort((a, b) => b.likeCount - a.likeCount).slice(1, count + 1),
            WEEK: [...pool].sort((a, b) => b.commentCount - a.commentCount).slice(0, count),
        },
    };
}

function latest(count: number, boards?: BoardKey[]): PostFeedRow[] {
    return POSTS.filter((p) => !boards || boards.includes(p.board.slug as BoardKey))
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        .slice(0, count);
}

function mostViewed(count: number): PostFeedRow[] {
    return [...POSTS].sort((a, b) => b.viewCount - a.viewCount).slice(0, count);
}

function highlights(count: number): HomeHighlight[] {
    const picks: [string, string | null, string | null][] = [
        ['7300000000000000101', null, '작업을 따라 하는 것과 작업을 이해하는 것은 다르다. 현장에서 막히는 지점을 사례별로 정리하고, 1~2년 안에 풀릴 부분과 오래 걸릴 부분을 나눴어요.'],
        ['7300000000000000107', '로봇 팔 하나로 커피 내리기, 실패 열두 번의 기록', '컵 위치를 맞추는 데만 일주일. 그리퍼 압력과 물줄기 각도를 바꿔 가며 찾은 값을 공유해요.'],
        ['7300000000000000102', null, '양자화한 작은 모델로도 영수증·간판 글자를 꽤 읽어요. 설치부터 속도 비교까지.'],
        ['7300000000000000103', null, '출하량·평균 단가·수주 잔고 세 숫자로 보는 협동로봇 시장. 투자 권유가 아니에요.'],
        ['7300000000000000104', null, null],
        ['7300000000000000108', null, '같은 문장을 다섯 모델로 읽혀 비교했어요.'],
    ];
    return picks.slice(0, count).map(([id, title, summary], i) => ({ id: `h-${i + 1}`, post: post(id), title, summary, imageUrl: null }));
}

function gallery(count: number): GalleryItem[] {
    const kinds: (GalleryItem['kind'])[] = ['VIDEO', null, 'GIF', null, 'VIDEO', 'GIF'];
    return POSTS.filter((p) => p.thumbnailUrl !== null)
        .filter((p) => p.board.slug === 'video' || p.board.slug === 'robot')
        .slice(0, count)
        .map((p, i) => ({
            postId: p.id,
            title: p.title,
            thumbnailUrl: p.thumbnailUrl,
            kind: kinds[i % kinds.length] ?? null,
            board: { slug: p.board.slug, name: p.board.name, nameEn: p.board.nameEn },
            likeCount: p.likeCount,
            createdAt: p.createdAt,
        }));
}

function boardCards(keys: BoardKey[], perBoard: number): BoardCard[] {
    return keys.map((key) => {
        const b = BOARDS[key];
        const rows = latest(perBoard + 1, [key]);
        const leadIndex = rows.findIndex((r) => r.thumbnailUrl !== null);
        const lead = leadIndex >= 0 ? rows[leadIndex]! : (rows[0] ?? null);
        return {
            board: { slug: b.slug, name: b.name, nameEn: b.nameEn, color: b.color, investmentNotice: b.investmentNotice },
            lead,
            items: rows.filter((r) => r !== lead).slice(0, perBoard),
        };
    });
}

const GROUPS: GroupRow[] = [
    {
        id: '8100000000000000001',
        name: '라즈베리 파이 로봇 만들기',
        summary: '주말마다 부품 하나씩, 바퀴 로봇을 처음부터 같이 만들어요',
        category: { id: 'c-robot', name: '로봇' },
        board: { slug: 'robot-groups', name: '로봇 소모임', nameEn: 'Robot clubs' },
        joinType: 'OPEN',
        capacity: 200,
        memberCount: 128,
        recruiting: true,
        recruitState: 'OPEN',
        offline: true,
        stockNotice: false,
        visibility: 'PUBLIC',
        myState: null,
    },
    {
        id: '8100000000000000002',
        name: 'AI 논문 읽는 모임',
        summary: '한 주에 한 편, 요약 당번을 돌아가며 맡아요',
        category: { id: 'c-ai', name: 'AI·테크' },
        board: { slug: 'ai-groups', name: 'AI·테크 소모임', nameEn: 'AI & Tech clubs' },
        joinType: 'APPROVAL',
        capacity: 300,
        memberCount: 211,
        recruiting: true,
        recruitState: 'OPEN',
        offline: false,
        stockNotice: false,
        visibility: 'PUBLIC',
        myState: null,
    },
    {
        id: '8100000000000000003',
        name: '서울 로봇 박람회 같이 가요',
        summary: '관람 일정 맞춰서 같이 다녀와요 — 후기는 게시판에',
        category: { id: 'c-free', name: '자유' },
        board: { slug: 'robot-groups', name: '로봇 소모임', nameEn: 'Robot clubs' },
        joinType: 'OPEN',
        capacity: 60,
        memberCount: 54,
        recruiting: true,
        recruitState: 'OPEN',
        offline: true,
        stockNotice: false,
        visibility: 'PUBLIC',
        myState: null,
    },
    {
        id: '8100000000000000004',
        name: '로봇 산업 실적 스터디',
        summary: '분기 실적 발표를 같이 읽어요(종목 추천은 하지 않아요)',
        category: { id: 'c-stock', name: '산업·주식' },
        board: { slug: 'stock-groups', name: '산업·주식 소모임', nameEn: 'Industry & Stocks clubs' },
        joinType: 'APPROVAL',
        capacity: 100,
        memberCount: 100,
        recruiting: false,
        recruitState: 'FULL',
        offline: false,
        stockNotice: true,
        visibility: 'PUBLIC',
        myState: null,
    },
];

const GROUP_BUYS: HomeGroupBuyItem[] = [
    { id: '8200000000000000001', title: '휴대용 납땜 스테이션 세트', imageUrl: thumb(14), price: 39000, listPrice: 59000, endsAt: new Date(Date.now() + 2 * 86_400_000).toISOString(), soldQty: 136, totalQty: 200 },
    { id: '8200000000000000002', title: '입문용 4축 로봇 팔 키트', imageUrl: thumb(220), price: 129000, listPrice: 169000, endsAt: new Date(Date.now() + 5 * 86_400_000).toISOString(), soldQty: 41, totalQty: 120 },
    { id: '8200000000000000003', title: '서보 모터 10개 묶음', imageUrl: thumb(130), price: 45000, listPrice: null, endsAt: new Date(Date.now() + 8 * 3_600_000).toISOString(), soldQty: 77, totalQty: null },
];

const RULES = ['주장에는 출처를 붙여요', '비방·인신공격은 금지예요', '허위정보는 신고할 수 있어요', '산업·주식 글은 투자 권유가 아니에요'].join('\n');

/* ───────────── 가젯 만들기 ───────────── */

/** 테마의 기본 꾸미기 — 기본형은 카드 없는 구역(머리 장식 없음), 다음식·네이버형은 카드 + 막대 머리 */
function baseStyle(news: boolean): GadgetStyle {
    return {
        icon: null,
        headerStyle: news ? 'BAR' : 'PLAIN',
        titleSize: 'M',
        titleColor: null,
        subtitle: '',
        cardLook: news ? 'DEFAULT' : 'FLAT',
        cardColor: null,
        mobileFold: 'NONE',
        empty: { keep: true, message: true, text: '' },
    };
}

let seq = 0;

interface GadgetSeed {
    type: string;
    title: string;
    desktop?: boolean;
    mobile?: boolean;
    style?: Partial<GadgetStyle>;
    config?: Record<string, unknown>;
    more?: { label: string | null; to: string } | null;
    data: unknown;
    empty?: boolean;
}

function gadget(news: boolean, s: GadgetSeed): HomeGadget {
    seq += 1;
    return {
        id: String(9000 + seq),
        type: s.type,
        title: s.title,
        desktop: s.desktop ?? true,
        mobile: s.mobile ?? true,
        empty: s.empty ?? false,
        locked: false,
        style: { ...baseStyle(news), ...s.style },
        config: s.config ?? {},
        more: s.more ?? null,
        data: s.data,
    };
}

/* ───────────── 배치(격자 좌표) ───────────── */

/** 가젯 하나 + 자리(칸 단위) */
interface Placed {
    gadget: HomeGadget;
    x: number;
    y: number;
    w: number;
    h: number;
}

type ThemeHome = Omit<HomeGadgetsResponse, 'theme'>;

function at(gadget: HomeGadget, x: number, y: number, w: number, h: number): Placed {
    return { gadget, x, y, w, h };
}

/** 자리 목록 → 응답의 `layout` + `gadgets` */
function home(columns: HomeGridColumns, placed: Placed[]): ThemeHome {
    return {
        layout: { columns, items: placed.map((p) => ({ gadgetId: p.gadget.id, x: p.x, y: p.y, w: p.w, h: p.h })) },
        gadgets: placed.map((p) => p.gadget),
    };
}

/**
 * 기본 테마 — 기본형(community `CLASSIC`), 24칸: 본문 17칸(모바일 전용 공구 슬라이드 · 하이라이트 · 인기글 · 최신글 · 갤러리) + 오른쪽 7칸(공구 · 소모임 · 운영 원칙 — 모두 데스크톱 전용).
 * 지금 화면(본문 + 오른쪽 300px)과 같게 — 24칸 중 7칸이 본문 폭 1068px 에서 300px. (옛 칸 `MAIN_LEFT` 는 비어 있어 옮기지 않았다)
 */
function classic(): ThemeHome {
    const g = (s: GadgetSeed) => gadget(false, s);
    return home(24, [
        at(g({ type: 'GROUP_BUY', title: '공동구매', desktop: false, style: { headerStyle: 'HIDDEN', cardLook: 'DEFAULT', empty: { keep: false, message: true, text: '' } }, data: { items: GROUP_BUYS } }), 0, 0, 17, 10),
        at(g({ type: 'HIGHLIGHT', title: '하이라이트', style: { headerStyle: 'HIDDEN', empty: { keep: false, message: true, text: '' } }, config: { lead: 'HERO', thumbs: 'ALL' }, data: { picks: highlights(5) } }), 0, 10, 17, 13),
        at(g({ type: 'POST_LIST', title: '인기글', config: { sort: 'POPULAR', rowStyle: 'RANK', meta: ['BOARD', 'COMMENTS', 'TIME'], periodTabs: true }, more: { label: '인기글 더보기', to: '/popular' }, data: popularPeriods(10) }), 0, 23, 17, 15),
        at(g({ type: 'POST_LIST', title: '최신글', config: { sort: 'LATEST', rowStyle: 'HEADLINE', meta: ['BOARD', 'COMMENTS', 'TIME'] }, more: { label: '최신글 더보기', to: '/latest' }, data: { items: latest(10) } }), 0, 38, 17, 20),
        at(g({ type: 'GALLERY', title: '영상·짤', config: { count: 6 }, more: { label: null, to: '/boards/video' }, data: { items: gallery(6) } }), 0, 58, 17, 11),
        at(g({ type: 'GROUP_BUY', title: '진행 중 공동구매', mobile: false, style: { cardLook: 'DEFAULT', empty: { keep: false, message: true, text: '' } }, data: { items: GROUP_BUYS } }), 17, 0, 7, 12),
        at(g({ type: 'GROUPS', title: '인기 소모임', mobile: false, style: { cardLook: 'DEFAULT' }, config: { count: 3 }, more: { label: '소모임 더 보기', to: '/groups' }, data: { items: GROUPS.slice(0, 3) } }), 17, 12, 7, 9),
        at(g({ type: 'NOTICE_TEXT', title: '운영 원칙', mobile: false, style: { cardLook: 'DEFAULT', empty: { keep: false, message: true, text: '' } }, config: { format: 'BULLETS' }, more: { label: '공지 보기', to: '/boards/notice' }, data: { text: RULES, format: 'BULLETS' } }), 17, 21, 7, 6),
    ]);
}

/**
 * 다음식 — 카드 두 열, 12칸: 본문 8칸(하이라이트 · 지금 뜨는 글 · 갤러리 · 방금 올라온 글) + 오른쪽 4칸(공구 · 로봇 인기 · 소모임 · 운영 원칙).
 * 지금 화면(넓을 때 본문 + 오른쪽 384px)과 같게 — 12칸 중 4칸이 본문 폭 1168px 에서 약 379px.
 */
function daum(): ThemeHome {
    const g = (s: GadgetSeed) => gadget(true, s);
    return home(12, [
        at(g({ type: 'HIGHLIGHT', title: '하이라이트', style: { subtitle: '운영자가 고른 글', empty: { keep: false, message: true, text: '' } }, config: { lead: 'HERO', thumbs: 'ALL' }, data: { picks: highlights(5) } }), 0, 0, 8, 13),
        at(g({ type: 'POST_LIST', title: '지금 뜨는 글', config: { sort: 'POPULAR', rowStyle: 'RANK', columns: 2, meta: ['BOARD', 'COMMENTS', 'TIME'], periodTabs: true }, more: { label: null, to: '/popular' }, data: popularPeriods(10) }), 0, 13, 8, 10),
        at(g({ type: 'GALLERY', title: '갤러리', style: { titleColor: 'ORANGE' }, config: { count: 6 }, more: { label: '영상·짤 게시판', to: '/boards/video' }, data: { items: gallery(6) } }), 0, 23, 8, 11),
        at(g({ type: 'POST_LIST', title: '방금 올라온 글', style: { titleColor: 'TEAL', subtitle: '전체 게시판 최신순' }, config: { sort: 'LATEST', rowStyle: 'HEADLINE', meta: ['BOARD', 'PREFIX', 'COMMENTS', 'TIME'], newBadgeHours: 1 }, more: { label: '최신글 더보기', to: '/latest' }, data: { items: latest(8) } }), 0, 34, 8, 18),
        at(g({ type: 'GROUP_BUY', title: '진행 중 공동구매', style: { titleColor: 'RED', empty: { keep: false, message: true, text: '' } }, data: { items: GROUP_BUYS } }), 8, 0, 4, 12),
        at(g({ type: 'POST_LIST', title: '로봇 인기', style: { titleColor: 'PURPLE' }, config: { sort: 'POPULAR', rowStyle: 'RANK', meta: ['COMMENTS', 'TIME'], boardIds: ['robot'] }, more: { label: '로봇 게시판', to: '/boards/robot' }, data: { items: popular(5, ['robot']) } }), 8, 12, 4, 9),
        at(g({ type: 'GROUPS', title: '인기 소모임', desktop: true, mobile: false, style: { titleColor: 'PURPLE' }, config: { count: 3 }, more: { label: '소모임 전체', to: '/groups' }, data: { items: GROUPS.slice(0, 3) } }), 8, 21, 4, 9),
        at(g({ type: 'NOTICE_TEXT', title: '운영 원칙', mobile: false, style: { headerStyle: 'PLAIN', empty: { keep: false, message: true, text: '' } }, config: { format: 'BULLETS' }, more: { label: '공지 보기', to: '/boards/notice' }, data: { text: RULES, format: 'BULLETS' } }), 8, 30, 4, 6),
    ]);
}

/**
 * 로보얌형(사용자 결정 2026-10-28 — 간결하게), 24칸 — 본문 넓은 열 17칸(하이라이트 큰 글 1 + 작은 글 3 · 지금 뜨는 글 순위 6 두 열 · [모바일 전용 공구] · 영상·짤 한 줄 4칸 · 방금 올라온 글 두 열 8줄) + 오른쪽 열 7칸(공구 · 인기 소모임).
 * 지금 화면(본문 + 오른쪽 300px)과 같게 — 24칸 중 7칸이 본문 폭 1168px 에서 약 329px(1068px 에서 300px).
 * 머리는 막대 대신 아이콘 + 제목(`PLAIN` — 아이콘 칩 색은 주 색상 하나), 게시판 이름은 회색(`NEUTRAL`), 구분선·NEW 표시는 끈다. 운영 원칙·게시판별 인기는 두지 않는다.
 * 모바일 한 열은 (y, x) 순서: 하이라이트 → 지금 뜨는 글 → 인기 소모임 → 공구(본문의 모바일 전용 가젯) → 영상·짤 → 방금 올라온 글(오른쪽 공구는 데스크톱 전용).
 *   ⚠ 고정 칸일 때는 소모임이 맨 끝이었지만, 격자는 위로 당겨진 모양이라 오른쪽 열의 소모임(12행)이 본문 공구(20행)보다 앞선다.
 * 하이라이트·영상·짤에 나온 글은 「지금 뜨는 글」에서 뺀다(한 화면에 같은 글이 겹치지 않게).
 */
function roboyam(): ThemeHome {
    const g = (s: GadgetSeed) => gadget(false, s);
    const plain = (icon: string, extra: Partial<GadgetStyle> = {}): Partial<GadgetStyle> => ({ headerStyle: 'PLAIN', cardLook: 'DEFAULT', icon, ...extra });
    const picks = highlights(4);
    const media: [string, GalleryItem['kind']][] = [
        ['7300000000000000104', 'VIDEO'],
        ['7300000000000000124', 'GIF'],
        ['7300000000000000113', 'VIDEO'],
        ['7300000000000000122', 'VIDEO'],
    ];
    const clips: GalleryItem[] = media.map(([id, kind]) => {
        const p = post(id);
        return { postId: p.id, title: p.title, thumbnailUrl: p.thumbnailUrl, kind, board: { slug: p.board.slug, name: p.board.name, nameEn: p.board.nameEn }, likeCount: p.likeCount, createdAt: p.createdAt };
    });
    const shown = new Set([...picks.map((h) => h.post.id), ...clips.map((c) => c.postId)]);
    const trending = popularPeriods(6, shown);
    const neutral = { mode: 'NEUTRAL', color: null };
    const groupBuy = (desktop: boolean): GadgetSeed => ({
        type: 'GROUP_BUY',
        title: '진행 중 공동구매',
        desktop,
        mobile: !desktop,
        style: plain('pi-shopping-bag', { empty: { keep: false, message: true, text: '' } }),
        data: { items: GROUP_BUYS },
    });
    return home(24, [
        at(g({ type: 'HIGHLIGHT', title: '하이라이트', style: plain('pi-sparkles', { empty: { keep: false, message: true, text: '' } }), config: { lead: 'HERO', thumbs: 'ALL' }, data: { picks } }), 0, 0, 17, 12),
        at(g({ type: 'POST_LIST', title: '인기글', style: plain('pi-chart-line'), config: { sort: 'POPULAR', rowStyle: 'RANK', columns: 2, meta: ['BOARD', 'COMMENTS', 'TIME'], badge: neutral, dividers: false, periodTabs: true }, more: { label: null, to: '/popular' }, data: trending }), 0, 12, 17, 8),
        at(g(groupBuy(false)), 0, 20, 17, 10),
        at(g({ type: 'GALLERY', title: '영상·짤', style: plain('pi-images'), config: { count: 4 }, more: { label: null, to: '/boards/video' }, data: { items: clips } }), 0, 30, 17, 7),
        at(g({ type: 'POST_LIST', title: '방금 올라온 글', style: plain('pi-clock'), config: { sort: 'LATEST', rowStyle: 'HEADLINE', thumbnail: 'NONE', titleLines: 1, columns: 2, meta: ['BOARD', 'COMMENTS', 'TIME'], badge: neutral, dividers: false }, more: { label: null, to: '/latest' }, data: { items: latest(8) } }), 0, 37, 17, 8),
        at(g(groupBuy(true)), 17, 0, 7, 12),
        at(g({ type: 'GROUPS', title: '인기 소모임', style: plain('pi-users'), config: { count: 3 }, more: { label: null, to: '/groups' }, data: { items: GROUPS.slice(0, 3) } }), 17, 12, 7, 9),
    ]);
}

/**
 * 네이버형, 24칸 — 본문 17칸(하이라이트 행 카드 → 게시판 카드 격자 · 갤러리) + 오른쪽 7칸(많이 본 글 · 공구 · 소모임). 지금 화면(본문 + 오른쪽 296px)과 같게.
 * 바로가기 줄(`QUICK_LINKS`)은 기본 배치에 두지 않는다(사용자 결정 2026-10-28 — 하이라이트를 거르는 탭처럼 보여서). 종류는 남아 운영자가 넣을 수 있다.
 */
function naver(): ThemeHome {
    const g = (s: GadgetSeed) => gadget(true, s);
    return home(24, [
        at(g({ type: 'HIGHLIGHT', title: '하이라이트', style: { empty: { keep: false, message: true, text: '' } }, config: { lead: 'NONE', thumbs: 'ALL' }, data: { picks: highlights(4) } }), 0, 0, 17, 8),
        at(g({ type: 'BOARD_CARDS', title: '게시판', style: { headerStyle: 'HIDDEN', cardLook: 'FLAT' }, config: { columns: 'AUTO' }, data: { cards: boardCards(['ai', 'robot', 'stock', 'game', 'sports', 'free'], 4) } }), 0, 8, 17, 30),
        at(g({ type: 'GALLERY', title: '갤러리', style: { titleColor: 'ORANGE' }, config: { count: 6 }, more: { label: '영상·짤 게시판', to: '/boards/video' }, data: { items: gallery(6) } }), 0, 38, 17, 11),
        at(g({ type: 'POST_LIST', title: '많이 본 글', config: { sort: 'VIEWS', rowStyle: 'RANK', meta: ['BOARD', 'TIME'] }, data: { items: mostViewed(5) } }), 17, 0, 7, 9),
        at(g({ type: 'GROUP_BUY', title: '진행 중 공동구매', style: { empty: { keep: false, message: true, text: '' } }, data: { items: GROUP_BUYS } }), 17, 9, 7, 12),
        at(g({ type: 'GROUPS', title: '인기 소모임', mobile: false, config: { count: 3 }, more: { label: '소모임 전체', to: '/groups' }, data: { items: GROUPS.slice(0, 3) } }), 17, 21, 7, 9),
    ]);
}

/** 모든 가젯의 내용을 비운다(`?mock=home-empty`) */
function emptied(gadgets: HomeGadget[]): HomeGadget[] {
    const blank: Record<string, unknown> = {
        POST_LIST: { items: [] },
        BOARD_CARDS: { cards: [] },
        GALLERY: { items: [] },
        HIGHLIGHT: { picks: [] },
        GROUPS: { items: [] },
        GROUP_BUY: { items: [] },
        QUICK_LINKS: { items: [] },
        NOTICE_TEXT: { text: '', format: 'PLAIN' },
    };
    return gadgets.map((g) => ({ ...g, empty: true, data: blank[g.type] ?? null }));
}

/**
 * `GET /api/home/gadgets` 목업 — 테마로 배치를 고른다(서버는 자기 사이트 테마를 안다).
 * 관리자 홈 배치 편집기의 저장은 관리자 앱 메모리에만 있어 여기에 전해지지 않는다 — 공개 홈은 늘 테마 기본 배치다(서버가 붙으면 저장한 배치).
 * @param {string} theme - 지금 테마 id(`default`·`daum`·`roboyam`·`naver`). 모르는 값이면 기본형.
 * @returns {Promise<HomeGadgetsResponse>} 배치 + 가젯 목록.
 */
export async function mockHomeGadgets(theme: string): Promise<HomeGadgetsResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('home-fail')) throw MOCK_ERRORS.internal();
    seq = 0;
    const { layout, gadgets: seeded } = theme === 'daum' ? daum() : theme === 'roboyam' ? roboyam() : theme === 'naver' ? naver() : classic();
    let gadgets = seeded;
    if (flags.has('home-empty')) gadgets = emptied(gadgets);
    if (flags.has('home-locked')) {
        gadgets = gadgets.map((g) => (g.type === 'GROUPS' ? { ...g, locked: true, config: {}, data: null, more: null, empty: false } : g));
    }
    if (flags.has('home-unknown')) {
        // 배치에 자리가 없는 모르는 종류 — 화면은 맨 아래에 자리를 붙이되 그리지 않는다
        gadgets = [gadget(theme !== 'default', { type: 'DEAL_PLACEMENT', title: '모르는 종류', data: { cards: [] } }), ...gadgets];
    }
    return mockClone({ theme: theme === 'daum' || theme === 'roboyam' || theme === 'naver' ? theme : 'default', layout, gadgets });
}
