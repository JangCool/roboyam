<template>
    <!--
        햄버거 메뉴(모바일, md 미만) — 관리 메뉴만 둔다(지금 모드의 메뉴 — 설정 메뉴일 때는 머리에 "설정"과 「메뉴로 돌아가기」). 열림·닫힘과 초점은 PrimeVue Drawer 가 맡는다.
        로그인한 관리자 정보·로그아웃은 상단 바 아바타(프로필 팝오버), 화면 설정·다크 모드는 상단 바 아이콘이 맡으므로 여기엔 두지 않는다.
    -->
    <Drawer v-model:visible="open" position="left" :style="{ width: '17rem', maxWidth: '85vw' }" :aria-label="t('shell.menu')">
        <template #header>
            <SiteBrand />
        </template>

        <div class="-mt-2">
            <!-- 설정 메뉴 머리 — 어디인지("설정")와 일반 메뉴로 나오는 단추 -->
            <div v-if="isSettings" class="mb-2 flex items-center gap-2 border-b border-(--line) pb-2">
                <i class="pi pi-cog w-4 shrink-0 text-center text-muted-color" aria-hidden="true" />
                <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ t('shell.settings') }}</span>
                <Button type="button" :label="t('shell.backToMenu')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" @click="onBack" />
            </div>
            <AppMenuList @navigate="close" />
        </div>
    </Drawer>
</template>

<script setup lang="ts">
const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const route = useRoute();
const { isSettings, backToMenu } = useAppMenu();

function close() {
    open.value = false;
}

async function onBack() {
    close();
    await backToMenu();
}

// 화면이 바뀌면 메뉴를 닫는다(뒤로 가기 포함)
watch(
    () => route.fullPath,
    () => close(),
);
</script>
