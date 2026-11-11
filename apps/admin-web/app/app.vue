<template>
    <NuxtLayout>
        <NuxtPage />
    </NuxtLayout>
</template>

<script setup lang="ts">
import { registerLicense } from '@primeui/license-manager';

import UtilAdminSite from '~/utils/util-admin-site';

// PrimeUI 라이선스 — 키는 이 앱 폴더의 환경 파일 VITE_PRIMEUI_LICENSE_KEY(.env.development = 로컬 개발 `pnpm dev`, .env.production = 운영 빌드 `pnpm generate` —
// 둘 다 저장소에 커밋한다, 칸 설명은 .env.example). 빌드할 때 코드에 박히므로 운영 패키지를 만드는 PC 에 .env.production 이 있어야 한다. PRO 제품 키는 charts 같은 이름으로 더한다.
registerLicense({ primeui: import.meta.env.VITE_PRIMEUI_LICENSE_KEY });

// 페이지마다 제목을 정하고(useSeoMeta) 여기서 "제목 · 이름 관리자" 로 붙인다. 이름이 바뀌면(서버에서 받으면) 다시 계산하도록 getter 로 준다
// (titleTemplate 함수 안에서 읽으면 반응하지 않는다). 언어가 바뀌어도 "관리자" 글자가 따라온다.
const { t } = useI18n();
const { siteLabel } = useAdminAuth();
useHead(() => {
    const site = siteLabel.value;
    const admin = t('shell.adminBadge');
    return {
        titleTemplate: (title?: string) => UtilAdminSite.formatTitle(title, site, admin),
    };
});
</script>
