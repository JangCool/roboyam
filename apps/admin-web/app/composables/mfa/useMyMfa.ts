import type { ApiError } from '~/composables/common/useApiClient';
import type { MfaEnrollmentView, MfaMeView, MfaTrustedDeviceView } from '~/composables/mfa/api/useMfaApi.types';

/**
 * 내 2차 인증 — 내 정보 › 로그인 보안 탭이 쓰는 상태와 동작(내 상태 · 신뢰 기기 · 복구 코드 새로 만들기 · 다시 등록).
 * 탭 컴포넌트가 서버를 직접 부르지 않게 하는 한 겹이다(components.md 1). 부를 때마다 새 상태다(탭을 열 때마다 새로 받는다 — 보안 상태라 묵은 값을 두지 않는다).
 * 등록 흐름 자체는 `useMfaEnrollment`.
 */
export function useMyMfa() {
    const api = useMfaApi();
    const toApiError = useApiErrorOf();

    /** 내 상태. `null` = 아직 못 받았다 */
    const status = ref<MfaMeView | null>(null);
    const devices = ref<MfaTrustedDeviceView[] | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    const devicesError = ref<ApiError | null>(null);

    /** 내 상태와 신뢰 기기를 함께 받는다 — 신뢰 기기가 실패해도 상태는 보인다 */
    async function load(): Promise<void> {
        loading.value = true;
        error.value = null;
        devicesError.value = null;
        const [me, list] = await Promise.allSettled([api.me(), api.trustedDevices()]);
        if (me.status === 'fulfilled') status.value = me.value;
        else error.value = toApiError(me.reason);
        if (list.status === 'fulfilled') devices.value = list.value;
        else devicesError.value = toApiError(list.reason);
        loading.value = false;
    }

    /** 내 상태만 다시(등록·복구 코드 새로 만들기 뒤) */
    async function reloadStatus(): Promise<void> {
        try {
            status.value = await api.me();
        } catch {
            // 앞 값을 그대로 둔다 — 다음에 탭을 열면 다시 받는다
        }
    }

    async function reloadDevices(): Promise<void> {
        try {
            devices.value = await api.trustedDevices();
            devicesError.value = null;
        } catch (e) {
            devicesError.value = toApiError(e);
        }
    }

    /** 기기 하나 끊기 — 실패는 던진다(부르는 쪽이 토스트) */
    async function revokeDevice(id: string): Promise<void> {
        await api.revokeDevice(id);
        devices.value = (devices.value ?? []).filter((d) => d.id !== id);
    }

    async function revokeAllDevices(): Promise<void> {
        await api.revokeAllDevices();
        devices.value = [];
    }

    /** 현재 OTP 를 확인하고 복구 코드를 새로 만든다 — 실패는 던진다 */
    async function regenerateRecoveryCodes(code: string): Promise<string[]> {
        const res = await api.regenerateRecoveryCodes(code);
        if (status.value) status.value = { ...status.value, recoveryCodesLeft: res.recoveryCodes.length };
        return res.recoveryCodes;
    }

    /** 비밀번호를 확인하고 다시 등록을 시작한다 — 실패는 던진다. 확정 전까지는 예전 OTP 가 그대로 쓰인다 */
    function reenroll(password: string): Promise<MfaEnrollmentView> {
        return api.reenroll(password);
    }

    return { status, devices, loading, error, devicesError, load, reloadStatus, reloadDevices, revokeDevice, revokeAllDevices, regenerateRecoveryCodes, reenroll };
}
