import type { NicknameImageStatus } from '~/composables/member/api/useNicknameImageApi.types';

/** 반려·해제 사유 입력의 문제 */
export type NicknameImageReasonProblem = 'REQUIRED' | 'TOO_LONG';

/**
 * 닉네임 이미지 심사(`/members/nickname-images`)의 순수 규칙 — 상태 탭·딱지 모양, 파일 정보 글자, 사유 입력 검사. 상태도 요청도 없다.
 * 사유 규칙(1~200자)은 서버 검증(`400 VALIDATION fields.reason`)과 같고 판정은 늘 서버가 한다. 문구는 화면이 `nicknameImage.*` 키로 고른다.
 */
export default class UtilNicknameImage {
    /** 탭 차례 — 심사 대기가 먼저 */
    static readonly STATUSES: readonly NicknameImageStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'REVOKED'];

    /** 반려·해제 사유 최대 글자 수 */
    static readonly REASON_MAX = 200;

    /** 서버가 맞춰 저장하는 그림 크기 — 미리보기를 이 크기 그대로 그린다 */
    static readonly WIDTH = 240;
    static readonly HEIGHT = 60;

    /** 확대 보기 배율 */
    static readonly ZOOM = 3;

    /**
     * 주소의 `?status=` 값이 탭 상태인가 — 운영자 알림(`NICKNAME_IMAGE_PENDING`)의 링크 등으로 들어올 때 첫 탭을 고른다.
     * @param {unknown} value - `route.query.status`.
     * @returns {NicknameImageStatus | null} 알맞은 상태, 아니면 null.
     */
    static statusOf(value: unknown): NicknameImageStatus | null {
        return typeof value === 'string' && (UtilNicknameImage.STATUSES as readonly string[]).includes(value) ? (value as NicknameImageStatus) : null;
    }

    /**
     * 상태 딱지·탭의 아이콘.
     * @param {NicknameImageStatus} status - 상태.
     * @returns {string} PrimeIcons 클래스.
     */
    static statusIcon(status: NicknameImageStatus): string {
        if (status === 'PENDING') return 'pi pi-hourglass';
        if (status === 'APPROVED') return 'pi pi-check-circle';
        if (status === 'REJECTED') return 'pi pi-times-circle';
        return 'pi pi-ban';
    }

    /**
     * 상태 딱지의 색.
     * @param {NicknameImageStatus} status - 상태.
     * @returns {'warn' | 'success' | 'danger' | 'secondary'} PrimeVue Tag severity.
     */
    static statusSeverity(status: NicknameImageStatus): 'warn' | 'success' | 'danger' | 'secondary' {
        if (status === 'PENDING') return 'warn';
        if (status === 'APPROVED') return 'success';
        if (status === 'REJECTED') return 'danger';
        return 'secondary';
    }

    /**
     * 형식 이름 — `image/png` → `PNG`. 모르는 꼴은 받은 글자 그대로.
     * @param {string} contentType - 서버가 준 형식.
     * @returns {string} 짧은 형식 이름.
     */
    static formatName(contentType: string): string {
        const match = /^image\/([a-z0-9.+-]+)$/i.exec(contentType.trim());
        if (!match) return contentType;
        const sub = match[1]!.toLowerCase();
        return (sub === 'jpeg' ? 'jpg' : sub).toUpperCase();
    }

    /**
     * 크기를 KB 로 — 소수 첫째 자리까지.
     * @param {number} bytes - 바이트 수.
     * @param {string} locale - 숫자 표기 언어.
     * @returns {string} 예: `12.3`.
     */
    static kilobytes(bytes: number, locale: string): string {
        return (Math.max(0, bytes) / 1024).toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    }

    /**
     * 사유가 문제 없으면 null — 앞뒤 공백을 뗀 글자 수(유니코드 한 글자씩)로 본다.
     * @param {string} text - 입력한 사유.
     * @returns {NicknameImageReasonProblem | null} 문제.
     */
    static reasonProblem(text: string): NicknameImageReasonProblem | null {
        const length = Array.from(text.trim()).length;
        if (length === 0) return 'REQUIRED';
        if (length > UtilNicknameImage.REASON_MAX) return 'TOO_LONG';
        return null;
    }
}
