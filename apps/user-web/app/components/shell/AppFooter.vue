<template>
    <!-- 데스크톱 푸터 — 약관·개인정보처리방침 링크 + 판매 스위치가 켜져 있으면 통신판매업자 정보(전자상거래법 제10조 ① — commerce-groupbuy 결정 56). 약관 본문 페이지는 아직 없어 새 탭 링크다 -->
    <footer class="border-t border-surface bg-surface-100 dark:bg-surface-900">
        <div class="mx-auto flex max-w-[var(--content-max)] items-center gap-4 px-4 py-4 text-xs text-muted-color">
            <b class="font-semibold text-surface-600 dark:text-surface-300">{{ siteLabel }}</b>
            <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
            <a href="/privacy" target="_blank" rel="noopener" class="font-semibold hover:underline">{{ t('common.privacy') }}</a>
        </div>
        <div v-if="shownBusiness" class="mx-auto max-w-[var(--content-max)] px-4 pb-4">
            <CommerceBusinessInfo :business="shownBusiness" variant="footer" />
        </div>
    </footer>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { siteLabel } = useSiteName();
const commerce = useCommerceInfo();
const { shownBusiness } = commerce;
onMounted(() => void commerce.ensureLoaded());
</script>
