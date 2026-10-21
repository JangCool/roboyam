<template>
    <!-- 햄버거 메뉴(모바일, md 미만) — 맨 위에 로그인한 관리자(이름·역할·로그아웃·화면 설정), 아래에 관리 메뉴(지금 모드의 메뉴 — 설정 메뉴일 때는 머리에 "설정"과 「메뉴로 돌아가기」). 열림·닫힘과 초점은 PrimeVue Drawer 가 맡는다 -->
    <Drawer v-model:visible="open" position="left" :style="{ width: '17rem', maxWidth: '85vw' }" :aria-label="t('shell.menu')">
        <template #header>
            <SiteBrand />
        </template>

        <div class="-mx-2 -mt-2 flex flex-col gap-2">
            <div v-if="user" class="mx-2 rounded-xl bg-surface-100 p-3 dark:bg-surface-800">
                <p class="text-xs text-muted-color">{{ t('shell.accountInfo') }}</p>
                <p class="mt-1 flex items-center gap-1.5">
                    <b class="min-w-0 truncate text-sm">{{ user.nickname }}</b>
                    <RoleBadge :role="user.role" />
                </p>
                <div class="mt-2 flex flex-wrap gap-2">
                    <Button type="button" :label="t('shell.displaySettings')" icon="pi pi-palette" size="small" severity="secondary" variant="outlined" @click="onSettings" />
                    <Button type="button" :label="t('common.logout')" icon="pi pi-sign-out" size="small" severity="secondary" variant="outlined" :loading="busy" @click="onLogout" />
                </div>
            </div>

            <div class="px-2">
                <!-- 설정 메뉴 머리 — 어디인지("설정")와 일반 메뉴로 나오는 단추 -->
                <div v-if="isSettings" class="mb-2 flex items-center gap-2 border-b border-(--line) pb-2">
                    <i class="pi pi-cog w-4 shrink-0 text-center text-muted-color" aria-hidden="true" />
                    <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ t('shell.settings') }}</span>
                    <Button type="button" :label="t('shell.backToMenu')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" @click="onBack" />
                </div>
                <AppMenuList @navigate="close" />
            </div>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
const open = defineModel<boolean>('visible', { required: true });
const emit = defineEmits<{ 'open-settings': [] }>();

const { t } = useI18n();
const route = useRoute();
const { user } = useAdminAuth();
const { busy, run } = useAdminLogoutFlow();
const { isSettings, backToMenu } = useAppMenu();

function close() {
    open.value = false;
}

function onSettings() {
    close();
    emit('open-settings');
}

async function onBack() {
    close();
    await backToMenu();
}

async function onLogout() {
    close();
    await run();
}

// 화면이 바뀌면 메뉴를 닫는다(뒤로 가기 포함)
watch(
    () => route.fullPath,
    () => close(),
);
</script>
