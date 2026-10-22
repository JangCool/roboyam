package com.community.sociallogin.config;

import com.community.social.entity.SocialProvider;
import java.net.InetAddress;
import java.net.URI;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 공급자 주소(인가·토큰·회원 정보)와 구글 ID 토큰의 발급자 — <b>코드에 고정</b>이다. 사용자·운영자가 넣는 주소는 없다.
 *
 * <p>시험용 덮어쓰기({@code roboyam.social.test-endpoints.*})는 아래를 <b>모두</b> 만족할 때만 쓰인다. 하나라도 어기면 무시하고 고정 주소를 쓴다(경고 로그).
 * <ol>
 *   <li>{@code enabled=true}</li>
 *   <li>활성 프로필이 {@code local} 이고 {@code dev}·{@code prod} 가 아니다(기본 프로필 local 포함)</li>
 *   <li>{@code baseUrl} 이 {@code http(s)://} 이고 호스트가 루프백(127.0.0.0/8·::1·localhost)이다 — 잘못 켜도 클라이언트 시크릿·인가 코드가 밖으로 나가지 않는다</li>
 * </ol>
 * 덮어쓰기가 쓰이면 기동 때 한 번 경고 로그를 남긴다.
 */
@Component
public class SocialEndpoints {

    private static final Logger log = LoggerFactory.getLogger(SocialEndpoints.class);

    /** 한 공급자의 주소 */
    public record Endpoints(String authorizeUrl, String tokenUrl, String userInfoUrl, Set<String> issuers) {
    }

    private static final Map<SocialProvider, Endpoints> FIXED = Map.of(
            SocialProvider.KAKAO, new Endpoints("https://kauth.kakao.com/oauth/authorize", "https://kauth.kakao.com/oauth/token",
                    "https://kapi.kakao.com/v2/user/me", Set.of()),
            SocialProvider.NAVER, new Endpoints("https://nid.naver.com/oauth2.0/authorize", "https://nid.naver.com/oauth2.0/token",
                    "https://openapi.naver.com/v1/nid/me", Set.of()),
            SocialProvider.GOOGLE, new Endpoints("https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
                    null, Set.of("https://accounts.google.com", "accounts.google.com")));

    private final Map<SocialProvider, Endpoints> endpoints;
    private final boolean testMode;

    public SocialEndpoints(SocialLoginProperties props, Environment environment) {
        String testBase = acceptedTestBase(props.testEndpoints(), environment);
        this.testMode = testBase != null;
        if (testBase == null) {
            this.endpoints = FIXED;
        } else {
            Map<SocialProvider, Endpoints> test = new EnumMap<>(SocialProvider.class);
            for (SocialProvider provider : SocialProvider.values()) {
                String base = testBase + "/" + provider.id();
                test.put(provider, new Endpoints(base + "/authorize", base + "/token", base + "/me", Set.of(base)));
            }
            this.endpoints = Map.copyOf(test);
            log.warn("소셜 로그인이 시험용 가짜 공급자({})를 쓴다 — local 프로필 전용 설정 roboyam.social.test-endpoints 가 켜져 있다", testBase);
        }
    }

    public Endpoints of(SocialProvider provider) {
        return endpoints.get(provider);
    }

    /** 시험용 가짜 공급자를 쓰는 중인가 */
    public boolean isTestMode() {
        return testMode;
    }

    /** 덮어쓰기를 받아들일 수 있으면 끝 {@code /} 를 뗀 주소, 아니면 null */
    static String acceptedTestBase(SocialLoginProperties.TestEndpoints test, Environment environment) {
        if (test == null || !test.enabled()) {
            return null;
        }
        boolean local = environment.acceptsProfiles(Profiles.of("local")) && !environment.acceptsProfiles(Profiles.of("dev", "prod"));
        if (!local) {
            log.warn("roboyam.social.test-endpoints 는 local 프로필 전용이라 무시한다 — 실제 공급자 주소를 쓴다");
            return null;
        }
        String base = test.baseUrl() == null ? "" : test.baseUrl().strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!isLoopbackHttp(base)) {
            log.warn("roboyam.social.test-endpoints.base-url 이 루프백 http(s) 주소가 아니라 무시한다 — 실제 공급자 주소를 쓴다");
            return null;
        }
        return base;
    }

    private static boolean isLoopbackHttp(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!("http".equals(scheme) || "https".equals(scheme)) || host == null || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return false;
            }
            if ("localhost".equalsIgnoreCase(host)) {
                return true;
            }
            String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
            // 숫자 주소만 받는다(이름 풀이를 하지 않는다): IPv4 는 숫자·점만, IPv6 는 콜론이 있고 16진 숫자·콜론·점만
            boolean ipv4 = literal.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
            boolean ipv6 = literal.indexOf(':') >= 0
                    && literal.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.');
            if (!ipv4 && !ipv6) {
                return false;
            }
            return InetAddress.getByName(literal).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
