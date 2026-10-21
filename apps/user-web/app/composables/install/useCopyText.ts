/**
 * 글자를 클립보드에 복사한다 — 콜백 주소 복사 버튼 같은 곳이 쓴다. 상태는 없다.
 *
 * 먼저 `navigator.clipboard`(보안 컨텍스트 — https 또는 localhost)를 쓰고, 안 되면 보이지 않는 입력칸으로 `execCommand('copy')` 한다.
 * 둘 다 안 되면 `false` — 부르는 쪽이 "직접 복사해 주세요" 를 알린다. 복사한 글자는 어디에도 남기지 않는다(로그 없음).
 */
export function useCopyText() {
    /** 복사했으면 true. 브라우저가 막으면 false(던지지 않는다) */
    async function copy(text: string): Promise<boolean> {
        try {
            if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
                await navigator.clipboard.writeText(text);
                return true;
            }
        } catch {
            // 권한이 없거나 문서에 초점이 없을 때 — 아래 방법으로 한 번 더
        }
        return copyByTextarea(text);
    }

    return { copy };
}

/** 옛 방식 — 화면 밖 입력칸에 글자를 넣고 선택해 복사 명령을 보낸다 */
function copyByTextarea(text: string): boolean {
    if (typeof document === 'undefined') return false;
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.setAttribute('aria-hidden', 'true');
    area.style.cssText = 'position:fixed;top:0;left:-9999px;opacity:0;';
    document.body.appendChild(area);
    try {
        area.select();
        return document.execCommand('copy');
    } catch {
        return false;
    } finally {
        document.body.removeChild(area);
    }
}
