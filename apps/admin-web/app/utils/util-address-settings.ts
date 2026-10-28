import type { AddressImportProgress } from '~/composables/address/api/useAddressSettingsApi.types';

/** 칸 문제 — 화면이 미리 알리는 것(판정은 서버 `AddressSettingsService`·`AddressImportService` — 서버 단계에서 같은 규칙) */
export type ScriptUrlProblem = 'NOT_HTTPS' | 'TOO_LONG';
export type StaleDaysProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';
export type ImportFileProblem = 'NOT_ZIP' | 'EMPTY' | 'TOO_LARGE';

/**
 * 주소 검색 설정 화면(`/settings/address`)의 순수 계산 — 칸 검사·파일 검사·진행률.
 */
export default class UtilAddressSettings {
    /** 서버 멀티파트 한도와 같은 파일 한 개 크기 한도(300MB — 「한글 전체분」 zip 은 약 100MB 안팎) */
    static readonly FILE_MAX_BYTES = 300 * 1024 * 1024;
    static readonly SCRIPT_URL_MAX = 500;
    /** 파일 고르기 창의 거르기 */
    static readonly FILE_ACCEPT = '.zip,application/zip,application/x-zip-compressed';

    /**
     * 다음 스크립트 주소 — 비우면 기본값이라 문제 아님. `https://` 로 시작하는 500자 이하.
     * @param {string} value - 입력값.
     * @returns {ScriptUrlProblem | null} 문제, 없으면 null.
     */
    static scriptUrlProblem(value: string): ScriptUrlProblem | null {
        const v = value.trim();
        if (!v) return null;
        if (v.length > UtilAddressSettings.SCRIPT_URL_MAX) return 'TOO_LONG';
        if (!/^https:\/\/\S+$/.test(v)) return 'NOT_HTTPS';
        return null;
    }

    /** 갱신 권고 일수 글자를 정수로 — 읽을 수 없으면 null */
    static parseDays(value: string): number | null {
        const v = value.trim();
        if (!/^\d+$/.test(v)) return null;
        return Number(v);
    }

    static staleDaysProblem(value: string, min: number, max: number): StaleDaysProblem | null {
        if (!value.trim()) return 'REQUIRED';
        const n = UtilAddressSettings.parseDays(value);
        if (n === null) return 'NOT_INTEGER';
        if (n < min || n > max) return 'OUT_OF_RANGE';
        return null;
    }

    /**
     * 올릴 파일 — zip 하나, 비어 있지 않고 300MB 이하. 안의 내용(24열·14열·17열 파이프 구분, CP949)은 서버가 읽으며 본다.
     * @param {File} file - 고른 파일.
     * @returns {ImportFileProblem | null} 문제, 없으면 null.
     */
    static fileProblem(file: File): ImportFileProblem | null {
        if (!/\.zip$/i.test(file.name)) return 'NOT_ZIP';
        if (file.size === 0) return 'EMPTY';
        if (file.size > UtilAddressSettings.FILE_MAX_BYTES) return 'TOO_LARGE';
        return null;
    }

    /** 파일 크기 글자(MB, 소수 한 자리) */
    static megabytes(bytes: number): string {
        return (bytes / 1024 / 1024).toFixed(1);
    }

    /**
     * 진행률(0~100) — 아직 세는 중(`totalRows` -1)이면 null(막대를 흐르는 모양으로 그린다).
     * @param {AddressImportProgress} p - 진행 상황.
     * @returns {number | null} 백분율.
     */
    static percent(p: AddressImportProgress): number | null {
        if (p.done) return 100;
        if (p.totalRows <= 0) return null;
        return Math.min(100, Math.floor((p.processedRows / p.totalRows) * 100));
    }
}
