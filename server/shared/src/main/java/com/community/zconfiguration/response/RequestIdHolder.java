package com.community.zconfiguration.response;

import com.community.zconfiguration.message.RequestLocale;
import java.util.Locale;
import java.util.UUID;

/**
 * 지금 요청의 요청 ID 와 응답 언어를 스레드에 들고 있는 곳. {@link RequestIdFilter} 가 요청 시작에 채우고 끝에 비운다.
 * 필터·보안 단계와 컨트롤러가 같은 값을 보게 하려고 스프링 요청 문맥이 아니라 이 홀더를 쓴다(그 문맥은 보안 필터 체인보다 늦게 채워진다).
 * 요청 스레드 밖(기동 코드 등)에서는 값이 없어 새 UUID·{@code ko} 로 되돌린다.
 */
public final class RequestIdHolder {

    private record Context(String requestId, Locale locale) {
    }

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private RequestIdHolder() {
    }

    static void set(String requestId, Locale locale) {
        CURRENT.set(new Context(requestId, locale));
    }

    static void clear() {
        CURRENT.remove();
    }

    /** 이 요청의 ID. 요청 문맥이 없으면 새 UUID */
    public static String requestId() {
        Context context = CURRENT.get();
        return context == null ? UUID.randomUUID().toString() : context.requestId();
    }

    /** 이 요청의 응답 언어({@code ko}|{@code en}). 요청 문맥이 없으면 {@code ko} */
    public static Locale locale() {
        Context context = CURRENT.get();
        return context == null ? RequestLocale.KO : context.locale();
    }
}
