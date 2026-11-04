package com.community.analytics.collect;

import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 방문 신호 받기({@code POST /api/visits} — docs/features/site-analytics-contract.md 「API」). <b>DB 에 쓰지 않는다</b> — 검사·분류·해시만 하고 메모리 모음({@link VisitBuffer})에 넣는다.
 * 1분마다 {@link VisitFlusher} 가 몰아 쓴다.
 *
 * <ul>
 *   <li>조용히 버리는 신호(응답은 늘 204): 설정이 꺼짐(방문 하위 항목이 하나도 동작하지 않음) · 봇·빈 User-Agent(광고 노출과 같은 정규식) · 형식 오류(본문 JSON·UUID·경로·종류) ·
 *       해시 키({@code ROBOYAM_INDEX_KEY}) 없음.</li>
 *   <li>방문자·세션 키 = {@link BlindIndex} HMAC(용도 {@code analytics.visitor}·{@code analytics.session}, <b>날짜를 섞지 않는다</b> — 같은 브라우저는 날이 바뀌어도 같은 키라
 *       신규·재방문을 가를 수 있다). 원본 UUID·IP·원본 User-Agent 는 남기지 않는다.</li>
 *   <li>켜진 항목만 모은다: 방문자·페이지뷰·지금 접속({@code visits}) · 원본 + 페이지별 일별 조회({@code rawEvents}) · 유입처(세션 첫 페이지 = {@code prevPath} 없음, {@code referrers}) ·
 *       이동(직전 → 이번, 같은 경로 다시 보기는 빼고, {@code flows}).</li>
 *   <li>유입처: {@code utm_*} 가 있으면 CAMPAIGN, 주소가 없으면 DIRECT, 그 밖은 도메인만 남기고 분류({@link ReferrerClassifier}). 기기: User-Agent 에서 MOBILE/DESKTOP 만.</li>
 *   <li>날짜·시간 칸은 서버가 받은 시각(한국 날짜, 정시) — 브라우저 시계를 믿지 않는다.</li>
 * </ul>
 */
@Service
public class VisitCollector {

    private static final Logger log = LoggerFactory.getLogger(VisitCollector.class);

    static final DataPurpose VISITOR = DataPurpose.of("analytics.visitor");
    static final DataPurpose SESSION = DataPurpose.of("analytics.session");

    /** 받는 본문 상한(글자) — 계약의 칸(경로 300 × 2 · 주소 300 · utm 100 × 3 …)을 넉넉히 넘는 값 */
    public static final int BODY_MAX = 4096;
    static final int PATH_MAX = 300;
    static final int UTM_MAX = 100;
    private static final int UA_MAX = 512;

    static final Set<String> KINDS = Set.of("HOME", "BOARD", "POST", "GROUP_BUY", "HOTDEAL", "GROUP", "POINT_SHOP", "SEARCH", "MY", "OTHER");

    /** 세지 않는 User-Agent — 광고 노출 집계(PublicAdService.BOT)와 같은 목록이다. 한쪽을 고치면 같이 고친다 */
    private static final Pattern BOT = Pattern.compile("(?i)(bot|crawl|spider|slurp|scrap|facebookexternalhit|embedly|preview|headless|phantomjs|lighthouse|pingdom|uptime"
            + "|monitor|curl|wget|python|java/|go-http|okhttp|axios|node-fetch|httpclient|libwww|postman|insomnia)");
    private static final Pattern MOBILE = Pattern.compile("(?i)(mobi|android|iphone|ipad|ipod|windows phone|blackberry|opera mini|silk/)");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final StatsCollectionSettings settings;
    private final BlindIndex index;
    private final SnowflakeIdGenerator ids;
    private final VisitBuffer buffer = new VisitBuffer();

    public VisitCollector(StatsCollectionSettings settings, BlindIndex index, SnowflakeIdGenerator ids) {
        this.settings = settings;
        this.index = index;
        this.ids = ids;
    }

    /** 몰아쓰기가 가져간다 */
    VisitBuffer buffer() {
        return buffer;
    }

