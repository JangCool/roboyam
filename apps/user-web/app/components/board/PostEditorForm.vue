<template>
    <!--
        글쓰기·고치기 폼 — 글쓰기(`/write`)와 고치기(`/posts/[id]/edit`)가 같이 쓴다. 그리기·화면 검사만 하고 저장(서버 호출)은 페이지가 한다.
        칸: 게시판(글쓰기만) · 말머리(운영진 전용은 운영진만) · 제목 1~100 · 본문 10~200,000(게시판 글 에디터 — 평문 Textarea / 간단·고급 서식 에디터, 에디터 글은 보이는 글자 수) · 19금(허용 게시판만 — 성인 전용 말머리를 고르면 켜고 잠근다, 19금 글은 본인인증한 만 19세 이상만 쓴다: 판정은 서버) · (`extra` 자리 — 찬반 토론 설정) · 비회원 이름·비밀번호·자동입력 방지.
        이미지는 고급 에디터 안에서 올린다(게시판이 허용하고 회원일 때만 — 비회원 글쓰기·비회원 글 고치기는 감춘다). 올리기 함수(`upload`)는 페이지가 API 컴포저블로 만들어 넘긴다.
        화면 검사는 미리 알려 주는 용도이고 판정은 서버다(서버 `fields` 가 있으면 그것을 칸 아래에 먼저 보인다).
        `lead` 자리(제목 위 — 핫딜 딜 칸)와 단계 입력(`step` — 모바일 핫딜: 1 = 게시판·말머리·`lead` + [다음], 2 = 나머지 + [이전]·[등록], 0 = 한 번에)을 둔다.
          단계를 오가도 칸 상태가 남게 숨기기만 한다(`v-show`). [등록]을 누르면 검사 전에 `attempt` 를 올린다(화면이 덧붙인 칸의 검사 문구도 함께 보이게).
    -->
    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
        <!-- 글쓰기 안내문(운영자가 적은 평문 — 줄바꿈 유지, HTML 로 해석하지 않는다) -->
        <Message v-if="board.writeGuideText" severity="info" :closable="false" size="small">
            <span class="whitespace-pre-line">{{ board.writeGuideText }}</span>
        </Message>

        <div class="grid gap-3 md:grid-cols-[1fr_12rem]">
            <LabeledField v-if="boardOptions.length" :label="t('post.boardLabel')" :error="fieldErrors.boardSlug ?? ''">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        :input-id="id"
                        :model-value="boardSlug"
                        :options="boardOptions"
                        option-label="label"
                        option-value="slug"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        @update:model-value="$emit('update:boardSlug', String($event ?? ''))"
                    />
                </template>
            </LabeledField>
            <div v-else class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ fixedTarget ? fixedTarget.label : t('post.boardLabel') }}</span>
                <span class="text-sm">{{ fixedTarget ? fixedTarget.value : localizedName(board.name, board.nameEn) }}</span>
            </div>
            <LabeledField :label="t('post.prefixLabel')" :error="fieldErrors.prefixId ?? ''">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        :input-id="id"
                        :model-value="modelValue.prefixId"
                        :options="prefixOptions"
                        option-label="label"
                        option-value="value"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        @update:model-value="pickPrefix(($event as string | null) ?? null)"
                    />
                </template>
            </LabeledField>
        </div>

        <!-- 화면이 제목 위에 덧붙이는 칸(핫딜 딜 칸) — 단계 입력의 1단계 -->
        <div v-if="$slots.lead" v-show="step !== 2" class="contents">
            <slot name="lead" />
        </div>

        <div v-show="step !== 1" class="flex flex-col gap-4">
        <!-- 2단계로 넘어온 뒤 1단계 칸에 문제가 있으면 화면이 알린다 -->
        <slot name="stepNotice" />
        <LabeledField :label="t('post.titleLabel')" :error="titleError">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    :model-value="modelValue.title"
                    :maxlength="UtilPost.TITLE_MAX"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    :placeholder="t('post.titlePlaceholder')"
                    fluid
                    @update:model-value="patch({ title: String($event ?? '') })"
                />
            </template>
            <template #aside>
                <span class="text-xs text-muted-color tabular-nums">{{ modelValue.title.trim().length }} / {{ UtilPost.TITLE_MAX }}</span>
            </template>
        </LabeledField>
        <!-- 화면이 제목 칸 바로 아래에 덧붙이는 안내(핫딜 제목 안내) -->
        <slot name="titleHint" />

        <!-- 본문 — 게시판 글 에디터 설정(`postEditor` 유효값)을 따른다: 평문은 Textarea, 간단·고급은 서식 에디터(`@roboyam/rich-editor`).
             수준이 바뀌면(글쓰기에서 게시판을 바꿈) 에디터를 새로 만든다(`:key`) — 확장은 만들 때 정해진다 -->
        <LabeledField :label="t('post.contentLabel')" :error="contentError">
            <template #default="{ id, describedBy, invalid }">
                <RichEditor
                    v-if="editorLevel !== 'NONE'"
                    :key="editorLevel"
                    ref="editorRef"
                    :model-value="modelValue.content"
                    :emoticons="emoticonSource"
                    :level="editorLevel"
                    :image-upload="imageOn"
                    :upload="upload"
                    :invalid="invalid"
                    :describedby="describedBy ?? ''"
                    :aria-label="t('post.contentLabel')"
                    :placeholder="t('post.contentPlaceholder')"
                    :disabled="submitting"
                    @update:model-value="patch({ content: $event })"
                    @ready="$emit('editorReady', $event)"
                    @media="media = $event"
                >
                    <!-- 이모티콘 고르기(이모지 탭 + 받은 팩) — 이모티콘은 그림 노드, 저장은 토큰 글자 `:emo-ID:` -->
                    <template #tools>
                        <EmoticonPicker icon :disabled="submitting" @emoji="editorRef?.insertText($event)" @emoticon="editorRef?.insertEmoticon($event)" />
                    </template>
                </RichEditor>
                <Textarea
                    v-else
                    :id="id"
                    :model-value="modelValue.content"
                    :maxlength="UtilPost.CONTENT_MAX"
                    rows="12"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    :placeholder="t('post.contentPlaceholder')"
                    fluid
                    auto-resize
                    @update:model-value="patch({ content: String($event ?? '') })"
                />
            </template>
            <template #aside>
                <span class="text-xs text-muted-color tabular-nums">{{ contentLength.toLocaleString() }} / {{ UtilPost.CONTENT_MAX.toLocaleString() }}</span>
            </template>
        </LabeledField>

        <!-- 링크·이미지 규칙 미리 알림(비회원 링크 0개 · 새 회원 링크 2·이미지 3) — 판정은 서버 -->
        <Message v-if="linkWarning" severity="warn" :closable="false" size="small">{{ linkWarning }}</Message>
        <p v-else-if="ruleHint" class="-mt-2 text-xs text-muted-color">{{ ruleHint }}</p>
        <!-- 이미지 올리기가 끝나지 않았다 — 등록을 막는다(올리는 중·실패한 것은 에디터 아래 목록에 있다) -->
        <Message v-if="tried && mediaProblem" severity="error" variant="simple" size="small" role="alert">{{ mediaProblem }}</Message>

        <div v-if="board.allowAdult" class="flex flex-col gap-1">
            <label class="inline-flex cursor-pointer items-center gap-2 self-start text-sm" :class="{ 'cursor-not-allowed': adultLocked }">
                <Checkbox :model-value="modelValue.adult || adultLocked" binary :input-id="adultId" :disabled="adultLocked" :aria-describedby="adultHintId" @update:model-value="patch({ adult: Boolean($event) })" />
                <span>{{ t('post.adultLabel') }}</span>
            </label>
            <p :id="adultHintId" class="m-0 text-xs text-muted-color" data-testid="post-adult-hint">{{ adultLocked ? t('post.adultByPrefix') : t('post.adultWriterHint') }}</p>
        </div>
        <p v-if="fieldErrors.adult" role="alert" class="-mt-2 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.adult }}</p>

        <!-- 화면이 덧붙이는 칸(찬반 토론 설정 등) — 값·저장은 그 화면이 갖는다 -->
        <slot name="extra" />

        <BoardGuestFields
            v-if="guestMode"
            :rules="board.guestRules"
            :name="modelValue.guestName"
            :password="modelValue.guestPassword"
            :captcha="modelValue.captcha"
            :name-error="guestNameError"
            :password-error="guestPasswordError"
            :captcha-error="captchaError"
            @update:name="patch({ guestName: $event })"
            @update:password="patch({ guestPassword: $event })"
            @update:captcha="patch({ captcha: $event })"
        />

        </div>

        <Message v-if="formError" severity="error" variant="simple" role="alert">{{ formError }}</Message>

        <div class="flex justify-end gap-2 border-t border-surface pt-4">
            <Button v-if="step === 2" type="button" :label="t('common.prev')" icon="pi pi-chevron-left" severity="secondary" variant="text" class="mr-auto" @click="$emit('prev')" />
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="$emit('cancel')" />
            <Button v-if="step === 1" type="button" :label="t('common.next')" icon="pi pi-chevron-right" icon-pos="right" data-testid="post-step-next" @click="$emit('next')" />
            <Button v-else type="submit" :label="submitLabel" icon="pi pi-check" :loading="submitting" />
        </div>
    </form>
