import type { Role } from '~/composables/auth/api/useAuthApi.types';
import { AUTH_USER_STATE_KEY } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';

/**
 * 내 장식·아바타를 서버에서 한 번 받아 겹치기(`useMyDecoration`)에 채운다 — 서버 값을 따로 받지 않는 미리보기·`/me`·내 아이템 슬롯이 「지금 내 모습」을 그리게.
 * 장착은 `GET /api/me/items`(장착 중인 줄), 아바타는 `GET /api/me/profile` 의 `avatarUrl`. 로그인한 사람마다 한 번만 받는다(같은 사람이면 다시 묻지 않는다 — `ensure`).
 * 실패해도 던지지 않는다(그 칸만 서버 값 없이 — 장식 없음·기본 아바타로 그린다). 사업자 전용 계정은 포인트 상점이 없어 장착을 묻지 않는다.
 * ⚠ setup 에서 부른다(안에서 API 컴포저블을 잡는다). 장착·해제·고르기 뒤에는 그 응답을 `useMyDecoration().apply*` 로 넣으므로 다시 받을 필요가 없다.
 */
export function useMyDecorationSync() {
    const itemsApi = useMyItemsApi();
    const profileApi = useProfileApi();
    const decoration = useMyDecoration();
    const user = useState<{ id: string; role?: Role } | null>(AUTH_USER_STATE_KEY);
    /** 받아 둔 사람 */
    const syncedFor = useState<string | null>('pointshop-my-decoration-synced', () => null);

    async function ensure(): Promise<void> {
        const me = user.value;
        if (!me || syncedFor.value === me.id) return;
        syncedFor.value = me.id;
        const business = UtilBusiness.isBusinessAccount(me.role);
        const [items, profile] = await Promise.allSettled([business ? Promise.resolve(null) : itemsApi.list(), profileApi.me()]);
        if (user.value?.id !== me.id) return;
        if (items.status === 'fulfilled' && items.value) {
            const color = items.value.items.find((r) => r.equipped && r.kind === 'NICKNAME_COLOR');
            const border = items.value.items.find((r) => r.equipped && r.kind === 'PROFILE_BORDER');
            decoration.applyEquipped({
                nicknameColor: color?.color ? { userItemId: color.id, itemId: color.itemId, color: color.color, expiresAt: color.expiresAt } : null,
                profileBorder: border?.borderStyle ? { userItemId: border.id, itemId: border.itemId, borderStyle: border.borderStyle, expiresAt: border.expiresAt } : null,
            });
        }
        if (profile.status === 'fulfilled') decoration.applyAvatar(profile.value.avatarUrl);
        // 둘 다 못 받았으면 다음에 다시 묻는다
        if (items.status === 'rejected' && profile.status === 'rejected') syncedFor.value = null;
    }

    return { ensure };
}