    /**
     * @param body      요청 본문(JSON 글자 — sendBeacon 은 text/plain 으로도 보낸다)
     * @param userAgent User-Agent 머리글
     * @param ownHost   요청을 받은 호스트(소문자) — 유입 주소가 우리 사이트면 직접으로 본다
     * @return 모았으면 true(응답에는 쓰지 않는다)
     */
    public boolean accept(String body, String userAgent, String ownHost) {
        StatsCollection s = settings.current();
        if (!s.anyVisitOn() || body == null || body.isEmpty() || body.length() > BODY_MAX || !index.isConfigured()) {
            return false;
        }
        String ua = userAgent == null ? "" : userAgent.strip();
        if (ua.isEmpty() || BOT.matcher(ua.length() > UA_MAX ? ua.substring(0, UA_MAX) : ua).find()) {
            return false;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (RuntimeException e) {
            return false;
        }
        if (root == null || !root.isObject()) {
            return false;
        }
        UUID visitorId = uuid(root.get("visitorId"));
        UUID sessionId = uuid(root.get("sessionId"));
        String path = path(root.get("path"));
        String kind = text(root.get("kind"), 20);
        if (visitorId == null || sessionId == null || path == null || kind == null || !KINDS.contains(kind)) {
            return false;
        }
        String prevPath = path(root.get("prevPath"));
        boolean newVisitor = root.get("newVisitor") != null && root.get("newVisitor").isBoolean() && root.get("newVisitor").booleanValue();
        JsonNode utm = root.get("utm");
        String utmSource = utm != null && utm.isObject() ? text(utm.get("source"), UTM_MAX) : null;
        String utmMedium = utm != null && utm.isObject() ? text(utm.get("medium"), UTM_MAX) : null;
        String utmCampaign = utm != null && utm.isObject() ? text(utm.get("campaign"), UTM_MAX) : null;

        String visitorHash;
        String sessionHash;
        try {
            visitorHash = index.hash(VISITOR, visitorId.toString());
            sessionHash = index.hash(SESSION, sessionId.toString());
        } catch (RuntimeException e) {
            log.warn("방문자 해시를 만들지 못해 이 신호는 세지 않는다: {}", e.getClass().getSimpleName());
            return false;
        }

        Instant now = Instant.now();
        LocalDate day = ServiceTime.dateOf(now);
        Instant hour = now.truncatedTo(ChronoUnit.HOURS); // 한국 시간은 UTC 와 정시 단위로 어긋나 한국 정시와 같다
        boolean sessionStart = prevPath == null;
        ReferrerClassifier.Referrer referrer = null;
        if (sessionStart) {
            boolean hasUtm = utmSource != null || utmMedium != null || utmCampaign != null;
            referrer = ReferrerClassifier.classify(text(root.get("referrer"), PATH_MAX), hasUtm, ownHost);
        }

        if (s.visitsOn()) {
            buffer.visit(day, visitorHash, hour, newVisitor, now);
        }
        if (s.rawEventsOn()) {
            buffer.raw(new VisitBuffer.RawView(ids.nextId(), now, visitorHash, sessionHash, path, kind, prevPath, referrer == null ? null : referrer.domain(),
                    referrer == null ? null : referrer.group(), utmSource, utmMedium, utmCampaign, MOBILE.matcher(ua).find() ? "MOBILE" : "DESKTOP", newVisitor), day);
        }
        if (s.referrersOn() && referrer != null) {
            buffer.referrer(day, referrer.group(), referrer.domain());
        }
        // 이동 — 새로 고침(같은 경로 다시)은 이동이 아니다(화면 쪽 합의 2026-11-04)
        if (s.flowsOn() && prevPath != null && !prevPath.equals(path)) {
            buffer.flow(day, prevPath, path);
        }
        return true;
    }

    private static UUID uuid(JsonNode node) {
        String v = text(node, 36);
        if (v == null || v.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(v.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 쿼리·조각을 뗀 경로 — 「/」로 시작(「//」 아님), 공백·제어 문자 없음, {@value #PATH_MAX}자 이하. 아니면 null */
    static String path(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String v = node.asString();
        int cut = v.length();
        int q = v.indexOf('?');
        int h = v.indexOf('#');
        if (q >= 0) {
            cut = Math.min(cut, q);
        }
        if (h >= 0) {
            cut = Math.min(cut, h);
        }
        v = v.substring(0, cut);
        if (v.isEmpty() || v.length() > PATH_MAX || v.charAt(0) != '/' || v.startsWith("//") || CONTROL.matcher(v).find()) {
            return null;
        }
        return v;
    }

    /** 글자 칸 — 비었거나 글자가 아니면 null, 길면 자른다 */
    private static String text(JsonNode node, int max) {
        if (node == null || !node.isString()) {
            return null;
        }
        String v = node.asString().strip();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() > max ? v.substring(0, max) : v;
    }
}
