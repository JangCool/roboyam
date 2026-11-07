import type { SitePopupDevice, SitePopupHideOption, SitePopupItem, SitePopupKind, SitePopupSaveRequest, SitePopupStatus, SitePopupTarget } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSiteNoticeBar from '~/utils/util-site-notice-bar';
import UtilSiteSetting from '~/utils/util-site-setting';

/**
 * 팝업 관리 화면의 순수 계산 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 규칙: 계약 `site-popup-contract.md` 「필드·검증」 — 이름 1~40자 · 그림 대체 글 100자 · 제목 40자 · 내용 1~1000자 · 단추 글자 20자 · 앞뒤 공백을 떼고 비면 null,
 * 링크는 공지 띠와 같은 모양(`UtilSiteNoticeBar.linkOk` — `/…` 또는 `http(s)://`, 500자), 기기 하나 이상, 끝은 시작보다 늦어야 한다.
 * 그림은 하이라이트 대표 이미지와 같은 검사(PNG·JPG·GIF·WEBP 2MB — `UtilSiteSetting.logoFile`, GIF 는 첫 장면만 — 서버 `FileUploadService`).
 */

/** 만들기·고치기 입력 — 기간은 순간 ISO 글자, 비면 '' (상시) */
export interface SitePopupForm {
    name: string;
    kind: SitePopupKind;
    imageFileId: string | null;
    /** 미리보기 주소 — 저장된 그림이면 서버 주소, 방금 올린 것이면 브라우저 임시 주소 */
    imageUrl: string | null;
    imageAlt: string;
    link: string;
    heading: string;
    body: string;
    buttonLabel: string;
    target: SitePopupTarget;
    devices: SitePopupDevice[];
    hideOption: SitePopupHideOption;
    visible: boolean;
    showFrom: string;
    showUntil: string;
}

export type SitePopupFormField = 'name' | 'imageFileId' | 'imageAlt' | 'link' | 'heading' | 'body' | 'buttonLabel' | 'devices' | 'showUntil';
export type SitePopupFormProblem = 'REQUIRED' | 'TOO_LONG' | 'IMAGE_REQUIRED' | 'LINK' | 'DEVICE_REQUIRED' | 'PERIOD_ORDER';
export type SitePopupImageProblem = 'EMPTY' | 'TYPE' | 'SIZE';

export default class UtilSitePopup {
    static readonly NAME_MAX = 40;
    static readonly IMAGE_ALT_MAX = 100;
    static readonly HEADING_MAX = 40;
    static readonly BODY_MAX = 1000;
    static readonly BUTTON_MAX = 20;
    static readonly LINK_MAX = 500;
    static readonly IMAGE_ACCEPT = UtilSiteSetting.LOGO_ACCEPT;
    static readonly IMAGE_MAX_BYTES = UtilSiteSetting.LOGO_MAX_BYTES;
    /** 이만큼 넘게 동시에 노출되면 「너무 많다」 안내(막지는 않는다) */
    static readonly SHOWING_SOFT_MAX = 3;

    static readonly KINDS: readonly SitePopupKind[] = ['IMAGE', 'TEXT'];
    static readonly TARGETS: readonly SitePopupTarget[] = ['HOME', 'ALL'];
    static readonly DEVICES: readonly SitePopupDevice[] = ['PC', 'MOBILE'];
    static readonly HIDE_OPTIONS: readonly SitePopupHideOption[] = ['TODAY', 'WEEK', 'NEVER', 'NONE'];

    static statusSeverity(status: SitePopupStatus): 'success' | 'info' | 'secondary' | 'warn' {
        if (status === 'SHOWING') return 'success';
        if (status === 'SCHEDULED') return 'info';
        if (status === 'OFF') return 'warn';
        return 'secondary';
    }

    static statusIcon(status: SitePopupStatus): string {
        if (status === 'SHOWING') return 'pi pi-eye';
        if (status === 'SCHEDULED') return 'pi pi-clock';
        if (status === 'OFF') return 'pi pi-eye-slash';
        return 'pi pi-calendar-times';
    }

    static kindIcon(kind: SitePopupKind): string {
        return kind === 'IMAGE' ? 'pi pi-image' : 'pi pi-align-left';
    }

    static deviceIcon(device: SitePopupDevice): string {
        return device === 'PC' ? 'pi pi-desktop' : 'pi pi-mobile';
    }

    /** 글자 수 — 앞뒤 공백을 뗀 길이(서버와 같다) */
    static length(text: string): number {
        return text.trim().length;
    }

    static emptyForm(): SitePopupForm {
        return {
            name: '',
            kind: 'IMAGE',
            imageFileId: null,
            imageUrl: null,
            imageAlt: '',
            link: '',
            heading: '',
            body: '',
            buttonLabel: '',
            target: 'HOME',
            devices: ['PC', 'MOBILE'],
            hideOption: 'TODAY',
            visible: true,
            showFrom: '',
            showUntil: '',
        };
    }

