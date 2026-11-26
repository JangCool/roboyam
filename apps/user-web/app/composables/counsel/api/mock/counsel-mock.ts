// MOCK: AI 연애상담소(ai-love-counsel-contract) 화면 목업 — 서버가 없어서 이 파일이 게시판·글·댓글·상담 칸을 흉내 낸다. 이 탭 메모리에만 남는다(새로 고치면 처음 값).
// 개발 서버(`import.meta.dev`)에서만 불러온다 — 부르는 줄이 모두 `if (import.meta.dev)` 안의 동적 import 라 운영 빌드에는 이 파일이 들어가지 않는다.
// 서버가 붙으면 이 파일과 `useCounselApi.ts`·`useBoardApi.ts`·`usePostApi.ts`·`useCommentApi.ts` 의 `// MOCK:` 줄을 지운다.
//
// 켜는 법: 개발 서버에서 `/boards/love?mock=counsel` 를 한 번 열면 이 탭이 새로 고칠 때까지 켜져 있다(주소에서 `mock` 이 빠져도 — 글 링크·목록으로 오가도 그대로).
//   - 연애·결혼(`love`) 게시판 말머리 줄 끝에 「AI 상담소」(목업 말머리)가 붙고, 고르면 목업 상담 글 6개와 「이번 주 상담 순위」가 나온다.
//   - 목업 글(ID `99900000000000000xx`)은 스위치 없이도 개발 서버에서 늘 이 파일이 답한다(글 보기·댓글 목록·댓글 쓰기·추천·상담 칸·조언 고르기).
//   - 게시판 정보(이름·정책·글쓰기 판정)는 실제 서버 값에 말머리만 덧붙인다 — 로컬 DB 에 `love` 게시판이 있어야 한다.
// 문구는 서버가 줄 값(사연·답·상담사 이름)이라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardDetail, PrefixView } from '~/composables/board/api/useBoardApi.types';
import type { CommentLikeResult, CommentListResponse, CommentSort, CommentThread, CommentView, CommentWriteRequest } from '~/composables/board/api/useCommentApi.types';
import type { CommentKind } from '~/composables/board/api/useDebateApi.types';
import type { AuthorAi, CounselStatus, PostAuthor, PostDetail, PostListQuery, PostListResponse, PostPrefixRef, PostRow } from '~/composables/board/api/usePostApi.types';
import type { CounselAiAnswer, CounselRanking, CounselRankRow, PostCounselView } from '~/composables/counsel/api/useCounselApi.types';

/** 목업 말머리를 붙일 게시판(연애·결혼) */
const BOARD_SLUG = 'love';
const BOARD_NAME = '연애·결혼';
/** 목업 말머리 「AI 상담소」 — 실제 말머리와 겹치지 않는 ID */
const PREFIX_ID = '9990000000000000000';
const PREFIX: PrefixView = { id: PREFIX_ID, name: 'AI 상담소', staffOnly: false, common: false, color: null, counsel: true };
const PREFIX_REF: PostPrefixRef = { id: PREFIX.id, name: PREFIX.name, staffOnly: false, color: null, counsel: true };

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const at = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString();

/** `?mock=counsel` 을 한 번 보면 새로 고칠 때까지 켜 둔다(목업 글을 한 번 열어도 켜진다) */
let sticky = false;
function on(): boolean {
    if (mockFlags().has('counsel')) sticky = true;
    return sticky;
}

/* ── 사람·AI ── */

interface Counselor {
    id: string;
    memberId: string;
    name: string;
    style: string;
    ai: AuthorAi;
}

/** AI 상담사 셋(시안 임시 이름 — 계약 「미확정」 상담사 계정) */
const COUNSELORS: Counselor[] = [
    { id: 'c1', memberId: '9991000000000000001', name: '팩폭언니', style: '직설형', ai: { provider: 'OPENAI', model: 'GPT' } },
    { id: 'c2', memberId: '9991000000000000002', name: '토닥이', style: '공감형', ai: { provider: 'ANTHROPIC', model: 'Claude' } },
    { id: 'c3', memberId: '9991000000000000003', name: '연애분석관', style: '현실 분석형', ai: { provider: 'GOOGLE', model: 'Gemini' } },
];

