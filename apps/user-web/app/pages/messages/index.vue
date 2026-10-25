<template>
    <!-- 쪽지함의 오른쪽 칸 — 대화를 고르지 않았을 때의 안내. 데스크톱(lg)에서만 보인다(모바일의 쪽지함은 목록이 화면 전체다) -->
    <div class="grid min-h-0 flex-1 place-items-center p-5 text-center text-sm text-muted-color" data-testid="dm-placeholder">
        <p class="m-0 whitespace-pre-line leading-relaxed">{{ t('dm.pickThread') }}</p>
    </div>
</template>

<script setup lang="ts">
/** 쪽지함 `/messages` — 목록은 부모 라우트(`pages/messages.vue`)가 그린다. 하단 탭이 보이는 화면이라 `shell: 'inner'` 를 쓰지 않는다. 로그인 필요 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
</script>
