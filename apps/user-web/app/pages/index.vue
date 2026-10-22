<template>
    <!-- 자리표시 홈 — 로그인 후 `/` 로 이동한 뒤 default 레이아웃(헤더·메뉴·하단 탭·푸터)을 눈으로 볼 자리. 홈 화면은 아직 만들지 않았다 -->
    <section class="py-10 text-center text-sm text-muted-color" data-testid="home-placeholder">
        <p class="font-semibold text-color">{{ t('shell.homePlaceholderTitle') }}</p>
        <p class="mt-1">{{ t('shell.homePlaceholderBody') }}</p>
    </section>
</template>

<script setup lang="ts">
import UtilSiteName from '~/utils/util-site-name';

const { t } = useI18n();
const { siteName } = useSiteName();
const { tagline } = useSiteTagline();

// 홈의 탭 제목은 서버가 첫 HTML 에 넣는 「이름 — 소개」 와 같게 둔다(앱이 뜬 뒤 제목이 바뀌지 않게). 이름을 모를 때(설치 전·못 받음)만 "홈" 이다.
useSeoMeta({ title: () => UtilSiteName.homeTitle(siteName.value, tagline.value) ?? t('shell.tabHome') });
</script>
