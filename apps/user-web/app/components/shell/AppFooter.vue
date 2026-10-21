<template>
    <!-- 데스크톱 푸터 — 약관·개인정보처리방침 링크만(사업자 정보·투자 고지 전문은 19-info 에서). 약관 본문 페이지는 아직 없어 새 탭 링크다 -->
    <footer class="border-t border-surface bg-surface-100 dark:bg-surface-900">
        <div class="mx-auto flex max-w-[var(--content-max)] items-center gap-4 px-4 py-4 text-xs text-muted-color">
            <b class="font-semibold text-surface-600 dark:text-surface-300">{{ siteLabel }}</b>
            <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
            <a href="/privacy" target="_blank" rel="noopener" class="font-semibold hover:underline">{{ t('common.privacy') }}</a>
        </div>
    </footer>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { siteLabel } = useSiteName();
</script>
