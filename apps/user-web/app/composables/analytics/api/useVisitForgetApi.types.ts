/**
 * 방문 기록 삭제 요청 — 공개 `POST /api/visits/forget` 의 모양(서버 api `VisitForget` — `docs/features/site-analytics-contract.md` 「2단계」).
 * 로그인·CSRF 없음(방문 신호 `POST /api/visits` 와 같다). 서버는 방문자 ID 원본을 저장하지 않고 해시(`BlindIndex` — 방문 기록과 같은 용도)만 요청 표에 둔다.
 * 처리는 DB 크론(5분마다)이 원본·「그 날 셌다」 표시·지금 접속에서 그 해시의 줄을 지운다. 집계 숫자는 사람을 가리키지 않아 남는다.
 */

/** 본문 */
export interface VisitForgetRequestBody {
    /** 이 브라우저의 방문자 ID(UUID — `UtilVisitStore.peekVisitorId`) */
    visitorId: string;
}

/** 202 응답 — 접수만 했다(지우기는 몇 분 안에) */
export interface VisitForgetResponse {
    /** 요청 번호 — 서버 Snowflake 숫자를 글자로(관리자 「정보주체 요청」 화면의 번호와 같다). 같은 방문자 ID 가 대기 중이면 그 요청의 번호다 */
    requestId: string;
    receivedAt: string;
}