</template>

<script setup lang="ts">
import type { RichEditorMediaState, RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostBoardOption, PostFixedTarget, PostFormValues } from '~/components/board/PostEditorForm.types';
import UtilBoard from '~/utils/util-board';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

interface Props {
    board: BoardDetail;
    modelValue: PostFormValues;
    submitLabel: string;
    /** 게시판 고르기(글쓰기만) — 비면 게시판 이름만 보인다 */
    boardOptions?: PostBoardOption[];
    boardSlug?: string;
    /** 게시판 고르기가 없을 때 게시판 이름 대신 보일 자리(소모임 글쓰기 — 「소모임: 이름」) */
    fixedTarget?: PostFixedTarget | null;
    /** 비회원 이름·비밀번호·자동입력 방지 칸을 보인다(비회원 글쓰기) */
    guestMode?: boolean;
    /** 비회원 규칙(링크 0개·이미지 불가)을 적용한다 — 비회원 글쓰기와 비회원 글 고치기 */
    guestRules?: boolean;
    /** 서버 `400 VALIDATION` 의 `fields` */
    fieldErrors?: Record<string, string>;
    /** 칸에 붙지 않는 서버 문구 */
    formError?: string;
    submitting?: boolean;
    /** 에디터 이미지 한 장을 올리는 함수(페이지가 `useImageApi` 로 만든다) — 없으면 이미지 단추를 감춘다 */
    upload?: RichImageUploader | null;
    /** 단계 입력 — 0 한 번에 · 1 `lead` 단계(게시판·말머리·`lead`) · 2 나머지 단계 */
    step?: 0 | 1 | 2;
}

