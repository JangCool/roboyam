package com.community.analytics.collect;

import com.community.board.service.BoardCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 방문 주소의 <b>라우트 표</b> — 신호의 경로·직전 경로를 user-web 라우트({@code apps/user-web/app/pages}) 기준의 「정해진 주소」로 바꾸고 화면 종류({@code kind})를 정한다
 * (site-analytics-contract 「3단계 결정 ②」·「검토 보강」). 원본·페이지별·이동 흐름 모두 이 주소로 쌓는다. 신호는 누구나 보낼 수 있어 <b>임의 주소로 줄을 늘리지 못하게</b> 한다.
 *
 * <ul>
 *   <li>동적 자리 중 게시판 {@code /boards/[slug]} 의 slug · 가젯 페이지 {@code /p/[key]} 의 key 만 값을 남긴다 — 실제 있는 게시판(게시판 사본 {@link BoardCatalog}, 15초)·가젯 페이지
 *       ({@link VisitReferenceCache}, 1분)일 때만. 나머지 동적 자리({@code [id]}·{@code [eventId]}·{@code [orderNo]} …)는 모양과 상관없이 문자 그대로 {@value #ID}.</li>
 *   <li>라우트 표에 없거나 없는 slug·key 면 「알 수 없는 주소」 {@value #UNKNOWN}(화면 종류 OTHER) 한 줄로 모은다 — 버리지는 않는다(페이지뷰·방문자는 센다).</li>
 *   <li>맞추기는 vue-router 와 같게: 끝 {@code /} 하나는 뗀다 · 정적 조각은 대소문자를 가리지 않는다(표의 글자로 쓴다) · 같은 자리에 정적 주소와 동적 주소가 둘 다 맞으면
 *       정적이 이긴다(예 {@code /groups/new} · {@code /messages/new}). slug·key 는 글자 그대로 견준다.</li>
 *   <li><b>화면 종류도 이 표가 정한다</b> — 브라우저가 보낸 {@code kind} 는 무시한다(페이지별 표 열쇠가 (날짜, 주소, 종류)라 종류만 바꿔 보내 줄을 늘리지 못하게).
 *       분류 규칙은 user-web {@code utils/util-visit-kind.ts} 와 같다({@link #kindOf} — 한쪽을 고치면 같이 고친다).</li>
 *   <li>라우트를 더하면 이 표({@link #TEMPLATES})도 같이 고친다(2026-11-11 user-web {@code pages/} 전부 — 정적 주소 + 동적 13개).</li>
 * </ul>
 * 모두 서버 메모리라 신호마다 DB 를 읽지 않는다.
 */
@Component
public class VisitRoutes {

    private static final Logger log = LoggerFactory.getLogger(VisitRoutes.class);

    /** 번호 자리를 묶은 글자 */
    public static final String ID = "{id}";
    /** 라우트 표에 없거나 없는 게시판·가젯 페이지 주소 */
    public static final String UNKNOWN = "/{unknown}";

    /**
     * user-web 라우트(2026-11-11 {@code apps/user-web/app/pages} — 파일 하나 = 주소 하나, {@code index.vue} 는 폴더 주소, {@code messages.vue}·{@code settings.vue} 는 자식 라우트의 틀).
     * {@code [slug]}·{@code [key]} 는 값을 남기는 자리, 그 밖의 {@code [이름]} 은 {@value #ID} 자리.
     */
    static final List<String> TEMPLATES = List.of(
            "/",
            "/about",
            "/boards","/boards/[slug]", "/boards/[slug]/actions", "/boards/[slug]/sanctions",
            "/business", "/business/ads/new", "/business/applications", "/business/applications/[id]", "/business/apply", "/business/deals/new",
            "/dev/address", "/dev/display", "/dev/identity", "/dev/payment",
            "/group-buys", "/group-buys/[id]", "/group-buys/[id]/order",
            "/groups", "/groups/me", "/groups/new", "/groups/[id]", "/groups/[id]/write", "/groups/[id]/events/new", "/groups/[id]/events/past",
            "/groups/[id]/events/[eventId]", "/groups/[id]/events/[eventId]/edit", "/groups/[id]/manage", "/groups/[id]/manage/applications",
            "/groups/[id]/manage/chat", "/groups/[id]/manage/edit", "/groups/[id]/manage/members", "/groups/[id]/manage/publish-requests",
            "/install", "/latest", "/licenses", "/login",
            "/me", "/me/grade", "/me/items", "/me/orders", "/me/orders/[orderNo]", "/me/points", "/me/reviews", "/me/robotikon",
            "/messages", "/messages/[id]", "/messages/new",
            "/notifications", "/onboarding",
            "/p/[key]",
            "/password/forgot", "/password/reset", "/password/social",
            "/point-shop", "/point-shop/[id]",
            "/policy", "/popular",
            "/posts/[id]", "/posts/[id]/edit",
            "/privacy",
            "/robotikon", "/robotikon/[id]", "/robotikon/contract", "/robotikon/guide", "/robotikon/studio", "/robotikon/studio/[id]", "/robotikon/studio/payout",
            "/sanction", "/sanction/appeal",
            "/search",
            "/settings", "/settings/account", "/settings/account/email", "/settings/account/password", "/settings/account/withdraw", "/settings/addresses",
            "/settings/avatar-upload", "/settings/blocks", "/settings/display", "/settings/identity", "/settings/memos", "/settings/messages",
            "/settings/nickname-image", "/settings/notifications", "/settings/profile", "/settings/view",
            "/signup", "/signup/social",
            "/terms", "/tips",
            "/users/[id]",
            "/verify-email", "/withdrawn",
            "/write");

    /** 바꾼 주소 · 화면 종류 · 라우트 표로 알아본 주소인가(아니면 {@value #UNKNOWN}) */
    public record Route(String path, String kind, boolean known) {
    }

    static final Route UNKNOWN_ROUTE = new Route(UNKNOWN, "OTHER", false);

    private enum Slot {
        STATIC, ID, SLUG, KEY
    }

    /** 한 라우트 — 조각마다 정적 글자(정적 자리) 또는 자리 종류 */
    private record Template(String[] segments, Slot[] slots, String kind) {
    }

    /** 화면 종류: 앞부분이 같은 주소 묶음 — 위에서부터 처음 맞는 줄(user-web util-visit-kind.ts 의 PREFIXES) */
    private static final List<String[]> PREFIXES = List.of(
            new String[] {"/boards", "BOARD"}, new String[] {"/latest", "BOARD"}, new String[] {"/popular", "BOARD"}, new String[] {"/write", "BOARD"},
            new String[] {"/group-buys", "GROUP_BUY"}, new String[] {"/business/deals", "HOTDEAL"}, new String[] {"/groups", "GROUP"},
            new String[] {"/point-shop", "POINT_SHOP"}, new String[] {"/search", "SEARCH"}, new String[] {"/me", "MY"}, new String[] {"/settings", "MY"},
            new String[] {"/messages", "MY"}, new String[] {"/notifications", "MY"}, new String[] {"/business", "MY"}, new String[] {"/sanction", "MY"});
    /** 화면 종류: 글 보기 {@code /posts/{id}} — 고치기({@code /posts/{id}/edit})는 글쓰기라 BOARD */
    private static final Pattern POST_VIEW = Pattern.compile("^/posts/[^/]+/?$");
    private static final Pattern POST_EDIT = Pattern.compile("^/posts/[^/]+/edit/?$");

    /** 라우트 표 — 위의 화면 종류 규칙이 먼저 준비되어야 한다(정적 초기화 차례) */
    private static final List<Template> ROUTES = TEMPLATES.stream().map(VisitRoutes::template).toList();

    private final Predicate<String> boardExists;
    private final Predicate<String> pageExists;

    @Autowired
    public VisitRoutes(BoardCatalog boards, VisitReferenceCache references) {
        this(slug -> boards.snapshot().board(slug).isPresent(), references::pageKeyExists);
    }

    VisitRoutes(Predicate<String> boardExists, Predicate<String> pageExists) {
        this.boardExists = boardExists;
        this.pageExists = pageExists;
    }

    /** 시험용 — 모든 slug·key 를 있는 것으로 본다 */
    public static VisitRoutes lenient() {
        return new VisitRoutes(slug -> true, key -> true);
    }

    /**
     * @param path 쿼리·조각을 뗀 경로(「/」로 시작 — 수집기가 형식을 이미 보았다)
     * @return 정해진 주소 · 화면 종류. 모르면 {@link #UNKNOWN_ROUTE}
     */
    public Route of(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            return UNKNOWN_ROUTE;
        }
        String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String[] parts = p.equals("/") ? new String[0] : p.substring(1).split("/", -1);
        for (String part : parts) {
            if (part.isEmpty()) {
                return UNKNOWN_ROUTE;
            }
        }
        Template best = null;
        for (Template t : ROUTES) {
            if (t.segments().length == parts.length && matches(t, parts) && (best == null || before(t, best))) {
                best = t;
            }
        }
        if (best == null) {
            return UNKNOWN_ROUTE;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            out.append('/');
            switch (best.slots()[i]) {
                case STATIC -> out.append(best.segments()[i]);
                case ID -> out.append(ID);
                case SLUG, KEY -> {
                    if (!exists(best.slots()[i], parts[i])) {
                        return UNKNOWN_ROUTE;
                    }
                    out.append(parts[i]);
                }
            }
        }
        return new Route(out.isEmpty() ? "/" : out.toString(), best.kind(), true);
    }

    private boolean exists(Slot slot, String value) {
        try {
            return slot == Slot.SLUG ? boardExists.test(value) : pageExists.test(value);
        } catch (RuntimeException e) {
            log.warn("방문 주소의 게시판·가젯 페이지를 확인하지 못해 「알 수 없는 주소」로 센다: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean matches(Template t, String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (t.slots()[i] == Slot.STATIC && !t.segments()[i].equalsIgnoreCase(parts[i])) {
                return false;
            }
        }
        return true;
    }

    /** a 가 b 보다 먼저인가 — 앞 조각부터 견주어 정적 자리가 먼저 나오는 쪽(vue-router 순위와 같다) */
    private static boolean before(Template a, Template b) {
        for (int i = 0; i < a.slots().length; i++) {
            boolean sa = a.slots()[i] == Slot.STATIC;
            boolean sb = b.slots()[i] == Slot.STATIC;
            if (sa != sb) {
                return sa;
            }
        }
        return false;
    }

    private static Template template(String path) {
        String[] parts = path.equals("/") ? new String[0] : path.substring(1).split("/");
        String[] segments = new String[parts.length];
        Slot[] slots = new Slot[parts.length];
        List<String> shape = new ArrayList<>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("[") && part.endsWith("]")) {
                String name = part.substring(1, part.length() - 1);
                slots[i] = name.equals("slug") ? Slot.SLUG : name.equals("key") ? Slot.KEY : Slot.ID;
                shape.add(slots[i] == Slot.ID ? ID : name);
            } else {
                slots[i] = Slot.STATIC;
                segments[i] = part.toLowerCase(Locale.ROOT);
                shape.add(segments[i]);
            }
        }
        return new Template(segments, slots, kindOf(shape.isEmpty() ? "/" : "/" + String.join("/", shape)));
    }

    // ── 화면 종류 — user-web utils/util-visit-kind.ts 와 같은 규칙 ─────────────────

    /** 주소(라우트 모양)의 화면 종류 — 표에 없으면 OTHER */
    static String kindOf(String path) {
        if (path.equals("/") || path.isEmpty()) {
            return "HOME";
        }
        if (POST_VIEW.matcher(path).matches()) {
            return "POST";
        }
        if (POST_EDIT.matcher(path).matches()) {
            return "BOARD";
        }
        for (String[] p : PREFIXES) {
            if (path.equals(p[0]) || path.startsWith(p[0] + "/")) {
                return p[1];
            }
        }
        return "OTHER";
    }
}
