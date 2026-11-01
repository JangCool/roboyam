package com.community.hotdeal.og;

import java.util.List;

/**
 * 상품 링크 가져오기(서버 규칙 1·2) — 운영 구현은 {@link SafeDealPageFetcher}(SSRF 방어 클라이언트). 시험은 외부 사이트를 부르지 않도록 이 자리를 바꾼다.
 */
public interface DealPageFetcher {

    /** 한 번 가져온 결과 */
    enum Outcome {
        /** 2xx 이고 HTML(머리를 읽었으면 OG 가 들어 있다) */
        OK,
        /** 2xx 인데 HTML 이 아니다 */
        NOT_HTML,
        /** 막힌 주소(사설망·메타데이터 등)로 가려 했다 — 체인이 거기서 멈춘다 */
        BLOCKED,
        /** 리디렉션이 3회를 넘었다 */
        TOO_MANY_REDIRECTS,
        /** 연결·시간 초과·4xx·5xx·이름 못 풂·모양이 틀림 */
        FAILED
    }

    /**
     * 상품 페이지.
     *
     * @param hosts    체인의 모든 호스트(처음 주소 → 리디렉션 차례, 정규화·중복 없음 — 따라가지 못한 마지막 Location 의 호스트도 든다). 판정(규칙 2)의 대상
     * @param finalUrl 마지막으로 연 주소(따라가지 못했으면 마지막으로 시도한 주소)
     * @param og       머리를 읽었고 HTML 이면 OG, 아니면 null
     * @param bodyBytes 읽은 본문 바이트 수(시험용 — 1MB 컷)
     */
    record Page(List<String> hosts, String finalUrl, Outcome outcome, OgTags og, int bodyBytes) {
    }

    /** OG 칸(없으면 null) — {@code imageUrl} 은 절대 주소로 바꾼 것 */
    record OgTags(String title, String siteName, String imageUrl) {
    }

    /**
     * 그림 파일.
     *
     * @param data 받은 바이트(실패면 null)
     */
    record Image(Outcome outcome, byte[] data) {
    }

    /**
     * 상품 페이지를 가져온다 — 리디렉션 최대 3회, 매 단계 주소 검사. 실패해도 던지지 않는다.
     *
     * @param readHead HTML 머리를 읽어 OG 를 뽑는다(아니면 본문을 읽지 않는다 — 체인만)
     */
    Page fetchPage(String url, boolean readHead);

    /** OG 그림을 가져온다 — 같은 클라이언트·같은 검사, {@code image/*} 만, 상한 {@link SafeDealPageFetcher#IMAGE_MAX_BYTES}. 실패해도 던지지 않는다 */
    Image fetchImage(String url);
}
