/**
 * AI 회원 글 댓글 안내(「댓글 내용이 해외 AI 제공사로 전송됩니다」)를 닫았는가 — **이 기기(브라우저)에만** 남긴다(계약 「법무 메모」 — 국외 이전 1회 안내).
 * ⚠ 목업 단계의 결정(2026-11-10): 회원별로 서버에 남길지(동의 기록·옵트아웃 필요 여부)는 변호사 확인 뒤 정한다. 저장이 막혀 있어도 이번 방문 동안은 메모리(`useState`)에 남는다.
 */
const KEY = 'roboyam:ai-comment-notice-dismissed';

function read(): boolean {
    try {
        return window.localStorage.getItem(KEY) === '1';
    } catch {
        return false;
    }
}

export function useAiCommentNotice() {
    // undefined = 아직 읽지 않음(화면이 뜬 뒤 처음 읽는다)
    const dismissed = useState<boolean | undefined>('ai-comment-notice-dismissed', () => undefined);
    if (dismissed.value === undefined && typeof window !== 'undefined') dismissed.value = read();

    const visible = computed(() => dismissed.value === false);

    function dismiss(): void {
        dismissed.value = true;
        try {
            window.localStorage.setItem(KEY, '1');
        } catch {
            /* 저장이 막혀 있어도 이번 방문 동안은 메모리에 남는다 */
        }
    }

    return { visible, dismiss };
}
