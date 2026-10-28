import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';

/** 관리자 미리보기의 홈 구성 — 관리자 홈 구성 편집기의 저장 전 값(가젯은 id·종류·제목·보이는 폭만) */
export interface AdminPreviewHome {
    layout: HomeLayout;
    gadgets: { id: string; type: string; title: string; desktop: boolean; mobile: boolean }[];
}

/** 관리자가 보내는 미리보기 값 — 메뉴 칸은 운영자 설정 모양 그대로(`THEME` 또는 값, 읽기는 `UtilMenuPosition` 이 한다) */
export interface AdminPreviewMessage {
    theme: string;
    menu: {
        menuPosition: unknown;
        topItems: unknown;
        barGroupIds: unknown;
        menuWidth: unknown;
        menuOverflow: unknown;
        menuStyle: Record<string, unknown>;
    } | null;
    home: AdminPreviewHome | null;
    /** 홈 구성 편집 중인가 — 격자에 손잡이를 얹는다 */
    editing: boolean;
}

/** 편집 중 격자가 관리자에게 알리는 일 — 옮기기(놓은 칸) · 크기(칸·행) · 빼기 */
export type AdminPreviewLayoutOp =
    | { op: 'place'; id: string; x: number; y: number }
    | { op: 'size'; id: string; w: number; h: number }
    | { op: 'remove'; id: string };

/** 관리자 → 공개 사이트 메시지 종류 */
export const ADMIN_PREVIEW_APPLY = 'roboyam:admin-preview';
/** 공개 사이트 → 관리자 메시지 종류 — 준비됨 · 메뉴 자리를 눌렀다 · 홈 격자 편집(`AdminPreviewLayoutOp`) */
export const ADMIN_PREVIEW_READY = 'roboyam:admin-preview-ready';
export const ADMIN_PREVIEW_EDIT_MENU = 'roboyam:admin-preview-edit-menu';
export const ADMIN_PREVIEW_LAYOUT = 'roboyam:admin-preview-layout';

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * 관리자 미리보기(관리자 테마 화면의 iframe, 사용자 결정 2026-10-28 — 미리보기는 실제 공개 화면과 똑같아야 한다) 메시지 읽기와 홈 구성 덮기.
 * 받는 쪽은 `plugins/03.admin-preview.client.ts`, 보내는 쪽은 admin-web `components/theme/ThemeLivePreview.vue`.
 */
export default class UtilAdminPreview {
    /**
     * 받은 메시지를 읽는다 — 모양이 아니면 null. 값의 뜻(테마 id·메뉴 칸 값) 검사는 쓰는 곳이 기존 읽기 함수로 한다.
     * @param {unknown} data - `MessageEvent.data`.
     * @returns {AdminPreviewMessage | null} 읽은 값.
     */
    static read(data: unknown): AdminPreviewMessage | null {
        if (!isRecord(data) || data.type !== ADMIN_PREVIEW_APPLY || typeof data.theme !== 'string') return null;
        const menu = isRecord(data.menu) && isRecord(data.menu.menuStyle) ? (data.menu as AdminPreviewMessage['menu']) : null;
        return { theme: data.theme, menu, home: UtilAdminPreview.readHome(data.home), editing: data.editing === true };
    }

    /**
     * 홈 구성 읽기 — 칸 수·자리 목록·가젯 목록이 모양이 맞을 때만(자리 범위·겹침은 화면의 `UtilHomeLayout.read` 가 바로잡는다).
     * @param {unknown} value - 메시지의 `home`.
     * @returns {AdminPreviewHome | null} 홈 구성.
     */
    static readHome(value: unknown): AdminPreviewHome | null {
        if (!isRecord(value) || !isRecord(value.layout) || !Array.isArray(value.layout.items) || !Array.isArray(value.gadgets)) return null;
        const gadgets = value.gadgets.filter(
            (g): g is AdminPreviewHome['gadgets'][number] =>
                isRecord(g) && typeof g.id === 'string' && typeof g.type === 'string' && typeof g.title === 'string' && typeof g.desktop === 'boolean' && typeof g.mobile === 'boolean',
        );
        return { layout: value.layout as unknown as HomeLayout, gadgets };
    }

    /**
     * 받은 홈 가젯 응답에 관리자의 홈 구성을 덮는다 — 배치는 관리자 것, 가젯은 같은 id 의 가젯(새로 넣은 가젯은 같은 종류의 첫 가젯 내용)을 관리자 제목·보이는 폭으로.
     * 이 테마 응답에 같은 종류가 없는 새 가젯은 그릴 내용이 없어 뺀다.
     * @param {HomeGadgetsResponse} res - 홈 가젯 응답.
     * @param {AdminPreviewHome} home - 관리자 홈 구성.
     * @returns {HomeGadgetsResponse} 덮은 응답.
     */
    static applyHome(res: HomeGadgetsResponse, home: AdminPreviewHome): HomeGadgetsResponse {
        const byId = new Map(res.gadgets.map((g) => [g.id, g]));
        const gadgets = home.gadgets
            .map((p): HomeGadget | null => {
                const base = byId.get(p.id) ?? res.gadgets.find((g) => g.type === p.type);
                return base ? { ...(JSON.parse(JSON.stringify(base)) as HomeGadget), id: p.id, title: p.title, desktop: p.desktop, mobile: p.mobile } : null;
            })
            .filter((g): g is HomeGadget => g !== null);
        return { ...res, layout: home.layout, gadgets };
    }
}
