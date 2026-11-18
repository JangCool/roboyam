package com.community.adultboard.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 19금 게시판 핀 확인 상태 — <b>로그인 세션 속성</b>(Spring Session JDBC, 쿠키 {@code SESSION} — 선례 {@code SecretPostUnlocks}). 기기(로그인)마다 따로이고
 * 서버 여러 대가 같은 DB 세션을 본다. 로그아웃하면(세션이 없어지면) 저절로 풀린다(adult-board-pin-contract).
 *
 * <p>핀번호·해시는 담지 않는다 — 회원 ID · 확인한 때 · 끝나는 때 · 그때의 {@code pinSetAt} 만. 판정({@link AdultBoardGate})이 회원 ID 와 DB 의
 * {@code pinSetAt} 을 견줘, 핀을 바꾸거나 접근을 끄면 <b>다른 기기의 확인도 무효</b>가 된다(세션 값은 그대로 남아도 통하지 않는다).
 *
 * <p>지금 요청을 {@link RequestContextHolder} 로 찾는다 — 요청 밖(배치·시험의 서비스 직접 호출)이거나 세션이 없으면 늘 「확인 없음」이고 남기지도 않는다
 * (세션을 새로 만들지 않는다 — 로그인한 요청에는 세션이 이미 있다).
 */
@Component
public class AdultBoardUnlocks {

    static final String ATTRIBUTE = "irobit.adultBoard.unlock";

    /** 이 세션에 남아 있는 핀 확인(없으면 null) — 유효한지는 부르는 쪽이 회원·{@code pinSetAt}·끝 시각으로 판정한다 */
    public Unlock current() {
        HttpSession session = session();
        return session != null && session.getAttribute(ATTRIBUTE) instanceof Unlock unlock ? unlock : null;
    }

    /** 이 세션의 핀 확인을 남기거나 바꾼다(연장·다시 묻기 기준 변경 포함) */
    public void store(Unlock unlock) {
        HttpSession session = session();
        if (session != null) {
            session.setAttribute(ATTRIBUTE, unlock);
        }
    }

    /** 이 세션의 핀 확인을 지운다(지금 잠그기·접근 끄기) */
    public void clear() {
        HttpSession session = session();
        if (session != null && session.getAttribute(ATTRIBUTE) != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    private static HttpSession session() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
        try {
            return request == null ? null : request.getSession(false);
        } catch (IllegalStateException e) {
            return null; // 이미 끝난 요청
        }
    }

    /**
     * 세션에 두는 값.
     *
     * @param userId     핀을 확인한 회원 — 같은 세션에 다른 회원이 들어와도 통하지 않게
     * @param verifiedAt 핀번호를 넣은 때({@code FIXED} 의 기준)
     * @param until      확인이 끝나는 때
     * @param pinSetAt   확인할 때의 핀 정한 시각 — DB 값과 다르면 무효
     */
    public record Unlock(long userId, Instant verifiedAt, Instant until, Instant pinSetAt) implements Serializable {

        /** 이 회원·이 핀으로 지금 살아 있는 확인인가 */
        public boolean validFor(long viewerId, Instant currentPinSetAt, Instant now) {
            return userId == viewerId && pinSetAt != null && pinSetAt.equals(currentPinSetAt) && until != null && until.isAfter(now);
        }

        public Unlock until(Instant newUntil) {
            return new Unlock(userId, verifiedAt, newUntil, pinSetAt);
        }
    }
}