const props = withDefaults(defineProps<Props>(), {
    boardOptions: () => [],
    boardSlug: '',
    fixedTarget: null,
    guestMode: false,
    guestRules: false,
    fieldErrors: () => ({}),
    formError: '',
    submitting: false,
    upload: null,
    step: 0,
});

const emit = defineEmits<{
    'update:modelValue': [value: PostFormValues];
    'update:boardSlug': [value: string];
    /** 에디터가 처음 만들어졌을 때의 정규화된 HTML — 고치기 화면이 「고쳤는가」의 기준으로 쓴다 */
    editorReady: [html: string];
    submit: [];
    cancel: [];
    /** [등록]을 눌렀다(검사 전) — 화면이 덧붙인 칸의 검사 문구를 함께 보이려고 */
    attempt: [];
    /** 단계 입력의 [다음]·[이전] */
    next: [];
    prev: [];
}>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const adultId = useId();
/** 한 번 [등록]을 누른 뒤부터 화면 검사 문구를 보인다 */
const tried = ref(false);

function patch(change: Partial<PostFormValues>): void {
    emit('update:modelValue', { ...props.modelValue, ...change });
}

/** 고를 수 있는 말머리 — 운영진 전용은 운영진에게만. 성인 전용 말머리는 이름 뒤에 19금 표시 */
const prefixOptions = computed(() => [
    { value: null as string | null, label: t('post.prefixNone') },
    ...UtilBoard.selectablePrefixes(props.board, props.board.viewer.staff).map((p) => ({
        value: p.id as string | null,
        label: p.adultOnly && props.board.allowAdult ? t('post.prefixAdultOnly', { name: p.name }) : p.name,
    })),
]);

const adultHintId = useId();
/** 성인 전용 말머리인가(19금 허용 게시판에서만 뜻이 있다 — 서버도 같다) */
function adultOnlyPrefix(prefixId: string | null): boolean {
    return props.board.allowAdult && prefixId !== null && props.board.prefixes.some((p) => p.id === prefixId && p.adultOnly === true);
}
/** 고른 말머리가 성인 전용이라 19금 체크가 켜진 채 잠겼다 */
const adultLocked = computed(() => adultOnlyPrefix(props.modelValue.prefixId));

/** 말머리 고르기 — 성인 전용 말머리면 19금을 켜고, 성인 전용 말머리에서 다른 말머리로 옮기면 그 말머리가 켰던 19금을 끈다 */
function pickPrefix(prefixId: string | null): void {
    const locked = adultOnlyPrefix(prefixId);
    patch({ prefixId, ...(locked ? { adult: true } : adultLocked.value ? { adult: false } : {}) });
}

