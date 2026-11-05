<template>
    <!-- 댓글 입력 — 새 댓글·답글·고치기가 같이 쓴다. 비회원(댓글 자격 GUEST 게시판)은 이름·비밀번호·자동입력 방지를 함께 받는다. 링크·이미지 규칙은 미리 알리고 판정은 서버.
         본문은 게시판 댓글 에디터 설정(`commentEditor` 유효값)을 따른다: 평문은 Textarea(`TEXT`), 간단은 서식 에디터(`HTML` — 이미지 없음). 글자 수는 보이는 글자 기준 -->
    <form class="flex flex-col gap-2" novalidate @submit.prevent="submit">
        <p v-if="replyToName" class="text-xs text-muted-color">{{ t('comment.replyingTo', { name: replyToName }) }}</p>
        <RichEditor
            v-if="format === 'HTML'"
            ref="editorRef"
            v-model="content"
            level="SIMPLE"
            compact
            :aria-label="ariaLabel || t('comment.inputLabel')"
            :placeholder="placeholder || t('comment.placeholder')"
            :invalid="Boolean(contentError)"
            :disabled="submitting"
            :emoticons="emoticonSource"
            :youtube-links="UtilPost.COMMENT_YOUTUBE_MAX"
        >
            <!-- 이모티콘 고르기 — 이모지는 글자로, 이모티콘은 그림 노드로(저장은 토큰 글자) -->
            <template #tools>
                <EmoticonPicker icon :disabled="submitting" @emoji="editorRef?.insertText($event)" @emoticon="editorRef?.insertEmoticon($event)" />
            </template>
        </RichEditor>
        <Textarea
            v-else
            ref="textareaRef"
            v-model="content"
            :aria-label="ariaLabel || t('comment.inputLabel')"
            :placeholder="placeholder || t('comment.placeholder')"
            :maxlength="maxLength"
            rows="3"
            :invalid="Boolean(contentError)"
            :autofocus="autofocus"
            fluid
            auto-resize
        />
        <!-- 평문 입력칸의 이모티콘은 토큰 글자로 들어간다 — 넣은 것을 그림 칩으로 미리 보인다 -->
        <EmoticonTokenPreview v-if="format !== 'HTML'" :text="content" @remove="removeToken" />
        <p v-if="contentError" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ contentError }}</p>
        <p v-else-if="linkWarning" class="text-xs text-amber-700 dark:text-amber-400">{{ linkWarning }}</p>

        <BoardGuestFields
            v-if="guestMode"
            v-model:name="guestName"
            v-model:password="guestPassword"
            v-model:captcha="captcha"
            :rules="board.guestRules"
            :name-error="nameError"
            :password-error="passwordError"
            :captcha-error="captchaError"
            compact
        />

        <Message v-if="formError" severity="error" variant="simple" size="small" role="alert">{{ formError }}</Message>

        <div class="flex items-center justify-between gap-2">
            <EmoticonPicker v-if="format !== 'HTML'" :disabled="submitting" @emoji="insertAtCursor" @emoticon="insertAtCursor(UtilRichEmoticon.token($event))" />
            <span class="mr-auto text-xs tabular-nums" :class="length > maxLength ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ length.toLocaleString() }} / {{ maxLength.toLocaleString() }}</span>
            <div class="flex gap-2">
                <Button v-if="cancelable" type="button" :label="t('common.cancel')" severity="secondary" variant="text" size="small" @click="$emit('cancel')" />
                <Button type="submit" :label="submitLabel || t('comment.submit')" size="small" :loading="submitting" />
            </div>
        </div>
    </form>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

interface Props {
    board: BoardDetail;
    /** 비회원 칸을 보인다(새 댓글·답글을 비회원이 쓸 때). 고치기에는 없다 */
    guestMode?: boolean;
    /** 비회원 규칙(링크 0개·이미지 불가)을 적용한다 — 비회원이 쓰거나 비회원 댓글을 고칠 때 */
    guestRules?: boolean;
    initialContent?: string;
    /** 고치는 댓글의 형식 — 지금 게시판 댓글 에디터 설정과 다르면 바꿔서 연다 */
    initialFormat?: ContentFormat;
    submitLabel?: string;
    placeholder?: string;
    ariaLabel?: string;
    /** 답글이면 상대 이름 */
    replyToName?: string;
    cancelable?: boolean;
    autofocus?: boolean;
    submitting?: boolean;
    /** 서버 `fields` */
    fieldErrors?: Record<string, string>;
    formError?: string;
    /** 글자 수 한도 — 입장 배지가 붙는 댓글(토론)은 `debate.opinionMax`, 그 밖은 일반 한도 */
    maxLength?: number;
}

const props = withDefaults(defineProps<Props>(), {
    guestMode: false,
    guestRules: false,
    initialContent: '',
    initialFormat: 'TEXT',
    submitLabel: '',
    placeholder: '',
    ariaLabel: '',
    replyToName: '',
    cancelable: false,
    autofocus: false,
    submitting: false,
    fieldErrors: () => ({}),
    formError: '',
    maxLength: UtilPost.COMMENT_MAX,
});

const emit = defineEmits<{
    submit: [value: { content: string; contentFormat: ContentFormat; guest: GuestCredentials | null }];
    cancel: [];
}>();

