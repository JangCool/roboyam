/**
 * 포트원 브라우저 SDK(`@portone/browser-sdk/v2`) 응답을 읽는 순수 함수 — 결제창(`/dev/payment`, 앞으로 충전·공구)·본인인증(`useIdentity`)이 같이 쓴다.
 * SDK 자체는 부르는 쪽이 클라이언트에서 동적 import 한다(`await import('@portone/browser-sdk/v2')`).
 */
export default class UtilPortone {
    /** PG 가 사용자 취소를 알릴 때의 문구 — KG이니시스 V2 테스트 창을 닫으면 「사용자가 결제를 취소하였습니다」(2026-10-27 실제 확인) */
    private static readonly PG_USER_CANCEL = /사용자.*취소/;

    /**
     * 사용자가 창을 닫아(취소해) 끝난 응답인가.
     *  - SDK 자체의 취소 코드 `Cancelled`(gRPC 취소).
     *  - PG 실패 `FAILURE_TYPE_PG` 이면서 PG 문구가 「사용자 … 취소」 — 이니시스는 창 닫기를 이 모양으로 준다(`pgCode` 없음). 코드는 카드 거절 같은 진짜 실패와 같아서 문구로만 가른다.
     * @param {string | undefined} code - SDK 응답의 `code`(실패일 때만 있다).
     * @param {string | undefined} message - SDK 응답의 `message`.
     * @returns {boolean} 사용자 취소면 true.
     */
    static isUserCancel(code: string | undefined, message: string | undefined): boolean {
        if (code === 'Cancelled') return true;
        return code === 'FAILURE_TYPE_PG' && !!message && UtilPortone.PG_USER_CANCEL.test(message);
    }
}
