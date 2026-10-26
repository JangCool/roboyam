<template>
    <!--
        글 하나의 조치 대화창 — 숨기기·숨김 해제·삭제·복구·공지로 지정·고정·고정 해제 · 투표 지우기(서랍의 투표 구획, post-poll-contract). 사유(1~200자) 필수, 숨기기는 「작성자에게 알림 보내기」 체크.
        열 때마다 사유를 비운다(앞 글에 적던 사유가 다음 글에 딸려 가면 안 된다). 서버 호출은 부른 화면이 `submit` 을 받아 한다.
        칸 오류(`fields.reason`)는 사유 칸 아래, 그 밖 거절(`PIN_LIMIT` 등 — 서버 문구 그대로)은 대화창 안 한 줄. 실패해도 쓰던 사유는 그대로 둔다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="kind ? t(`post.dialog.${kind}.title`) : ''" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
        <form v-if="kind" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div v-if="item" class="rounded-lg border border-(--line) px-3 py-2">
                <p class="m-0 text-sm font-medium break-words" :class="item.title === null ? 'italic text-muted-color' : ''">{{ UtilPostMessages.title(t, item) }}</p>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ localizedName(item.board.name, item.board.nameEn) }} · #{{ item.id }}</p>
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`post.dialog.${kind}.text`) }}</p>

            <PostReasonField v-model="reason" :error="reasonError" :disabled="busy" />

            <div v-if="kind === 'HIDE'" class="flex items-start gap-2">
                <Checkbox v-model="notify" binary :input-id="`${formId}-notify`" :disabled="busy" />
                <label :for="`${formId}-notify`" class="flex flex-col text-sm">
                    {{ t('post.notifyAuthor') }}
                    <span class="text-xs text-muted-color">{{ t('post.notifyAuthorHelp') }}</span>
                </label>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button v-if="kind" type="submit" :form="formId" :label="t(`post.action.${kind}`)" :severity="UtilPost.isDanger(kind) ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdminPostItem } from '~/composables/post/api/usePostApi.types';
import UtilPost, { type PostReasonKind } from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 조치(이동 제외) 또는 투표 지우기(`POLL_DELETE`) — 없으면 아무것도 그리지 않는다 */
    kind?: PostReasonKind | null;
    item?: AdminPostItem | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 준 `fields.reason` */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { kind: null, item: null, busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ submit: [payload: { reason: string; notify: boolean }] }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const formId = useId();

const reason = ref('');
const notify = ref(true);
const tried = ref(false);

const reasonError = computed(() => props.fieldError || (tried.value ? UtilPostMessages.reasonProblem(t, UtilPost.reasonProblem(reason.value)) : ''));

watch(open, (isOpen) => {
    if (!isOpen) return;
    reason.value = '';
    notify.value = true;
    tried.value = false;
});

function submit(): void {
    if (props.busy) return;
    tried.value = true;
    if (UtilPost.reasonProblem(reason.value)) return;
    emit('submit', { reason: reason.value.trim(), notify: notify.value });
}
</script>
