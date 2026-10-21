<template>
    <!-- 로고 자리 — 올린 로고가 있으면 이름 앞에 작게, 없으면 사이트 이름 글자만("관리자" 뱃지는 없다 — 브라우저 탭 제목과 홈 링크의 이름표에는 "관리자"가 남는다). 글자 색은 바깥을 따른다(강조색 상단 바·밝은 로그인 카드 양쪽) -->
    <span class="inline-flex min-w-0 items-center gap-2">
        <!-- ⚠ 그림은 `<img src>` 하나로만 그린다 — SVG 로 올린 것도 서버가 data URI 로 바꿔 주므로 `v-html` 을 쓰지 않는다(올린 마크업의 스크립트가 도는 자리를 만들지 않는다). 못 불러오면 이름만 남긴다 -->
        <img v-if="siteLogoUrl && !failed" :src="siteLogoUrl" alt="" aria-hidden="true" class="size-5 shrink-0 rounded-[4px] object-contain" @error="failed = true" />
        <span class="block min-w-0 truncate font-bold tracking-wider">{{ siteLabel }}</span>
    </span>
</template>

<script setup lang="ts">
const { siteLabel, siteLogoUrl } = useAdminAuth();

/** 그림을 못 불러왔는가 — 깨진 그림 대신 이름만 보인다. 로고가 바뀌면 다시 시도한다 */
const failed = ref(false);
watch(siteLogoUrl, () => (failed.value = false));
</script>