function member(n: number, name: string): PostAuthor {
    return { kind: 'MEMBER', memberId: `99920000000000000${String(n).padStart(2, '0')}`, name, grade: null, gradeLabel: null, role: 'USER', ipMasked: null, avatarUrl: null };
}

function aiAuthor(c: Counselor): PostAuthor {
    return { kind: 'MEMBER', memberId: c.memberId, name: c.name, grade: null, gradeLabel: null, role: 'USER', ipMasked: null, avatarUrl: null, ai: c.ai };
}

/** 내가 쓴 댓글의 작성자(목업 — 로그인한 내 이름은 모른다) */
const ME: PostAuthor = member(99, '나');

function comment(postId: string, id: string, author: PostAuthor, content: string, agoMs: number, likeCount: number, mine = false): CommentThread {
    return {
        id,
        postId,
        parentId: null,
        replyTo: null,
        author,
        content,
        contentFormat: 'TEXT',
        state: 'VISIBLE',
        createdAt: at(-agoMs),
        updatedAt: null,
        likeCount,
        liked: false,
        mine,
        replies: [],
    };
}

/* ── 상담 글 ── */

interface MockCounselPost {
    id: string;
    title: string;
    content: string;
    author: PostAuthor;
    /** 내 사연(사연 쓴 사람으로 본다 — 「이 조언 고르기」가 보인다) */
    mine: boolean;
    createdAgo: number;
    viewCount: number;
    likeCount: number;
    risk: boolean;
    /** AI 상담사 답(상담사 차례대로, 아직 쓰는 중이면 null) */
    ai: (CommentThread | null)[];
    members: CommentThread[];
    pickedCommentId: string | null;
}

const pid = (n: number) => `99900000000000000${String(n).padStart(2, '0')}`;

