import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';

/**
 * 게시판 화면의 오류 문구.
 *
 * ⚠ 단순 버전 — 원본(community)은 글쓰기 제한·댓글 차단·임시저장 등 게시판 전반의 문구를 담은 큰 파일이다.
 * 설치 마법사가 쓰는 `of` 하나만 가져왔다. 게시판 화면을 만들 때 원본의 나머지 메서드를 이 클래스에 이어 붙인다.
 */
export default class UtilBoardMessages {
    /**
     * 잡은 오류를 화면에 보일 한 줄로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 화면에 보일 한 줄. `ApiError` 면 서버 문구, 아니면 네트워크 오류 문구.
     */
    static of(t: Translate, e: unknown): string {
        return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
    }
}
