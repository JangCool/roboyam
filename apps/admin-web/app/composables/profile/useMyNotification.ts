/**
 * 내 알림 수신 설정 — 관리자 알림을 이메일로 받을지(on/off 하나). 내 정보 > 개인 설정의 "알림 설정" 카드가 쓴다. 서버와 말하는 것은 `useProfileApi` 다.
 *
 * 바꾸면 **곧바로 저장한다**(저장 단추 없음). 화면을 먼저 바꾸고 서버가 실패하면 되돌린다 — 저장이 안 됐는데 스위치만 바뀌어 있으면 사람은 된 줄 안다.
 * 읽는 중·저장 중·못 읽었을 때는 잠근다(`locked`). **못 읽었으면 잠근 채 둔다** — 모르는 값을 켜짐으로 보이면 거짓말이다.
 * 이 화면에서만 쓰는 값이라 컴포넌트 안의 `ref` 로 둔다(공유 상태가 아니다).
 */
export function useMyNotification() {
    const t = useTranslate();
    const api = useProfileApi();
    const toast = useAppToast();

    const emailEnabled = ref(true);
    const locked = ref(true);

    async function load(): Promise<void> {
        try {
            emailEnabled.value = (await api.notification()).emailEnabled;
            locked.value = false;
        } catch {
            locked.value = true;
        }
    }

    async function change(next: boolean): Promise<void> {
        const before = emailEnabled.value;
        emailEnabled.value = next;
        locked.value = true;
        try {
            emailEnabled.value = (await api.saveNotification({ emailEnabled: next })).emailEnabled;
            toast.success(next ? t('profile.emailNotificationOn') : t('profile.emailNotificationOff'));
        } catch (e) {
            // 되돌린다 — 서버 문구가 있으면 그대로 보인다
            emailEnabled.value = before;
            toast.error(e instanceof Error && e.message ? e.message : t('common.failed'));
        } finally {
            locked.value = false;
        }
    }

    return { emailEnabled, locked, load, change };
}
