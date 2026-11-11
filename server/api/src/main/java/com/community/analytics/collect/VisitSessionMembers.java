package com.community.analytics.collect;

import com.community.auth.dto.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.stereotype.Component;

/**
 * 방문 신호를 보낸 브라우저의 <b>로그인 회원</b> — 뺄 계정(운영자 회원 계정·AI 회원)의 신호를 버리려고 본다(site-analytics-contract 「3단계 — 뺄 계정」·「검토 보강 — 세션 확인 비용」).
 *
 * <p>로그인 세션은 DB(Spring Session JDBC, 쿠키 {@code SESSION})에 있어 신호마다 읽으면 페이지뷰마다 DB 읽기가 생긴다(「신호마다 DB 를 건드리지 않는다」와 충돌) — 그래서 각 api
 * 서버 메모리에 「세션 → 회원 번호(없음 포함)」를 약 {@value #TTL_MILLIS}ms 둔다. 같은 브라우저의 두 번째 신호부터 DB 를 읽지 않는다.
 * <ul>
 *   <li>쿠키 값은 Spring Session 의 쿠키 규칙({@link CookieSerializer})으로 읽고, 처음 보는 세션만 세션 저장소에서 한 번 찾는다 — <b>세션을 새로 만들지 않고</b> 마지막 활동 시각도
 *       바꾸지 않는다(찾기만). 쿠키가 없으면(로그인하지 않은 방문) DB 를 보지 않는다.</li>
 *   <li>로그아웃·계정 연결은 최대 캐시 시간만큼 늦게 반영된다(통계라 허용). 세션 ID 는 로그에 남기지 않는다.</li>
 *   <li>메모리 상한 {@value #MAX_ENTRIES}개 — 넘으면 끝난 줄을 지우고, 그래도 넘으면 비운다(꾸민 쿠키로 메모리가 터지지 않게).</li>
 * </ul>
 */
@Component
public class VisitSessionMembers {

    private static final Logger log = LoggerFactory.getLogger(VisitSessionMembers.class);

    static final long TTL_MILLIS = 10 * 60_000L;
    static final int MAX_ENTRIES = 100_000;

    /** 회원 번호(로그인하지 않은 세션·없는 세션은 null) · 끝나는 시각 */
    private record Entry(Long memberId, long expiresAt) {
    }

    private final CookieSerializer cookies;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public VisitSessionMembers(CookieSerializer cookies, FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.cookies = cookies;
        this.sessions = sessions;
    }

    /** 이 요청의 로그인 회원 번호 — 로그인하지 않았거나 알 수 없으면 null */
    public Long memberOf(HttpServletRequest request) {
        List<String> ids;
        try {
            ids = cookies.readCookieValues(request);
        } catch (RuntimeException e) {
            return null;
        }
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            Long member = memberOf(id, now);
            if (member != null) {
                return member;
            }
        }
        return null;
    }

    private Long memberOf(String sessionId, long now) {
        Entry e = cache.get(sessionId);
        if (e != null && now < e.expiresAt()) {
            return e.memberId();
        }
        Long member;
        try {
            member = member(sessions.findById(sessionId));
        } catch (RuntimeException ex) {
            log.warn("방문 신호의 로그인 세션을 읽지 못해 회원 없음으로 본다: {}", ex.getClass().getSimpleName());
            return null; // 담지 않는다 — 다음 신호가 다시 본다
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.values().removeIf(x -> x.expiresAt() <= now);
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(sessionId, new Entry(member, now + TTL_MILLIS));
        return member;
    }

    /** 세션의 보안 컨텍스트에 든 회원(로그인 컨트롤러가 {@code HttpSessionSecurityContextRepository} 로 저장한 것) */
    private static Long member(Session session) {
        if (session == null) {
            return null;
        }
        Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(context instanceof SecurityContext sc)) {
            return null;
        }
        Authentication auth = sc.getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthUser user ? user.id() : null;
    }
}
