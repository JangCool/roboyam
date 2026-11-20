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
        태그(post-tags-contract): 게시판 `tagsEnabled` 면 본문(·대표 이미지) 아래 태그 칸(`PostTagInput` — 최대 5개). 게시판을 바꿔도 값은 들고 가고, 꺼진 게시판이면 칸을 감추고 보내지 않는다.
        출처(shared V232): 게시판 `sourcesEnabled` 면 태그 위에 출처 칸(`PostSourcesField` — 줄마다 이름 + 주소, 최대 5). 태그와 같게 게시판을 바꿔도 값은 들고 가고, 꺼진 게시판이면 감추고 보내지 않는다.
        대표 이미지(list-thumbnail-contract 「2단계」): 게시판 `authorThumbnailEnabled`(기본 끔)를 켠 게시판의 회원 글쓰기면 본문 바로 아래 「대표 이미지」 줄(`PostThumbnailPicker` — 본문 이미지 고르기 · 자동 · 따로 올리기 · 없음). 자동이 아니면 본문 `thumbnail` 로 보낸다.
        포인트 안내(point-board-tier-contract): 고른 게시판 등급이 정보·핵심 글이고 회원이면 본문 바로 아래 받을 포인트(쓰면·추천 문턱·우수 글) + 지금 글자 수·사진 수와 적립 조건(채웠는지 표시). 일반·끔·비회원은 안내 없음.
          등급·금액·조건은 게시판 응답 `pointTier`(끔·소모임 게시판이면 null — 안내 없음). 사진 수는 서버와 같이 본문에 붙은 올린 이미지만 센다(평문 본문은 0).
        구입 경로(trial-group-contract 「구입 경로」·「구현 결정」 5): 게시판 `purchaseSourceMode` 가 끔이 아니면 본문 바로 아래 「어떻게 구했나요」(`PostPurchaseSourceField`) — 내 돈으로 샀어요(+ 구매 인증 사진 — 글 저장 뒤
          `POST /api/posts/{id}/purchase-proof`, 실패해도 글은 그대로 · 글 보기에서 다시) · 업체에서 받았어요 · 빌려서 써 봤어요 · 뽑힌 체험단 사용기로 쓰기(체험단 사용기 받는 게시판에 쓸 내 뽑힌 체험단이 있을 때).
          `REQUIRED` 면 필수. `/write?board=&trialParticipantId=`(내 체험단 「쓰러 가기」)로 열리면 체험단으로 고정 + 게시판 고르기도 그 게시판만(말머리 [체험단]·표시 상자는 서버가 단다).
        게시판 제재(board-sanction-contract): 고른 게시판에서 쓰기 제한 중이면 폼 대신 「…까지 글·댓글 쓰기가 제한돼요 · 사유 · [자세히]」. 등록 때 `403 BOARD_WRITE_RESTRICTED` 면 폼 위에 같은 안내(쓰던 글은 그대로).
        가정: 쓸 수 있는 게시판 목록은 서버가 판정해 준다(`GET /api/boards/writable` 후보). `?board` 가 쓸 수 없는 게시판이면 그 까닭을 보인다.
          금지어·작성 간격·하루 한도·같은 글 반복은 서버가 거절하고 그 문구를 폼 아래에 그대로 보인다. 저장하지 않고 나가면 확인을 묻는다.
    -->
    <div class="content-card">
        <!-- 쓰던 도중 성인 전용 게시판의 핀 확인이 끝났다(등록·태그 추천이 403 ADULT_BOARD_PIN_REQUIRED) — 폼을 그대로 둔 채 위에 핀 입력. 맞게 넣고 다시 [등록] -->
        <AdultBoardPinDialog v-model:visible="pinDialog" :locked-until="pinLockedUntil" @unlocked="formError = ''" />
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

            <!-- 익명 게시판 — 글·댓글이 「익명」으로 올라간다는 안내(anonymous-board-contract). 익명 여부는 서버가 게시판 설정으로 정한다(요청 모양은 그대로) -->
            <AnonymousBoardNotice v-if="boardState === 'ready' && board?.viewer.canWrite && board.authorMode === 'ANONYMOUS'" />
            <div v-if="boardState === 'loading'" role="status" :aria-label="t('common.loading')" class="h-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <!-- 성인 전용 게시판 — 이 기기에서 핀번호를 아직 안 넣었다(403 ADULT_BOARD_PIN_REQUIRED, adult-board-pin-contract). 맞게 넣으면 게시판 정보를 다시 받는다(쓰던 글은 그대로) -->
            <AdultBoardPinGate v-else-if="boardState === 'pin'" :locked-until="pinLockedUntil" :heading-level="2" @unlocked="loadBoard" />
            <ErrorState v-else-if="boardState === 'error'" :title="t('post.boardFailed')":description="boardError" retry @retry="loadBoard" />
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
                @attempt="onAttempt"
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
                <template #belowContent="{ imageUpload }">
                    <!-- 구입 경로(trial-group-contract) — 게시판 「구입 경로 받기」가 끔이 아닐 때만 -->
                    <PostPurchaseSourceField
                        v-if="purchaseShown"
                        v-model="purchase"
                        v-model:proof="proofFile"
                        :required="purchaseRequired"
                        :trials="trialOptions"
                        :locked="trialLocked"
                        :proof-allowed="board?.viewer.guest === false"
                        :disabled="submitting"
                        :error="fieldErrors.purchaseSource || fieldErrors.trialParticipantId || ''"
                        :show-problems="purchaseTried"
                    />
                    <!-- 포인트 안내(point-board-tier-contract) — 정보·핵심 글 게시판의 회원 글쓰기만. 받을 포인트 + 지금 글자 수·사진 수와 적립 조건 -->
                    <div v-if="tierGuide" class="rounded-lg border border-surface px-3 py-2.5 text-sm" role="note" data-testid="write-point-tier">
                        <p class="flex items-center gap-1.5 font-semibold">
                            <i class="pi pi-wallet text-xs text-primary" aria-hidden="true" />
                            {{ t('point.writeTierTitle', { tier: t(`point.writeTierTierName.${tierGuide.tier}`) }) }}
                        </p>
                        <p v-if="tierGuideLine" class="mt-1">{{ tierGuideLine }}</p>
                        <template v-if="tierConditions.length">
                            <ul class="m-0 mt-2 flex list-none flex-wrap gap-x-4 gap-y-1 p-0 text-xs tabular-nums" data-testid="write-point-conditions">
                                <li v-for="c in tierConditions" :key="c.key" class="inline-flex items-center gap-1" :class="c.met ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">
                                    <i :class="c.met ? 'pi pi-check-circle' : 'pi pi-circle'" class="text-[11px]" aria-hidden="true" />
                                    {{ c.label }}
                                </li>
                            </ul>
                            <p class="mt-1 text-xs" :class="tierMet ? 'text-green-700 dark:text-green-400' : 'text-muted-color'" aria-live="polite" data-testid="write-point-met">
                                {{ tierMet ? t('point.writeTierMet') : t('point.writeTierUnmet') }}
                            </p>
                        </template>
                        <p v-if="tierGuide.holdHours > 0 && (tierGuide.likeBonusPoints > 0 || tierGuide.featuredPoints > 0)" class="mt-1 text-xs text-muted-color">{{ t('point.writeTierHold', { h: tierGuide.holdHours }) }}</p>
                    </div>
                    <!-- 출처(V232) — 게시판 옵션 「출처 칸 쓰기」(`sourcesEnabled`)를 켠 게시판만. 본문 바로 아래(대표 이미지보다 먼저 — 글 보기에서도 본문 바로 밑에 보인다, 사용자 2026-11-16) -->
                    <PostSourcesField v-if="sourcesShown" v-model="sources" :disabled="submitting" :error="fieldErrors.sources ?? ''" :show-problems="sourcesTried" />
                    <!-- 대표 이미지(list-thumbnail-contract 「2단계」) — 게시판 옵션을 켠 게시판의 회원 글쓰기만 -->
                    <PostThumbnailPicker
                        v-if="thumbnailShown"
                        v-model="thumbnail"
                        :content="form.content"
                        :content-format="form.contentFormat"
                        :can-upload="imageUpload"
                        :upload="uploadThumbnail"
                        :disabled="submitting"
                        :error="fieldErrors.thumbnail ?? ''"
                    />
                    <!-- 태그(post-tags-contract) — 게시판 옵션 「태그 쓰기」(`tagsEnabled`)를 켠 게시판만(비회원 글도 — 글쓰기 권한 그대로) -->
                    <PostTagInput v-if="tagsShown" v-model="tags" :board-slug="selectedSlug" :disabled="submitting" :error="tagsError" :required="tagsRequired" />
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
import { AUTO_THUMBNAIL, type PostThumbnailChoice } from '~/components/board/PostThumbnailPicker.types';
import type { PostThumbnailInput } from '~/composables/board/api/usePostApi.types';
import UtilPostThumbnail from '~/utils/util-post-thumbnail';
import type { DealFieldActions } from '~/components/hotdeal/HotdealDealFields.types';
import UtilAdultBoard from '~/utils/util-adult-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';
import UtilJjalMessages from '~/utils/util-jjal-messages';
import UtilDebate from '~/utils/util-debate';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilPoll from '~/utils/util-poll';
import UtilPollMessages from '~/utils/util-poll-messages';
import UtilPoint from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';
import UtilPost from '~/utils/util-post';
import UtilPostOps from '~/utils/util-post-ops';
import UtilPostTag from '~/utils/util-post-tag';
import UtilPostSource, { type PostSourceRow } from '~/utils/util-post-source';
import { EMPTY_PURCHASE_SOURCE, type PurchaseSourceValue, type PurchaseTrialOption } from '~/components/board/PostPurchaseSourceField.types';
import type { TrialWritable } from '~/composables/trial/api/useTrialApi.types';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
/** 저장 때 서버의 로보티콘 오류(`409 EMOTICON_NOT_OWNED`)를 미리 검사와 같은 문구로 — 이름표는 카탈로그에서 */
const emoticonCatalog = useEmoticonCatalog();
const emoticonLabel = (id: string): string => emoticonCatalog.resolve(id)?.label ?? id;
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
/** 체험단 연결로 열렸으면(`?trialParticipantId=`) 그 사용기 게시판만 고른다 */
const options = computed<PostBoardOption[]>(() =>
    writable.value.filter((b) => !trialLockedSlug.value || b.slug === trialLockedSlug.value).map((b) => ({ slug: b.slug, label: localizedName(b.name, b.nameEn) })),
);
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
const boardState = ref<'idle' | 'loading' | 'ready' | 'pin' | 'error'>('idle');
const boardError = ref('');
/** 「핀 필요」 오류에 실려 온 틀림 잠금의 끝 시각(없으면 null) */
const pinLockedUntil = ref<string | null>(null);
/** 쓰던 도중(폼이 떠 있을 때) 핀 확인이 끝났다 — 폼을 치우지 않고 대화상자로 묻는다(쓰던 글이 그대로 남는다) */
const pinDialog = ref(false);
// 어느 호출이든(등록·태그 추천 등) 「핀 필요」를 받았다 — 폼이 떠 있으면 대화상자로. 게시판 정보를 받는 중이면 `loadBoard` 의 오류 처리가 폼 자리에 핀 입력을 그린다
useAdultBoardPinDemand((lockedUntil) => {
    if (boardState.value !== 'ready') return;
    pinLockedUntil.value = lockedUntil;
    pinDialog.value = true;
});

