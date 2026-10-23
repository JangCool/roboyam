<template>
    <!--
        메일 미리보기 — 서버(목업)가 견본 값을 채우고 레이아웃을 씌운 문서 HTML 을 **격리된 iframe**(`srcdoc` + 빈 `sandbox`: 스크립트·같은 출처·폼·팝업 모두 막음)으로 보인다.
        관리 화면 DOM 에 `v-html` 로 넣지 않는다 — 메일 HTML 의 스타일·링크가 화면에 새지 않고, 운영자가 넣은 글이 화면 코드로 실행되지 않게 한다.
        메일 템플릿 편집 화면(`/settings/mail-templates/[id]`)과 레이아웃 편집 화면(`/settings/mail-layouts/[id]`)이 같이 쓴다. 받는 일(서버 호출)은 부르는 쪽이 한다.
    -->
    <div class="flex flex-col gap-2">
        <p v-if="subject" class="m-0 flex min-w-0 items-center gap-2 text-[13px]">
            <span class="shrink-0 text-muted-color">{{ t('mailTemplate.previewSubject') }}</span>
            <span class="min-w-0 truncate font-medium" :title="subject">{{ subject }}</span>
        </p>
        <div class="relative overflow-hidden rounded-lg border border-(--line) bg-surface-100 dark:bg-surface-800">
            <iframe v-if="html" :srcdoc="html" sandbox="" referrerpolicy="no-referrer" :title="t('mailTemplate.previewFrameTitle')" class="block w-full border-0 bg-white" :style="{ height }" />
            <div v-else class="flex items-center justify-center text-xs text-muted-color" :style="{ height }">
                <span v-if="!loading">{{ error || t('mailTemplate.previewEmpty') }}</span>
            </div>
            <div v-if="loading" class="absolute inset-x-0 top-0 h-0.5 overflow-hidden" role="status" :aria-label="t('mailTemplate.previewLoading')">
                <ProgressBar mode="indeterminate" style="height: 2px" />
            </div>
        </div>
        <p v-if="html && error" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
        <p class="m-0 text-[11px] text-muted-color">{{ t('mailTemplate.previewNote') }}</p>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 미리보기 제목(머리말 포함) */
    subject?: string;
    /** 문서 HTML — iframe `srcdoc` 로만 보인다 */
    html?: string;
    loading?: boolean;
    /** 미리보기를 받지 못한 까닭(서버 문구) */
    error?: string;
    /** 틀 높이(CSS 길이) */
    height?: string;
}

withDefaults(defineProps<Props>(), { subject: '', html: '', loading: false, error: '', height: '26rem' });

const { t } = useI18n();
</script>
