<template>
    <!-- 댓글 입력 — 새 댓글·답글·고치기가 같이 쓴다. 비회원(댓글 자격 GUEST 게시판)은 이름·비밀번호·자동입력 방지를 함께 받는다. 링크·이미지 규칙은 미리 알리고 판정은 서버 -->
    <form class="flex flex-col gap-2" novalidate @submit.prevent="submit">
        <p v-if="replyToName" class="text-xs text-muted-color">{{ t('comment.replyingTo', { name: replyToName }) }}</p>
        <Textarea
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
            <span class="text-xs text-muted-color tabular-nums">{{ content.trim().length.toLocaleString() }} / {{ UtilPost.COMMENT_MAX.toLocaleString() }}</span>
            <div class="flex gap-2">
                <Button v-if="cancelable" type="button" :label="t('common.cancel')" severity="secondary" variant="text" size="small" @click="$emit('cancel')" />
                <Button type="submit" :label="submitLabel || t('comment.submit')" size="small" :loading="submitting" />
            </div>
        </div>
    </form>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { GuestCredentials } from '~/composables/board/api/usePostApi.types';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

interface Props {
    board: BoardDetail;
    /** 비회원 칸을 보인다(새 댓글·답글을 비회원이 쓸 때). 고치기에는 없다 */
    guestMode?: boolean;
    /** 비회원 규칙(링크 0개·이미지 불가)을 적용한다 — 비회원이 쓰거나 비회원 댓글을 고칠 때 */
    guestRules?: boolean;
    initialContent?: string;
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
    submit: [value: { content: string; guest: GuestCredentials | null }];
    cancel: [];
}>();

const { t } = useI18n();
const content = ref(props.initialContent);
/** 비밀번호는 이 폼의 메모리에만 — 부르는 쪽이 성공 뒤 `key` 를 바꿔 폼을 새로 만든다 */
const guestName = ref('');
const guestPassword = ref('');
const captcha = ref(false);
const tried = ref(false);

const contentProblem = computed(() => {
    const length = content.value.trim().length;
    return length === 0 ? 'REQUIRED' : length > UtilPost.COMMENT_MAX ? 'LENGTH' : null;
});
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
        UtilPost.linkProblem(content.value, { guest: props.guestRules, guestRules: props.board.guestRules, newMember: props.board.newMemberRules, allowImages: false }),
        props.board.guestRules,
        props.board.newMemberRules,
    ),
);

function submit(): void {
    tried.value = true;
    if (props.submitting || contentProblem.value || nameProblem.value || passwordProblem.value || captchaMissing.value) return;
    emit('submit', {
        content: content.value.trim(),
        guest: props.guestMode ? { name: guestName.value.trim(), password: guestPassword.value, captchaToken: captcha.value ? UtilPost.PLACEHOLDER_CAPTCHA_TOKEN : '' } : null,
    });
}
</script>
