<template>
    <!--
        핫딜 조치 대화창 — 종료(`END`) · 재개(`REOPEN`) · 의심 후보 무시(`DISMISS`). 사유·메모는 **선택**(0~200자, 감사·작성자 알림에 쓴다 — 03-hotdeal 7-1).
        재개는 「의심·종료 요청이 리셋돼요」 안내 + D2 「자동·운영진 종료는 운영진만 재개, 작성자 종료는 24시간 안 작성자가 되돌릴 수 있음」(작성자 기한이 남았으면 그 시각), 마감이 이미 지난 딜이면(`expired`) 「작성자가 마감을 고쳐야 해요」 경고를 더 보인다.
        열 때마다 칸을 비운다. 서버 호출은 부른 화면이 `submit` 을 받아 한다. 칸 오류(`fields.reason`·`fields.note`)는 칸 아래, 그 밖은 대화창 안 한 줄(서버 문구 그대로).
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="kind ? t(`hotdeal.dialog.${kind}.title`) : ''" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
        <form v-if="kind" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div v-if="target" class="rounded-lg border border-(--line) px-3 py-2">
                <p class="m-0 text-sm font-medium break-words">{{ target }}</p>
                <p v-if="subtitle" class="m-0 mt-0.5 text-xs text-muted-color">{{ subtitle }}</p>
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.dialog.${kind}.text`) }}</p>
            <Message v-if="kind === 'REOPEN'" severity="secondary" size="small" :closable="false" icon="pi pi-info-circle">
                <span class="flex flex-col gap-0.5">
                    <span>{{ t('hotdeal.dialog.reopenWho') }}</span>
                    <span v-if="endedBy === 'AUTHOR' && UtilHotdeal.authorCanReopen(authorReopenUntil)" class="font-medium">{{ t('hotdeal.dialog.reopenAuthorCan', { at: time.full(authorReopenUntil) }) }}</span>
                </span>
            </Message>
            <Message v-if="kind === 'REOPEN' && expired" severity="warn" size="small" :closable="false" icon="pi pi-clock">{{ t('hotdeal.dialog.reopenExpired') }}</Message>

            <LabeledField :label="t(kind === 'DISMISS' ? 'hotdeal.dialog.noteLabel' : 'hotdeal.dialog.reasonLabel')" :hint="t('hotdeal.dialog.counter', { n: UtilHotdeal.textLength(text), max: UtilHotdeal.REASON_MAX })" :error="textError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea
                        :id="id"
                        v-model="text"
                        rows="3"
                        auto-resize
                        fluid
                        :placeholder="t(kind === 'DISMISS' ? 'hotdeal.dialog.notePlaceholder' : 'hotdeal.dialog.reasonPlaceholder')"
                        :disabled="busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                    />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button v-if="kind" type="submit" :form="formId" :label="t(`hotdeal.dialog.${kind}.submit`)" :severity="kind === 'END' ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { DealEndedBy } from '~/composables/hotdeal/api/useHotdealApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

export type HotdealReasonKind = 'END' | 'REOPEN' | 'DISMISS';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    kind?: HotdealReasonKind | null;
    /** 대상 한 줄(글 제목 · 회원과 판매처) */
    target?: string;
    subtitle?: string;
    /** 재개할 딜의 마감이 이미 지났다 */
    expired?: boolean;
    /** 재개할 딜을 누가 종료했나 — 작성자 종료면 작성자 되돌리기 기한(D2)을 함께 보인다 */
    endedBy?: DealEndedBy | null;
    authorReopenUntil?: string | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 준 `fields.reason`·`fields.note` */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { kind: null, target: '', subtitle: '', expired: false, endedBy: null, authorReopenUntil: null, busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ submit: [text: string] }>();

const { t } = useI18n();
const time = useDisplayTime();
const formId = useId();

const text = ref('');
const problem = computed(() => UtilHotdeal.textProblem(text.value));
const textError = computed(() => props.fieldError || UtilHotdealMessages.textProblem(t, problem.value, UtilHotdeal.REASON_MAX));

watch(open, (isOpen) => {
    if (isOpen) text.value = '';
});

function submit(): void {
    if (props.busy || problem.value) return;
    emit('submit', text.value.trim());
}
</script>
