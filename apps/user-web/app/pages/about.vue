<template>
    <div>
        <!--
            사이트 소개(`/about`) — 기본 레이아웃, 비회원도 본다. 푸터 「사이트 소개」 링크(사이트 정보의 `about` 이 true 일 때)가 여기로 온다.
            모양은 약관 화면(`TermsDocumentView`)과 같은 흰 카드(`gadget-card` data-headless) + `PageTitleHead` + 글 모양(`rich-text`).
            본문은 관리자 설정 › 일반 「사이트 소개」에서 정하고, 서버가 정화한 HTML 만 `v-html` 로 그린다(`GET /api/site/about`). 없으면(`html: null`) 빈 상태.
        -->
        <div class="gadget-card" data-headless data-testid="site-about">
            <PageTitleHead :title="t('siteAbout.title')" divider />

            <div v-if="status === 'loading'" class="mt-4" role="status" :aria-label="t('common.loading')" data-testid="site-about-loading">
                <div class="h-4 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="mt-4 h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="status === 'error'" :title="t('siteAbout.loadError')" :description="errorMessage" retry data-testid="site-about-error" @retry="load" />

            <!-- eslint-disable-next-line vue/no-v-html -->
            <article v-else-if="html" class="rich-text mt-4 text-[0.9375rem]" :aria-label="t('siteAbout.title')" data-testid="site-about-body" v-html="html" />

            <EmptyState v-else icon="pi pi-info-circle" :title="t('siteAbout.empty')" data-testid="site-about-empty" />
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilBoardMessages from '~/utils/util-board-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('siteAbout.title') });

const api = useSiteAboutApi();

const html = ref<string | null>(null);
const status = ref<'loading' | 'error' | 'ready'>('loading');
const errorMessage = ref('');

async function load(): Promise<void> {
    status.value = 'loading';
    errorMessage.value = '';
    try {
        const res = await api.about();
        html.value = res.html?.trim() ? res.html : null;
        status.value = 'ready';
    } catch (e) {
        html.value = null;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

onMounted(() => void load());
</script>
