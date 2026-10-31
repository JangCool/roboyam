<template>
    <!--
        계약서 사본(수동 결제 근거 — ★-2) — 신청 건 입금 확인 패널과 광고 수동 등록이 같이 쓴다.
        PDF·JPG·PNG, 한 장 10MB, 여러 장. 원본은 지우지 않는다 — [바꾸기] 는 새 파일을 더하고 옛 파일에 「대체됨」을 남긴다.
        열람은 권한(`ad.manage` 이상) + 기록(안내 한 줄) — `viewable` 이면 줄마다 [보기](`view`, 부모가 `data:` 주소를 받아 연다).
        서버는 부르지 않는다 — 고른 파일은 `upload`·`replace` 로 올린다(신청 건 화면은 한 장씩 나눠 올린다).
    -->
    <div class="flex flex-col gap-2">
        <ul v-if="files.length" class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
            <li v-for="f in files" :key="f.id" class="flex items-center gap-3 px-3 py-2 text-[13px]" :class="f.replacedAt ? 'opacity-60' : ''">
                <i :class="f.mimeType === 'application/pdf' ? 'pi pi-file-pdf' : 'pi pi-image'" class="text-muted-color" aria-hidden="true" />
                <div class="flex min-w-0 flex-1 flex-col">
                    <span class="inline-flex min-w-0 items-center gap-2">
                        <span class="truncate" :class="f.replacedAt ? 'line-through' : 'font-medium'">{{ f.fileName }}</span>
                        <Tag v-if="f.replacedAt" :value="t('bizApplication.contract.replaced')" severity="secondary" />
                    </span>
                    <span class="text-[11px] text-muted-color">{{ UtilBizApplication.fileSize(f.size) }} · {{ time.shortDateTime(f.uploadedAt) }} · {{ f.uploadedBy }}</span>
                </div>
                <Button v-if="viewable" type="button" :label="t('bizApplication.contract.view')" icon="pi pi-eye" size="small" severity="secondary" variant="text" :disabled="busy" @click="emit('view', f.id)" />
                <Button v-if="editable && !f.replacedAt" type="button" :label="t('bizApplication.contract.replace')" icon="pi pi-sync" size="small" severity="secondary" variant="text" :disabled="busy" @click="pickReplace(f.id)" />
            </li>
        </ul>
        <p v-else class="m-0 flex items-center gap-1.5 text-[13px] text-amber-700 dark:text-amber-400"><i class="pi pi-exclamation-triangle" aria-hidden="true" />{{ t('bizApplication.contract.none') }}</p>

        <div v-if="editable" class="flex flex-wrap items-center gap-2">
            <FileUpload
                ref="uploader"
                mode="basic"
                custom-upload
                auto
                multiple
                :accept="UtilBizApplication.CONTRACT_ACCEPT"
                :choose-label="t('bizApplication.contract.add')"
                choose-icon="pi pi-upload"
                :invalid-file-type-message="t('bizApplication.contract.badType')"
                :disabled="busy"
                :choose-button-props="{ size: 'small', severity: 'secondary', variant: 'outlined' }"
                @uploader="onUpload"
            />
            <small class="text-xs text-muted-color">{{ t('bizApplication.contract.rule') }}</small>
            <input ref="replaceInput" type="file" class="hidden" tabindex="-1" :accept="UtilBizApplication.CONTRACT_ACCEPT" :aria-label="t('bizApplication.contract.replace')" @change="onReplace" />
        </div>
        <Message v-if="problem" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ problem }}</Message>
        <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-eye mt-0.5 text-xs" aria-hidden="true" />{{ t('bizApplication.contract.viewLog') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { FileUploadUploaderEvent } from 'primevue/fileupload';
import type { BizContractFile } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    files: BizContractFile[];
    editable?: boolean;
    /** 줄마다 [보기] — 누르면 `view`(파일 id) */
    viewable?: boolean;
    busy?: boolean;
    /** 서버가 준 파일 오류 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { editable: false, viewable: false, busy: false, error: '' });
const emit = defineEmits<{ upload: [files: File[]]; replace: [body: { id: string; file: File }]; view: [id: string] }>();

const { t } = useI18n();
const time = useDisplayTime();
const uploader = ref<{ clear: () => void } | null>(null);
const replaceInput = ref<HTMLInputElement | null>(null);
const replaceId = ref<string | null>(null);
const localProblem = ref('');

const problem = computed(() => localProblem.value || props.error);

/** 고른 파일 검사 — 형식·크기가 맞지 않으면 첫 문제를 보이고 올리지 않는다 */
function check(files: File[]): boolean {
    for (const f of files) {
        const p = UtilBizApplication.contractProblem(f);
        if (p) {
            localProblem.value = p === 'SIZE' ? t('bizApplication.contract.tooLarge', { name: f.name }) : t('bizApplication.contract.badType');
            return false;
        }
    }
    localProblem.value = '';
    return true;
}

function onUpload(event: FileUploadUploaderEvent): void {
    const files = Array.isArray(event.files) ? event.files : [event.files];
    uploader.value?.clear();
    if (files.length && check(files)) emit('upload', files);
}

function pickReplace(id: string): void {
    replaceId.value = id;
    replaceInput.value?.click();
}

function onReplace(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file && replaceId.value && check([file])) emit('replace', { id: replaceId.value, file });
}
</script>
