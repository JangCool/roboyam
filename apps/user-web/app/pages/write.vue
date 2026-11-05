<template>
    <!--
        글쓰기 `/write?board=slug` — 게시판 고르기(쓸 수 있는 게시판만, `?board` 로 미리 선택) → 말머리·제목·본문·이미지 자리·19금 → 비회원이면 이름·비밀번호·자동입력 방지 → 등록 후 글 보기로.
        community 대비: 임시저장·글 유형(사진·영상·AI)·투표·핫딜/리뷰 칸·소모임 글·예약 발행을 뺐다. 본문은 게시판 글 에디터 설정(평문 Textarea / 간단·고급 서식 에디터)을 따르고,
          에디터 설정이 다른 게시판으로 바꾸면 쓰던 본문을 그 형식으로 바꿔 들고 간다(평문 ↔ HTML — HTML → 평문은 서식·이미지가 빠진다).
          비회원 글쓰기(`writeMin = GUEST` 게시판)를 더했다.
        찬반 토론(post-debate-contract): 게시판 `debateEnabled` 이고 회원이면 「찬반 토론」 칸(열기 + 중립도 받기 · 입장 바꾸기 허용, 기본 켬). 켜면 `POST /api/posts` 본문 `debate` 로 글과 함께 연다.
        투표(post-poll-contract): 게시판 `pollEnabled` 이고 회원이면 「투표」 칸(`PollSettingsFields` — 찬반 토론 칸 위). 켜면 화면 검사(빈 선택지 제외 2개 이상·중복·길이·마감 범위) 뒤 본문 `poll` 로 글과 함께 붙인다.
          서버 `fields."poll.*"`·`fields.poll` 은 투표 칸 안에 보인다.
        핫딜 게시판(`kind DEAL` — `hotdeal-contract.md`): 제목 위에 딜 칸(`HotdealDealFields` — 링크 → 판정·OG 자동 채움 → 같은 딜 안내 → 가격·배송·마감·재고·제휴·메모).
          링크는 디바운스 뒤 `link-check`(`useDealLinkCheck`). [등록]을 누르면 글 칸·딜 칸 검사 문구를 함께 보이고, 서버 `fields."deal.*"` 도 다른 칸 오류와 함께 칸 아래에 붙인다.
          좁은 화면(md 미만)은 `Stepper` 단계 입력(딜 정보 → 설명) — [다음]은 딜 칸 검사를 통과해야 넘어가고, 2단계에서 딜 칸 오류가 생기면 「딜 정보에 확인할 칸이 N개」 + [딜 정보로].
          제목 칸 아래 핫딜 제목 안내(`HotdealTitleHint`). `?from={postId}`(끝난 딜에서 새 딜)면 `prefill` 로 링크·몰·이미지·제목을 채운다(가격·마감은 비움).
          하루 글 한도·같은 쇼핑몰 간격(`429 DEAL_DAILY_POST_LIMIT`·`DEAL_SAME_STORE_TOO_SOON`)은 폼 아래 안내, 저장 응답 `warnings[]` 는 토스트.
        게시판 제재(board-sanction-contract): 고른 게시판에서 쓰기 제한 중이면 폼 대신 「…까지 글·댓글 쓰기가 제한돼요 · 사유 · [자세히]」. 등록 때 `403 BOARD_WRITE_RESTRICTED` 면 폼 위에 같은 안내(쓰던 글은 그대로).
        가정: 쓸 수 있는 게시판 목록은 서버가 판정해 준다(`GET /api/boards/writable` 후보). `?board` 가 쓸 수 없는 게시판이면 그 까닭을 보인다.
          금지어·작성 간격·하루 한도·같은 글 반복은 서버가 거절하고 그 문구를 폼 아래에 그대로 보인다. 저장하지 않고 나가면 확인을 묻는다.
    -->
    <div class="content-card">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface bg-card px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-times" variant="text" severity="secondary" :aria-label="t('common.cancel')" @click="cancel" />
            <span class="text-sm font-semibold">{{ t('post.writeTitle') }}</span>
        </div>
        <h1 class="mb-4 text-lg font-bold max-md:hidden">{{ t('post.writeTitle') }}</h1>

        <div v-if="boardsState === 'loading'" role="status" :aria-label="t('common.loading')" class="flex flex-col gap-3">
            <div v-for="n in 4" :key="n" class="h-9 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="boardsState === 'error'" :title="t('post.boardsFailed')" :description="boardsError" retry @retry="loadBoards" />

        <!-- 쓸 수 있는 게시판이 하나도 없다 — 비회원이면 로그인 안내 -->
        <EmptyState v-else-if="options.length === 0 && !requestedBlocked" :title="t('post.noWritableTitle')" :description="loggedIn ? t('post.noWritableText') : t('post.noWritableGuest')" icon="pi pi-lock">
            <Button v-if="!loggedIn" type="button" :label="t('common.login')" @click="prompt('write')" />
        </EmptyState>

        <template v-else>
            <!-- 이 게시판에서 쓰기 제한 중(게시판 제재) — 게시판 응답(`viewer.writeRestriction`) 또는 등록 때 받은 `403 BOARD_WRITE_RESTRICTED`. 쓰던 글은 그대로 둔다 -->
            <BoardWriteRestrictionNotice v-if="writeRestriction" :restriction="writeRestriction" class="mb-4" />
            <!-- 주소의 게시판에 쓸 수 없다 — 까닭(로그인 필요·등급 미달·잠금·운영진 전용) -->
            <Message v-else-if="requestedBlocked" severity="warn" :closable="false" class="mb-4" data-testid="write-blocked">
                {{ requestedBlocked }}
                <Button v-if="requestedReason === 'LOGIN_REQUIRED'" type="button" :label="t('common.login')" size="small" class="ml-2" @click="prompt('write')" />
            </Message>

            <!-- 핫딜 단계 입력(모바일) — 단계 머리만(칸은 폼이 단계마다 보이고 숨긴다) -->
            <Stepper v-if="formStep !== 0 && board && board.viewer.canWrite" :value="formStep" linear class="mb-3" :aria-label="t('hotdeal.stepsLabel')" data-testid="hotdeal-stepper" @update:value="onStepPick">
                <StepList>
                    <Step :value="1">{{ t('hotdeal.stepDeal') }}</Step>
                    <Step :value="2">{{ t('hotdeal.stepBody') }}</Step>
                </StepList>
            </Stepper>

            <div v-if="boardState === 'loading'" role="status" :aria-label="t('common.loading')" class="h-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <ErrorState v-else-if="boardState === 'error'" :title="t('post.boardFailed')" :description="boardError" retry @retry="loadBoard" />
            <PostEditorForm
                v-else-if="board && board.viewer.canWrite"
                v-model="form"
                v-model:board-slug="selectedSlug"
                :board="board"
                :board-options="options"
                :guest-mode="board.viewer.guest"
                :guest-rules="board.viewer.guest"
                :field-errors="fieldErrors"
                :form-error="formError"
                :submitting="submitting"
                :submit-label="t('post.submitCreate')"
                :upload="uploadImage"
                :step="formStep"
                @attempt="dealTried = true"
                @next="onStepNext"
                @prev="step = 1"
                @submit="submit"
                @cancel="cancel"
            >
                <!-- 핫딜 딜 칸 — 제목 위(단계 입력의 1단계). 끝난 딜에서 왔으면(`?from=`) 위에 안내 -->
                <template v-if="isDeal" #lead>
                    <Message v-if="prefillState === 'done'" severity="info" :closable="false" size="small" data-testid="hotdeal-prefill">{{ t('hotdeal.prefillNotice') }}</Message>
                    <p v-else-if="prefillState === 'loading'" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-spin pi-spinner text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.prefillLoading') }}</p>
                    <HotdealDealFields
                        v-model="dealForm"
                        :check="linkCheck.state.value"
                        :errors="dealErrors"
                        :actions="dealActions"
                        :rules="dealRules"
                        :legend="formStep === 0"
                        :disabled="submitting"
                        :affiliate-mode="board?.dealAffiliateMode ?? 'MEMBER'"
                        @use-title="onUseTitle"
                    />
                </template>
                <template v-if="formStep === 2 && dealErrorCount > 0" #stepNotice>
                    <Message severity="error" :closable="false" size="small" data-testid="hotdeal-step-errors">
                        {{ t('hotdeal.stepErrors', { n: dealErrorCount }) }}
                        <Button type="button" :label="t('hotdeal.stepGoDeal')" size="small" variant="link" class="ml-1 p-0" @click="step = 1" />
                    </Message>
                </template>
                <!-- 핫딜 제목 안내 — 「상품명만」 + 앞 [몰]·끝 (…원) 지우기 -->
                <template v-if="isDeal" #titleHint>
                    <HotdealTitleHint :title="form.title" @strip="onStripTitle" />
                </template>
                <!-- 찬반 토론 — 게시판이 글쓴이 토론을 받고(`debateEnabled`) 회원일 때만. 켜면 글과 함께 연다 -->
                <template #extra>
                    <PollSettingsFields v-if="pollShown" v-model="poll" :disabled="submitting" :errors="pollErrors" />
                    <DebateSettingsFields v-if="debateShown" v-model="debate" :disabled="submitting" :error="fieldErrors.debate ?? ''" />
                </template>
            </PostEditorForm>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { BoardDetail, BoardView, WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';
import type { BoardWriteRestriction } from '~/composables/board/api/useBoardStaffApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import { DEFAULT_DEBATE_SETTINGS, type DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';
import type { PollFormValues } from '~/components/board/PollSettingsFields.types';
import { EMPTY_POST_FORM, type PostBoardOption, type PostFormValues } from '~/components/board/PostEditorForm.types';
import type { DealFieldActions } from '~/components/hotdeal/HotdealDealFields.types';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilDebate from '~/utils/util-debate';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilPoll from '~/utils/util-poll';
import UtilPollMessages from '~/utils/util-poll-messages';
import UtilPost from '~/utils/util-post';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const boardApi = useBoardApi();
const postApi = usePostApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const { localizedName } = useLocalizedName();
const { user } = useAuth();
const current = useCurrentBoard();
const imageApi = useImageApi();
/** 에디터 이미지 올리기 — 서버 호출은 API 컴포저블 한 겹(폼·에디터는 이 함수만 받는다) */
const uploadImage: RichImageUploader = (file, options) => imageApi.upload(file, options);

const requestedSlug = computed(() => (typeof route.query.board === 'string' ? route.query.board : ''));

/* ── 쓸 수 있는 게시판 ── */
const writable = ref<BoardView[]>([]);
const boardsState = ref<'loading' | 'ready' | 'error'>('loading');
const boardsError = ref('');
const options = computed<PostBoardOption[]>(() => writable.value.map((b) => ({ slug: b.slug, label: localizedName(b.name, b.nameEn) })));
const selectedSlug = ref('');

/** 주소의 게시판에 쓸 수 없을 때의 까닭 — 그 게시판 정보로 판정한다 */
const requestedReason = ref<WriteBlockedReason | null>(null);
const requestedGrade = ref<string | null>(null);
/** 주소의 게시판이 핫딜 게시판이었다 — 막힌 까닭을 핫딜 문구로 */
const requestedDeal = ref(false);
const requestedBlocked = computed(() =>
    requestedReason.value
        ? requestedDeal.value
            ? UtilHotdealMessages.writeBlocked(t, requestedReason.value, requestedGrade.value)
            : UtilBoardMessages.writeBlocked(t, requestedReason.value, requestedGrade.value)
        : '',
);
const loggedIn = computed(() => Boolean(user.value) && board.value?.viewer.guest !== true);

/* ── 게시판 제재(board-sanction-contract) — 이 게시판에서 쓰기 제한 중 ── */
/** 주소의 게시판(쓸 수 있는 목록에 없을 때)의 쓰기 제한 */
const requestedRestriction = ref<BoardWriteRestriction | null>(null);
/** 등록하다 `403 BOARD_WRITE_RESTRICTED` 로 처음 알게 된 쓰기 제한 — 고른 게시판을 바꾸면 지운다 */
const errorRestriction = ref<{ slug: string; restriction: BoardWriteRestriction } | null>(null);
/** 보일 쓰기 제한 — 고른 게시판의 것 → 등록 때 받은 것(그 게시판일 때만) → 주소의 게시판의 것(막힌 까닭 안내와 같은 자리) */
const writeRestriction = computed<BoardWriteRestriction | null>(() => {
    if (board.value?.viewer.writeRestriction) return board.value.viewer.writeRestriction;
    if (errorRestriction.value && errorRestriction.value.slug === selectedSlug.value) return errorRestriction.value.restriction;
    return requestedRestriction.value;
});

async function loadBoards(): Promise<void> {
    boardsState.value = 'loading';
    requestedReason.value = null;
    requestedRestriction.value = null;
    try {
        writable.value = await boardApi.writable();
        boardsState.value = 'ready';
        const wanted = requestedSlug.value;
        if (wanted && writable.value.some((b) => b.slug === wanted)) {
            selectedSlug.value = wanted;
        } else {
            if (wanted) await explainBlocked(wanted);
            selectedSlug.value = writable.value[0]?.slug ?? '';
        }
    } catch (e) {
        boardsError.value = UtilBoardMessages.of(t, e);
        boardsState.value = 'error';
    }
}

async function explainBlocked(slug: string): Promise<void> {
    try {
        const detail = await boardApi.board(slug);
        requestedReason.value = detail.viewer.writeBlockedReason;
        requestedRestriction.value = detail.viewer.writeRestriction ?? null;
        requestedGrade.value = detail.writeMinGradeLabel;
        requestedDeal.value = UtilHotdeal.isDealBoard(detail);
    } catch {
        // 없는 게시판이면 안내 없이 다른 게시판을 고르게 한다
    }
}

/* ── 고른 게시판 ── */
const board = ref<BoardDetail | null>(null);
const boardState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const boardError = ref('');

let boardRequest = 0;
async function loadBoard(): Promise<void> {
    const slug = selectedSlug.value;
    if (!slug) {
        board.value = null;
        boardState.value = 'idle';
        return;
    }
    const mine = ++boardRequest;
    boardState.value = 'loading';
    try {
        const detail = await boardApi.board(slug);
        if (mine !== boardRequest) return;
        board.value = detail;
        boardState.value = 'ready';
        current.set(slug);
        // 다른 게시판의 말머리·19금 표시는 들고 가지 않는다
        if (form.value.prefixId && !detail.prefixes.some((p) => p.id === form.value.prefixId)) form.value = { ...form.value, prefixId: null };
        if (!detail.allowAdult && form.value.adult) form.value = { ...form.value, adult: false };
        // 본문 형식은 게시판 글 에디터 설정을 따른다 — 다르면 쓰던 본문을 바꿔 들고 간다
        const format = UtilBoard.postFormat(detail);
        if (form.value.contentFormat !== format) form.value = { ...form.value, content: UtilPost.convertContent(form.value.content, form.value.contentFormat, format), contentFormat: format };
    } catch (e) {
        if (mine !== boardRequest) return;
        boardError.value = UtilBoardMessages.of(t, e);
        boardState.value = 'error';
    }
}

watch(selectedSlug, () => void loadBoard());
void loadBoards();
onBeforeUnmount(() => current.clear(selectedSlug.value));

/* ── 폼 ── */
const form = ref<PostFormValues>({ ...EMPTY_POST_FORM });
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const submitting = ref(false);
/** 등록을 마쳤다 — 나가기 확인을 묻지 않는다 */
let saved = false;

/** 찬반 토론 칸 — 게시판을 바꿔도 값은 들고 가고, 받지 않는 게시판이면 칸을 감추고 보내지 않는다 */
const debate = ref<DebateSettingsValues>({ ...DEFAULT_DEBATE_SETTINGS });
const debateShown = computed(() => Boolean(board.value?.debateEnabled) && board.value?.viewer.guest === false);

/** 투표 칸 — 게시판을 바꿔도 값은 들고 가고, 받지 않는 게시판(`pollEnabled` 꺼짐)·비회원이면 칸을 감추고 보내지 않는다 */
const poll = ref<PollFormValues>(UtilPoll.emptyForm());
const pollShown = computed(() => Boolean(board.value?.pollEnabled) && board.value?.viewer.guest === false);
/** 한 번 [등록]을 누른 뒤부터 투표 칸의 화면 검사 문구를 보인다 */
const pollTried = ref(false);
const pollProblems = computed(() => UtilPoll.problems(poll.value, Date.now()));
const pollErrors = computed(() => UtilPollMessages.fieldErrors(t, fieldErrors.value, pollTried.value ? pollProblems.value : null));
// 투표 칸을 고치면 그 칸의 낡은 서버 오류를 지운다
watch(poll, () => {
    fieldErrors.value = UtilPoll.withoutPollFields(fieldErrors.value);
});

/* ── 핫딜 딜 칸 ── */
const hotdealApi = useHotdealApi();
const isDeal = computed(() => UtilHotdeal.isDealBoard(board.value));
const dealForm = ref(UtilHotdeal.emptyForm());
const linkCheck = useDealLinkCheck(dealForm, selectedSlug);
/** 한 번 [등록]·[다음]을 누른 뒤부터 딜 칸의 화면 검사 문구를 보인다 */
const dealTried = ref(false);
/** 핫딜 설정 공개 칸(게시판 응답 `dealRules`, 없으면 기본값) — 금액 상한·할인율 경고·기간 종료 일수 */
const dealRules = computed(() => UtilHotdeal.rulesOf(board.value));
const dealProblems = computed(() => UtilHotdeal.problems(dealForm.value, linkCheck.state.value.result, Date.now(), dealRules.value));
const dealErrors = computed(() => UtilHotdealMessages.fieldErrors(t, fieldErrors.value, dealTried.value ? dealProblems.value : null, dealRules.value));
const dealErrorCount = computed(() => UtilHotdealMessages.errorCount(dealErrors.value));
const dealActions: DealFieldActions = {
    upload: async (file) => {
        const uploaded = await hotdealApi.uploadImage(file);
        return { url: uploaded.url, source: 'UPLOAD', imageId: uploaded.id };
    },
    retryCheck: () => linkCheck.retry(),
};
// 딜 칸을 고치면 그 칸의 낡은 서버 오류를 지운다
watch(dealForm, () => {
    if (UtilHotdeal.hasDealFields(fieldErrors.value)) fieldErrors.value = UtilHotdeal.withoutDealFields(fieldErrors.value);
});
/** 「제목으로 쓰기」 — 링크의 상품 이름을 제목 칸에 */
function onUseTitle(title: string): void {
    form.value = { ...form.value, title: title.slice(0, UtilPost.TITLE_MAX) };
}
/** 제목 안내의 [지우기] — 앞 [몰]·끝 (…원) 을 뗀 제목 */
function onStripTitle(title: string): void {
    form.value = { ...form.value, title };
}

/**
 * 끝난 딜에서 새 딜 올리기(`?from={postId}` — 05 §2-10) — 핫딜 게시판을 처음 열 때 한 번 `prefill` 로 링크·몰·이미지·제목을 채운다(가격·마감은 비운다).
 * 링크는 채운 뒤 지금 규칙으로 다시 판정한다(같은 딜·제휴·차단). 못 받으면 알리고 빈 칸으로 쓴다
 */
const fromPostId = computed(() => (typeof route.query.from === 'string' ? route.query.from : ''));
const prefillState = ref<'idle' | 'loading' | 'done' | 'failed'>('idle');
async function loadPrefill(): Promise<void> {
    if (!fromPostId.value || prefillState.value !== 'idle') return;
    prefillState.value = 'loading';
    try {
        const prefill = await hotdealApi.prefill(fromPostId.value);
        dealForm.value = UtilHotdeal.formOfPrefill(prefill);
        if (!form.value.title.trim()) form.value = { ...form.value, title: prefill.title.slice(0, UtilPost.TITLE_MAX) };
        prefillState.value = 'done';
    } catch (e) {
        prefillState.value = 'failed';
        // 아직 진행 중인 딜(`409 DEAL_NOT_ENDED`)은 핫딜 문구, 그 밖은 서버 문구
        toast.error(t('hotdeal.prefillFailed', { message: UtilHotdealMessages.dealError(t, e, 'prefill') ?? UtilBoardMessages.of(t, e) }), 8000);
    }
}
// 핫딜 게시판이 정해진 뒤 한 번
watch(isDeal, (deal) => {
    if (deal) void loadPrefill();
});

/** 단계 입력 — 좁은 화면의 핫딜 글쓰기만(넓으면 한 번에) */
const narrow = useMediaQuery('(max-width: 767px)');
const step = ref<1 | 2>(1);
const formStep = computed<0 | 1 | 2>(() => (isDeal.value && narrow.value ? step.value : 0));
function onStepNext(): void {
    dealTried.value = true;
    if (UtilHotdeal.hasProblem(dealProblems.value)) return;
    step.value = 2;
    window.scrollTo({ top: 0 });
}
/** 단계 머리를 눌러 고르기 — 앞 단계로는 늘, 뒤 단계로는 [다음]과 같은 검사 뒤 */
function onStepPick(value: string | number | undefined): void {
    if (value === 1) step.value = 1;
    else if (value === 2) onStepNext();
}

const dirty = computed(() => (isDeal.value && dealForm.value.productUrl.trim() !== '') || form.value.title.trim() !== '' || UtilPost.hasContent(form.value.content, form.value.contentFormat) || (pollShown.value && poll.value.enabled));

async function submit(): Promise<void> {
    const b = board.value;
    if (!b || submitting.value) return;
    pollTried.value = true;
    if (pollShown.value && UtilPoll.hasProblem(UtilPoll.problems(poll.value, Date.now()))) return;
    dealTried.value = true;
    if (isDeal.value && UtilHotdeal.hasProblem(dealProblems.value)) {
        step.value = 1;
        return;
    }
    fieldErrors.value = {};
    formError.value = '';
    submitting.value = true;
    try {
        const result = await postApi.create({
            boardSlug: b.slug,
            prefixId: form.value.prefixId,
            title: form.value.title.trim(),
            // 에디터 HTML 은 그대로 보낸다(서버가 정화한다), 평문은 앞뒤 공백을 뗀다
            content: form.value.contentFormat === 'HTML' ? form.value.content : form.value.content.trim(),
            contentFormat: form.value.contentFormat,
            adult: b.allowAdult && form.value.adult,
            guest: b.viewer.guest ? { name: form.value.guestName.trim(), password: form.value.guestPassword, captchaToken: form.value.captcha ? UtilPost.PLACEHOLDER_CAPTCHA_TOKEN : '' } : null,
            debate: debateShown.value ? UtilDebate.openBody(debate.value) : null,
            poll: pollShown.value ? UtilPoll.inputOf(poll.value) : null,
            ...(isDeal.value ? { deal: UtilHotdeal.inputOf(dealForm.value) } : {}),
        });
        saved = true;
        form.value = { ...EMPTY_POST_FORM };
        debate.value = { ...DEFAULT_DEBATE_SETTINGS };
        poll.value = UtilPoll.emptyForm();
        dealForm.value = UtilHotdeal.emptyForm();
        toast.success(t('post.created'));
        // 저장은 됐지만 알릴 것(서버 `warnings[]`) — 서버 문구 그대로
        for (const w of result.warnings ?? []) toast.show(w.message, { life: 8000 });
        await router.replace(`/posts/${result.id}`);
    } catch (e) {
        const dealLimit = isDeal.value && e instanceof ApiError && (e.code === 'DEAL_DAILY_POST_LIMIT' || e.code === 'DEAL_SAME_STORE_TOO_SOON');
        const restricted = UtilBoardSanction.restrictionFromError(e);
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('write');
        // 이 게시판에서 쓰기 제한 중(게시판 제재) — 폼 위 안내(쓰던 내용은 그대로)
        else if (restricted) errorRestriction.value = { slug: b.slug, restriction: restricted };
        // 하루 핫딜 글 한도 · 같은 쇼핑몰 간격(429) — 폼 아래 안내(쓰던 내용은 그대로)
        else if (dealLimit) formError.value = UtilHotdealMessages.dealError(t, e) ?? UtilBoardMessages.of(t, e);
        // 핫딜 쓰기 자격(403 — 이메일 인증·첫 등급·정지) — 게시판 목록 판정에 없는 핫딜 자격이라 저장 때 처음 알 수 있다. 핫딜 문구로(등급 이름은 서버가 주지 않아 「일반 등급부터」)
        else if (isDeal.value && e instanceof ApiError && UtilHotdeal.isWriteBlockedCode(e.code)) formError.value = UtilHotdealMessages.writeBlocked(t, e.code, UtilHotdealMessages.gradeLabelOf(e));
        else if (e instanceof ApiError && e.code === 'IMAGE_UPLOAD_NOT_ALLOWED') fieldErrors.value = { content: e.message }; // 이미지를 막은 게시판 — 본문 칸 아래에 서버 문구
        else if (e instanceof ApiError && UtilHotdeal.isFieldErrorCode(e.code) && e.fields && Object.keys(e.fields).length) {
            fieldErrors.value = { ...e.fields };
            // 딜 칸 오류만 있으면 단계 입력은 딜 정보 단계로
            if (UtilHotdeal.hasDealFields(e.fields) && !('title' in e.fields) && !('content' in e.fields)) step.value = 1;
        }
        else formError.value = UtilBoardMessages.of(t, e);
    } finally {
        submitting.value = false;
    }
}

function cancel(): void {
    const slug = board.value?.slug ?? requestedSlug.value;
    void router.push(slug ? `/boards/${slug}` : '/');
}

/* ── 저장하지 않고 나가기 ── */
onBeforeRouteLeave(async () => {
    if (saved || !dirty.value) return true;
    return await confirm({ title: t('post.leaveTitle'), message: t('post.leaveMessage'), confirmLabel: t('post.leaveConfirm'), tone: 'danger' });
});
function onBeforeUnload(event: BeforeUnloadEvent): void {
    if (saved || !dirty.value) return;
    event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));

useSeoMeta({ title: () => t('post.writeTitle'), robots: 'noindex' });
</script>
