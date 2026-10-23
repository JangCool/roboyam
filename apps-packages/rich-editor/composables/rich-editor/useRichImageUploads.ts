import type { RichImageUploadResult } from '#layers/rich-editor/components/RichEditor.types';
import type { RichImageUploadsOptions, RichUploadItem } from '#layers/rich-editor/composables/rich-editor/useRichImageUploads.types';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';
import UtilRichMessages from '#layers/rich-editor/utils/util-rich-messages';

/**
 * 에디터의 이미지 올리기 목록 — 고른 파일을 미리 검사하고, 차례로(동시에 2장까지) 올리고, 상태를 화면에 알린다.
 * community `frontend/app/composables/useImageUploads.ts` 에서 옮겼다 — 서버 호출(`useMediaApi`)은 레이어에 둘 수 없어 **앱이 넘긴 함수**(`uploader`)로 올린다.
 * `RichEditor` 하나만 쓴다(그 화면의 상태 — 공유 상태가 아니다). 올리기가 끝난 이미지는 목록에서 사라지고 `onUploaded` 로 에디터에 넘어간다.
 * 실패한 것은 남는다: 서버 오류·네트워크·속도 제한처럼 다시 보내 볼 만하면 「다시 시도」, 아니면 「삭제」만 준다.
 * **형식 미리 검사는 서버 판정을 대신하지 않는다** — 통과해도 서버가 거절하면 서버 문구를 그대로 보인다. 용량·장수 한도는 서버 설정이라 화면이 미리 막지 않는다.
 */

const CONCURRENCY = 2;
let sequence = 0;
let batchSequence = 0;

export function useRichImageUploads(options: RichImageUploadsOptions) {
    // 문구 함수는 setup 때 붙잡아 둔다(`await` 뒤에는 Nuxt 문맥이 없다)
    const t = useTranslate();
    const items = ref<RichUploadItem[]>([]);
    /** 다시 시도할 때 쓰려고 파일을 들고 있는다(반응형이 아니다) */
    const files = new Map<string, File>();
    const controllers = new Map<string, AbortController>();
    /** 올라왔지만 같은 묶음의 앞 파일을 기다리느라 아직 본문에 안 들어간 것 */
    const ready = new Map<string, RichImageUploadResult>();
    /** 스크린리더용 짧은 알림 */
    const announce = ref('');

    const active = computed(() => items.value.filter((i) => i.status !== 'failed'));
    const failed = computed(() => items.value.filter((i) => i.status === 'failed'));

    function patch(id: string, change: Partial<RichUploadItem>) {
        const item = items.value.find((i) => i.id === id);
        if (item) Object.assign(item, change);
    }

    function forget(id: string) {
        items.value = items.value.filter((i) => i.id !== id);
        files.delete(id);
        controllers.delete(id);
        ready.delete(id);
        options.onGone?.(id);
    }

    /** 파일들을 목록에 넣는다. 넣은 것의 id 를 돌려준다(자리 정하기용) */
    function add(picked: File[]): string[] {
        const ids: string[] = [];
        const batch = ++batchSequence;
        for (const file of picked) {
            const id = `up${++sequence}`;
            const isGif = UtilRichMedia.isGif(file);
            const problem = UtilRichMedia.precheck(file);
            if (problem) {
                // 보내지 않는다 — 같은 문구를 미리 보여 주고 지우기만 준다
                items.value.push({ id, name: file.name, bytes: file.size, isGif, status: 'failed', progress: 0, batch, failure: { code: problem, message: UtilRichMessages.precheck(t, problem), retryable: false } });
                ids.push(id);
                continue;
            }
            files.set(id, file);
            items.value.push({ id, name: file.name, bytes: file.size, isGif, status: 'queued', progress: 0, failure: null, batch });
            ids.push(id);
        }
        void pump();
        return ids;
    }

    let running = 0;

    async function pump() {
        while (running < CONCURRENCY) {
            const next = items.value.find((i) => i.status === 'queued');
            if (!next) return;
            running++;
            // 기다리지 않고 다음 것을 계속 띄운다(동시에 CONCURRENCY 장까지)
            void start(next.id).finally(() => {
                running--;
                void pump();
            });
        }
    }

    async function start(id: string) {
        const file = files.get(id);
        const upload = options.uploader();
        if (!file) return;
        if (!upload) {
            patch(id, { status: 'failed', progress: 0, failure: UtilRichMessages.failure(t, null) });
            return;
        }
        const controller = new AbortController();
        controllers.set(id, controller);
        patch(id, { status: 'uploading', progress: 0, failure: null });
        try {
            const result = await upload(file, {
                signal: controller.signal,
                onProgress: (fraction) => patch(id, fraction >= 1 ? { status: 'processing', progress: 1 } : { progress: fraction }),
            });
            if (!items.value.some((i) => i.id === id)) return; // 그 사이에 지웠다
            // 본문에는 고른 순서대로 넣는다 — 앞 파일이 아직 올라가는 중이면 여기서 기다린다
            ready.set(id, result);
            patch(id, { status: 'processing', progress: 1 });
            flush();
        } catch (e) {
            if (!items.value.some((i) => i.id === id)) return; // 취소·지우기로 끊었다
            const failure = UtilRichMessages.failure(t, e);
            patch(id, { status: 'failed', progress: 0, failure });
            controllers.delete(id);
            announce.value = t('richEditor.uploadFailedAnnounce', { name: file.name, message: failure.message });
            flush(); // 앞 파일이 실패로 끝났으니 기다리던 뒷 파일이 들어갈 수 있다
        }
    }

    /** 올라온 이미지를 본문에 넣는다 — 같은 묶음에서 앞선 파일이 아직 대기·올리는 중이면 기다린다 */
    function flush() {
        for (const item of [...items.value]) {
            const result = ready.get(item.id);
            if (!result) continue;
            const index = items.value.findIndex((i) => i.id === item.id);
            const waiting = items.value.slice(0, index).some((i) => i.batch === item.batch && i.status !== 'failed' && !ready.has(i.id));
            if (waiting) continue;
            // 에디터가 자리를 꺼낸 다음에 목록에서 뺀다(순서가 바뀌면 자리를 잃는다)
            options.onUploaded(item.id, result);
            forget(item.id);
            announce.value = t('richEditor.uploadedAnnounce', { name: item.name });
        }
    }

    /** 실패한 것을 다시 보낸다(다시 보내 볼 만한 실패일 때만 단추가 보인다) */
    function retry(id: string) {
        const item = items.value.find((i) => i.id === id);
        if (!item || item.status !== 'failed' || !files.has(id)) return;
        patch(id, { status: 'queued', progress: 0, failure: null });
        void pump();
    }

    /** 올리는 중이면 끊고, 목록에서 지운다(취소·지우기 모두) */
    function remove(id: string) {
        controllers.get(id)?.abort();
        forget(id);
        flush(); // 지운 파일을 기다리던 뒷 파일이 있을 수 있다
    }

    onScopeDispose(() => {
        for (const controller of controllers.values()) controller.abort();
        controllers.clear();
    });

    return { items, active, failed, announce, add, retry, remove };
}
