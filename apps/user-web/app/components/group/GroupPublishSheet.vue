<template>
    <!-- 소모임 글 공개 확인 시트(5단계) — 개인정보 확인 체크를 해야 확인 단추가 켜진다. 공개하기(승인)·직접 공개하기·다시 공개하기·(옛) 전체 공개로 전환이 같은 시트에 다른 문구를 넘긴다.
         요청은 보내지 않는다 — `confirm` 을 올리면 부르는 쪽(글 상세 패널·공개 요청 처리 화면)이 보낸다. 열 때마다 체크는 해제로 시작한다 -->
    <AppSheet v-model:visible="open" :title="copy.title" :description="copy.text">
        <div class="px-5 pt-4" data-testid="group-publish-sheet">
            <div class="flex items-start gap-2.5">
                <Checkbox v-model="acknowledged" binary :input-id="checkId" :disabled="busy" data-testid="group-publish-ack" />
                <label :for="checkId" class="cursor-pointer text-sm leading-snug">
                    {{ copy.ackLabel }}
                    <small v-if="ackHelp" class="mt-0.5 block text-xs text-muted-color">{{ ackHelp }}</small>
                </label>
            </div>
            <p v-if="error" role="alert" class="mt-3 text-sm text-red-600 dark:text-red-400" data-testid="group-publish-error">{{ error }}</p>
            <div class="mt-5 flex gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="busy" @click="open = false" />
                <Button type="button" :label="copy.confirmLabel" class="flex-1" :loading="busy" :disabled="!acknowledged || busy" data-testid="group-publish-confirm" @click="submit" />
            </div>
        </div>
    </AppSheet>
</template>

<script setup lang="ts">
import type { VisibilitySheetCopy } from '~/utils/util-group-visibility-messages';

interface Props {
    /** 제목·설명·체크 문구·확인 단추 글자(`UtilGroupVisibilityMessages.publishSheet` 등) */
    copy: VisibilitySheetCopy;
    /** 체크 아래 작은 설명 — 없으면 그리지 않는다 */
    ackHelp?: string;
    /** 요청이 가는 중 — 입력과 단추를 막는다(두 번 눌러도 요청은 한 번) */
    busy?: boolean;
    /** 실패한 까닭(서버 문구) — 있으면 시트 안에 알린다 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), {
    ackHelp: '',
    busy: false,
    error: '',
});

const emit = defineEmits<{ confirm: [] }>();

const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const checkId = useId();
const acknowledged = ref(false);

watch(open, (visible) => {
    if (visible) acknowledged.value = false;
});

function submit(): void {
    if (!acknowledged.value || props.busy) return;
    emit('confirm');
}
</script>