function build(): MockCounselPost[] {
    const p1 = pid(1);
    const p2 = pid(2);
    const p3 = pid(3);
    const p4 = pid(4);
    const p5 = pid(5);
    const p6 = pid(6);
    return [
        {
            id: p1,
            title: '3년 사귄 남자친구가 결혼 얘기만 나오면 말을 돌려요',
            content:
                '저는 서른하나, 남자친구는 서른셋이에요. 사귄 지 3년이 넘었고 사이는 좋아요.\n그런데 결혼 이야기만 꺼내면 "아직 모아 둔 게 없어서", "내년쯤?" 하고 넘어가요. 벌써 이 말을 2년째 듣고 있어요.\n\n헤어질 생각은 없는데 계속 기다리기만 해도 될까요? 어떻게 이야기를 꺼내야 서로 상처받지 않을까요?',
            author: member(1, '봄날의산책'),
            mine: true,
            createdAgo: 2 * DAY,
            viewCount: 842,
            likeCount: 31,
            risk: false,
            ai: [
                comment(p1, `${p1}01`, aiAuthor(COUNSELORS[0]!), '2년째 "내년쯤"이면 그건 계획이 아니라 미루기예요. 돈 핑계는 시간을 버는 말일 때가 많아요.\n"언제"가 아니라 "무엇이 준비되면"을 물어보세요. 기준이 안 나오면 그 사람 마음이 아직 거기 없는 거예요.', 2 * DAY - 6 * MINUTE, 18),
                comment(p1, `${p1}02`, aiAuthor(COUNSELORS[1]!), '오래 기다리면서 혼자 마음 졸였을 것 같아요. 그 마음 자체가 충분히 자연스러워요.\n"결혼하자"보다 "나는 우리 미래가 궁금해, 너는 어떻게 그리고 있어?"처럼 그의 생각을 먼저 듣는 대화로 시작해 보면 어떨까요. 다그치지 않는 자리에서 사람은 더 솔직해지거든요.', 2 * DAY - 7 * MINUTE, 42),
                comment(p1, `${p1}03`, aiAuthor(COUNSELORS[2]!), '정리하면 ① 관계 만족도 높음 ② 결혼 시점 합의 없음 ③ 경제적 준비를 이유로 듦.\n먼저 둘이 생각하는 "결혼 준비 비용"을 숫자로 맞춰 보세요. 기준 금액과 모으는 기간이 나오면 막연한 "내년"이 날짜로 바뀝니다. 그래도 날짜가 안 나오면 그때 관계를 다시 보는 게 순서예요.', 2 * DAY - 8 * MINUTE, 25),
            ],
            members: [
                comment(p1, `${p1}11`, member(11, '현실주의자'), '저도 비슷했는데 결국 제가 먼저 기한을 말했어요. 서운해도 그게 낫더라고요.', 2 * DAY - 2 * HOUR, 9),
                comment(p1, `${p1}12`, member(12, '라떼한잔'), '남자 입장에서 돈 얘기는 진짜일 수도 있어요. 같이 통장 만들어 보세요.', 2 * DAY - 3 * HOUR, 6),
                comment(p1, `${p1}13`, member(13, '하늘색우산'), '토닥이 답 진짜 좋네요… 저도 저렇게 물어봐야겠어요.', DAY, 3),
            ],
            pickedCommentId: `${p1}02`,
        },
        {
            id: p2,
            title: '소개팅 후 애프터 연락, 제가 먼저 해도 될까요?',
            content:
                '지난 주말 소개팅을 했는데 대화도 잘 통하고 분위기도 좋았어요. 헤어질 때 "또 봬요"라고 했고요.\n그런데 사흘째 연락이 없어요. 제가 먼저 연락하면 너무 적극적으로 보일까요? 아니면 그쪽도 기다리는 중일까요?',
            author: member(2, '초록색운동화'),
            mine: true,
            createdAgo: 65 * MINUTE,
            viewCount: 126,
            likeCount: 4,
            risk: false,
            ai: [
                comment(p2, `${p2}01`, aiAuthor(COUNSELORS[0]!), '먼저 하세요. 사흘 기다린 것도 충분히 기다린 거예요.\n"그날 얘기한 그 식당 가 볼래요?"처럼 구체적인 걸 던지면 답도 쉬워요. 답이 미지근하면 그게 답이고요.', 60 * MINUTE, 7),
                comment(p2, `${p2}02`, aiAuthor(COUNSELORS[1]!), '좋은 시간이었다면 그 마음을 전하는 건 전혀 이상하지 않아요. 상대도 같은 고민을 하고 있을 수 있어요.\n부담 없게 "그날 즐거웠어요, 덕분에 주말이 좋았네요" 한 줄이면 충분해요.', 59 * MINUTE, 5),
                comment(p2, `${p2}03`, aiAuthor(COUNSELORS[2]!), '소개팅 뒤 첫 연락은 보통 1~3일 안에 오가요. 사흘째면 상대가 바쁘거나, 먼저 연락하길 기다리는 경우가 많아요.\n먼저 연락해도 손해 볼 것은 거의 없고, 답을 보면 상대 관심도를 바로 알 수 있어요.', 58 * MINUTE, 3),
            ],
            members: [
                comment(p2, `${p2}11`, member(21, '연애초보탈출'), '저는 먼저 연락해서 지금 여자친구랑 1년 됐어요 ㅎㅎ 고고', 40 * MINUTE, 4),
                comment(p2, `${p2}12`, member(22, '밤산책'), '너무 길게 말고 짧게 보내세요. 질문 하나 넣으면 답이 와요.', 20 * MINUTE, 2),
            ],
            pickedCommentId: null,
        },
        {
            id: p3,
            title: '헤어진 지 한 달, 다시 연락이 왔어요',
            content: '제가 먼저 이별을 말했어요. 한 달 지나서 "잘 지내?" 하고 연락이 왔는데 답을 해야 할지 모르겠어요. 아직 마음이 조금 남아 있긴 해요.',
            author: member(3, '새벽세시'),
            mine: true,
            createdAgo: 3 * MINUTE,
            viewCount: 12,
            likeCount: 0,
            risk: false,
            ai: [null, null, null],
            members: [],
            pickedCommentId: null,
        },
        {
            id: p4,
            title: '요즘 너무 지쳐서 다 그만두고 싶어요',
            content: '연애도 일도 다 잘 안 풀려요. 요즘은 아무것도 하기 싫고, 다 그만두고 싶다는 생각이 자꾸 들어요. 누구한테 말할 사람도 없어서 여기에 적어요.',
            author: member(4, '회색구름'),
            mine: true,
            createdAgo: 25 * MINUTE,
            viewCount: 40,
            likeCount: 2,
            risk: true,
            ai: [],
            members: [comment(p4, `${p4}11`, member(41, '따뜻한차'), '여기 적어 주셔서 다행이에요. 오늘은 무리하지 말고 쉬세요. 이야기 들어줄게요.', 15 * MINUTE, 6)],
            pickedCommentId: null,
        },
        {
            id: p5,
            title: '장거리 연애 1년차, 만나는 비용 문제로 자주 다퉈요',
            content:
                '서울-부산 장거리 연애 중이에요. 한 달에 두 번 만나는데 교통비·숙박비를 거의 제가 내요(제가 수입이 조금 더 많긴 해요).\n말을 꺼내면 쪼잔해 보일까 봐 참다가 결국 다른 일로 터져요. 어떻게 정리하면 좋을까요?',
            author: member(5, '바다가보고싶다'),
            mine: false,
            createdAgo: 5 * HOUR,
            viewCount: 310,
            likeCount: 12,
            risk: false,
            ai: [
                comment(p5, `${p5}01`, aiAuthor(COUNSELORS[0]!), '참다가 딴 데서 터지는 게 제일 손해예요. 돈 얘기는 쪼잔한 게 아니라 관계 운영비 얘기예요.\n"한 달 데이트 예산 얼마, 어떻게 나눌까"로 숫자부터 꺼내세요.', 5 * HOUR - 5 * MINUTE, 22),
                comment(p5, `${p5}02`, aiAuthor(COUNSELORS[1]!), '혼자 부담을 안고 있었던 만큼 서운함이 쌓였을 것 같아요. 그 마음을 먼저 말해도 괜찮아요.\n"돈이 아까운 게 아니라, 나 혼자 애쓰는 느낌이 들 때가 있어"처럼 감정을 중심으로 이야기해 보세요.', 5 * HOUR - 6 * MINUTE, 19),
                comment(p5, `${p5}03`, aiAuthor(COUNSELORS[2]!), '장거리 커플이 자주 쓰는 방법은 ① 공동 데이트 통장 ② 오가는 쪽이 교통비, 맞이하는 쪽이 숙박·식비 ③ 수입 비율대로 나누기예요.\n두 사람 수입 차이를 감안하면 ③이 덜 다툴 가능성이 높아요.', 5 * HOUR - 7 * MINUTE, 27),
            ],
            members: [
                comment(p5, `${p5}11`, member(51, '부산갈매기'), '저희는 데이트 통장 만들고 싸울 일이 확 줄었어요. 강추.', 4 * HOUR, 11),
                comment(p5, `${p5}12`, member(52, '퇴근길라디오'), '번갈아 가면서 오가면 비용도 자연히 나눠지더라고요.', 3 * HOUR, 5),
                comment(p5, `${p5}13`, member(53, '고양이집사'), '분석관 답이 현실적이네요. 비율로 나누는 거 좋아요.', 2 * HOUR, 2),
            ],
            pickedCommentId: null,
        },
        {
            id: p6,
            title: '결혼 준비 중 양가 예단 문제로 크게 싸웠어요',
            content: '상견례까지 마쳤는데 예단 이야기에서 양가 생각이 너무 달라요. 예비 신랑은 "부모님 뜻이니까"만 반복하고요. 둘이 먼저 정리하고 싶은데 어디서부터 이야기해야 할까요?',
            author: member(6, '오월의신부'),
            mine: false,
            createdAgo: 4 * DAY,
            viewCount: 1290,
            likeCount: 48,
            risk: false,
            ai: [
                comment(p6, `${p6}01`, aiAuthor(COUNSELORS[0]!), '"부모님 뜻"은 결정이 아니라 회피예요. 두 사람의 결혼이니 두 사람 안부터 정하고 양가에 같이 가세요.', 4 * DAY - 5 * MINUTE, 30),
                comment(p6, `${p6}02`, aiAuthor(COUNSELORS[1]!), '큰 결정을 앞두고 서로 지쳐 있을 것 같아요. 다툼보다 "우리는 같은 편"이라는 확인이 먼저예요.', 4 * DAY - 6 * MINUTE, 21),
                comment(p6, `${p6}03`, aiAuthor(COUNSELORS[2]!), '예단은 집안마다 기준이 달라서 금액보다 "생략·간소화·현금" 중 방식을 먼저 맞추는 게 빨라요.', 4 * DAY - 7 * MINUTE, 17),
            ],
            members: [
                comment(p6, `${p6}11`, member(61, '결혼3년차'), '저희는 둘이 먼저 "예단 생략, 대신 양가에 똑같이 선물"로 정하고 각자 자기 부모님을 설득했어요. 각자 자기 집을 맡는 게 핵심이에요. 상대 부모님 설득은 서로 하지 마세요.', 3 * DAY, 64),
                comment(p6, `${p6}12`, member(62, '꽃길만걷자'), '예비 신랑분이 중간에서 정리해 주셔야 해요. 그 대화부터 하세요.', 3 * DAY - HOUR, 12),
                comment(p6, `${p6}13`, member(63, '봄비'), '저도 예단 때문에 파혼 직전까지 갔어요… 힘내세요.', 2 * DAY, 4),
            ],
            pickedCommentId: `${p6}11`,
        },
    ];
}

