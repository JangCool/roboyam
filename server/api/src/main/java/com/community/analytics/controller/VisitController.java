package com.community.analytics.controller;

import com.community.analytics.collect.VisitCollector;
import com.community.zconfiguration.security.PublicVisitPaths;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 방문 신호({@code POST /api/visits} — 로그인·CSRF 없음, {@link PublicVisitPaths}). docs/features/site-analytics-contract.md 「API」.
 * 본문은 JSON 글자로 직접 읽는다 — sendBeacon 은 {@code text/plain} 으로도 보내고, 형식이 틀려도 400 이 아니라 <b>늘 204</b> 여야 해서다(셌든 버렸든 같다).
 * 상한({@link VisitCollector#BODY_MAX}자)보다 긴 본문은 끝까지 읽지 않고 버린다.
 */
@RestController
public class VisitController {

    private final VisitCollector collector;

    public VisitController(VisitCollector collector) {
        this.collector = collector;
    }

    @PostMapping(PublicVisitPaths.VISITS)
    public ResponseEntity<Void> visit(HttpServletRequest request) {
        String body = read(request);
        if (body != null) {
            String host = request.getServerName();
            collector.accept(body, request.getHeader(HttpHeaders.USER_AGENT), host == null ? null : host.toLowerCase(Locale.ROOT));
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 상한 안의 본문(UTF-8) — 없거나 길거나 읽지 못하면 null */
    private static String read(HttpServletRequest request) {
        long declared = request.getContentLengthLong();
        int max = VisitCollector.BODY_MAX * 4; // UTF-8 한 글자 최대 4바이트
        if (declared > max) {
            return null;
        }
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readNBytes(max + 1);
            return bytes.length == 0 || bytes.length > max ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
