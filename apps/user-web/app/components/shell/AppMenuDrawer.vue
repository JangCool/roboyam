<template>
    <!--
        하단 탭 「메뉴」의 서랍(모바일, 사용자 2026-11-12 「메뉴 페이지 말고 drawer 로」) — 메뉴 단추가 하단 탭 오른쪽 끝이라 오른쪽에서 연다(예전 햄버거 서랍과 같은 쪽).
        본문은 전체 화면 메뉴(`/menu`)와 같은 `AppMenuPanel`. 링크를 눌러 주소가 바뀌면 닫는다. 열림 상태는 `useMenuDrawer`(공유) — 하단 탭 단추가 열고 밝힌다.
    -->
    <Drawer v-model:visible="open" position="right" :style="{ width: '20rem', maxWidth: '88vw' }" :show-close-icon="false" :aria-label="t('shell.menu')" data-testid="menu-drawer">
        <!-- 제목 줄 — 오른쪽 끝에 로그아웃(로그인했을 때만, 사용자 2026-11-12). 누르면 서랍을 닫고 확인창을 띄운다. 닫기 X 는 뺐다(바깥 누르기·Esc·링크 누르기로 닫힌다, 사용자 2026-11-12) -->
        <template #header>
            <div class="flex min-w-0 flex-1 items-center gap-2">
                <span class="text-base font-bold">{{ t('shell.menu') }}</span>
                <Button
                    v-if="user"
                    type="button"
                    :label="t('common.logout')"
                    icon="pi pi-sign-out"
                    size="small"
                    severity="secondary"
                    variant="outlined"
                    class="ml-auto rounded-md!"
                    data-testid="menu-drawer-logout"
                    @click="onLogout"
                />
            </div>
        </template>
        <AppMenuPanel class="-mt-3" @click="closeOnLink" />
    </Drawer>
</template>

<script setup lang="ts">
const { t } = useI18n();
const route = useRoute();
const { open } = useMenuDrawer();
const { user } = useAuth();
const { run: logout } = useLogoutFlow();

/** 로그아웃 — 서랍을 먼저 닫고 확인창(`useLogoutFlow`)을 띄운다 */
function onLogout(): void {
    open.value = false;
    void logout();
}

/** 메뉴 안 링크를 누르면 닫는다 — 지금 보는 화면과 같은 주소여도(주소가 안 바뀌어도) */
function closeOnLink(event: MouseEvent): void {
    if ((event.target as HTMLElement | null)?.closest('a')) open.value = false;
}

// 다른 화면으로 가면 닫는다(뒤로 가기 등)
watch(
    () => route.fullPath,
    () => {
        open.value = false;
    },
);
</script>
