package com.community.sociallogin.service;

import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.CallbackOutcome;
import com.community.sociallogin.dto.SocialFailure;
import com.community.sociallogin.dto.SocialMode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.net.URI;
import java.util.Locale;

/**
 * 소셜 흐름이 사용자 앱으로 돌려보내는 경로 — <b>같은 사이트의 상대 경로만</b> 쓴다(오픈 리다이렉트 금지). 쿼리에는 정해진 코드와 우리 공급자 id 만 싣는다
 * (개인정보·토큰·입력값을 싣지 않는다).
 *
 * <table>
 *   <caption>결과 → 경로</caption>
 *   <tr><td>로그인 성공</td><td>{@code next}(검사를 마친 값) 또는 {@code /}</td></tr>
 *   <tr><td>처음 보는 소셜 계정</td><td>{@code /signup/social}</td></tr>
 *   <tr><td>이메일이 기존 회원과 같음</td><td>{@code /login?error=email_exists&provider=kakao}</td></tr>
 *   <tr><td>로그인 실패</td><td>{@code /login?error=social_failed&provider=kakao&reason=<사유>}</td></tr>
 *   <tr><td>연결 성공</td><td>{@code /settings/account?linked=kakao}</td></tr>
 *   <tr><td>다른 회원에 연결된 소셜 계정</td><td>{@code /settings/account?link=taken&provider=kakao}</td></tr>
 *   <tr><td>연결 실패</td><td>{@code /settings/account?link=failed&provider=kakao&reason=<사유>}</td></tr>
 * </table>
 */
public final class SocialReturnPaths {

    public static final String SIGNUP = "/signup/social";
    public static final String LOGIN = "/login";
    public static final String ACCOUNT = "/settings/account";
    static final int NEXT_MAX_LENGTH = 512;

    private SocialReturnPaths() {
    }

    /**
     * 돌아갈 경로를 검사한다. 비었으면 null(= {@code /}).
     *
     * @throws ValidationException 같은 사이트의 상대 경로가 아닐 때({@code fields.next}) — 절대 주소·{@code //호스트}·역슬래시·제어 문자·512자 초과
     */
    public static String checkNext(String next) {
        if (next == null || next.isEmpty()) {
            return null;
        }
        if (!isSafeRelativePath(next)) {
            throw ValidationException.of("next", new MessageRef("social.next.invalid"));
        }
        return next;
    }

    static boolean isSafeRelativePath(String path) {
        if (path.length() > NEXT_MAX_LENGTH || !path.startsWith("/") || path.startsWith("//")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            // 역슬래시(브라우저가 / 로 읽는다)·공백·제어 문자·ASCII 밖의 글자를 받지 않는다(경로는 퍼센트 인코딩해서 보낸다)
            if (c == '\\' || c <= 0x20 || c >= 0x7f) {
                return false;
            }
        }
        String lower = path.toLowerCase(Locale.ROOT);
        // 인코딩한 / · \ 로 시작해 화면이 풀어 쓸 때 //호스트 가 되는 것을 막는다
        if (lower.startsWith("/%2f") || lower.startsWith("/%5c")) {
            return false;
        }
        try {
            URI uri = new URI(path);
            return uri.getScheme() == null && uri.getRawAuthority() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 콜백 결과의 이동 경로 */
    public static String of(CallbackOutcome outcome) {
        String provider = outcome.provider() == null ? null : outcome.provider().id();
        return switch (outcome.kind()) {
            case LOGGED_IN -> outcome.next() == null ? "/" : outcome.next();
            case SIGNUP_REQUIRED -> SIGNUP;
            case EMAIL_EXISTS -> LOGIN + "?error=email_exists" + providerParam(provider);
            case LINKED -> ACCOUNT + "?linked=" + provider;
            case LINK_TAKEN -> ACCOUNT + "?link=taken" + providerParam(provider);
            case FAILED -> failure(outcome.mode(), outcome.provider(), outcome.failure());
        };
    }

    private static String failure(SocialMode mode, SocialProvider provider, SocialFailure failure) {
        String id = provider == null ? null : provider.id();
        String reason = "&reason=" + failure.code();
        return mode == SocialMode.LINK
                ? ACCOUNT + "?link=failed" + providerParam(id) + reason
                : LOGIN + "?error=social_failed" + providerParam(id) + reason;
    }

    private static String providerParam(String providerId) {
        return providerId == null ? "" : "&provider=" + providerId;
    }
}
