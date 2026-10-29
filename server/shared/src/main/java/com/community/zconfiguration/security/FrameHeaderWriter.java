package com.community.zconfiguration.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.web.header.HeaderWriter;

/**
 * 프레임 헤더 — 기본은 {@code X-Frame-Options: DENY}(Spring Security 기본값과 같다). {@link FrameAncestorsPolicy} 가 허락한 응답만 그 대신
 * {@code Content-Security-Policy: frame-ancestors …} 를 붙인다. 정책이 없으면(관리자 앱) 늘 DENY 다.
 */
final class FrameHeaderWriter implements HeaderWriter {

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String CSP = "Content-Security-Policy";

    private final FrameAncestorsPolicy policy;

    FrameHeaderWriter(FrameAncestorsPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> ancestors = policy == null ? Optional.empty() : policy.frameAncestors(request);
        if (ancestors.isPresent()) {
            if (!response.containsHeader(CSP)) {
                response.setHeader(CSP, "frame-ancestors " + ancestors.get());
            }
            return;
        }
        if (!response.containsHeader(X_FRAME_OPTIONS)) {
            response.setHeader(X_FRAME_OPTIONS, "DENY");
        }
    }
}
