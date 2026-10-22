import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BoardWriteLock, WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';

/**
 * 게시판 화면의 계산이 낀 문구 — 오류 한 줄·글쓰기/댓글이 막힌 까닭·쓰기 잠금 안내. 문구 본문은 `i18n/locales/{ko,en}/board.ts`.
 * 막힌 까닭의 판정은 서버(`BoardViewer.writeBlockedReason`·`commentBlockedReason`)이고, 여기는 그것을 글로 바꿀 뿐이다.
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

    /**
     * 글쓰기가 막힌 까닭 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {WriteBlockedReason | null} reason - 서버가 준 까닭.
     * @param {string | null} gradeLabel - 필요한 등급 이름(`writeMinGradeLabel`).
     * @returns {string} 안내 문구. 까닭이 없으면 빈 글자.
     */
    static writeBlocked(t: Translate, reason: WriteBlockedReason | null, gradeLabel: string | null): string {
        switch (reason) {
            case null:
                return '';
            case 'LOGIN_REQUIRED':
                return t('board.writeLogin');
            case 'STAFF_ONLY':
                return t('board.writeStaffOnly');
            case 'WRITE_LOCKED':
                return t('board.writeLocked');
            case 'GRADE_TOO_LOW':
                return gradeLabel ? t('board.writeGrade', { grade: gradeLabel }) : t('board.writeGradeUnknown');
            case 'EMAIL_NOT_VERIFIED':
                return t('board.writeEmail');
            case 'ACCOUNT_RESTRICTED':
                return t('board.writeRestricted');
            default:
                return t('board.writeBlocked');
        }
    }

    /**
     * 댓글이 막힌 까닭 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {WriteBlockedReason | null} reason - 서버가 준 까닭.
     * @param {string | null} gradeLabel - 필요한 등급 이름(`commentMinGradeLabel`).
     * @returns {string} 안내 문구. 까닭이 없으면 빈 글자.
     */
    static commentBlocked(t: Translate, reason: WriteBlockedReason | null, gradeLabel: string | null): string {
        switch (reason) {
            case null:
                return '';
            case 'COMMENTS_OFF':
                return t('board.commentOff');
            case 'LOGIN_REQUIRED':
                return t('board.commentLogin');
            case 'STAFF_ONLY':
                return t('board.commentStaffOnly');
            case 'WRITE_LOCKED':
                return t('board.commentLocked');
            case 'GRADE_TOO_LOW':
                return gradeLabel ? t('board.commentGrade', { grade: gradeLabel }) : t('board.commentGradeUnknown');
            case 'EMAIL_NOT_VERIFIED':
                return t('board.writeEmail');
            case 'ACCOUNT_RESTRICTED':
                return t('board.writeRestricted');
            default:
                return t('board.commentBlocked');
        }
    }

    /**
     * 읽기 전용 잠금 안내
     * @param {Translate} t - 번역 함수.
     * @param {BoardWriteLock | null | undefined} lock - 잠금 범위.
     * @returns {string} 안내 문구. 잠금이 없으면 빈 글자.
     */
    static lockNotice(t: Translate, lock: BoardWriteLock | null | undefined): string {
        if (lock === 'POST') return t('board.lockPost');
        if (lock === 'POST_COMMENT') return t('board.lockPostComment');
        return '';
    }
}
