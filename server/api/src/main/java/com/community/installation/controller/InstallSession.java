package com.community.installation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 서버 세션에 두는 <b>"설치 인증됨"</b> 표시 — 웹 계층의 도우미다(서비스는 세션을 모른다).
 *
 * <ul>
 *   <li>토큰을 맞히면 표시를 한다. 이후 마법사 호출은 이 세션으로 인증한다(토큰을 매번 보내지 않는다).</li>
 *   <li><b>세션 고정 공격을 막으려고 표시하기 전에 세션 ID 를 바꾼다</b>(이미 세션이 있을 때) — 옛 세션 ID 는 쓸 수 없다.</li>
 *   <li><b>30분 동안 쉬면 만료</b>다(세션의 최대 유휴 시간). 마법사를 부를 때마다 세션이 접근돼 시간이 다시 시작된다.</li>
 *   <li>설치가 끝나면 세션을 없앤다 — 인증 표시는 그 순간 무효다.</li>
 * </ul>
 * 표시에는 토큰도 해시도 담지 않는다(불리언 하나). 세션은 DB(Spring Session JDBC)에 있어 서버 여러 대가 같이 본다.
 */
@Component
public class InstallSession {

    private static final Logger log = LoggerFactory.getLogger(InstallSession.class);

    static final String ATTRIBUTE = "roboyam.install.authorized";
    static final int IDLE_SECONDS = 30 * 60;

    /** 이 요청의 세션을 "설치 인증됨" 으로 만든다. 세션 ID 를 새로 바꾼다 */
    public void authorize(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(ATTRIBUTE, Boolean.TRUE);
        session.setMaxInactiveInterval(IDLE_SECONDS);
    }

    /** 이 요청의 세션이 설치 인증을 마쳤는가(세션이 없거나 만료됐으면 false) */
    public boolean isAuthorized(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(ATTRIBUTE));
    }

    /**
     * 설치 인증 세션을 끝낸다(설치가 끝났을 때). <b>던지지 않는다</b> — 이미 커밋된 설치의 응답이 세션 정리 실패 때문에 오류가 되면 안 된다.
     * 못 끝내도 세션은 30분 유휴 뒤 만료되고 설치가 끝나 마법사 API 가 닫혀 있어 쓸 곳이 없다.
     */
    public void end(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        } catch (RuntimeException e) {
            log.warn("설치가 끝난 뒤 설치 인증 세션을 끝내지 못했다 — 세션은 30분 유휴 뒤 만료된다", e);
        }
    }
}
