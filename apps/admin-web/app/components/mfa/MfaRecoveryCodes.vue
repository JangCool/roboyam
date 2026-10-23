<template>
    <!--
        복구 코드 10개 보이기 — 2열 글자판 + [복사] · [내려받기(.txt)] + (선택) 「안전한 곳에 저장했어요」 체크.
        등록 확정 직후와 「복구 코드 새로 만들기」 뒤에만 보인다(서버는 해시만 들고 있어 다시 보여 줄 수 없다). 코드는 이 화면 메모리에만 있고 로그·저장소에 남기지 않는다.
        내려받기는 브라우저에서 Blob 으로 만든다(서버를 부르지 않는다).
    -->
    <div class="flex flex-col gap-3">
        <ol class="m-0 grid list-none grid-cols-2 gap-x-4 gap-y-1.5 rounded-lg border border-(--line) bg-surface-50 p-3 font-mono text-sm tabular-nums tracking-wider dark:bg-surface-950" :aria-label="t('mfa.recoveryListLabel')">
            <li v-for="(code, index) in codes" :key="code" class="flex items-center gap-2">
                <span class="w-5 text-right text-xs text-muted-color" aria-hidden="true">{{ index + 1 }}.</span>
                <span class="select-all">{{ code }}</span>
            </li>
        </ol>

        <div class="flex flex-wrap gap-2">
            <Button type="button" :label="t('mfa.recoveryCopy')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" @click="copy" />
            <Button type="button" :label="t('mfa.recoveryDownload')" icon="pi pi-download" size="small" severity="secondary" variant="outlined" @click="download" />
        </div>

        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('mfa.recoveryNote') }}</p>

        <div v-if="confirmable" class="flex items-center gap-2">
            <Checkbox v-model="saved" :input-id="checkId" binary />
            <label :for="checkId" class="text-sm">{{ t('mfa.recoverySaved') }}</label>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilMfa from '~/utils/util-mfa';

interface Props {
    codes: string[];
    /** 내려받기 파일 첫 줄에 넣을 계정(이메일) */
    account?: string;
    /** 「안전한 곳에 저장했어요」 체크를 보인다(`v-model:saved`) */
    confirmable?: boolean;
}

const props = withDefaults(defineProps<Props>(), { account: '', confirmable: false });
const saved = defineModel<boolean>('saved', { default: false });

const { t } = useI18n();
const toast = useAppToast();
const { siteLabel } = useAdminAuth();
const checkId = useId();

async function copy(): Promise<void> {
    try {
        await navigator.clipboard.writeText(props.codes.join('\n'));
        toast.success(t('mfa.recoveryCopied'));
    } catch {
        toast.error(t('mfa.copyFailed'), 6000);
    }
}

function download(): void {
    const heading = t('mfa.recoveryFileHeading', { site: siteLabel.value, account: props.account || '-' });
    const text = UtilMfa.recoveryFileText(heading, props.codes, t('mfa.recoveryFileNote'));
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = UtilMfa.recoveryFileName(siteLabel.value);
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
</script>
