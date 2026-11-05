<template>
    <!-- 햄버거 메뉴(모바일) — 맨 위에 로그인 안내(로그인 전만), 아래에 게시판 메뉴, 맨 아래에 약관 링크 -->
    <Drawer v-model:visible="open" position="left" :style="{ width: '17rem', maxWidth: '85vw' }" :aria-label="t('shell.menu')">
        <template #header>
            <SiteBrand class="font-bold tracking-wider" />
        </template>

        <div class="-mx-5 flex flex-col pt-1">
            <!-- 로그인 전에만 — 로그인·가입 안내. 로그인한 뒤의 계정 줄(내 정보·포인트·쪽지·사업자·관리자 앱·로그아웃)은 헤더 계정 메뉴에 있어 여기 두지 않는다(사용자 결정 2026-11-05) -->
            <div v-if="loaded && !user" class="mx-4 mb-2 rounded-xl bg-surface-100 p-3 dark:bg-surface-800">
                <p class="text-sm font-semibold">{{ t('shell.loginInvite') }}</p>
                <p class="mt-0.5 text-xs text-muted-color">{{ t('shell.loginInviteHint') }}</p>
                <div class="mt-2 flex gap-2">
                    <Button type="button" :label="t('common.login')" size="small" class="flex-1" @click="go(UtilRedirect.toLogin(route.fullPath))" />
                    <Button type="button" :label="t('common.signup')" size="small" severity="secondary" variant="outlined" class="flex-1" @click="go('/signup')" />
                </div>
            </div>

            <AppMenuList @navigate="close" />

            <!-- 약관 본문 페이지는 아직 없다(19-info). 라우터 밖 링크라 새 탭에서 열린다 -->
            <p class="mt-4 flex gap-3 px-4 pb-2 text-xs text-muted-color">
                <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
                <a href="/privacy" target="_blank" rel="noopener" class="font-semibold hover:underline">{{ t('common.privacy') }}</a>
            </p>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';
import UtilRedirect from '~/utils/util-redirect';

const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const route = useRoute();
const { user, loaded } = useAuth();

function close() {
    open.value = false;
}

async function go(to: RouteLocationRaw) {
    close();
    await navigateTo(to);
}

// 화면이 바뀌면 메뉴를 닫는다(뒤로 가기 포함)
watch(
    () => route.fullPath,
    () => close(),
);
</script>
