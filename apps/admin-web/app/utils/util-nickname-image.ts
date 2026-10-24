import { ApiError } from '~/composables/common/useApiClient';
import type { NicknameImageGradeOption, NicknameImageSettingsField, NicknameImageSettingsMode, NicknameImageSettingsRequest, NicknameImageStatus } from '~/composables/member/api/useNicknameImageApi.types';

/** 반려·해제 사유 입력의 문제 */
export type NicknameImageReasonProblem = 'REQUIRED' | 'TOO_LONG';

/** 신청 조건의 숫자 칸 */
export type NicknameImageNumberField = 'minDays' | 'minActivity' | 'minPoints';

/**
 * 신청 조건 입력 중인 값 — 숫자 칸은 글자로 들고 저장할 때 바꾼다(`InputNumber` 는 범위 밖 값을 말없이 고친다 — 포인트 정책과 같은 이유).
 * 등급은 `''` = 등급 조건 없음(고르개가 null 값 항목을 다루기 어려워 빈 글자로 든다) — 보낼 때 null.
 */
export interface NicknameImageSettingsForm {
    mode: NicknameImageSettingsMode;
    gradeKey: string;
    minDays: string;
    minActivity: string;
    minPoints: string;
    requireEmailVerified: boolean;
}

/** 칸의 문제 — 숫자 칸(빈칸·정수 아님·범위 밖) · 등급(등급 기준인데 안 고름 · 지워진 등급) */
export type NicknameImageSettingsProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE' | 'GRADE_REQUIRED' | 'GRADE_MISSING';

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

    // ── 신청 조건 설정(`/settings/nickname-image`) ─ 범위는 서버 검증과 같고 판정은 늘 서버가 한다

    /** 방식 차례 — 등급 기준이 먼저(기본값) */
    static readonly MODES: readonly NicknameImageSettingsMode[] = ['GRADE', 'CUSTOM'];

    /** 숫자 칸 차례와 범위(0 = 그 조건 없음) */
    static readonly NUMBER_FIELDS: readonly NicknameImageNumberField[] = ['minDays', 'minActivity', 'minPoints'];
    static readonly LIMITS: Readonly<Record<NicknameImageNumberField, readonly [number, number]>> = {
        minDays: [0, 3650],
        minActivity: [0, 100000],
        minPoints: [0, 100000000],
    };

    /** 서버가 칸 오류로 돌려줄 수 있는 칸 */
    static readonly SETTINGS_FIELDS: readonly NicknameImageSettingsField[] = ['mode', 'gradeKey', 'minDays', 'minActivity', 'minPoints', 'requireEmailVerified'];

    /** 저장된 값 → 입력 값 */
    static settingsFormOf(value: NicknameImageSettingsRequest): NicknameImageSettingsForm {
        return {
            mode: value.mode,
            gradeKey: value.gradeKey ?? '',
            minDays: String(value.minDays),
            minActivity: String(value.minActivity),
            minPoints: String(value.minPoints),
            requireEmailVerified: value.requireEmailVerified,
        };
    }

    /**
     * 숫자 칸 글자를 정수로 — 앞뒤 공백·천 단위 쉼표는 떼고 본다.
     * @param {string} text - 입력한 글자.
     * @returns {number | null} 0 이상의 정수가 아니면 null.
     */
    static parseCount(text: string): number | null {
        const cleaned = text.trim().replace(/,/g, '');
        if (!/^\d+$/.test(cleaned)) return null;
        return Number(cleaned);
    }

    /**
     * 칸별 문제 — 화면이 미리 알리는 용도. 숫자 칸은 사용자 정의일 때만 본다(등급 기준이면 쓰이지 않는다).
     * @param {NicknameImageSettingsForm} form - 입력 값.
     * @param {readonly NicknameImageGradeOption[]} grades - 고를 수 있는 등급.
     * @returns {Partial<Record<NicknameImageSettingsField, NicknameImageSettingsProblem>>} 문제가 있는 칸만.
     */
    static settingsProblems(form: NicknameImageSettingsForm, grades: readonly NicknameImageGradeOption[]): Partial<Record<NicknameImageSettingsField, NicknameImageSettingsProblem>> {
        const out: Partial<Record<NicknameImageSettingsField, NicknameImageSettingsProblem>> = {};
        if (form.gradeKey && !grades.some((grade) => grade.key === form.gradeKey)) out.gradeKey = 'GRADE_MISSING';
        else if (form.mode === 'GRADE' && !form.gradeKey) out.gradeKey = 'GRADE_REQUIRED';
        if (form.mode !== 'CUSTOM') return out;
        for (const field of UtilNicknameImage.NUMBER_FIELDS) {
            if (!form[field].trim()) {
                out[field] = 'REQUIRED';
                continue;
            }
            const value = UtilNicknameImage.parseCount(form[field]);
            const [min, max] = UtilNicknameImage.LIMITS[field];
            if (value === null) out[field] = 'NOT_INTEGER';
            else if (value < min || value > max) out[field] = 'OUT_OF_RANGE';
        }
        return out;
    }

    /**
     * 보낼 값 — 등급 기준이면 이메일 인증은 늘 켜서 보낸다. 숫자 칸이 정수가 아니면(등급 기준에서 비운 칸 등) 저장된 값을 그대로 보낸다.
     * @param {NicknameImageSettingsForm} form - 입력 값.
     * @param {NicknameImageSettingsRequest} saved - 저장된 값.
     * @returns {NicknameImageSettingsRequest} 요청 본문.
     */
    static toSettingsRequest(form: NicknameImageSettingsForm, saved: NicknameImageSettingsRequest): NicknameImageSettingsRequest {
        const count = (field: NicknameImageNumberField): number => UtilNicknameImage.parseCount(form[field]) ?? saved[field];
        return {
            mode: form.mode,
            gradeKey: form.gradeKey || null,
            minDays: count('minDays'),
            minActivity: count('minActivity'),
            minPoints: count('minPoints'),
            requireEmailVerified: form.mode === 'GRADE' ? true : form.requireEmailVerified,
        };
    }

    /**
     * 저장된 값과 다른 칸 — 숫자 칸은 정수로 바꿔 비교한다(`030` 과 `30` 은 같다).
     * @param {NicknameImageSettingsForm} form - 입력 값.
     * @param {NicknameImageSettingsRequest} saved - 저장된 값.
     * @returns {NicknameImageSettingsField[]} 바뀐 칸.
     */
    static changedSettings(form: NicknameImageSettingsForm, saved: NicknameImageSettingsRequest): NicknameImageSettingsField[] {
        const out: NicknameImageSettingsField[] = [];
        if (form.mode !== saved.mode) out.push('mode');
        if ((form.gradeKey || null) !== saved.gradeKey) out.push('gradeKey');
        for (const field of UtilNicknameImage.NUMBER_FIELDS) {
            const value = UtilNicknameImage.parseCount(form[field]);
            if (value === null ? form[field].trim() !== String(saved[field]) : value !== saved[field]) out.push(field);
        }
        // 등급 기준은 이메일 인증을 늘 켜서 보낸다 — 보낼 값으로 비교한다
        const email = form.mode === 'GRADE' ? true : form.requireEmailVerified;
        if (email !== saved.requireEmailVerified) out.push('requireEmailVerified');
        return out;
    }

    /**
     * 서버가 칸별로 거절한 문구(`400 VALIDATION fields.<칸>`) — 아는 칸은 칸 아래로, 모르는 칸은 한 줄로 모은다.
     * @param {unknown} e - 저장 오류.
     * @returns {{ fields: Partial<Record<NicknameImageSettingsField, string>>; rest: string } | null} 칸 오류가 없으면 null.
     */
    static settingsFieldErrorsOf(e: unknown): { fields: Partial<Record<NicknameImageSettingsField, string>>; rest: string } | null {
        if (!(e instanceof ApiError) || !e.fields || Object.keys(e.fields).length === 0) return null;
        const fields: Partial<Record<NicknameImageSettingsField, string>> = {};
        const rest: string[] = [];
        for (const [key, message] of Object.entries(e.fields)) {
            if ((UtilNicknameImage.SETTINGS_FIELDS as readonly string[]).includes(key)) fields[key as NicknameImageSettingsField] = message;
            else rest.push(message);
        }
        return { fields, rest: rest.join(' ') };
    }
}
