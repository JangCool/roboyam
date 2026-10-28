import type { AdminPreviewHome } from '~/utils/util-admin-preview';

/** 관리자 쪽 창 — 허용한 주소들과, 메시지를 받은 뒤 정해진 그 주소(`plugins/03.admin-preview.client.ts` 가 채운다) */
const channel: { origins: string[]; parent: string | null } = { origins: [], parent: null };

/**
 * 관리자 미리보기 채널을 연다(플러그인만 부른다) — 허용 주소를 정한다.
 * @param {string[]} origins - 메시지를 주고받을 관리자 주소들.
 */
export function openAdminPreviewChannel(origins: string[]): void {
    channel.origins = [...origins];
    channel.parent = null;
}

/**
 * 메시지를 받은 관리자 주소를 기억한다 — 그 뒤로는 그 주소로만 보낸다.
 * @param {string} origin - 받은 메시지의 주소.
 */
export function pinAdminPreviewOrigin(origin: string): void {
    channel.parent = origin;
}

/**
 * 관리자에게 보낸다 — 처음에는 허용한 모든 주소로(맞지 않는 주소로 보낸 것은 브라우저가 버린다), 받은 뒤로는 그 주소로만. 미리보기가 아니면 아무것도 하지 않는다.
 * @param {Record<string, unknown>} message - 보낼 값(구조 복제가 되는 값만).
 */
export function postToAdminPreview(message: Record<string, unknown>): void {
    if (window.parent === window) return;
    for (const origin of channel.parent ? [channel.parent] : channel.origins) window.parent.postMessage(message, origin);
}

/**
 * 관리자 미리보기 상태(관리자 테마 화면의 iframe 안 — `plugins/03.admin-preview.client.ts` 가 켠다).
 * 켜져 있으면 회원 보기 설정의 메뉴 위치를 쓰지 않고(`useMenuPosition` — 운영자 설정 > 테마 기본만), 홈은 관리자의 홈 구성(`home`)을 덮어 그린다(`pages/index.vue`).
 * 편집 중(`editing`)이면 홈 격자가 가젯 위에 끌기·크기·빼기 손잡이를 얹는다(`HomeGadgetGrid` — 바뀐 자리는 관리자에게 알리고, 계산·저장은 관리자가 한다).
 * 컴포저블은 setup 에서 부른다.
 *
 * @returns `active` 미리보기 중인가 · `editing` 홈 구성 편집 중인가 · `home` 관리자 홈 구성(없으면 공개 홈 그대로).
 */
export function useAdminPreview() {
    const active = useState<boolean>('admin-preview-active', () => false);
    const editing = useState<boolean>('admin-preview-editing', () => false);
    const home = useState<AdminPreviewHome | null>('admin-preview-home', () => null);
    return { active, editing, home };
}
