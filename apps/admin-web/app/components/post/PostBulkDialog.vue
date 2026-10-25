<template>
    <!--
        일괄 처리 대화창 — 고른 글에 숨기기·숨김 해제·삭제를 사유 하나로(숨기기는 「작성자에게 알림」). 상태가 맞지 않아 빼는 글 수를 알린다.
        보내고 나면(`result`) 같은 대화창이 결과 모습으로 바뀐다 — 글마다 처리됨/못 함 + 까닭(아는 코드는 문구, 모르는 코드는 코드 그대로). 서버는 글마다 따로 처리한다.
        열 때마다 사유를 비운다. 서버 호출은 부른 화면이 `submit` 을 받아 한다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="result ? t('post.bulkResultTitle') : t(`post.bulkTitle.${action}`, { n: count })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }">
        <!-- 결과 -->
        <div v-if="result" class="flex flex-col gap-3">
            <p class="m-0 text-sm font-medium">{{ UtilPostMessages.bulkSummary(t, result) }}</p>
            <ul class="m-0 flex max-h-80 list-none flex-col divide-y divide-(--line) overflow-y-auto rounded-lg border border-(--line) p-0">
                <li v-for="row in UtilPost.bulkRows(result)" :key="row.id" class="flex items-start gap-2 px-3 py-2 text-[13px]">
                    <i :class="row.ok ? 'pi pi-check-circle text-green-600 dark:text-green-400' : 'pi pi-times-circle text-red-600 dark:text-red-400'" class="mt-0.5 text-xs" aria-hidden="true" />
                    <div class="flex min-w-0 flex-1 flex-col">
                        <span class="truncate">{{ titles[row.id] ?? `#${row.id}` }}</span>
                        <span class="text-xs text-muted-color">{{ row.ok ? t('post.bulkRowDone') : `${t('post.bulkRowFailed')} · ${UtilPostMessages.bulkCode(t, row.code)}` }}</span>
                    </div>
                </li>
            </ul>
        </div>

        <!-- 사유 -->
        <form v-else :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`post.bulkText.${action}`) }}</p>
            <Message v-if="skipped > 0" severity="warn" size="small" :closable="false">{{ t('post.bulkSkipped', { n: skipped }) }}</Message>

            <PostReasonField v-model="reason" :error="reasonError" :disabled="busy" />

            <div v-if="action === 'HIDE'" class="flex items-start gap-2">
                <Checkbox v-model="notify" binary :input-id="`${formId}-notify`" :disabled="busy" />
                <label :for="`${formId}-notify`" class="flex flex-col text-sm">
                    {{ t('post.notifyAuthor') }}
                    <span class="text-xs text-muted-color">{{ t('post.notifyAuthorHelp') }}</span>
                </label>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button v-if="result" type="button" :label="t('common.close')" @click="open = false" />
            <template v-else>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
                <Button type="submit" :form="formId" :label="t(`post.bulkAction.${action}`, { n: count })" :severity="action === 'UNHIDE' ? undefined : 'danger'" :loading="busy" :disabled="count === 0" />
            </template>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { PostBulkAction, PostBulkResult } from '~/composables/post/api/usePostApi.types';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    action: PostBulkAction;
    /** 보낼 글 수(이 조치를 할 수 있는 글) */
    count: number;
    /** 상태가 맞지 않아 빼는 글 수 */
    skipped?: number;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    fieldError?: string;
    /** 끝난 결과 — 있으면 결과 모습 */
    result?: PostBulkResult | null;
    /** 글 번호 → 제목(결과 줄) */
    titles?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { skipped: 0, busy: false, error: '', fieldError: '', result: null, titles: () => ({}) });
const emit = defineEmits<{ submit: [payload: { reason: string; notify: boolean }] }>();

const { t } = useI18n();
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
    if (props.busy || props.count === 0) return;
    tried.value = true;
    if (UtilPost.reasonProblem(reason.value)) return;
    emit('submit', { reason: reason.value.trim(), notify: notify.value });
}
</script>