const { t } = useI18n();
/** 이 폼의 본문 형식 — 게시판 댓글 에디터 설정(폼이 만들어질 때 정해진다. 설정이 바뀌면 부르는 쪽이 폼을 새로 만든다) */
const format: ContentFormat = UtilBoard.commentFormat(props.board);
const content = ref(UtilPost.convertContent(props.initialContent, props.initialFormat, format));
const editorRef = ref<{ focus: () => void; insertText: (text: string) => void; insertEmoticon: (id: string) => void } | null>(null);
const textareaRef = ref<{ $el: HTMLTextAreaElement } | null>(null);
/** 에디터의 이모티콘 그림 찾기(카탈로그) — 고치는 댓글에 토큰이 있으면 그림으로 보이게 미리 받는다 */
const catalog = useEmoticonCatalog();
const emoticonSource = catalog.editorSource;
if (UtilRichEmoticon.has(props.initialContent)) void catalog.ensureLoaded();

/** 평문 입력칸의 커서 자리에 글자(이모지·토큰)를 넣는다 — 고른 글자가 있으면 바꾼다 */
function insertAtCursor(text: string): void {
    const el = textareaRef.value?.$el;
    const value = content.value;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    content.value = value.slice(0, start) + text + value.slice(end);
    nextTick(() => {
        if (!el) return;
        el.focus();
        el.setSelectionRange(start + text.length, start + text.length);
    });
}

/** 미리보기 칩의 ✕ — 본문의 n번째 토큰을 지운다 */
function removeToken(index: number): void {
    content.value = UtilRichEmoticon.removeAt(content.value, index);
}
// 고치기·답글 폼은 열리자마자 입력할 수 있게 — 에디터는 autofocus 속성이 없어 직접 초점을 준다
onMounted(() => {
    if (props.autofocus && format === 'HTML') nextTick(() => editorRef.value?.focus());
});
/** 비밀번호는 이 폼의 메모리에만 — 부르는 쪽이 성공 뒤 `key` 를 바꿔 폼을 새로 만든다 */
const guestName = ref('');
const guestPassword = ref('');
const captcha = ref(false);
const tried = ref(false);

/** 보이는 글자 수(에디터는 태그를 뗀 글자 — 서버 기준) */
const length = computed(() => UtilPost.contentLength(content.value, format));
const contentProblem = computed(() => UtilPost.commentProblem(content.value, format, props.maxLength));
const nameProblem = computed(() => (props.guestMode ? UtilPost.guestNameProblem(guestName.value, props.board.guestRules) : null));
const passwordProblem = computed(() => (props.guestMode ? UtilPost.guestPasswordProblem(guestPassword.value, props.board.guestRules) : null));
const captchaMissing = computed(() => props.guestMode && props.board.guestRules.captchaRequired && !captcha.value);

/** 이모티콘 토큰이 한 본문 한도(20개)를 넘었다 — 판정은 서버(`400 VALIDATION`), 화면은 미리 막는다 */
/** 본문 로보티콘 — 저장 직전 서버 검사와 같은 규칙(개수 21개부터 · 가지지 않은 팩)으로 미리 막는다(`useEmoticonBodyCheck` → `UtilEmoticonRule.check`) */
const emoticonCheck = useEmoticonBodyCheck(() => content.value);
const emoticonOver = computed(() => emoticonCheck.problem.value !== null);
watch(
    () => UtilRichEmoticon.has(content.value),
    (has) => {
        if (has) emoticonCheck.prepare();
    },
    { immediate: true },
);
const contentError = computed(
    () =>
        props.fieldErrors.content ||
        (tried.value && contentProblem.value ? (contentProblem.value === 'REQUIRED' ? t('comment.required') : t('comment.tooLong', { max: props.maxLength.toLocaleString() })) : '') ||
        (tried.value ? UtilEmoticonMessages.bodyProblem(t, emoticonCheck.problem.value, emoticonCheck.labelOf) : ''),
);
const nameError = computed(() => props.fieldErrors['guest.name'] || (tried.value ? UtilPostMessages.guestName(t, nameProblem.value, props.board.guestRules) : ''));
const passwordError = computed(() => props.fieldErrors['guest.password'] || (tried.value ? UtilPostMessages.guestPassword(t, passwordProblem.value, props.board.guestRules) : ''));
const captchaError = computed(() => props.fieldErrors['guest.captchaToken'] || (tried.value && captchaMissing.value ? t('post.captchaRequired') : ''));

const linkWarning = computed(() =>
    UtilPostMessages.links(
        t,
        UtilPost.linkProblem(content.value, { guest: props.guestRules, guestRules: props.board.guestRules, newMember: props.board.newMemberRules, allowImages: false, format }),
        props.board.guestRules,
        props.board.newMemberRules,
    ),
);

function submit(): void {
    tried.value = true;
    if (props.submitting || contentProblem.value || emoticonOver.value || nameProblem.value || passwordProblem.value || captchaMissing.value) return;
    emit('submit', {
        // 에디터 HTML 은 그대로 보낸다(서버가 정화한다), 평문은 앞뒤 공백을 뗀다
        content: format === 'HTML' ? content.value : content.value.trim(),
        contentFormat: format,
        guest: props.guestMode ? { name: guestName.value.trim(), password: guestPassword.value, captchaToken: captcha.value ? UtilPost.PLACEHOLDER_CAPTCHA_TOKEN : '' } : null,
    });
}
</script>
