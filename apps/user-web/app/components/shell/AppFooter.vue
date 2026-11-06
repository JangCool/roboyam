<template>
    <!-- 데스크톱 푸터 — 약관·개인정보처리방침 링크 + 판매 스위치가 켜져 있으면 통신판매업자 정보(전자상거래법 제10조 ① — commerce-groupbuy 결정 56). 약관 본문 페이지는 아직 없어 새 탭 링크다.
         두 단: 왼쪽 = 사이트 이름 · 링크 · ©, 오른쪽 = 사업자 정보 표(`CommerceBusinessInfo` footer). 사업자 정보가 없으면 왼쪽 단만 -->
    <footer class="border-t border-surface bg-surface-100 dark:bg-surface-900">
        <div class="mx-auto grid max-w-[var(--content-max)] grid-cols-[10rem_minmax(0,1fr)] gap-x-8 px-4 py-6 text-xs text-muted-color">
            <div class="flex flex-col items-start gap-1">
                <b class="mb-1 text-sm font-semibold text-surface-700 dark:text-surface-200">{{ siteLabel }}</b>
                <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
                <a href="/privacy" target="_blank" rel="noopener" class="font-semibold text-surface-700 hover:underline dark:text-surface-200">{{ t('common.privacy') }}</a>
                <span class="mt-2">© {{ year }} {{ siteLabel }}</span>
            </div>
            <CommerceBusinessInfo v-if="shownBusiness" :business="shownBusiness" variant="footer" />
        </div>
    </footer>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { siteLabel } = useSiteName();
const commerce = useCommerceInfo();
const { shownBusiness } = commerce;
const year = new Date().getFullYear();
onMounted(() => void commerce.ensureLoaded());
</script>
