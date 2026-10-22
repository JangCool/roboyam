<template>
    <!--
        글쓰기·고치기 폼 — 글쓰기(`/write`)와 고치기(`/posts/[id]/edit`)가 같이 쓴다. 그리기·화면 검사만 하고 저장(서버 호출)은 페이지가 한다.
        칸: 게시판(글쓰기만) · 말머리(운영진 전용은 운영진만) · 제목 1~100 · 본문 10~200,000(목업은 Textarea — 에디터는 후속) · 이미지 올리기 자리 · 19금(허용 게시판만) · 비회원 이름·비밀번호·자동입력 방지.
        화면 검사는 미리 알려 주는 용도이고 판정은 서버다(서버 `fields` 가 있으면 그것을 칸 아래에 먼저 보인다).
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
                <span class="text-sm font-medium">{{ t('post.boardLabel') }}</span>
                <span class="text-sm">{{ localizedName(board.name, board.nameEn) }}</span>
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
                        @update:model-value="patch({ prefixId: ($event as string | null) ?? null })"
                    />
                </template>
            </LabeledField>
        </div>

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

        <!-- 본문 — 목업은 평문 Textarea 다. 에디터(서식·이미지 붙여넣기)는 후속 -->
        <LabeledField :label="t('post.contentLabel')" :error="contentError">
            <template #default="{ id, describedBy, invalid }">
                <Textarea
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

        <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <!-- 이미지 올리기 자리(후속) — 에디터·파일 올리기가 붙을 때 동작한다. 게시판 설정(imageUploadAllowed)과 비회원 보안 설정(guestImageUploadAllowed)으로 보일지 정한다 -->
            <Button v-if="imageAllowed" type="button" icon="pi pi-image" :label="t('post.imageUpload')" severity="secondary" variant="outlined" size="small" disabled :title="t('post.imageUploadLater')" />
            <label v-if="board.allowAdult" class="inline-flex cursor-pointer items-center gap-2 text-sm">
                <Checkbox :model-value="modelValue.adult" binary :input-id="adultId" @update:model-value="patch({ adult: Boolean($event) })" />
                <span>{{ t('post.adultLabel') }}</span>
            </label>
        </div>
        <p v-if="fieldErrors.adult" role="alert" class="-mt-2 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.adult }}</p>

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

        <Message v-if="formError" severity="error" variant="simple" role="alert">{{ formError }}</Message>

        <div class="flex justify-end gap-2 border-t border-surface pt-4">
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="$emit('cancel')" />
            <Button type="submit" :label="submitLabel" icon="pi pi-check" :loading="submitting" />
        </div>
    </form>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostBoardOption, PostFormValues } from '~/components/board/PostEditorForm.types';
import UtilBoard from '~/utils/util-board';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

interface Props {
    board: BoardDetail;
    modelValue: PostFormValues;
    submitLabel: string;
    /** 게시판 고르기(글쓰기만) — 비면 게시판 이름만 보인다 */
    boardOptions?: PostBoardOption[];
    boardSlug?: string;
    /** 비회원 이름·비밀번호·자동입력 방지 칸을 보인다(비회원 글쓰기) */
    guestMode?: boolean;
    /** 비회원 규칙(링크 0개·이미지 불가)을 적용한다 — 비회원 글쓰기와 비회원 글 고치기 */
    guestRules?: boolean;
    /** 서버 `400 VALIDATION` 의 `fields` */
    fieldErrors?: Record<string, string>;
    /** 칸에 붙지 않는 서버 문구 */
    formError?: string;
    submitting?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    boardOptions: () => [],
    boardSlug: '',
    guestMode: false,
    guestRules: false,
    fieldErrors: () => ({}),
    formError: '',
    submitting: false,
});

const emit = defineEmits<{
    'update:modelValue': [value: PostFormValues];
    'update:boardSlug': [value: string];
    submit: [];
    cancel: [];
}>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const adultId = useId();
/** 한 번 [등록]을 누른 뒤부터 화면 검사 문구를 보인다 */
const tried = ref(false);

function patch(change: Partial<PostFormValues>): void {
    emit('update:modelValue', { ...props.modelValue, ...change });
}

/** 고를 수 있는 말머리 — 운영진 전용은 운영진에게만 */
const prefixOptions = computed(() => [
    { value: null as string | null, label: t('post.prefixNone') },
    ...UtilBoard.selectablePrefixes(props.board, props.board.viewer.staff).map((p) => ({ value: p.id as string | null, label: p.name })),
]);

const contentLength = computed(() => props.modelValue.content.trim().length);
const imageAllowed = computed(() => props.board.imageUploadAllowed && (!props.guestRules || props.board.guestRules.imageUploadAllowed));

const titleProblem = computed(() => UtilPost.titleProblem(props.modelValue.title));
const contentProblem = computed(() => UtilPost.contentProblem(props.modelValue.content));
const nameProblem = computed(() => (props.guestMode ? UtilPost.guestNameProblem(props.modelValue.guestName, props.board.guestRules) : null));
const passwordProblem = computed(() => (props.guestMode ? UtilPost.guestPasswordProblem(props.modelValue.guestPassword, props.board.guestRules) : null));
const captchaMissing = computed(() => props.guestMode && props.board.guestRules.captchaRequired && !props.modelValue.captcha);

const titleError = computed(() => props.fieldErrors.title || (tried.value ? UtilPostMessages.title(t, titleProblem.value) : ''));
const contentError = computed(() => props.fieldErrors.content || (tried.value ? UtilPostMessages.content(t, contentProblem.value) : ''));
const guestNameError = computed(() => props.fieldErrors['guest.name'] || (tried.value ? UtilPostMessages.guestName(t, nameProblem.value, props.board.guestRules) : ''));
const guestPasswordError = computed(() => props.fieldErrors['guest.password'] || (tried.value ? UtilPostMessages.guestPassword(t, passwordProblem.value, props.board.guestRules) : ''));
const captchaError = computed(() => props.fieldErrors['guest.captchaToken'] || (tried.value && captchaMissing.value ? t('post.captchaRequired') : ''));

const linkWarning = computed(() =>
    UtilPostMessages.links(
        t,
        UtilPost.linkProblem(props.modelValue.content, { guest: props.guestRules, guestRules: props.board.guestRules, newMember: props.board.newMemberRules, allowImages: true }),
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
    tried.value = true;
    if (props.submitting) return;
    if (titleProblem.value || contentProblem.value || nameProblem.value || passwordProblem.value || captchaMissing.value) return;
    emit('submit');
}
</script>
