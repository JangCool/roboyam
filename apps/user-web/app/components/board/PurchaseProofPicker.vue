<template>
    <!--
        구매 인증 사진 고르기(trial-group-contract 「구매 인증」) — 글쓰기 「내 돈으로 샀어요」 아래 · 글 보기의 [구매 인증하기] 대화상자가 같이 쓴다.
        주문 내역·영수증 사진 한 장(JPG·PNG·WEBP 10MB) + 안내(비공개 — 확인하는 사람만 봄, 이름·주소·카드번호는 가리고 찍기, 확인 뒤 보관 기간이 지나면 파기).
        파일은 값으로만 올린다(서버 호출은 쓰는 화면이 한다 — 글 저장 뒤 `POST /api/posts/{id}/purchase-proof`).
    -->
    <div class="flex flex-col gap-2 rounded-lg border border-dashed border-surface p-3" data-testid="purchase-proof-picker">
        <p class="m-0 flex items-center gap-1.5 text-sm font-medium"><i class="pi pi-receipt text-xs text-primary" aria-hidden="true" />{{ t('trial.proofPickTitle') }}</p>
        <ul class="m-0 list-disc pl-4 text-xs leading-relaxed text-muted-color">
            <li>{{ t('trial.proofGuidePrivate') }}</li>
            <li>{{ t('trial.proofGuideMask') }}</li>
            <li>{{ t('trial.proofGuidePurge') }}</li>
        </ul>
        <div class="flex flex-wrap items-center gap-2">
            <input :id="inputId" ref="fileInput" type="file" class="sr-only" :accept="UtilTrial.PHOTO_ACCEPT" :disabled="disabled" @change="onPick" />
            <Button type="button" :label="modelValue ? t('trial.proofPickChange') : t('trial.proofPick')" icon="pi pi-image" size="small" severity="secondary" variant="outlined" :disabled="disabled" @click="fileInput?.click()" />
            <template v-if="modelValue">
                <img v-if="preview" :src="preview" :alt="t('trial.proofPickPreview')" class="size-14 rounded object-cover" />
                <span class="min-w-0 truncate text-xs text-muted-color">{{ modelValue.name }}</span>
                <Button type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" :aria-label="t('trial.proofPickRemove')" :disabled="disabled" @click="clear" />
            </template>
            <span v-else-if="optional" class="text-xs text-muted-color">{{ t('trial.proofPickOptional') }}</span>
        </div>
        <small v-if="shownError" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ shownError }}</small>
    </div>
</template>

<script setup lang="ts">
import UtilTrial from '~/utils/util-trial';

interface Props {
    /** 고른 사진(없으면 null) */
    modelValue: File | null;
    disabled?: boolean;
    /** 서버·화면 오류 */
    error?: string;
    /** 지금 안 올려도 된다는 안내(글쓰기 — 나중에 글 보기에서 올릴 수 있다) */
    optional?: boolean;
}

const props = withDefaults(defineProps<Props>(), { disabled: false, error: '', optional: false });
const emit = defineEmits<{ 'update:modelValue': [file: File | null] }>();

const { t } = useI18n();
const inputId = useId();
const fileInput = ref<HTMLInputElement | null>(null);
const pickError = ref('');
const shownError = computed(() => props.error || pickError.value);

/** 미리보기 주소 — 고른 파일이 바뀌면 앞 주소를 놓는다 */
const preview = ref<string | null>(null);
watch(
    () => props.modelValue,
    (file) => {
        if (preview.value) URL.revokeObjectURL(preview.value);
        preview.value = file ? URL.createObjectURL(file) : null;
    },
    { immediate: true },
);
onBeforeUnmount(() => {
    if (preview.value) URL.revokeObjectURL(preview.value);
});

function onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) return;
    const problem = UtilTrial.photoProblem(file);
    if (problem) {
        pickError.value = problem === 'type' ? t('trial.photoTypeError') : t('trial.photoSizeError');
        return;
    }
    pickError.value = '';
    emit('update:modelValue', file);
}

function clear(): void {
    pickError.value = '';
    emit('update:modelValue', null);
}
</script>
