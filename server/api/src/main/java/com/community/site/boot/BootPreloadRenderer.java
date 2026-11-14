package com.community.site.boot;

import com.community.ad.service.PublicAdService;
import com.community.auth.controller.LoginSession;
import com.community.auth.dto.AuthUser;
import com.community.board.service.PublicBoardService;
import com.community.commerce.sales.CommerceSalesService;
import com.community.display.service.MemberDisplayService;
import com.community.groupbuy.controller.CommerceInfoController;
import com.community.home.service.GadgetService;
import com.community.installation.controller.InstallSession;
import com.community.installation.service.InstallationService;
import com.community.menu.service.MenuLimits;
import com.community.menu.service.PublicMenuService;
import com.community.popup.service.PublicSitePopupService;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.service.PublicSiteNoticeService;
import com.community.site.service.PublicSiteService;
import com.community.terms.service.PublicTermsService;
import com.community.viewsetting.service.MemberViewSettingsService;
import com.community.zconfiguration.message.RequestLocale;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 첫 화면 데이터(initial state) — 화면 주소의 {@code index.html} 을 내릴 때, 화면이 처음에 부르는 GET API 의 응답을 서버가 미리 만들어 HTML 에 JSON 으로 함께 싣는다
 * (사용자 결정 2026-11-14 「권하는 순서」 3번). 화면(user-web {@code UtilBootData} · {@code useApiClient})은 그 주소를 처음 부를 때 서버에 묻지 않고 이 응답을 쓴다.
 *
 * <p>측정(운영, 2026-11-14): 첫 화면이 API 를 17번 불렀고, 서로를 기다려 네 차례(설치 상태 → 사이트·로그인 → 표시 설정 → 메뉴·게시판·홈·광고 …)로 나뉘어 본문이 약 4.2초에 찼다.
 *
 * <ul>
 *   <li>각 응답은 <b>그 API 의 컨트롤러가 부르는 서비스를 그대로</b> 불러 같은 공통 래퍼({@link ApiResponse#success})에 담고, MVC 와 같은 {@link JsonMapper} 로 JSON 을 만든다.
 *       서비스가 아니라 컨트롤러에 있던 조립은 함께 쓰는 곳으로 옮겼다 — 로그인 상태 {@link LoginSession#current}, 판매 정보 {@link CommerceInfoController.Info#of}.</li>
 *   <li>로그인이 필요한 API(보기 설정)는 로그인한 요청일 때만 싣는다.</li>
 *   <li>실은 응답에는 그때의 언어(요청의 Accept-Language — {@link RequestLocale})를 적는다. 화면 언어가 다르면 화면이 쓰지 않고 다시 묻는다.</li>
 *   <li>하나가 실패하면(예외) 그 주소만 빼고 계속한다 — 화면은 빠진 주소를 평소처럼 서버에 묻는다.</li>
 *   <li>주소(질의 포함)는 화면이 만드는 것과 같아야 화면이 찾는다 — 화면 쪽 비교는 경로 + 정렬한 질의.</li>
 * </ul>
 */
@Service
public class BootPreloadRenderer {

    private static final Logger log = LoggerFactory.getLogger(BootPreloadRenderer.class);

    /** 화면 푸터가 부르는 메뉴 묶음(user-web 푸터 메뉴 `?set=footer`) */
    private static final String FOOTER_SET = "footer";
    /** 모든 화면의 날개 광고 · 홈의 띠·네모·한 줄 광고(화면이 보내는 `types` 그대로) */
    private static final String WING_TYPES = "WING_BANNER";
    private static final String HOME_AD_TYPES = "STRIP_BANNER,SQUARE_BANNER,TEXT_LINE";

    private final JsonMapper json;
    private final InstallationService installation;
    private final InstallSession installSession;
    private final PublicSiteService site;
    private final LoginSession loginSession;
    private final MemberDisplayService display;
    private final MemberViewSettingsService viewSettings;
    private final PublicBoardService boards;
    private final PublicMenuService menus;
    private final PublicAdService ads;
    private final PublicSitePopupService popups;
    private final PublicSiteNoticeService notice;
    private final PublicTermsService terms;
    private final CommerceSalesService sales;
    private final GadgetService gadgets;

    public BootPreloadRenderer(JsonMapper json, InstallationService installation, InstallSession installSession, PublicSiteService site, LoginSession loginSession,
                               MemberDisplayService display, MemberViewSettingsService viewSettings, PublicBoardService boards, PublicMenuService menus,
                               PublicAdService ads, PublicSitePopupService popups, PublicSiteNoticeService notice, PublicTermsService terms,
                               CommerceSalesService sales, GadgetService gadgets) {
        this.json = json;
        this.installation = installation;
        this.installSession = installSession;
        this.site = site;
        this.loginSession = loginSession;
        this.display = display;
        this.viewSettings = viewSettings;
        this.boards = boards;
        this.menus = menus;
        this.ads = ads;
        this.popups = popups;
        this.notice = notice;
        this.terms = terms;
        this.sales = sales;
        this.gadgets = gadgets;
    }

    /**
     * 이 화면 주소에서 미리 만들어 둘 API 응답들 — {@code {"lang":"ko","responses":{"/api/site/info":{"s":200,"b":{…}}, …}}}. 실을 것이 없으면 null
     *
     * @param request  화면 주소 요청(보안 필터 체인을 지난 것 — 로그인 정보가 들어 있다)
     * @param response 그 응답 — 로그인 상태 응답이 세션 쿠키 기한을 늘릴 때 쓴다
     * @param path     화면 주소(서블릿 경로)
     */
    public String render(HttpServletRequest request, HttpServletResponse response, String path) {
        long started = System.nanoTime();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        PublicSiteSnapshot snapshot = site.snapshot();

        Map<String, Supplier<Object>> parts = new LinkedHashMap<>();
        parts.put("/api/system/installation/status", () -> installation.status(installSession.isAuthorized(request)));
        parts.put("/api/site/theme", snapshot::theme);
        parts.put("/api/site/info", snapshot::info);
        parts.put("/api/auth/me", () -> loginSession.current(request, response, authentication));
        parts.put("/api/display/effective", () -> display.effective(userId));
        if (userId != null) {
            parts.put("/api/me/settings", () -> viewSettings.mine(userId));
        }
        parts.put("/api/boards", boards::boards);
        parts.put("/api/menu", () -> menus.menu(MenuLimits.MAIN_SET, userId));
        parts.put("/api/menu?set=" + FOOTER_SET, () -> menus.menu(FOOTER_SET, userId));
        parts.put("/api/ads/placements", ads::placements);
        parts.put("/api/ads?types=" + WING_TYPES, () -> ads.list(WING_TYPES, null, null));
        parts.put("/api/site/popups", () -> popups.snapshot().response());
        parts.put("/api/site/notice-bar", () -> notice.snapshot().response());
        parts.put("/api/terms/summary", terms::summary);
        parts.put("/api/commerce/info", () -> CommerceInfoController.Info.of(sales.publicInfo()));
        if ("/".equals(path) || "/index.html".equals(path)) {
            parts.put("/api/home/gadgets", () -> gadgets.gadgets(null, userId));
            parts.put("/api/ads?types=" + HOME_AD_TYPES, () -> ads.list(HOME_AD_TYPES, null, null));
        }

        Map<String, Object> responses = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<Object>> part : parts.entrySet()) {
            try {
                responses.put(part.getKey(), Map.of("s", 200, "b", ApiResponse.success(part.getValue().get())));
            } catch (RuntimeException e) {
                log.warn("첫 화면 데이터 {} 를 싣지 못해 뺀다: {}", part.getKey(), e.toString());
            }
        }
        if (responses.isEmpty()) {
            return null;
        }
        String out = json.writeValueAsString(Map.of("lang", RequestLocale.of(request).getLanguage(), "responses", responses));
        if (log.isDebugEnabled()) {
            log.debug("첫 화면 데이터 {}개를 실었다({}) — {}ms", responses.size(), path, (System.nanoTime() - started) / 1_000_000);
        }
        return out;
    }
}
