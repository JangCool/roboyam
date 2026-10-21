<template>
    <!-- 자리표시 홈 — 로그인 후 `/` 로 이동한 뒤 default 레이아웃(헤더·메뉴·하단 탭·푸터)을 눈으로 볼 자리. 홈 화면은 아직 만들지 않았다 -->
    <section class="py-10 text-center text-sm text-muted-color" data-testid="home-placeholder">
        <p class="font-semibold text-color">{{ t('shell.homePlaceholderTitle') }}</p>
        <p class="mt-1">{{ t('shell.homePlaceholderBody') }}</p>
    </section>
</template>

<script setup lang="ts">
const { t } = useI18n();
useSeoMeta({ title: () => t('shell.tabHome') });
</script>
