import type { TemplateListItemView } from '~/composables/template/api/useNotificationTemplateApi.types';

/**
 * 템플릿 목록 화면(메뉴 「운영자 템플릿」 `/settings/notification-templates/templates/operator` · 「회원 템플릿」 `…/member`)의 상태 — 목록을 받고, 한 줄을 지운다.
 * 두 화면이 setup 에서 부를 때마다 새 상태가 생긴다(`useState` 가 아니다 — 화면끼리 공유하지 않는다). 표·거르기는 `NotificationTemplateList` 가 그리고, 이 상태를 props 로 받는다.
 * 서버 호출은 `useNotificationTemplateApi` 한 겹이다(지금은 메모리 목업). 받는 사람으로 거르는 것은 화면이 한다(목업 가정 — 수가 적다).
 *
 * ⚠ 화면 setup 에서 부른다(오류를 `ApiError` 로 바꾸는 함수·번역 함수를 그때 붙잡는다).
 *
 * @returns `templates`(null = 아직 못 받음) · `loading` · `loadError` · `denied`(403 — 오류가 아니라 안내) · `deletingId` · `load()` · `confirmDelete(item)`.
 */
export function useNotificationTemplateList() {
    const t = useTranslate();
    const api = useNotificationTemplateApi();
    const toast = useAppToast();
    const toApiError = useApiErrorOf();
    const { confirm } = useConfirmDialog();

    const templates = ref<TemplateListItemView[] | null>(null);
    const loading = ref(false);
    const loadError = ref('');
    const denied = ref(false);
    const deletingId = ref<string | null>(null);

    async function load(): Promise<void> {
        loading.value = true;
        loadError.value = '';
        try {
            templates.value = await api.templates();
        } catch (e) {
            const error = toApiError(e);
            if (error.status === 403) denied.value = true;
            else loadError.value = error.message;
        } finally {
            loading.value = false;
        }
    }

    /** 확인 후 바로 지운다 — 쓰는 중이면 서버가 409 `TEMPLATE_IN_USE`(화면은 단추를 먼저 잠근다) */
    async function confirmDelete(item: TemplateListItemView): Promise<void> {
        if (deletingId.value) return;
        const ok = await confirm({ title: t('notificationTemplate.deleteTitle', { name: item.name }), message: t('notificationTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
        if (!ok) return;
        deletingId.value = item.id;
        try {
            await api.remove(item.id);
            toast.success(t('notificationTemplate.deleted'));
            templates.value = (templates.value ?? []).filter((other) => other.id !== item.id);
        } catch (e) {
            // 목록 한 줄 조치의 실패는 토스트(사유 포함 8초 — forms.md 2-3), 서버 문구 그대로
            toast.error(toApiError(e).message, 8000);
        } finally {
            deletingId.value = null;
        }
    }

    return { templates, loading, loadError, denied, deletingId, load, confirmDelete };
}
