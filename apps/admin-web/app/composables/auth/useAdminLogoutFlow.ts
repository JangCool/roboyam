/**
 * 로그아웃 — 확인 대화상자 → 로그아웃 → 토스트 → 로그인 화면. 헤더의 로그아웃 단추와 모바일 드로어가 같은 흐름을 쓴다.
 * 서버가 실패해도 화면 상태는 지운다(`useAdminAuth().logout`) — 눌렀는데 로그인 상태로 남는 것이 가장 나쁘다.
 */
export function useAdminLogoutFlow() {
    const t = useTranslate();
    const { logout } = useAdminAuth();
    const { confirm } = useConfirmDialog();
    const toast = useAppToast();
    const busy = ref(false);

    async function run(): Promise<void> {
        if (busy.value) return;
        const ok = await confirm({
            title: t('shell.logoutTitle'),
            message: t('shell.logoutMessage'),
            confirmLabel: t('common.logout'),
        });
        if (!ok) return;
        busy.value = true;
        try {
            await logout();
        } catch {
            /* 화면 상태는 이미 지워졌다 */
        } finally {
            busy.value = false;
        }
        toast.show(t('shell.loggedOut'));
        await navigateTo('/login');
    }

    return { busy, run };
}
