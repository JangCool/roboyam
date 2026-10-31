<template>
    <!--
        첨부 하나 고르기 — PrimeVue `FileUpload`(basic, 직접 올림). 형식·크기(10MB)는 여기서 미리 걸러 알려 주고, 올리기(API)는 부르는 쪽이 `select` 를 받아 한다(`components.md` — 부품은 서버를 부르지 않는다).
        고른 파일은 이름·크기 줄 + [삭제]. 사업자등록증 사본(PDF·JPG·PNG)·광고 이미지가 같이 쓴다.
    -->
    <div class="flex flex-col gap-2" data-testid="business-file-picker">
        <div v-if="file" class="flex items-center gap-3 rounded-lg border border-hairline p-2.5">
            <img v-if="file.previewUrl" :src="file.previewUrl" alt="" class="size-12 shrink-0 rounded object-cover" />
            <span v-else class="grid size-12 shrink-0 place-items-center rounded bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-file-pdf text-lg" /></span>
            <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium">{{ file.name }}</span>
                <span class="block text-xs text-muted-color">{{ UtilBusinessMessages.fileSize(file.size) }}</span>
            </span>
            <Button type="button" icon="pi pi-trash" severity="secondary" variant="text" rounded :aria-label="t('common.delete')" :disabled="busy" @click="emit('remove')" />
        </div>
        <FileUpload
            v-else
            ref="uploader"
            mode="basic"
            custom-upload
            :auto="false"
            :accept="accept"
            :choose-label="chooseLabel"
            choose-icon="pi pi-upload"
            :disabled="busy"
            :pt="{ input: { id: inputId, 'aria-describedby': describedBy } }"
            @select="onSelect"
        />
        <p v-if="busy" class="m-0 flex items-center gap-1.5 text-xs text-muted-color" role="status"><i class="pi pi-spin pi-spinner text-[0.6875rem]" aria-hidden="true" />{{ t('common.wait') }}</p>
        <p v-if="localError" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ localError }}</p>
    </div>
</template>

<script setup lang="ts">
import type { BusinessFile } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

interface Props {
    file: Pick<BusinessFile, 'name' | 'size' | 'previewUrl'> | null;
    /** 받는 형식(MIME 쉼표 목록) */
    accept: string;
    chooseLabel: string;
    /** 올리는 중 */
    busy?: boolean;
    inputId?: string;
    describedBy?: string;
    /** 형식이 맞지 않을 때 문구 */
    typeError: string;
    /** 크기가 넘칠 때 문구 */
    sizeError: string;
}

const props = withDefaults(defineProps<Props>(), { busy: false, inputId: undefined, describedBy: undefined });
const emit = defineEmits<{ select: [file: File]; remove: [] }>();

const { t } = useI18n();
const localError = ref('');
const uploader = ref<{ clear: () => void } | null>(null);

function onSelect(event: { files: File[] }): void {
    localError.value = '';
    const file = event.files?.[0];
    uploader.value?.clear();
    if (!file) return;
    const types = props.accept.split(',').map((s) => s.trim());
    if (!types.includes(file.type)) {
        localError.value = props.typeError;
        return;
    }
    if (file.size > UtilBusiness.FILE_MAX_BYTES) {
        localError.value = props.sizeError;
        return;
    }
    emit('select', file);
}
</script>
