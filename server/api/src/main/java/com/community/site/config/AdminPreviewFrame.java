package com.community.site.config;

import com.community.zconfiguration.security.FrameAncestorsPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * 관리자 테마 화면의 「실제 화면」(관리자 앱이 공개 사이트를 {@code /?adminPreview=1} iframe 으로 띄운다 — site-theme-layout-contract) 을 허락한다.
 *
 * <ul>
 *   <li>허락하는 곳: 관리자 앱 주소({@code roboyam.admin-preview.origin} = 환경 변수 {@code ADMIN_URL})의 origin(스킴·호스트·포트) 하나. 비었거나 http(s) 주소가 아니면
 *       아무 데도 허락하지 않는다(늘 {@code X-Frame-Options: DENY}).</li>
 *   <li>허락하는 응답: 화면 주소({@link SiteHeadFilter#isPage})의 {@code GET}·{@code HEAD} 이면서 {@code adminPreview=1} 이 붙은 것만 —
 *       {@code Content-Security-Policy: frame-ancestors 'self' <관리자 origin>}, XFO 없음. 그 밖의 화면·API·정적 파일은 그대로 DENY 다.</li>
 *   <li>같은 응답의 {@code index.html} 머리에 관리자 origin 을 {@code <meta name="roboyam-admin-origin">} 로 넣는다({@link SiteHeadFilter}) — 공개 화면이 그 주소의
 *       메시지만 받는다(user-web {@code plugins/03.admin-preview.client.ts}). 화면 빌드에 주소를 박지 않으므로 이미지는 환경마다 한 벌이다.</li>
 * </ul>
 */
@Component
public class AdminPreviewFrame implements FrameAncestorsPolicy {

    private static final Logger log = LoggerFactory.getLogger(AdminPreviewFrame.class);

    static final String PARAM = "adminPreview";

    /** 관리자 앱 origin — 설정이 없거나 쓸 수 없으면 null */
    private final String origin;

    public AdminPreviewFrame(@Value("${roboyam.admin-preview.origin:}") String adminUrl) {
        this.origin = originOf(adminUrl);
        if (origin == null && adminUrl != null && !adminUrl.isBlank()) {
            log.warn("관리자 앱 주소(ADMIN_URL)가 http(s) 주소가 아니라 관리자 미리보기 iframe 을 허락하지 않는다: {}", adminUrl);
        }
    }

    /** 관리자 미리보기를 허락할 관리자 앱 origin(설정이 없으면 비어 있다) */
    public Optional<String> adminOrigin() {
        return Optional.ofNullable(origin);
    }

    /** 관리자 미리보기 화면 요청인가 — 관리자 주소가 있고, 화면 주소의 GET·HEAD 이고, {@code adminPreview=1} */
    public boolean isPreview(HttpServletRequest request) {
        if (origin == null) {
            return false;
        }
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            return false;
        }
        return "1".equals(request.getParameter(PARAM)) && SiteHeadFilter.isPage(request.getServletPath());
    }

    @Override
    public Optional<String> frameAncestors(HttpServletRequest request) {
        return isPreview(request) ? Optional.of("'self' " + origin) : Optional.empty();
    }

    /** {@code http(s)://호스트[:포트][/…]} → {@code http(s)://호스트[:포트]}(소문자). 그 밖은 null */
    static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null || uri.getRawUserInfo() != null) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return scheme + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
