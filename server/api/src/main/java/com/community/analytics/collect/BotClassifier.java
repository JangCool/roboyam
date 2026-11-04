package com.community.analytics.collect;

import java.util.regex.Pattern;

/**
 * User-Agent 로 봇을 가른다(site-analytics-contract 「2단계 — 봇」). 방문 신호에서 봇을 빼는 판단({@link #isBot})과 봇 요청 세기의 종류({@link #kind})가 같은 규칙을 쓴다.
 *
 * <ul>
 *   <li>바탕은 광고 노출 집계({@code PublicAdService.BOT})·핫딜 이동({@code DealGoService.BOT})과 <b>같은 정규식</b>({@link #BOT}) — 한쪽을 고치면 같이 고친다.</li>
 *   <li>여기에 국내 검색 엔진 이름을 더한다: 네이버({@code Yeti}) · 다음({@code Daum}·{@code Daumoa}) 은 UA 에 bot/crawl 이 없어 바탕 정규식이 놓친다.</li>
 *   <li>종류(화면 {@code VISIT_BOT_KINDS}): GOOGLE · NAVER · BING · DAUM · TOOL(curl·wget·스크립트 라이브러리·헤드리스 브라우저·빈 UA) · OTHER_CRAWLER(그 밖의 바탕 정규식).</li>
 *   <li>원본 UA 는 어디에도 남기지 않는다 — 종류 이름만.</li>
 * </ul>
 */
public final class BotClassifier {

    public static final String GOOGLE = "GOOGLE";
    public static final String NAVER = "NAVER";
    public static final String BING = "BING";
    public static final String DAUM = "DAUM";
    public static final String OTHER_CRAWLER = "OTHER_CRAWLER";
    public static final String TOOL = "TOOL";

    /** 앞에서 이만큼만 본다 — 긴 머리글로 정규식을 오래 돌리지 않게 */
    static final int UA_MAX = 512;

    /** 광고 노출 집계와 같은 목록(PublicAdService.BOT · DealGoService.BOT · 예전 VisitCollector.BOT) */
    private static final Pattern BOT = Pattern.compile("(?i)(bot|crawl|spider|slurp|scrap|facebookexternalhit|embedly|preview|headless|phantomjs|lighthouse|pingdom|uptime"
            + "|monitor|curl|wget|python|java/|go-http|okhttp|axios|node-fetch|httpclient|libwww|postman|insomnia)");
    private static final Pattern GOOGLE_UA = Pattern.compile("(?i)(googlebot|google-inspectiontool|googleother|adsbot-google|mediapartners-google|apis-google"
            + "|feedfetcher-google|storebot-google|google-extended|google-read-aloud|duplexweb-google)");
    private static final Pattern NAVER_UA = Pattern.compile("(?i)(yeti/|naverbot|naver\\.me/spd)");
    private static final Pattern BING_UA = Pattern.compile("(?i)(bingbot|bingpreview|msnbot|adidxbot)");
    private static final Pattern DAUM_UA = Pattern.compile("(?i)(daumoa|daum/|daumbot)");
    private static final Pattern TOOL_UA = Pattern.compile("(?i)(curl|wget|python|java/|go-http|okhttp|axios|node-fetch|httpclient|libwww|postman|insomnia|headless|phantomjs"
            + "|lighthouse)");

    private BotClassifier() {
    }

    /** 봇인가 — 빈 UA 도 봇으로 본다(방문 신호에서 빼는 기존 규칙과 같다) */
    public static boolean isBot(String userAgent) {
        return kind(userAgent) != null;
    }

    /** 봇 종류 — 사람(봇이 아님)이면 null */
    public static String kind(String userAgent) {
        String ua = userAgent == null ? "" : userAgent.strip();
        if (ua.isEmpty()) {
            return TOOL;
        }
        if (ua.length() > UA_MAX) {
            ua = ua.substring(0, UA_MAX);
        }
        if (GOOGLE_UA.matcher(ua).find()) {
            return GOOGLE;
        }
        if (NAVER_UA.matcher(ua).find()) {
            return NAVER;
        }
        if (BING_UA.matcher(ua).find()) {
            return BING;
        }
        if (DAUM_UA.matcher(ua).find()) {
            return DAUM;
        }
        if (!BOT.matcher(ua).find()) {
            return null;
        }
        return TOOL_UA.matcher(ua).find() ? TOOL : OTHER_CRAWLER;
    }
}
