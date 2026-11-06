<template>
    <!-- 사이트 푸터 — 약관·개인정보처리방침 링크 + 판매 스위치가 켜져 있으면 통신판매업자 정보(전자상거래법 제10조 ① — commerce-groupbuy 결정 56). 약관 본문 페이지는 아직 없어 새 탭 링크다.
         데스크톱(md 이상): 두 단 — 왼쪽 = 사이트 이름 · 링크 · ©, 오른쪽 = 사업자 정보 표(`CommerceBusinessInfo` footer). 사업자 정보가 없으면 왼쪽 단만.
         모바일(사용자 요청 2026-11-06 — 모바일에도 보인다): 한 단으로 쌓는다 — 사이트 이름 · 링크 한 줄, 그 아래 사업자 정보 표(이름표 | 값), 맨 아래 ©. 하단 탭 바로 위에 놓인다 -->
    <footer class="border-t border-surface bg-surface-100 dark:bg-surface-900">
        <div class="mx-auto grid max-w-[var(--content-max)] grid-cols-1 gap-y-3 px-4 py-5 text-xs text-muted-color md:grid-cols-[10rem_minmax(0,1fr)] md:gap-x-8 md:py-6">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 md:flex-col md:items-start md:gap-1">
                <b class="text-sm font-semibold text-surface-700 md:mb-1 dark:text-surface-200">{{ siteLabel }}</b>
                <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
                <a href="/privacy" target="_blank" rel="noopener" class="font-semibold text-surface-700 hover:underline dark:text-surface-200">{{ t('common.privacy') }}</a>
                <span class="max-md:hidden md:mt-2">{{ copyright }}</span>
            </div>
            <CommerceBusinessInfo v-if="shownBusiness" :business="shownBusiness" variant="footer" class="max-md:border-t max-md:border-surface max-md:pt-3" />
            <span class="md:hidden">{{ copyright }}</span>
        </div>
    </footer>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { siteLabel } = useSiteName();
const commerce = useCommerceInfo();
const { shownBusiness } = commerce;
/** © 줄 — 데스크톱은 왼쪽 단 맨 아래, 모바일은 푸터 맨 아래 */
const copyright = computed(() => `© ${new Date().getFullYear()} ${siteLabel.value}`);
onMounted(() => void commerce.ensureLoaded());
</script>