let POSTS: MockCounselPost[] | null = null;
function posts(): MockCounselPost[] {
    POSTS ??= build();
    return POSTS;
}

function find(postId: string): MockCounselPost {
    const p = posts().find((x) => x.id === postId);
    if (!p) throw new ApiError({ status: 404, code: 'POST_NOT_FOUND', message: '글을 찾을 수 없어요.' });
    return p;
}

function statusOf(p: MockCounselPost): CounselStatus {
    if (p.risk) return 'RISK';
    if (p.pickedCommentId) return 'PICKED';
    return p.ai.some(Boolean) ? 'ANSWERED' : 'WAITING';
}

const aiCount = (p: MockCounselPost) => p.ai.filter(Boolean).length;
const memberCount = (p: MockCounselPost) => p.members.reduce((n, c) => n + 1 + c.replies.length, 0);

function row(p: MockCounselPost): PostRow {
    return {
        id: p.id,
        prefix: PREFIX_REF,
        title: p.title,
        commentCount: aiCount(p) + memberCount(p),
        hasImage: false,
        thumbnailUrl: null,
        author: p.author,
        createdAt: at(-p.createdAgo),
        viewCount: p.viewCount,
        likeCount: p.likeCount,
        pinned: false,
        adult: false,
        hiddenReason: null,
        counsel: { status: statusOf(p), aiAnswerCount: aiCount(p) },
    };
}

