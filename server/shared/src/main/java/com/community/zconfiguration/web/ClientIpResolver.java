package com.community.zconfiguration.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;

/**
 * 요청한 사람의 IP — <b>요청자가 꾸밀 수 없는 값</b>만 쓴다. 설치 토큰 잠금(IP 당 연속 실패)이 이것을 쓴다.
 *
 * <p>{@code request.getRemoteAddr()} 를 그대로 쓰면 안 된다: {@code server.forward-headers-strategy=framework} 의 {@code ForwardedHeaderFilter} 가
 * 컨트롤러가 보는 요청에서 {@code X-Forwarded-*} 헤더를 지우고 {@code getRemoteAddr()} 를 헤더의 <b>첫 번째(요청자가 보낸) 값</b>으로 바꾼다.
 * Caddy 는 자기가 본 접속 주소를 목록의 <b>끝에 덧붙이므로</b> 앞쪽은 헤더 한 줄로 마음대로 바꿀 수 있다(요청마다 바꾸면 IP 별 잠금을 피한다).
 * 그래서 감싼 요청들을 벗겨 <b>원래 요청</b>의 헤더·접속 주소를 읽고, 뒤에서 {@code roboyam.proxy.trusted-hops} 번째 값을 고른다.
 */
@Component
public class ClientIpResolver {

    /** 이보다 긴 값은 IP 가 아니다(꾸민 값·잘못된 값 방지) */
    private static final int MAX_LENGTH = 64;

    private final ProxyProperties props;

    public ClientIpResolver(ProxyProperties props) {
        this.props = props;
    }

    /** 이 요청을 보낸 사람의 IP. 믿을 수 있는 헤더 값이 없으면 접속 주소 */
    public String resolve(HttpServletRequest request) {
        HttpServletRequest original = request;
        while (original instanceof HttpServletRequestWrapper wrapper && wrapper.getRequest() instanceof HttpServletRequest inner) {
            original = inner;
        }
        return pick(original.getHeader("X-Forwarded-For"), original.getRemoteAddr(), props.trustedHops());
    }

    /**
     * @param forwardedFor 원래 요청의 {@code X-Forwarded-For} 헤더 값. 없으면 null
     * @param remoteAddr   원래 요청의 접속 주소(바로 앞 프록시 또는 클라이언트)
     * @param trustedHops  믿는 프록시의 단 수. 0 이하면 헤더를 믿지 않는다
     * @return 뒤에서 {@code trustedHops} 번째 값. 헤더가 없거나 단이 모자라거나 값이 이상하면 접속 주소
     */
    static String pick(String forwardedFor, String remoteAddr, int trustedHops) {
        if (forwardedFor == null || forwardedFor.isBlank() || trustedHops <= 0) {
            return remoteAddr;
        }
        String[] parts = forwardedFor.split(",");
        if (parts.length < trustedHops) {
            return remoteAddr;
        }
        String candidate = parts[parts.length - trustedHops].trim();
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
            return remoteAddr;
        }
        return candidate;
    }
}
