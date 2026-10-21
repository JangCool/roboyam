/**
 * 로그아웃 — 확인 대화상자 → 로그아웃 → 토스트 → 홈 (design 01-shell 8).
 * 햄버거 메뉴·데스크톱 계정 메뉴·내 정보가 같은 흐름을 쓴다.
 * 서버가 실패해도 화면 상태는 지운다(`useAuth().logout`) — 눌렀는데 로그인 상태로 남는 것이 가장 나쁘다.
 *
 * ⚠ 원본(community)은 로그아웃 요청보다 먼저 이 기기를 푸시에서 뺀다(`usePush().unsubscribeThisDevice()` — 다음에 쓰는 사람에게 알림이 가지 않게).
 * 푸시는 이 범위 밖이라 가져오지 않았다 — 푸시 화면을 만들 때 그 호출을 여기에 되살린다.
 */
export function useLogoutFlow() {
    const t = useTranslate();
    const { logout } = useAuth();
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
        await navigateTo('/');
    }

    return { busy, run };
}
