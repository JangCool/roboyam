package com.community.zconfiguration.security;

/**
 * 공개 방문 신호 주소(docs/features/site-analytics-contract.md 「API」) — 보안 설정(로그인 없이 · <b>CSRF 검사 없이</b> 받기)과 컨트롤러가 같은 값을 쓴다.
 * 화면이 페이지를 떠날 때도 {@code navigator.sendBeacon} 으로 보내 머리글({@code X-XSRF-TOKEN})을 붙일 수 없어서다(광고 집계 {@link PublicAdPaths} 와 같은 이유).
 * 남이 대신 보내도 통계 숫자만 늘고(방문자는 하루 한 번 거르기) 상태를 바꾸지 않으며, 형식이 틀리든 맞든 늘 204 다.
 *
 * <p>{@link #FORGET}(방문 기록 삭제 요청, 「2단계」)도 로그인·CSRF 없이 받는다 — 로그인과 무관한 브라우저 방문자 ID 의 기록이라 계정이 없고, 남이 대신 보내려면 그 브라우저의
 * 방문자 ID(로컬 저장소의 무작위 UUID)를 알아야 한다. 결과도 「통계 원본에서 그 ID 의 줄을 지움」뿐이다. IP 당 횟수 제한은 서버 메모리에서 한다.
 */
public final class PublicVisitPaths {

    public static final String VISITS = "/api/visits";
    public static final String FORGET = "/api/visits/forget";

    private PublicVisitPaths() {
    }
}
