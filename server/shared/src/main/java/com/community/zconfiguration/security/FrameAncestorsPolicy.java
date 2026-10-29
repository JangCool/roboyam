package com.community.zconfiguration.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 응답을 다른 화면의 iframe 에 띄워도 되는가 — 앱이 이 빈을 두면 {@link SecurityConfig} 가 요청마다 묻는다({@link FrameHeaderWriter}).
 * 값이 있으면 그 응답에 {@code Content-Security-Policy: frame-ancestors <값>} 을 붙이고 {@code X-Frame-Options} 는 붙이지 않는다. 없으면(빈이 없을 때도) 늘 {@code X-Frame-Options: DENY} 다.
 * 공개 사이트(api)는 관리자 테마 화면의 「실제 화면」 요청만 허락한다({@code AdminPreviewFrame}). 관리자 앱(admin)은 이 빈이 없다 — 어디서도 iframe 에 뜨지 않는다.
 */
public interface FrameAncestorsPolicy {

    /** @return {@code frame-ancestors} 의 값(예: {@code 'self' https://admin.example.com}). 허락하지 않으면 비어 있다 */
    Optional<String> frameAncestors(HttpServletRequest request);
}
