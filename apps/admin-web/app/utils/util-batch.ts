/** 배치 화면(배치 관리·배치 로그)이 함께 쓰는 순수 규칙 — 실행 결과 딱지의 색과 아이콘. 문구는 `util-batch-messages.ts`. */
export default class UtilBatch {
    /**
     * 결과 딱지의 색.
     * @param {string | null | undefined} status - 실행 결과 코드(`OK` · `FAIL` · `SKIP`).
     * @returns {'success' | 'danger' | 'secondary'} PrimeVue Tag 의 severity. 모르는 값은 secondary.
     */
    static statusSeverity(status: string | null | undefined): 'success' | 'danger' | 'secondary' {
        if (status === 'OK') return 'success';
        if (status === 'FAIL') return 'danger';
        return 'secondary';
    }

    /**
     * 결과를 짚는 아이콘 — 색만으로는 성공과 「할 일 없음」이 곁눈으로 안 갈리고, 색을 못 가리는 사람에게는 아예 같아 보인다.
     * @param {string | null | undefined} status - 실행 결과 코드.
     * @returns {string | undefined} primeicons 클래스. 모르는 값은 undefined.
     */
    static statusIcon(status: string | null | undefined): string | undefined {
        if (status === 'OK') return 'pi pi-check';
        if (status === 'FAIL') return 'pi pi-times';
        if (status === 'SKIP') return 'pi pi-minus';
        return undefined;
    }
}
