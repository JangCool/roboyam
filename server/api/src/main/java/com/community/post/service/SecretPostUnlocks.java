package com.community.post.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 비회원 비밀글의 「비밀번호로 열람」 권한(V168) — 비밀번호를 맞힌 방문자에게 그 글을 {@value #TTL_MINUTES}분 동안 읽게 한다.
 *
 * <p><b>저장 방식 = 서버 세션 속성</b>(Spring Session JDBC, 쿠키 {@code SESSION}). 이 프로젝트는 로그인 전 임시 상태(설치 인증 {@code InstallSession}, 소셜 흐름
 * {@code SocialFlowSession})를 이미 서버 세션에 두므로 같은 방식을 쓴다 — 서명 쿠키를 새로 만들지 않아 키 관리가 없고, 서버 여러 대가 같은 DB 세션을 본다.
 * 비밀번호·해시는 담지 않는다(글 ID → 끝 시각만). 비로그인 상태에서 새로 만든 세션은 기한을 열람 시간에 맞춰 짧게 둔다(로그인 세션 30일을 쓰지 않는다).
 * 한 세션에 {@value #MAX_POSTS}개까지(넘으면 가장 먼저 연 것부터 버린다).
 *
 * <p>판정({@link SecretPostGate})이 서비스 안에서 이 값을 보므로 지금 요청을 {@link RequestContextHolder} 로 찾는다 — 요청 밖(배치·시험의 서비스 직접 호출)이면
 * 늘 「열람 권한 없음」이고 남기지도 않는다.
 */
@Component
public class SecretPostUnlocks {

    static final String ATTRIBUTE = "irobit.post.secretUnlocks";
    static final int TTL_MINUTES = 60;
    static final int MAX_POSTS = 20;

    /** 이 방문자에게 그 글의 열람 권한을 준다(이미 있으면 끝 시각을 늘린다) */
    public void grant(long postId, Instant now) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        boolean created = request.getSession(false) == null;
        HttpSession session = request.getSession(true);
        if (created) {
            session.setMaxInactiveInterval((int) Duration.ofMinutes(TTL_MINUTES).toSeconds());
        }
        LinkedHashMap<Long, Instant> unlocks = alive(session, now);
        unlocks.remove(postId);
        unlocks.put(postId, now.plus(Duration.ofMinutes(TTL_MINUTES)));
        while (unlocks.size() > MAX_POSTS) {
            unlocks.remove(unlocks.keySet().iterator().next());
        }
        session.setAttribute(ATTRIBUTE, new Grants(unlocks));
    }

    /** 이 방문자가 지금 그 글의 열람 권한을 가졌나 */
    public boolean has(long postId, Instant now) {
        HttpServletRequest request = currentRequest();
        HttpSession session = request == null ? null : request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof Grants grants)) {
            return false;
        }
        Instant until = grants.until().get(postId);
        return until != null && until.isAfter(now);
    }

    private static LinkedHashMap<Long, Instant> alive(HttpSession session, Instant now) {
        LinkedHashMap<Long, Instant> out = new LinkedHashMap<>();
        if (session.getAttribute(ATTRIBUTE) instanceof Grants grants) {
            grants.until().forEach((id, until) -> {
                if (until.isAfter(now)) {
                    out.put(id, until);
                }
            });
        }
        return out;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    /** 세션에 두는 값 — 글 ID → 열람 끝 시각(차례 유지) */
    record Grants(LinkedHashMap<Long, Instant> until) implements Serializable {

        Grants {
            until = new LinkedHashMap<>(until);
        }
    }
}
