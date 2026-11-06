<template>
    <!-- 데스크톱 푸터 — 약관·개인정보처리방침 링크 + 판매 스위치가 켜져 있으면 통신판매업자 정보(전자상거래법 제10조 ① — commerce-groupbuy 결정 56). 약관 본문 페이지는 아직 없어 새 탭 링크다.
         첫 줄: 사이트 이름 | 링크들 · 그 아래 가는 선으로 나눈 사업자 정보(뜻 묶음별 줄 — `CommerceBusinessInfo` footer) -->
    <footer class="border-t border-surface bg-surface-100 dark:bg-surface-900">
        <div class="mx-auto max-w-[var(--content-max)] px-4 py-5 text-xs text-muted-color">
            <div class="flex items-center gap-3">
                <b class="font-semibold text-surface-700 dark:text-surface-200">{{ siteLabel }}</b>
                <span aria-hidden="true" class="h-3 w-px bg-surface-300 dark:bg-surface-700" />
                <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
                <a href="/privacy" target="_blank" rel="noopener" class="font-semibold text-surface-700 hover:underline dark:text-surface-200">{{ t('common.privacy') }}</a>
            </div>
            <div v-if="shownBusiness" class="mt-3 border-t border-surface pt-3">
                <CommerceBusinessInfo :business="shownBusiness" variant="footer" />
            </div>
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
