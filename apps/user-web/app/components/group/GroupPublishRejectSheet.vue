<template>
    <!-- 공개 요청 거절 시트(5단계) — 사유는 선택이고 적으면 작성자에게 전달된다. 글자 수 한도는 서버가 준 값(`publishTextMax`, 기본 100자) — 넘으면 거절하기가 꺼진다.
         요청은 보내지 않는다 — `submit`(앞뒤 공백을 뗀 사유, 없으면 빈 글자)을 올리면 부르는 쪽이 보낸다. 열 때마다 빈 입력으로 시작한다 -->
    <AppSheet v-model:visible="open" :title="t('groupVisibility.rejectTitle')" :description="t('groupVisibility.rejectDescription')">
        <form class="mt-3 px-5" novalidate data-testid="group-publish-reject-sheet" @submit.prevent="submit">
            <LabeledField :label="t('groupVisibility.rejectLabel')" :hint="t('group.optional')" :help="t('groupVisibility.rejectHelp')" :error="shownError">
                <template #aside>
                    <small class="text-xs tabular-nums" :class="tooLong ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: length, max }) }}</small>
                </template>
                <template #default="{ id: fieldId, describedBy, invalid }">
                    <Textarea :id="fieldId" v-model="reason" name="reason" rows="3" fluid class="resize-none" :invalid="invalid" :disabled="busy" :aria-describedby="describedBy" data-testid="group-publish-reject-reason" />
                </template>
            </LabeledField>
            <div class="mt-4 flex gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="busy" @click="open = false" />
                <Button type="submit" :label="t('groupVisibility.rejectConfirm')" severity="danger" class="flex-1" :loading="busy" :disabled="tooLong || busy" data-testid="group-publish-reject-confirm" />
            </div>
        </form>
    </AppSheet>
</template>

<script setup lang="ts">
import UtilGroupVisibility from '~/utils/util-group-visibility';

interface Props {
    /** 사유 글자 수 한도 — 서버가 준 값. 안 주면 기본 100 */
    max?: number;
    busy?: boolean;
    /** 서버가 거절한 까닭(`fields.reason` 또는 서버 문구) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), {
    max: 100, // = UtilGroupVisibility.TEXT_MAX(기본값 자리에는 상수 글자만 둔다)
    busy: false,
    error: '',
});

const emit = defineEmits<{ submit: [reason: string] }>();

const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const reason = ref('');
const length = computed(() => UtilGroupVisibility.length(reason.value));
const tooLong = computed(() => UtilGroupVisibility.tooLong(reason.value, props.max));
const shownError = computed(() => (tooLong.value ? t('groupVisibility.tooLong', { max: props.max }) : props.error));

watch(open, (visible) => {
    if (visible) reason.value = '';
});

function submit(): void {
    if (tooLong.value || props.busy) return;
    emit('submit', reason.value.trim());
}
</script>
