import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { RichImagePrecheck } from '#layers/rich-editor/utils/util-rich-media';

/**
 * 에디터 이미지 올리기 실패를 화면 상태로 바꾼다 — community `util-media-messages.ts` 에서 옮겼다.
 *
 * **서버 `message` 가 있으면 그것을 그대로 쓴다**(형식·크기·속도 제한 문구는 서버 몫 — `.claude/guides/frontend/forms.md`). 화면이 정하는 것은
 *  - 다시 시도가 의미 있는가(`retryable`) — 서버·네트워크가 잠깐 나쁜 경우와 속도 제한(429)만. 형식·크기는 다시 보내도 같다,
 *  - 서버 문구가 아예 없을 때(프록시가 던진 413·네트워크 끊김)의 대체 문구다.
 * 레이어는 앱의 `ApiError` 를 모른다 — 오류의 `status`·`code`·`message` 칸만 읽는다(앱 `useApiClient` 의 `ApiError` 가 이 모양이다).
 */
export interface RichUploadFailure {
    code: string;
    message: string;
    /** 같은 파일을 다시 보내 볼 만한 실패인가 */
    retryable: boolean;
}

interface ErrorLike {
    status?: unknown;
    code?: unknown;
    message?: unknown;
}

function isErrorLike(e: unknown): e is ErrorLike {
    return typeof e === 'object' && e !== null;
}

export default class UtilRichMessages {
    /**
     * 보내기 전 검사 문구.
     * @param {Translate} t - 번역 함수.
     * @param {RichImagePrecheck} problem - 검사 결과.
     * @returns {string} 한 줄 문구.
     */
    static precheck(t: Translate, problem: RichImagePrecheck): string {
        return problem === 'IMAGE_EMPTY' ? t('richEditor.uploadEmpty') : t('richEditor.uploadUnsupported');
    }

    /**
     * 이미지 올리기 오류를 화면 상태로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류(앱 `ApiError` 또는 그 밖의 것).
     * @returns {RichUploadFailure} 실패 정보.
     */
    static failure(t: Translate, e: unknown): RichUploadFailure {
        const status = isErrorLike(e) && typeof e.status === 'number' ? e.status : 0;
        const code = isErrorLike(e) && typeof e.code === 'string' ? e.code : 'NETWORK';
        const serverMessage = isErrorLike(e) && typeof e.message === 'string' ? e.message.trim() : '';
        // 프록시·로드밸런서가 본문 없이 413 을 돌려주면 서버 코드가 없다(앱 클라이언트가 `HTTP_413` 으로 둔다)
        const proxyTooLarge = status === 413 && code.startsWith('HTTP_');
        let message = serverMessage;
        if (proxyTooLarge) message = t('richEditor.uploadTooLarge');
        else if (!message || status === 0) message = t('richEditor.uploadNetwork');
        const retryable = status === 0 || status === 429 || status >= 500;
        return { code: proxyTooLarge ? 'IMAGE_TOO_LARGE' : code, message, retryable };
    }
}
