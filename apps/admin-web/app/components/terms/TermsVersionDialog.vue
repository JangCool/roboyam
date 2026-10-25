<template>
    <!--
        게시본 본문 보기 — 이력 표의 [보기] 가 연다. 버전·시행일·게시 시각·게시자·상태·변경 요지와 본문(서버가 정화한 HTML).
        [이 본문으로 초안 만들기] 는 본문을 초안 편집기에 넣는다(`copy` — 저장은 화면의 [초안 저장]). 서버를 부르지 않는다 — 본문은 화면이 받아 넘긴다.
        ⚠ 본문은 서버 정화본(`p br strong em u s a ul ol li blockquote h2 h3 hr`)만 `v-html` 로 그린다 — 다른 글자(운영자 이름·변경 요지)는 글자로 따로 그린다.
    -->
    <Dialog v-model:visible="visible" modal :draggable="false" :header="header" :style="{ width: 'min(48rem, calc(100vw - 2rem))' }">
        <div v-if="loading" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton height="1rem" width="50%" />
            <Skeleton height="12rem" />
        </div>
        <ErrorState v-else-if="error" :title="t('terms.versionLoadFailed')" :description="error" retry @retry="emit('retry')" />
        <div v-else-if="detail" class="flex flex-col gap-3">
            <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
                <dt class="text-muted-color">{{ t('terms.colStatus') }}</dt>
                <dd class="m-0"><Tag class="terms-tag" :value="t(`terms.status.${detail.status}`)" :severity="UtilTerms.statusSeverity(detail.status)" /></dd>
                <dt class="text-muted-color">{{ t('terms.effectiveDate') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.serviceDate(detail.effectiveDate) }}</dd>
                <dt class="text-muted-color">{{ t('terms.colPublishedAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(detail.publishedAt) }} · {{ detail.publishedBy ?? t('terms.unknownOperator') }}</dd>
                <template v-if="detail.changeNote">
                    <dt class="text-muted-color">{{ t('terms.changeNote') }}</dt>
                    <dd class="m-0 whitespace-pre-line break-words">{{ detail.changeNote }}</dd>
                </template>
            </dl>
            <div class="max-h-[60vh] overflow-y-auto rounded-lg border border-surface p-4">
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div class="rich-text text-sm" v-html="detail.body" />
            </div>
        </div>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="visible = false" />
            <Button v-if="canCopy && detail" type="button" :label="t('terms.copyToDraft')" icon="pi pi-copy" severity="secondary" variant="outlined" @click="emit('copy', detail)" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { TermsVersionDetail } from '~/composables/terms/api/useTermsApi.types';
import UtilTerms from '~/utils/util-terms';

interface Props {
    /** 종류 이름(이용약관·개인정보처리방침) */
    kindLabel: string;
    /** 받은 본문 — 받는 중이거나 못 받았으면 null */
    detail?: TermsVersionDetail | null;
    loading?: boolean;
    /** 못 받은 까닭(서버 문구) */
    error?: string;
    /** [이 본문으로 초안 만들기] 를 보일지(`terms.manage`) */
    canCopy?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    detail: null,
    loading: false,
    error: '',
    canCopy: false,
});

const visible = defineModel<boolean>('visible', { required: true });
const emit = defineEmits<{ copy: [detail: TermsVersionDetail]; retry: [] }>();

const { t } = useI18n();
const time = useDisplayTime();

const header = computed(() => (props.detail ? t('terms.versionTitle', { name: props.kindLabel, version: props.detail.version }) : props.kindLabel));
</script>

<style scoped>
/* 뱃지 글자 상하 중앙(`components.md` 2-4) */
.terms-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.terms-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