/** 게시판 글 에디터 수준 — `NONE` 이면 Textarea */
const editorLevel = computed(() => UtilBoard.postEditor(props.board));
/** 에디터 이미지 올리기 — 고급 + 게시판 허용 + 회원(비회원 글쓰기·비회원 글 고치기는 감춘다) + 올리기 함수 */
const imageOn = computed(() => props.upload !== null && UtilBoard.imageUploadOn(props.board, props.guestMode || props.guestRules || props.board.viewer.guest));
/** 에디터 — 이모티콘 고르기 창이 커서 자리에 넣는다 */
const editorRef = ref<{ insertText: (text: string) => void; insertEmoticon: (id: string) => void } | null>(null);
/** 에디터의 이모티콘 그림 찾기(카탈로그) — 고치는 글에 토큰이 있으면 그림으로 보이게 미리 받는다(늦게 오면 에디터가 다시 그린다) */
const catalog = useEmoticonCatalog();
const emoticonSource = catalog.editorSource;
watch(
    () => UtilRichEmoticon.has(props.modelValue.content),
    (has) => {
        if (has) void catalog.ensureLoaded();
    },
    { immediate: true },
);
/** 에디터의 첨부 상태 — 올리는 중·실패한 이미지가 있으면 등록을 막는다 */
const media = ref<RichEditorMediaState>({ images: 0, videos: 0, uploading: 0, failed: 0 });
const mediaProblem = computed(() => (media.value.uploading > 0 ? t('post.imagesUploading') : media.value.failed > 0 ? t('post.imagesFailed') : ''));

const contentLength = computed(() => UtilPost.contentLength(props.modelValue.content, props.modelValue.contentFormat));

const titleProblem = computed(() => UtilPost.titleProblem(props.modelValue.title));
const contentProblem = computed(() => UtilPost.contentProblem(props.modelValue.content, props.modelValue.contentFormat));
const nameProblem = computed(() => (props.guestMode ? UtilPost.guestNameProblem(props.modelValue.guestName, props.board.guestRules) : null));
const passwordProblem = computed(() => (props.guestMode ? UtilPost.guestPasswordProblem(props.modelValue.guestPassword, props.board.guestRules) : null));
const captchaMissing = computed(() => props.guestMode && props.board.guestRules.captchaRequired && !props.modelValue.captcha);

const titleError = computed(() => props.fieldErrors.title || (tried.value ? UtilPostMessages.title(t, titleProblem.value) : ''));
/** 이모티콘 토큰이 한 본문 한도(20개)를 넘었다 — 판정은 서버(`400 VALIDATION`), 화면은 미리 막는다 */
/** 본문 로보티콘 — 저장 직전 서버 검사와 같은 규칙(개수 21개부터 · 가지지 않은 팩)으로 미리 막는다(`useEmoticonBodyCheck` → `UtilEmoticonRule.check`) */
const emoticonCheck = useEmoticonBodyCheck(() => props.modelValue.content);
const emoticonOver = computed(() => emoticonCheck.problem.value !== null);
watch(
    () => UtilRichEmoticon.has(props.modelValue.content),
    (has) => {
        if (has) emoticonCheck.prepare();
    },
    { immediate: true },
);
const contentError = computed(
    () =>
        props.fieldErrors.content ||
        (tried.value ? UtilPostMessages.content(t, contentProblem.value) : '') ||
        (tried.value ? UtilEmoticonMessages.bodyProblem(t, emoticonCheck.problem.value, emoticonCheck.labelOf) : ''),
);
const guestNameError = computed(() => props.fieldErrors['guest.name'] || (tried.value ? UtilPostMessages.guestName(t, nameProblem.value, props.board.guestRules) : ''));
const guestPasswordError = computed(() => props.fieldErrors['guest.password'] || (tried.value ? UtilPostMessages.guestPassword(t, passwordProblem.value, props.board.guestRules) : ''));
const captchaError = computed(() => props.fieldErrors['guest.captchaToken'] || (tried.value && captchaMissing.value ? t('post.captchaRequired') : ''));

const linkWarning = computed(() =>
    UtilPostMessages.links(
        t,
        UtilPost.linkProblem(props.modelValue.content, { guest: props.guestRules, guestRules: props.board.guestRules, newMember: props.board.newMemberRules, allowImages: true, format: props.modelValue.contentFormat }),
        props.board.guestRules,
        props.board.newMemberRules,
    ),
);

/** 지켜야 할 링크 규칙을 미리 한 줄로 — 비회원·새 회원에게만 */
const ruleHint = computed(() => {
    if (props.guestRules) return props.board.guestRules.linkLimit === 0 ? t('post.guestNoLink') : t('post.guestLinkLimit', { n: props.board.guestRules.linkLimit });
    if (props.board.newMemberRules.applies) return t('post.newMemberRule', { links: props.board.newMemberRules.linkLimit, images: props.board.newMemberRules.imageLimit });
    return '';
});

function submit(): void {
    // 1단계에서 엔터로 제출되면 [다음]과 같게
    if (props.step === 1) return void emit('next');
    emit('attempt');
    tried.value = true;
    if (props.submitting) return;
    if (titleProblem.value || contentProblem.value || emoticonOver.value || nameProblem.value || passwordProblem.value || captchaMissing.value || mediaProblem.value) return;
    emit('submit');
}
</script>
