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
        />
        <Textarea
            v-else
            v-model="content"
            :aria-label="ariaLabel || t('comment.inputLabel')"
            :placeholder="placeholder || t('comment.placeholder')"
            :maxlength="UtilPost.COMMENT_MAX"
            rows="3"
            :invalid="Boolean(contentError)"
            :autofocus="autofocus"
            fluid
            auto-resize
        />
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
            <span class="text-xs tabular-nums" :class="length > UtilPost.COMMENT_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ length.toLocaleString() }} / {{ UtilPost.COMMENT_MAX.toLocaleString() }}</span>
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
});

const emit = defineEmits<{
    submit: [value: { content: string; contentFormat: ContentFormat; guest: GuestCredentials | null }];
    cancel: [];
}>();

const { t } = useI18n();
/** 이 폼의 본문 형식 — 게시판 댓글 에디터 설정(폼이 만들어질 때 정해진다. 설정이 바뀌면 부르는 쪽이 폼을 새로 만든다) */
const format: ContentFormat = UtilBoard.commentFormat(props.board);
const content = ref(UtilPost.convertContent(props.initialContent, props.initialFormat, format));
const editorRef = ref<{ focus: () => void } | null>(null);
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
const contentProblem = computed(() => UtilPost.commentProblem(content.value, format));
const nameProblem = computed(() => (props.guestMode ? UtilPost.guestNameProblem(guestName.value, props.board.guestRules) : null));
const passwordProblem = computed(() => (props.guestMode ? UtilPost.guestPasswordProblem(guestPassword.value, props.board.guestRules) : null));
const captchaMissing = computed(() => props.guestMode && props.board.guestRules.captchaRequired && !captcha.value);

const contentError = computed(
    () => props.fieldErrors.content || (tried.value && contentProblem.value ? (contentProblem.value === 'REQUIRED' ? t('comment.required') : t('comment.tooLong', { max: UtilPost.COMMENT_MAX.toLocaleString() })) : ''),
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
    if (props.submitting || contentProblem.value || nameProblem.value || passwordProblem.value || captchaMissing.value) return;
    emit('submit', {
        // 에디터 HTML 은 그대로 보낸다(서버가 정화한다), 평문은 앞뒤 공백을 뗀다
        content: format === 'HTML' ? content.value : content.value.trim(),
        contentFormat: format,
        guest: props.guestMode ? { name: guestName.value.trim(), password: guestPassword.value, captchaToken: captcha.value ? UtilPost.PLACEHOLDER_CAPTCHA_TOKEN : '' } : null,
    });
}
</script>