    static formOf(item: SitePopupItem): SitePopupForm {
        return {
            name: item.name,
            kind: item.kind,
            imageFileId: item.imageFileId,
            imageUrl: item.imageUrl,
            imageAlt: item.imageAlt ?? '',
            link: item.link ?? '',
            heading: item.heading ?? '',
            body: item.body ?? '',
            buttonLabel: item.buttonLabel ?? '',
            target: item.target,
            devices: [...item.devices],
            hideOption: item.hideOption,
            visible: item.visible,
            showFrom: item.showFrom ?? '',
            showUntil: item.showUntil ?? '',
        };
    }

    /**
     * 칸 문제 — 종류에 딸리지 않는 칸(이미지면 제목·내용, 텍스트면 그림)은 보지 않는다(값은 그대로 보낸다 — 종류를 되돌리면 다시 쓰인다).
     * @param {SitePopupForm} form - 입력 중인 값.
     * @returns 칸 → 문제.
     */
    static problems(form: SitePopupForm): Partial<Record<SitePopupFormField, SitePopupFormProblem>> {
        const out: Partial<Record<SitePopupFormField, SitePopupFormProblem>> = {};
        const len = UtilSitePopup.length;
        if (!len(form.name)) out.name = 'REQUIRED';
        else if (len(form.name) > UtilSitePopup.NAME_MAX) out.name = 'TOO_LONG';
        const link = form.link.trim();
        if (link && (link.length > UtilSitePopup.LINK_MAX || !UtilSiteNoticeBar.linkOk(link))) out.link = 'LINK';
        if (form.kind === 'IMAGE') {
            if (!form.imageFileId) out.imageFileId = 'IMAGE_REQUIRED';
            if (len(form.imageAlt) > UtilSitePopup.IMAGE_ALT_MAX) out.imageAlt = 'TOO_LONG';
        } else {
            if (len(form.heading) > UtilSitePopup.HEADING_MAX) out.heading = 'TOO_LONG';
            if (!len(form.body)) out.body = 'REQUIRED';
            else if (len(form.body) > UtilSitePopup.BODY_MAX) out.body = 'TOO_LONG';
            if (len(form.buttonLabel) > UtilSitePopup.BUTTON_MAX) out.buttonLabel = 'TOO_LONG';
        }
        if (form.devices.length === 0) out.devices = 'DEVICE_REQUIRED';
        if (form.showFrom && form.showUntil && Date.parse(form.showUntil) <= Date.parse(form.showFrom)) out.showUntil = 'PERIOD_ORDER';
        return out;
    }

    /** 요청 — 글자 칸은 앞뒤 공백을 떼고 비면 null, 고칠 때도 모든 칸을 보낸다 */
    static toRequest(form: SitePopupForm): SitePopupSaveRequest {
        const text = (value: string) => value.trim() || null;
        return {
            name: form.name.trim(),
            kind: form.kind,
            imageFileId: form.imageFileId,
            imageAlt: text(form.imageAlt),
            link: text(form.link),
            heading: text(form.heading),
            body: text(form.body),
            buttonLabel: text(form.buttonLabel),
            target: form.target,
            devices: UtilSitePopup.DEVICES.filter((device) => form.devices.includes(device)),
            hideOption: form.hideOption,
            visible: form.visible,
            showFrom: form.showFrom || null,
            showUntil: form.showUntil || null,
        };
    }

    static imageProblem(file: { size: number; type: string }): SitePopupImageProblem | null {
        return UtilSiteSetting.logoFile(file);
    }

    /**
     * 한 칸 옮긴 새 차례 — 끝에서 더 못 가면 null.
     * @param {readonly string[]} ids - 지금 차례.
     * @param {string} id - 옮길 팝업.
     * @param {-1 | 1} delta - 위(-1)·아래(1).
     * @returns {string[] | null} 새 차례.
     */
    static moved(ids: readonly string[], id: string, delta: -1 | 1): string[] | null {
        const from = ids.indexOf(id);
        const to = from + delta;
        if (from < 0 || to < 0 || to >= ids.length) return null;
        const next = [...ids];
        next[from] = ids[to]!;
        next[to] = id;
        return next;
    }

    /** 미리보기에 넘길 모양 — 저장한 줄이든 입력 중인 값이든 같은 모양으로 */
    static previewOf(source: SitePopupForm | SitePopupItem): SitePopupPreview {
        const text = (value: string | null) => (value ?? '').trim() || null;
        return {
            key: 'id' in source ? source.id : 'draft',
            kind: source.kind,
            imageUrl: source.imageUrl,
            imageAlt: text(source.imageAlt),
            link: text(source.link),
            heading: text(source.heading),
            body: text(source.body),
            buttonLabel: text(source.buttonLabel),
            hideOption: source.hideOption,
        };
    }
}

/** 미리보기 카드(`SitePopupCard`)가 그리는 값 — 공개 사이트 응답(`PublicSitePopup`)의 보이는 칸과 같다 */
export interface SitePopupPreview {
    key: string;
    kind: SitePopupKind;
    imageUrl: string | null;
    imageAlt: string | null;
    link: string | null;
    heading: string | null;
    body: string | null;
    buttonLabel: string | null;
    hideOption: SitePopupHideOption;
}