/* ── 순위 ── */

/** 이번 주 고른 수(목업 바탕값 — 사람 회원 15 · 팩폭언니 10 · 토닥이 7 · 연애분석관 5 → 41% · 27% · 19% · 13%) */
const basePicks: Record<string, number> = { HUMAN: 15, c1: 10, c2: 7, c3: 5 };
/** 이 탭에서 고른 것(조언 고르기) — 순위에 더한다 */
const extraPicks: Record<string, number> = {};

/** 큰 나머지 방식으로 합이 100 이 되게 반올림 */
function percents(values: number[]): number[] {
    const total = values.reduce((a, b) => a + b, 0) || 1;
    const raw = values.map((v) => (v * 100) / total);
    const out = raw.map(Math.floor);
    let left = 100 - out.reduce((a, b) => a + b, 0);
    const order = raw.map((v, i) => ({ i, rest: v - Math.floor(v) })).sort((a, b) => b.rest - a.rest);
    for (const { i } of order) {
        if (left <= 0) break;
        out[i]! += 1;
        left -= 1;
    }
    return out;
}

/** 한국 날짜 YYYY-MM-DD */
function kstDate(ms: number): string {
    return new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
}

function ranking(): CounselRanking {
    const keys = ['HUMAN', ...COUNSELORS.map((c) => c.id)];
    const picks = keys.map((k) => (basePicks[k] ?? 0) + (extraPicks[k] ?? 0));
    const pct = percents(picks);
    const rows: Omit<CounselRankRow, 'rank'>[] = keys.map((k, i) => {
        const c = COUNSELORS.find((x) => x.id === k);
        return { kind: c ? 'AI' : 'HUMAN', name: c?.name ?? null, ai: c?.ai ?? null, picks: picks[i]!, percent: pct[i]! };
    });
    rows.sort((a, b) => b.picks - a.picks);
    // 이번 주 월요일(한국 시각)
    const kstNow = new Date(Date.now() + 9 * HOUR);
    const monday = Date.now() - ((kstNow.getUTCDay() + 6) % 7) * DAY;
    return {
        weekStart: kstDate(monday),
        weekEnd: kstDate(monday + 6 * DAY),
        totalPicks: picks.reduce((a, b) => a + b, 0),
        rows: rows.map((r, i) => ({ ...r, rank: i + 1 })),
    };
}

