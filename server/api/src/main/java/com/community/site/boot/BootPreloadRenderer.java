package com.community.site.boot;

import com.community.auth.dto.AuthUser;
import com.community.zconfiguration.message.RequestLocale;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 첫 화면 데이터 싣기(사용자 결정 2026-11-14 「권하는 순서」 3번) — 화면 주소의 {@code index.html} 을 내릴 때, 화면이 처음에 부르는 GET API 의 응답을
 * <b>서버 안에서 미리 불러</b> HTML 에 JSON 으로 함께 싣는다. 화면(user-web {@code UtilBootData} · {@code useApiClient})은 그 주소를 처음 부를 때 서버에 묻지 않고 이 응답을 쓴다.
 *
 * <p>측정(운영, 2026-11-14): 첫 화면이 API 를 17번 불렀고, 서로를 기다려 네 차례(설치 상태 → 사이트·로그인 → 표시 설정 → 메뉴·게시판·홈·광고 …)로 나뉘어 본문이 약 4.2초에 찼다.
 *
 * <ul>
 *   <li>응답은 <b>그 API 의 컨트롤러를 그대로</b> 부른다(서블릿 include — 같은 요청 스레드라 로그인·언어가 화면이 직접 부를 때와 같다). 그래서 응답 모양이 API 와 똑같고, 따로 맞출 코드가 없다.
 *       include 에는 서블릿 필터(보안 필터 체인 포함)가 돌지 않는다 — 그래서 <b>GET·읽기 전용이고 비로그인도 부를 수 있는 API</b>({@link #COMMON}·{@link #HOME})만 싣고,
 *       로그인이 필요한 API({@link #MEMBER})는 로그인한 요청일 때만 싣는다.</li>
 *   <li>실은 응답에는 그때의 언어(요청의 Accept-Language — {@link RequestLocale})를 적는다. 화면 언어가 다르면 화면이 쓰지 않고 다시 묻는다.</li>
 *   <li>하나가 실패하면(예외) 그 주소만 빼고 계속한다 — 화면은 빠진 주소를 평소처럼 서버에 묻는다. 상태 코드가 2xx 가 아닌 응답(오류 본문)도 그대로 실어 화면이 같은 오류로 처리한다.</li>
 *   <li>본문이 JSON 이 아니면 싣지 않는다.</li>
 * </ul>
 */
@Service
public class BootPreloadRenderer {

    private static final Logger log = LoggerFactory.getLogger(BootPreloadRenderer.class);

    /** 모든 화면 — 앱이 뜰 때 플러그인·셸(머리글·메뉴·푸터·날개 광고·공지 띠·팝업)이 부른다. 질의는 화면이 만드는 것과 같은 값 */
    static final List<String> COMMON = List.of(
            "/api/system/installation/status",
            "/api/site/theme",
            "/api/site/info",
            "/api/auth/me",
            "/api/display/effective",
            "/api/boards",
            "/api/menu",
            "/api/menu?set=footer",
            "/api/ads/placements",
            "/api/ads?types=WING_BANNER",
            "/api/site/popups",
            "/api/site/notice-bar",
            "/api/terms/summary",
            "/api/commerce/info");
    /** 로그인한 요청만 — 비로그인이면 화면도 부르지 않는다 */
    static final List<String> MEMBER = List.of("/api/me/settings");
    /** 홈({@code /}) — 가젯 배치·데이터와 홈 띠·네모·한 줄 광고 */
    static final List<String> HOME = List.of("/api/home/gadgets", "/api/ads?types=STRIP_BANNER,SQUARE_BANNER,TEXT_LINE");

    /**
     * 이 화면 주소에서 미리 불러 둘 API 응답들 — {@code {"lang":"ko","responses":{"/api/site/info":{"s":200,"b":{…}}, …}}}. 실을 것이 없으면 null
     *
     * @param request  화면 주소 요청(보안 필터 체인을 지난 것)
     * @param response 그 응답 — 실은 API 가 내린 쿠키만 여기로 넘어간다
     * @param path     화면 주소(서블릿 경로)
     */
    public String render(HttpServletRequest request, HttpServletResponse response, String path) {
        long started = System.nanoTime();
        List<String> urls = new ArrayList<>(COMMON);
        if (isMember()) {
            urls.addAll(MEMBER);
        }
        if ("/".equals(path) || "/index.html".equals(path)) {
            urls.addAll(HOME);
        }
        StringBuilder out = new StringBuilder(32 * 1024);
        out.append("{\"lang\":\"").append(RequestLocale.of(request).getLanguage()).append("\",\"responses\":{");
        int count = 0;
        for (String url : urls) {
            String entry = call(request, response, url);
            if (entry == null) {
                continue;
            }
            if (count++ > 0) {
                out.append(',');
            }
            out.append('"').append(url).append("\":").append(entry);
        }
        out.append("}}");
        if (log.isDebugEnabled()) {
            log.debug("첫 화면 데이터 {}개를 실었다({}) — {}ms", count, path, (System.nanoTime() - started) / 1_000_000);
        }
        return count == 0 ? null : out.toString();
    }

    /** API 하나를 서버 안에서 부른다 — {@code {"s":상태,"b":본문}}. 실패하거나 JSON 이 아니면 null */
    private static String call(HttpServletRequest request, HttpServletResponse response, String url) {
        RequestDispatcher dispatcher = request.getRequestDispatcher(url);
        if (dispatcher == null) {
            return null;
        }
        CapturingResponse captured = new CapturingResponse(response);
        try {
            dispatcher.include(request, captured);
        } catch (ServletException | IOException | RuntimeException e) {
            log.warn("첫 화면 데이터 {} 를 싣지 못해 뺀다: {}", url, e.toString());
            return null;
        }
        String type = captured.contentType();
        byte[] body = captured.body();
        if (body.length == 0) {
            return "{\"s\":" + captured.status() + ",\"b\":null}";
        }
        if (type == null || !type.toLowerCase().contains("json")) {
            return null;
        }
        return "{\"s\":" + captured.status() + ",\"b\":" + new String(body, StandardCharsets.UTF_8) + "}";
    }

    private static boolean isMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthUser;
    }
}
