import type { DocIntakeBundle, DocIntakeTarget } from '~/composables/docintake/api/useDocIntakeApi.types';
import type { DocIntakeDialog, DocIntakeEvent, DocIntakeHost } from '~/composables/docintake/useDocIntake.types';

interface Options {
    /**
     * 계약서 전자 팩스 — 파일을 기존 계약서 올리기 API 로 올리고 새 파일 id 를 돌려준다(부모가 상세를 새로 고친다). 없으면 등록증처럼 접수 기록 API 로 올린다.
     */
    uploadFile?: (file: File) => Promise<string | null>;
}

/**
 * 접수 기록(인쇄본·팩스) 한 대상의 화면 상태 — 심사 화면(`BizAppReviewPage`)·광고 일정(`/ads`)·사업자 화면(`BusinessAdminPage`)이 같은 흐름을 쓴다.
 * 공유 상태가 아니라 부른 화면마다 따로 든다(`useState` 없음). 대상(`target`)이 바뀌면 그 대상 것을 다시 받는다.
 * 서버 호출은 `useDocIntakeApi`(**목업**). 성공하면 대화상자를 닫고 토스트, 칸 오류는 `fieldErrors`, 그 밖은 `error`(대화상자 안). 409 는 다시 읽는다.
 * ⚠ 컴포넌트 setup 에서 부른다(토스트·번역을 그때 붙잡는다).
 */
export function useDocIntake(target: MaybeRefOrGetter<DocIntakeTarget | null>, options: Options = {}) {
    const api = useDocIntakeApi();
    const toApiError = useApiErrorOf();
    const toast = useAppToast();
    const t = useTranslate();
    const { user } = useAdminAuth();

    const bundle = ref<DocIntakeBundle | null>(null);
    const working = ref(false);
    const error = ref('');
    const fieldErrors = ref<Record<string, string>>({});
    const dialog = ref<DocIntakeDialog | null>(null);

    const host = computed<DocIntakeHost>(() => ({
        bundle: bundle.value,
        receiver: user.value?.nickname ?? '',
        busy: working.value,
        error: error.value,
        fieldErrors: fieldErrors.value,
        dialog: dialog.value,
    }));

    let seq = 0;
    async function load(): Promise<void> {
        const current = toValue(target);
        const mine = ++seq;
        if (!current) {
            bundle.value = null;
            return;
        }
        try {
            const found = await api.list({ ...current });
            if (mine === seq) bundle.value = found;
        } catch (e) {
            if (mine === seq) toast.error(toApiError(e).message, 8000);
        }
    }

    // 대상이 바뀌면(다른 건·다른 사업자) 열린 창을 닫고 그 대상 것을 받는다 — 같은 대상의 상세 새로 고침에는 다시 부르지 않는다
    watch(
        () => {
            const v = toValue(target);
            return v ? `${v.doc}:${v.ownerId}` : '';
        },
        () => {
            bundle.value = null;
            dialog.value = null;
            void load();
        },
        { immediate: true },
    );

    async function run(call: (current: DocIntakeTarget) => Promise<DocIntakeBundle>, done: string, closeDialog = true): Promise<void> {
        const current = toValue(target);
        if (!current || working.value) return;
        working.value = true;
        error.value = '';
        fieldErrors.value = {};
        try {
            bundle.value = await call({ ...current });
            if (closeDialog) dialog.value = null;
            if (done) toast.success(done);
        } catch (e) {
            const failure = toApiError(e);
            if (failure.status === 409) void load();
            if (failure.code === 'VALIDATION' && failure.fields) fieldErrors.value = failure.fields;
            else if (dialog.value) error.value = failure.message;
            else toast.error(failure.message, 8000);
        } finally {
            working.value = false;
        }
    }

    function handle(event: DocIntakeEvent): void {
        switch (event.type) {
            case 'dialog':
                error.value = '';
                fieldErrors.value = {};
                dialog.value = event.dialog;
                return;
            case 'create':
                void run((c) => api.create(c, event.body), t('docIntake.done.created'));
                return;
            case 'createFile':
                void run(async (c) => {
                    if (c.doc === 'CONTRACT' && options.uploadFile) {
                        const fileId = await options.uploadFile(event.file);
                        return fileId ? api.markFileChannel(c, fileId, 'FAX') : api.list(c);
                    }
                    return api.uploadFaxFile(c, event.file);
                }, t('docIntake.done.fileUploaded'));
                return;
            case 'handling':
                void run((c) => api.changeHandling(c, event.id, event.body), t('docIntake.done.handling'));
                return;
            case 'dispose':
                void run((c) => api.dispose(c, event.id, event.body), t('docIntake.done.disposed'));
                return;
            case 'void':
                void run((c) => api.void(c, event.id, event.reason), t('docIntake.done.voided'));
                return;
            case 'mailDeclared':
                void run((c) => api.setMailDeclared(c, event.on), '', false);
                return;
        }
    }

    return { host, bundle, load, handle };
}
