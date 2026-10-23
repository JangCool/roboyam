/**
 * 관리자 2차 인증(TOTP) 입력 손질·형식 검사 — 입력만 보고 답하는 순수 함수. 판정은 서버다(서버 규칙 이름은 서버 단계에서 정한다 — 후보 `MfaCodeRules`).
 *  - OTP: 숫자 6자리(RFC 6238, 30초 창).
 *  - 복구 코드: `XXXXX-XXXXX` — 대문자·숫자 10자, 가운데 하이픈. 대소문자를 가리지 않고 하이픈은 화면이 넣는다.
 *  - 설정 범위: 유예 1~90일 · 기기 신뢰 1~12개월(1개월 = 30일) · OTP 실패 한도 3~10회.
 */
export default class UtilMfa {
    static readonly OTP_LENGTH = 6;
    static readonly RECOVERY_CODE_COUNT = 10;
    /** 복구 코드가 이 개수 이하로 남으면 새로 만들라고 알린다 */
    static readonly RECOVERY_LOW = 3;
    static readonly GRACE_DAYS = { min: 1, max: 90, default: 14 } as const;
    static readonly TRUST_MONTHS = { min: 1, max: 12, default: 1 } as const;
    static readonly MAX_FAILURES = { min: 3, max: 10, default: 5 } as const;
    /** 실패 한도를 넘었을 때 잠그는 시간(분) — 고정 */
    static readonly LOCK_MINUTES = 10;
    /** 1개월을 며칠로 치는가 */
    static readonly DAYS_PER_MONTH = 30;

    /**
     * OTP 입력에서 숫자만 남기고 6자리로 자른다.
     * @param {string} input - 입력(붙여넣기 포함).
     * @returns {string} 숫자만, 최대 6자.
     */
    static cleanOtp(input: string): string {
        return input.replace(/\D/g, '').slice(0, UtilMfa.OTP_LENGTH);
    }

    /** @returns {boolean} 숫자 6자리면 true. */
    static isOtp(code: string): boolean {
        return /^\d{6}$/.test(code);
    }

    /**
     * 복구 코드 입력 손질 — 대문자로, 영문·숫자만 남기고 10자에서 자르고, 5자 뒤에 하이픈을 넣는다.
     * @param {string} input - 입력(소문자·공백·하이픈이 섞여도 된다).
     * @returns {string} `ABCDE-12345` 꼴(덜 쳤으면 그만큼만).
     */
    static cleanRecovery(input: string): string {
        const raw = input.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10);
        return raw.length > 5 ? `${raw.slice(0, 5)}-${raw.slice(5)}` : raw;
    }

    /** @returns {boolean} `XXXXX-XXXXX`(대문자·숫자) 꼴이면 true. */
    static isRecoveryCode(code: string): boolean {
        return /^[A-Z0-9]{5}-[A-Z0-9]{5}$/.test(code);
    }

    /**
     * 비밀키를 4자씩 띄어 쓴다(읽고 옮겨 적기 쉽게). 복사는 띄어 쓰지 않은 원문으로 한다.
     * @param {string} secret - base32 비밀키.
     * @returns {string} `ABCD EFGH …`.
     */
    static groupSecret(secret: string): string {
        return (secret.replace(/\s/g, '').match(/.{1,4}/g) ?? []).join(' ');
    }

    /**
     * 마감까지 남은 날(올림). 지났으면 0.
     * @param {string | null} iso - 마감 시각(UTC ISO).
     * @param {number} [now] - 지금(밀리초).
     * @returns {number} 남은 날 수.
     */
    static daysLeft(iso: string | null, now: number = Date.now()): number {
        if (!iso) return 0;
        const end = Date.parse(iso);
        if (Number.isNaN(end)) return 0;
        return Math.max(0, Math.ceil((end - now) / 86_400_000));
    }

    /**
     * 복구 코드 내려받기 파일의 내용 — 제목 줄(사이트·계정)과 코드 한 줄씩. 화면 문구가 아니라 파일 내용이라 줄 머리는 부르는 쪽이 넘긴다.
     * @param {string} heading - 첫 줄(예: 「roboyam 관리자 · name@example.com — 복구 코드」).
     * @param {readonly string[]} codes - 복구 코드들.
     * @param {string} note - 끝 줄 안내(한 번씩만 쓸 수 있어요 …).
     * @returns {string} 줄바꿈으로 이은 글.
     */
    static recoveryFileText(heading: string, codes: readonly string[], note: string): string {
        return [heading, '', ...codes, '', note, ''].join('\r\n');
    }

    /**
     * 복구 코드 내려받기 파일 이름 — `<사이트 이름>-recovery-codes-YYYYMMDD.txt`. 파일 이름에 쓸 수 없는 글자·공백은 `-` 로 바꾼다.
     * @returns {string} 사이트 이름이 비면 `recovery-codes-YYYYMMDD.txt`.
     */
    static recoveryFileName(site: string, now: Date = new Date()): string {
        const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
        const safe = site
            .replace(/[\\/:*?"<>|\s\p{Cc}]+/gu, '-')
            .replace(/^-+|-+$/g, '')
            .slice(0, 40);
        return `${safe ? `${safe}-` : ''}recovery-codes-${stamp}.txt`;
    }

    /**
     * 정수 범위 검사 — 설정 화면이 미리 알리는 용도(판정은 서버).
     * @returns {boolean} 정수이고 범위 안이면 true.
     */
    static inRange(value: number, range: { min: number; max: number }): boolean {
        return Number.isInteger(value) && value >= range.min && value <= range.max;
    }
}