/* ── 상담 칸 ── */

function counselView(p: MockCounselPost): PostCounselView {
    const counselors: CounselAiAnswer[] = p.risk
        ? []
        : COUNSELORS.map((c, i) => ({ counselorId: c.id, name: c.name, style: c.style, ai: c.ai, comment: p.ai[i] ?? null }));
    return {
        status: statusOf(p),
        canPick: p.mine && !p.risk && !p.pickedCommentId,
        pickedCommentId: p.pickedCommentId,
        answerDueAt: at(-p.createdAgo + 10 * MINUTE),
        counselors,
    };
}

/** 댓글 하나 찾기(AI 답 · 회원 원댓글 · 답글) */
function findComment(id: string): CommentView | null {
    for (const p of posts()) {
        for (const c of p.ai) if (c?.id === id) return c;
        for (const c of p.members) {
            if (c.id === id) return c;
            const reply = c.replies.find((r) => r.id === id);
            if (reply) return reply;
        }
    }
    return null;
}

let seq = 0;

const CounselMock = {
    /** 목업 글 ID 인가 — 이 글은 서버 대신 이 파일이 답한다 */
    ownsPost(postId: string): boolean {
        return posts().some((p) => p.id === postId);
    },

    /** 목업 댓글 ID 인가 */
    ownsComment(commentId: string): boolean {
        return findComment(commentId) !== null;
    },

    /** 목업 말머리 ID 인가 — 그 말머리 목록은 이 파일이 답한다 */
    ownsPrefix(prefixId: string | undefined): boolean {
        return prefixId === PREFIX_ID;
    },

    /** 실제 게시판 정보에 「AI 상담소」 목업 말머리를 덧붙인다 — 스위치가 켜졌고 연애·결혼 게시판일 때만. 같은 이름의 실제 말머리가 있으면 그것을 목업 것으로 바꾼다 */
    decorateBoard(board: BoardDetail): BoardDetail {
        if (!on() || board.slug !== BOARD_SLUG) return board;
        return { ...board, prefixes: [...board.prefixes.filter((p) => p.name !== PREFIX.name && p.id !== PREFIX_ID), { ...PREFIX }] };
    },

    /** 「AI 상담소」 말머리 목록 — 검색어는 제목으로, 정렬은 최신·오래된 */
    async posts(query: PostListQuery): Promise<PostListResponse> {
        await mockDelay();
        const q = query.q.trim();
        const items = posts()
            .filter((p) => !q || p.title.includes(q))
            .map(row)
            .sort((a, b) => (query.sort === 'OLDEST' ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt)));
        return mockClone({ globalPinned: [], pinned: [], items, page: 1, totalPages: 1, totalCount: items.length });
    },

    async post(postId: string): Promise<PostDetail> {
        await mockDelay();
        sticky = true;
        const p = find(postId);
        p.viewCount += 1;
        const list = posts();
        const i = list.indexOf(p);
        const neighbor = (n: MockCounselPost | undefined) => (n ? { id: n.id, title: n.title } : null);
        return mockClone({
            id: p.id,
            board: { id: '0', slug: BOARD_SLUG, name: BOARD_NAME, nameEn: null },
            prefix: PREFIX_REF,
            title: p.title,
            content: p.content,
            contentFormat: 'TEXT',
            author: p.author,
            createdAt: at(-p.createdAgo),
            updatedAt: null,
            viewCount: p.viewCount,
            likeCount: p.likeCount,
            liked: false,
            commentCount: aiCount(p) + memberCount(p),
            adult: false,
            mine: p.mine,
            protection: { active: false, editBlocked: false, deleteMode: 'ALLOW', delayMinutes: 0, threshold: 0 },
            deleteScheduledAt: null,
            prev: neighbor(list[i + 1]),
            next: neighbor(list[i - 1]),
            externalLinkNofollow: true,
        } satisfies PostDetail);
    },

    /** 회원 답변(일반 댓글) — AI 상담사 답은 상담 칸(`counsel`)이 따로 준다 */
    async comments(postId: string, sort: CommentSort, kind: CommentKind): Promise<CommentListResponse> {
        await mockDelay();
        const p = find(postId);
        const threads = kind === 'GENERAL' || kind === 'ALL' ? [...p.members] : [];
        threads.sort((a, b) => (sort === 'LATEST' ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt)));
        return mockClone({ threads, best: [], totalCount: memberCount(p), kind, reactions: { dislikeEnabled: false, likeLabel: null, dislikeLabel: null } });
    },

    async createComment(postId: string, body: CommentWriteRequest): Promise<CommentView> {
        await mockDelay();
        const p = find(postId);
        const text = body.content.trim();
        if (!text) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields: { content: '내용을 입력해 주세요.' } });
        seq += 1;
        const created = comment(postId, `${postId}9${String(seq).padStart(2, '0')}`, ME, text, 0, 0, true);
        created.contentFormat = body.contentFormat;
        const parent = body.parentId ? p.members.find((c) => c.id === body.parentId) : null;
        if (parent) {
            const replyTo = body.replyToId ? findComment(body.replyToId) : null;
            parent.replies.push({ ...created, parentId: parent.id, replyTo: replyTo ? { id: replyTo.id, name: replyTo.author.name ?? '' } : null });
        } else p.members.push(created);
        return mockClone(created);
    },

    async likeComment(commentId: string): Promise<CommentLikeResult> {
        await mockDelay();
        const c = findComment(commentId);
        if (!c) throw new ApiError({ status: 404, code: 'COMMENT_NOT_FOUND', message: '댓글을 찾을 수 없어요.' });
        if (!c.liked) {
            c.liked = true;
            c.likeCount += 1;
        }
        return { likeCount: c.likeCount, liked: c.liked, dislikeCount: null, disliked: null };
    },

    async counsel(postId: string): Promise<PostCounselView> {
        await mockDelay();
        const p = posts().find((x) => x.id === postId);
        if (!p) throw new ApiError({ status: 404, code: 'COUNSEL_NOT_FOUND', message: 'AI 상담소 글이 아니에요.' });
        return mockClone(counselView(p));
    },

    /** 조언 고르기 — 사연 쓴 사람만, 하나만(목업은 다시 고르기 없음 — 계약 「미확정」). 순위에 한 표를 더한다 */
    async pick(postId: string, commentId: string): Promise<PostCounselView> {
        await mockDelay();
        const p = find(postId);
        if (!p.mine) throw new ApiError({ status: 403, code: 'COUNSEL_PICK_FORBIDDEN', message: '사연을 쓴 사람만 고를 수 있어요.' });
        if (p.risk) throw new ApiError({ status: 409, code: 'COUNSEL_PICK_UNAVAILABLE', message: '이 사연에서는 조언을 고르지 않아요.' });
        if (p.pickedCommentId) throw new ApiError({ status: 409, code: 'COUNSEL_ALREADY_PICKED', message: '이미 조언을 골랐어요.' });
        const aiIndex = p.ai.findIndex((c) => c?.id === commentId);
        const memberPick = p.members.find((c) => c.id === commentId);
        if (aiIndex < 0 && !memberPick) throw new ApiError({ status: 404, code: 'COMMENT_NOT_FOUND', message: '댓글을 찾을 수 없어요.' });
        if (memberPick?.mine) throw new ApiError({ status: 409, code: 'COUNSEL_PICK_OWN', message: '내 답은 고를 수 없어요.' });
        p.pickedCommentId = commentId;
        const key = aiIndex >= 0 ? COUNSELORS[aiIndex]!.id : 'HUMAN';
        extraPicks[key] = (extraPicks[key] ?? 0) + 1;
        return mockClone(counselView(p));
    },

    async ranking(slug: string): Promise<CounselRanking> {
        await mockDelay();
        if (slug !== BOARD_SLUG) throw new ApiError({ status: 404, code: 'COUNSEL_NOT_FOUND', message: 'AI 상담소가 없는 게시판이에요.' });
        return ranking();
    },
};

export default CounselMock;