let boardRequest = 0;
/** 마지막으로 미리 채운 글 양식 본문 — 손대지 않았는지 가르는 기준(게시판을 바꾸면 새 양식으로 갈아 끼운다) */
let appliedTemplate = '';

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
        // 글 양식(게시판 `writeTemplate`) — 본문이 비었거나 앞 게시판의 양식 그대로면 이 게시판의 양식으로 바꿔 채운다(손댄 본문은 그대로 둔다)
        const untouched = !UtilPost.hasContent(form.value.content, form.value.contentFormat) || UtilPost.isUntouchedTemplate(form.value.content, appliedTemplate, form.value.contentFormat);
        if (untouched) {
            appliedTemplate = UtilPost.templateContent(detail.writeTemplate, format);
            form.value = { ...form.value, content: appliedTemplate, contentFormat: format };
        } else if (form.value.contentFormat !== format) {
            form.value = { ...form.value, content: UtilPost.convertContent(form.value.content, form.value.contentFormat, format), contentFormat: format };
        }
    } catch (e) {
        if (mine !== boardRequest) return;
        boardError.value = UtilBoardMessages.of(t, e);
        const pin = UtilAdultBoard.pinRequired(e);
        pinLockedUntil.value = pin?.lockedUntil ?? null;
        boardState.value = pin ? 'pin' : 'error';
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

/*
 * ── 대표 이미지(list-thumbnail-contract 「2단계」) — 게시판 옵션을 켠 게시판의 회원 글쓰기만(비회원은 이미지를 못 올린다). 기본 자동, 게시판을 바꿔도 들고 간다.
 * 저장은 실제로 쓰일 값(`effective` — 고른 이미지가 본문에서 빠졌으면 자동)으로, 자동이면 칸을 뺀다 ──
 */
const thumbnail = ref<PostThumbnailChoice>({ ...AUTO_THUMBNAIL });
/** 게시판 정책 「글쓴이가 대표 이미지 고르기」(`authorThumbnailEnabled`, 기본 끔)를 켠 게시판의 회원 글쓰기만. 꺼진 게시판은 칸을 싣지 않는다(자동) */
const thumbnailShown = computed(() => board.value?.viewer.guest === false && board.value.authorThumbnailEnabled === true);
const uploadThumbnail = (file: File) => imageApi.uploadThumbnail(file);
/** 저장 요청의 `thumbnail` 칸 — 자동이면 뺀다 */
function thumbnailField(): { thumbnail?: PostThumbnailInput } {
    if (!thumbnailShown.value) return {};
    const chosen = UtilPostThumbnail.effective(thumbnail.value, UtilPostThumbnail.bodyImages(form.value.content, form.value.contentFormat));
    return chosen.mode === 'AUTO' ? {} : { thumbnail: UtilPostThumbnail.inputOf(chosen) };
}

/* ── 태그(post-tags-contract) — 게시판을 바꿔도 값은 들고 가고, 태그를 쓰지 않는 게시판이면 칸을 감추고 보내지 않는다 ── */
const tags = ref<string[]>([]);
const tagsShown = computed(() => board.value?.tagsEnabled === true);
/** 태그 필수(게시판 유효 정책 `tagRequired` — 뉴스 게시판 글 규칙). 서버는 「태그 쓰기」가 꺼진 게시판이면 늘 false 로 준다 */
const tagsRequired = computed(() => tagsShown.value && board.value?.tagRequired === true);
/** [등록]을 한 번 누른 뒤부터 「태그를 하나 이상 넣어 주세요」를 보인다 */
const tagsTried = ref(false);
const tagsMissing = computed(() => tagsShown.value && tagsRequired.value && tags.value.length === 0);
/** 서버 오류 — `fields.tags` 또는 `fields."tags[0]"` 꼴의 첫 문구 */
const tagsServerError = computed(() => UtilPostTag.fieldError(fieldErrors.value));
/** 태그 칸 오류 — 서버 문구가 먼저, 아니면 필수 검사 */
const tagsError = computed(() => tagsServerError.value || (tagsTried.value && tagsMissing.value ? t('postTag.required') : ''));
// 태그를 고치면 그 칸의 낡은 서버 오류를 지운다
watch(tags, () => {
    if (tagsServerError.value) fieldErrors.value = UtilPostTag.withoutFieldErrors(fieldErrors.value);
});

/*
 * ── 포인트 안내(point-board-tier-contract) — 고른 게시판 등급이 정보·핵심 글이고 회원 글쓰기일 때만. 일반·끔·비회원은 안내 없음(일반은 대부분의 게시판이라 매번 보이면 시끄럽다 — 가정).
 * 지금 글자 수(서식·공백·그림 뺀 글자)·사진 수와 적립 조건을 같이 보인다. 판정은 서버다(화면은 안내만). 등급은 게시판 응답 `pointTier` ──
 */
const tierGuide = computed(() => {
    const b = board.value;
    const tier = b?.pointTier;
    if (!b || !tier || b.viewer.guest || (tier.tier !== 'INFO' && tier.tier !== 'CORE')) return null;
    return tier;
});
const tierGuideLine = computed(() => (tierGuide.value ? UtilPointMessages.writeTierLine(t, tierGuide.value) : ''));
/** 적립 조건 — 0 인 조건은 뺀다 */
const tierConditions = computed(() => {
    const g = tierGuide.value;
    if (!g) return [];
    const out: { key: string; label: string; met: boolean }[] = [];
    if (g.minLength > 0) {
        const n = UtilPost.pointLength(form.value.content, form.value.contentFormat);
        out.push({ key: 'length', label: t('point.writeTierLength', { current: UtilPoint.number(n), min: UtilPoint.number(g.minLength) }), met: n >= g.minLength });
    }
    if (g.minImages > 0) {
        const n = UtilPost.photoCount(form.value.content, form.value.contentFormat);
        out.push({ key: 'images', label: t('point.writeTierImages', { current: UtilPoint.number(n), min: UtilPoint.number(g.minImages) }), met: n >= g.minImages });
    }
    return out;
});
const tierMet = computed(() => tierConditions.value.every((c) => c.met));

/* ── 출처(V232) — 게시판을 바꿔도 값은 들고 가고, 출처 칸을 쓰지 않는 게시판이면 칸을 감추고 보내지 않는다 ── */
const sources = ref<PostSourceRow[]>([]);
const sourcesShown = computed(() => board.value?.sourcesEnabled === true);
/** [등록]을 한 번 누른 뒤부터 줄 아래 주소 문제를 보인다 */
const sourcesTried = ref(false);
// 출처를 고치면 그 칸의 낡은 서버 오류를 지운다
watch(sources, () => {
    if (fieldErrors.value.sources) fieldErrors.value = Object.fromEntries(Object.entries(fieldErrors.value).filter(([k]) => k !== 'sources'));
});

/*
 * ── 구입 경로(trial-group-contract 「구입 경로」) — 게시판 `purchaseSourceMode` 가 끔이 아닐 때만. 게시판을 바꿔도 값은 들고 가고, 끈 게시판이면 감추고 보내지 않는다.
 * 뽑힌 내 체험단은 회원이고 「체험단 사용기 받기」 게시판(또는 체험단 연결로 열림)일 때 게시판마다 받는다(서버 `GET /api/me/trials/writable?board=` — 연결 전 줄만) ──
 */
const trialApi = useTrialApi();
const purchase = ref<PurchaseSourceValue>({ ...EMPTY_PURCHASE_SOURCE });
const proofFile = ref<File | null>(null);
const purchaseTried = ref(false);
const purchaseShown = computed(() => (board.value?.purchaseSourceMode ?? 'OFF') !== 'OFF');
const purchaseRequired = computed(() => board.value?.purchaseSourceMode === 'REQUIRED');
/** 주소의 체험단 줄(`?trialParticipantId=`) — 있으면 체험단으로 고정 */
const lockedParticipantId = computed(() => (typeof route.query.trialParticipantId === 'string' ? route.query.trialParticipantId : ''));
const writableTrials = ref<TrialWritable[]>([]);
/** 받은 게시판(같은 게시판이면 다시 묻지 않는다) */
let writableFor = '';
/** 이 게시판에 쓸 수 있는 내 뽑힌 체험단(서버가 연결 전 줄만 준다) */
const trialOptions = computed<PurchaseTrialOption[]>(() => writableTrials.value.filter((w) => w.board.slug === selectedSlug.value).map((w) => ({ participantId: w.participantId, label: w.trialTitle })));
const trialLocked = computed(() => !!lockedParticipantId.value);
const trialLockedSlug = computed(() => (trialLocked.value && requestedSlug.value ? requestedSlug.value : ''));
async function loadWritable(slug: string): Promise<void> {
    if (!user.value || writableFor === slug) return;
    writableFor = slug;
    try {
        writableTrials.value = await trialApi.writable(slug);
    } catch {
        writableFor = ''; // 못 받으면 체험단 고르기를 감춘다(다음에 다시)
        writableTrials.value = [];
    }
}
// 구입 경로를 받는 게시판이 정해지면 — 체험단 고르기 후보(체험단 사용기 받는 게시판·체험단 연결로 열림)
watch(
    () => [purchaseShown.value, board.value?.slug] as const,
    ([shown, slug]) => {
        if (shown && slug && (board.value?.trialReviewsEnabled || lockedParticipantId.value)) void loadWritable(slug);
    },
);
// 체험단 연결로 열렸다 — 체험단으로 고정
watch(
    lockedParticipantId,
    (id) => {
        if (id) purchase.value = { source: 'TRIAL', trialParticipantId: id };
    },
    { immediate: true },
);
/** 보낼 구입 경로 칸 — 끈 게시판이면 뺀다 */
function purchaseFields(): { purchaseSource?: PurchaseSourceValue['source']; trialParticipantId?: string | null } {
    if (!purchaseShown.value) return {};
    const p = purchase.value;
    // 체험단은 줄 ID 로만 잇는다(서버가 TRIAL 로 고정 — `purchaseSource` 에 TRIAL 을 싣지 않는다)
    return p.source === 'TRIAL' ? { purchaseSource: null, trialParticipantId: p.trialParticipantId } : { purchaseSource: p.source, trialParticipantId: null };
}
/** 구입 경로 화면 검사 — 꼭 골라야 함인데 비었거나 체험단을 골랐는데 줄이 없다 */
const purchaseProblem = computed(() => purchaseShown.value && !trialLocked.value && ((purchaseRequired.value && !purchase.value.source) || (purchase.value.source === 'TRIAL' && !purchase.value.trialParticipantId)));
// 구입 경로를 고치면 그 칸의 낡은 서버 오류를 지운다
watch(purchase, () => {
    if (fieldErrors.value.purchaseSource || fieldErrors.value.trialParticipantId) fieldErrors.value = Object.fromEntries(Object.entries(fieldErrors.value).filter(([k]) => k !== 'purchaseSource' && k !== 'trialParticipantId'));
});

/** [등록]을 눌렀다(폼 검사 전) — 폼 밖 칸(딜·태그·출처)의 검사 문구도 함께 보이게 */
function onAttempt(): void {
    purchaseTried.value = true;
    dealTried.value = true;
    tagsTried.value = true;
    sourcesTried.value = true;
}

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

const dirty = computed(
    () =>
        (isDeal.value && dealForm.value.productUrl.trim() !== '') ||
        form.value.title.trim() !== '' ||
        // 미리 채운 글 양식 그대로면 쓴 것이 없다
        (UtilPost.hasContent(form.value.content, form.value.contentFormat) && !UtilPost.isUntouchedTemplate(form.value.content, appliedTemplate, form.value.contentFormat)) ||
        (pollShown.value && poll.value.enabled) ||
        (thumbnailShown.value && thumbnail.value.mode !== 'AUTO') ||
        (tagsShown.value && tags.value.length > 0) ||
        (sourcesShown.value && UtilPostSource.toRequest(sources.value).length > 0) ||
        (purchaseShown.value && !trialLocked.value && purchase.value.source !== null),
);

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
    tagsTried.value = true;
    if (tagsMissing.value) return;
    sourcesTried.value = true;
    if (sourcesShown.value && UtilPostSource.hasProblem(sources.value)) return;
    purchaseTried.value = true;
    if (purchaseProblem.value) return;
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
            // 비밀글(운영 게시판 `secretMode`) — 글쓴이가 고름 · 모든 글 비밀 · 없음이면 칸을 뺀다(비회원도 같다 — 비회원 비밀글은 글 비밀번호로 연다)
            ...UtilPostOps.secretField(UtilPostOps.writeSecret(UtilPostOps.secretMode(b), form.value.secret)),
            ...(isDeal.value ? { deal: UtilHotdeal.inputOf(dealForm.value) } : {}),
            ...thumbnailField(),
            ...(tagsShown.value ? { tags: tags.value.slice() } : {}),
            ...(sourcesShown.value ? { sources: UtilPostSource.toRequest(sources.value) } : {}),
            ...purchaseFields(),
        });
        saved = true;
        // 구매 인증 사진 — 글이 저장된 뒤 올린다. 실패해도 글은 그대로(글 보기에서 다시 올릴 수 있다)
        const proof = purchaseShown.value && purchase.value.source === 'SELF' ? proofFile.value : null;
        if (proof) {
            try {
                await postApi.uploadPurchaseProof(result.id, proof);
                toast.success(t('trial.proofUploaded'));
            } catch (e) {
                toast.error(t('trial.proofUploadFailedAfterSave', { message: UtilBoardMessages.of(t, e) }), 8000);
            }
        }
        purchase.value = { ...EMPTY_PURCHASE_SOURCE };
        proofFile.value = null;
        form.value = { ...EMPTY_POST_FORM };
        thumbnail.value = { ...AUTO_THUMBNAIL };
        tags.value = [];
        sources.value = [];
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
        // 성인 전용 게시판의 핀 확인이 쓰는 사이 끝났다 — 핀 입력 대화상자가 뜬다(`useAdultBoardPinDemand`). 쓰던 내용은 그대로, 폼 아래 오류는 두지 않는다
        else if (UtilAdultBoard.pinRequired(e)) formError.value = '';
        // 이 게시판에서 쓰기 제한 중(게시판 제재) — 폼 위 안내(쓰던 내용은 그대로)
        else if (restricted) errorRestriction.value = { slug: b.slug, restriction: restricted };
        // 하루 핫딜 글 한도 · 같은 쇼핑몰 간격(429) — 폼 아래 안내(쓰던 내용은 그대로)
        else if (dealLimit) formError.value = UtilHotdealMessages.dealError(t, e) ?? UtilBoardMessages.of(t, e);
        // 핫딜 쓰기 자격(403 — 이메일 인증·첫 등급·정지) — 게시판 목록 판정에 없는 핫딜 자격이라 저장 때 처음 알 수 있다. 핫딜 문구로(등급 이름은 서버가 주지 않아 「일반 등급부터」)
        else if (isDeal.value && e instanceof ApiError && UtilHotdeal.isWriteBlockedCode(e.code)) formError.value = UtilHotdealMessages.writeBlocked(t, e.code, UtilHotdealMessages.gradeLabelOf(e));
        else if (e instanceof ApiError && e.code === 'IMAGE_UPLOAD_NOT_ALLOWED') fieldErrors.value = { content: e.message }; // 이미지를 막은 게시판 — 본문 칸 아래에 서버 문구
        else if (UtilEmoticonMessages.bodyError(t, e, emoticonLabel)) fieldErrors.value = { content: UtilEmoticonMessages.bodyError(t, e, emoticonLabel) ?? '' }; // 가지지 않은 팩의 로보티콘 — 본문 칸 아래
        else if (UtilJjalMessages.bodyError(e)) fieldErrors.value = { content: UtilJjalMessages.bodyError(e) ?? '' }; // 짤을 막은 게시판·내 보관함에 없는 짤·내려간 짤 — 본문 칸 아래에 서버 문구
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
