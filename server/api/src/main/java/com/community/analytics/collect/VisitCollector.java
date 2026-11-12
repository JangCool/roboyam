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
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 방문 신호 받기({@code POST /api/visits} — docs/features/site-analytics-contract.md 「API」)와 2단계의 검색어·봇 세기. <b>DB 에 쓰지 않는다</b> — 검사·분류·해시만 하고
 * 메모리 모음({@link VisitBuffer})에 넣는다. 1분마다 {@link VisitFlusher} 가 몰아 쓴다.
 *
 * <ul>
 *   <li>조용히 버리는 신호(응답은 늘 204): 설정이 꺼짐(방문 하위 항목이 하나도 동작하지 않음) · 봇·빈 User-Agent({@link BotClassifier}) · 형식 오류(본문 JSON·UUID·경로) ·
 *       해시 키({@code IROBIT_INDEX_KEY}) 없음 · <b>뺄 계정</b>(3단계 — 로그인 세션의 회원이 운영자 회원 계정·AI 회원, {@link VisitReferenceCache}). 설정이 켜진 동안 봇·형식 오류·
 *       뺄 계정으로 버린 신호는 수집 진단용으로 센다(내용은 남기지 않는다). 로그인 회원은 부르는 쪽(컨트롤러 — {@link VisitSessionMembers})이 넘긴다.</li>
 *   <li><b>주소·화면 종류는 라우트 표</b>({@link VisitRoutes}, 3단계)가 정한다 — 경로·직전 경로를 정해진 주소({@code /posts/{id}} …)로 바꾸고 원본·페이지별·이동 흐름 모두 그 주소로
 *       쌓는다. 브라우저가 보낸 {@code kind} 는 읽지 않는다. 라우트 표에 없거나 없는 게시판·가젯 페이지면 「알 수 없는 주소」로 묶고 진단에 센다.</li>
 *   <li>방문자·세션 키 = {@link BlindIndex} HMAC(용도 {@code analytics.visitor}·{@code analytics.session}, <b>날짜를 섞지 않는다</b> — 같은 브라우저는 날이 바뀌어도 같은 키라
 *       신규·재방문을 가를 수 있다). 원본 UUID·IP·원본 User-Agent 는 남기지 않는다.</li>
 *   <li>켜진 항목만 모은다: 방문자·페이지뷰·지금 접속 + 로그인 회원(회원 주·월 순방문자, 3단계 결정 ④) + 페이지별 일별 조회({@code visits} — 3단계부터 페이지별은 여기에 딸린다) · 원본({@code rawEvents}) · 유입처 + 캠페인 utm 묶음
 *       (세션 첫 페이지 = {@code prevPath} 없음, {@code referrers}) · 이동(직전 → 이번, 같은 경로 다시 보기(받은 주소 그대로 견준다)는 빼고 — 세션 첫 페이지는 직전 {@code ""}
 *       「바로 들어옴」, {@code flows}) · 환경(세션 첫 페이지의 {@code env} 분류값 + 서버가 UA 로 가른 기기, {@code environment}).</li>
 *   <li>유입처: {@code utm_*} 가 있으면 CAMPAIGN, 주소가 없으면 DIRECT, 그 밖은 도메인만 남기고 분류({@link ReferrerClassifier}). 원본의 기기: User-Agent 에서 MOBILE/DESKTOP 만(1단계 그대로).</li>
 *   <li>환경의 기기: MOBILE · TABLET · DESKTOP — UA 로 가르고, 맥 UA 인데 브라우저가 iOS 라고 분류했으면(iPadOS 13 뒤의 아이패드) TABLET.
 *       {@code env} 의 브라우저·OS 가 허용 목록 밖이면 OTHER, 화면 구간이 목록 밖이거나 {@code env} 가 객체가 아니면 환경만 세지 않는다(신호의 나머지는 그대로).</li>
 *   <li>검색어({@link #countSearch}) · 봇({@link #countBotPage}) — 신호가 아니라 그 요청을 받는 곳(검색 API · 화면 HTML 필터)이 부른다. 같은 모음·같은 1분 몰아쓰기.</li>
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

    /** 화면 종류 값(계약 「API」) — 3단계부터 라우트 표({@link VisitRoutes})가 정하고 브라우저 값은 읽지 않는다 */
    static final Set<String> KINDS = Set.of("HOME", "BOARD", "POST", "GROUP_BUY", "HOTDEAL", "GROUP", "POINT_SHOP", "SEARCH", "MY", "OTHER");
    /** 환경 분류값 — user-web {@code @irobit/analytics} 의 {@code util-visit-env.ts} · admin-web {@code useVisitAnalyticsApi.types.ts} 와 같은 값 */
    static final Set<String> BROWSERS = Set.of("CHROME", "SAFARI", "EDGE", "SAMSUNG", "WHALE", "FIREFOX", "OTHER");
    static final Set<String> OSES = Set.of("WINDOWS", "MACOS", "ANDROID", "IOS", "LINUX", "OTHER");
    static final Set<String> SCREENS = Set.of("W480", "W768", "W1024", "W1440", "WIDE");

    /** 검색어 저장 길이(글자 — 코드 포인트) */
    static final int TERM_MIN = 2;
    static final int TERM_MAX = 50;

    private static final Pattern MOBILE = Pattern.compile("(?i)(mobi|android|iphone|ipad|ipod|windows phone|blackberry|opera mini|silk/)");
    private static final Pattern TABLET = Pattern.compile("(?i)(ipad|tablet|kindle|silk/|playbook)");
    private static final Pattern PHONE_LIKE = Pattern.compile("(?i)(iphone|ipod|windows phone|blackberry|opera mini|mobi)");
    private static final Pattern ANDROID = Pattern.compile("(?i)android");
    private static final Pattern MAC = Pattern.compile("(?i)macintosh");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    /** 공백을 한 칸으로 줄인 뒤에도 제어 문자가 남은 검색어(남기지 않는다) */
    private static final Pattern CONTROL_ONLY = Pattern.compile("\\p{Cntrl}");
    /** 저장하지 않는 검색어 모양 — 이메일 · 전화번호(국내 휴대·지역 번호, +82) · 주민등록번호 */
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?82[-.\\s]?|0)\\d{1,2}[-.\\s)]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");
    private static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{6}[-\\s]?[1-8]\\d{6}(?!\\d)");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final StatsCollectionSettings settings;
    private final BlindIndex index;
    private final SnowflakeIdGenerator ids;
    private final VisitRoutes routes;
    /** 뺄 계정 목록(시험용 생성자는 null — 뺄 계정 없음) */
    private final VisitReferenceCache references;
    private final VisitBuffer buffer = new VisitBuffer();

    @Autowired
    public VisitCollector(StatsCollectionSettings settings, BlindIndex index, SnowflakeIdGenerator ids, VisitRoutes routes, VisitReferenceCache references) {
        this.settings = settings;
        this.index = index;
        this.ids = ids;
        this.routes = routes;
        this.references = references;
    }

    /** 시험용 — 라우트 표는 모든 게시판 slug·가젯 페이지 key 를 있는 것으로 보고({@link VisitRoutes#lenient}), 뺄 계정은 없다 */
    public VisitCollector(StatsCollectionSettings settings, BlindIndex index, SnowflakeIdGenerator ids) {
        this(settings, index, ids, VisitRoutes.lenient(), null);
    }

    /** 몰아쓰기가 가져간다 */
    VisitBuffer buffer() {
        return buffer;
    }

    /** 로그인하지 않은 신호로 받는다({@link #accept(String, String, String, Supplier)}) */
    public boolean accept(String body, String userAgent, String ownHost) {
        return accept(body, userAgent, ownHost, () -> null);
    }

    /**
     * @param body      요청 본문(JSON 글자 — sendBeacon 은 text/plain 으로도 보낸다)
     * @param userAgent User-Agent 머리글
     * @param ownHost   요청을 받은 호스트(소문자) — 유입 주소가 우리 사이트면 직접으로 본다
     * @param member    로그인 회원 번호(없으면 null) — 형식 검사를 지난 신호에서만 부른다(세션 사본이 비었을 때만 DB 를 읽는다)
     * @return 모았으면 true(응답에는 쓰지 않는다)
     */
    public boolean accept(String body, String userAgent, String ownHost, Supplier<Long> member) {
        StatsCollection s = settings.current();
        if (!s.anyVisitOn()) {
            return false;
        }
        if (body == null || body.isEmpty() || body.length() > BODY_MAX) {
            buffer.ignoredInvalid();
            return false;
        }
        if (!index.isConfigured()) {
            return false;
        }
        String ua = userAgent == null ? "" : userAgent.strip();
        if (BotClassifier.isBot(ua)) {
            buffer.ignoredBot();
            return false;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (RuntimeException e) {
            buffer.ignoredInvalid();
            return false;
        }
        if (root == null || !root.isObject()) {
            buffer.ignoredInvalid();
            return false;
        }
        UUID visitorId = uuid(root.get("visitorId"));
        UUID sessionId = uuid(root.get("sessionId"));
        String path = path(root.get("path"));
        if (visitorId == null || sessionId == null || path == null) {
            buffer.ignoredInvalid();
            return false;
        }
        // 로그인 회원(3단계) — 뺄 계정 가르기와 회원 주·월 순방문자에 쓴다(세션 사본이 비었을 때만 DB 를 읽는다, 한 번만 묻는다)
        Long memberId = member == null ? null : member.get();
        // 뺄 계정(3단계) — 로그인 세션의 회원이 운영자 회원 계정·AI 회원이면 이 신호는 통계 어디에도 넣지 않는다
        if (references != null && references.excludedMember(memberId)) {
            buffer.ignoredExcluded();
            return false;
        }
        String prevPath = path(root.get("prevPath"));
        // 라우트 표(3단계) — 정해진 주소·화면 종류(브라우저의 kind 는 읽지 않는다). 직전 경로도 같은 표로
        VisitRoutes.Route route = routes.of(path);
        if (!route.known()) {
            buffer.unknownPath();
        }
        String kind = route.kind();
        String routePath = route.path();
        String routePrev = prevPath == null ? null : routes.of(prevPath).path();
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
        boolean hasUtm = utmSource != null || utmMedium != null || utmCampaign != null;
        ReferrerClassifier.Referrer referrer = null;
        if (sessionStart) {
            referrer = ReferrerClassifier.classify(text(root.get("referrer"), PATH_MAX), hasUtm, ownHost);
        }

        if (s.visitsOn()) {
            buffer.visit(day, visitorHash, hour, newVisitor, now);
            // 회원 주·월 순방문자(3단계 결정 ④) — 「방문자·페이지뷰」에 딸린다(새 설정 칸 없음). 로그인하지 않고 본 회원은 빠진다(방문자 쪽에는 들어간다)
            if (memberId != null) {
                buffer.member(day, memberId);
            }
        }
        // 페이지별 일별 — 3단계부터 「방문자·페이지뷰」에 딸린다(원본 기록과 무관)
        if (s.pagesOn()) {
            buffer.page(day, routePath, kind);
        }
        if (s.rawEventsOn()) {
            buffer.raw(new VisitBuffer.RawView(ids.nextId(), now, visitorHash, sessionHash, routePath, kind, routePrev, referrer == null ? null : referrer.domain(),
                    referrer == null ? null : referrer.group(), utmSource, utmMedium, utmCampaign, MOBILE.matcher(ua).find() ? "MOBILE" : "DESKTOP", newVisitor));
        }
        if (s.referrersOn() && referrer != null) {
            buffer.referrer(day, referrer.group(), referrer.domain());
            // 캠페인 일별 집계(analytics V3) — 세션 첫 페이지의 utm 묶음. 원본 기록이 아니라 유입 경로 수집에 딸린다(관리자 화면이 원본을 읽지 않게)
            if (hasUtm) {
                buffer.campaign(day, utmSource, utmMedium, utmCampaign);
            }
        }
        // 이동 — 새로 고침(같은 경로 다시)은 이동이 아니다(화면 쪽 합의 2026-11-04). 받은 주소 그대로 견준다 — 글에서 다른 글로 간 것(/posts/{id} → /posts/{id})은 이동이다.
        // 세션 첫 페이지는 직전 "" 줄 = 「바로 들어옴」(「방문자 통계」 서버 결정 2026-11-04 — 경로는 늘 「/」로 시작해 "" 와 겹치지 않는다)
        if (s.flowsOn() && (prevPath == null || !prevPath.equals(path))) {
            buffer.flow(day, routePrev == null ? "" : routePrev, routePath);
        }
        // 환경(2단계) — 방문(세션 첫 페이지) 기준. 브라우저는 세션 첫 페이지에만 env 를 싣는다
        if (s.environmentOn() && sessionStart) {
            JsonNode env = root.get("env");
            if (env != null && env.isObject()) {
                String screen = text(env.get("screen"), 10);
                if (screen != null && SCREENS.contains(screen)) {
                    String browser = allowed(text(env.get("browser"), 10), BROWSERS);
                    String os = allowed(text(env.get("os"), 10), OSES);
                    buffer.env(day, device(ua, os), browser, os, screen);
                }
            }
        }
        return true;
    }

    /**
     * 사이트 검색 한 번(검색 API 의 첫 쪽 응답 뒤 — 쪽 넘김·커서 더보기는 부르지 않는다). 검색어 수집이 꺼졌거나 봇이면 세지 않는다.
     * 다듬기(앞뒤 공백·연속 공백 한 칸·영문 소문자) 뒤 {@value #TERM_MIN}자 미만 · {@value #TERM_MAX}자 초과 · 이메일·전화번호·주민등록번호 모양은 검색어를 남기지 않고 「걸러진 검색」만 센다.
     *
     * @param query     요청의 검색어 원문
     * @param noResult  결과가 0건이었나
     * @param userAgent User-Agent 머리글
     */
    public void countSearch(String query, boolean noResult, String userAgent) {
        countSearch(query, noResult, userAgent, null);
    }

    /**
     * {@link #countSearch(String, boolean, String)} + 뺄 계정(3단계) — 검색한 로그인 회원이 운영자 회원 계정·AI 회원이면 세지 않는다(방문 신호와 같은 규칙).
     *
     * @param memberId 검색 요청의 로그인 회원 번호(없으면 null)
     */
    public void countSearch(String query, boolean noResult, String userAgent, Long memberId) {
        if (!settings.current().searchTermsOn() || query == null || BotClassifier.isBot(userAgent)) {
            return;
        }
        if (references != null && references.excludedMember(memberId)) {
            return;
        }
        LocalDate day = ServiceTime.dateOf(Instant.now());
        String term = normalizeTerm(query);
        if (term == null) {
            buffer.searchFiltered(day);
            return;
        }
        buffer.search(day, term, noResult);
    }

    /**
     * 화면 HTML 요청 한 번(공개 사이트 서버가 SPA 주소에 머리를 채운 {@code index.html} 을 내릴 때) — 봇 UA 면 종류별로 센다. 봇 수집이 꺼졌거나 사람이면 아무것도 하지 않는다.
     * 한 화면 요청 = 1(화면이 그 뒤에 부르는 {@code /api/**} 는 세지 않는다 — 요청 하나가 여러 API 를 불러도 한 번).
     */
    public void countBotPage(String userAgent) {
        if (!settings.current().botsOn()) {
            return;
        }
        String kind = BotClassifier.kind(userAgent);
        if (kind != null) {
            buffer.bot(ServiceTime.dateOf(Instant.now()), kind);
        }
    }

    /** 저장할 검색어 — 다듬은 뒤 길이·개인정보 모양에 걸리면 null */
    static String normalizeTerm(String query) {
        String term = WHITESPACE.matcher(query).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
        int length = term.codePointCount(0, term.length());
        if (length < TERM_MIN || length > TERM_MAX || CONTROL_ONLY.matcher(term).find()) {
            return null;
        }
        if (EMAIL.matcher(term).find() || PHONE.matcher(term).find() || RRN.matcher(term).find()) {
            return null;
        }
        return term;
    }

    /** 환경의 기기 — MOBILE · TABLET · DESKTOP */
    static String device(String ua, String os) {
        if (TABLET.matcher(ua).find()) {
            return "TABLET";
        }
        if (ANDROID.matcher(ua).find() && !PHONE_LIKE.matcher(ua).find()) {
            return "TABLET"; // 안드로이드 태블릿은 UA 에 Mobile 이 없다
        }
        if (MOBILE.matcher(ua).find()) {
            return "MOBILE";
        }
        if ("IOS".equals(os) && MAC.matcher(ua).find()) {
            return "TABLET"; // iPadOS 13 뒤 아이패드는 맥 UA — 브라우저가 터치 점 수로 iOS 라고 분류했다
        }
        return "DESKTOP";
    }

    private static String allowed(String value, Set<String> values) {
        return value != null && values.contains(value) ? value : "OTHER";
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
