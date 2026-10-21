package com.community.zconfiguration.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.proxy.* — 우리가 운영해 믿는 프록시(Caddy)에 관한 설정.
 *
 * @param trustedHops 믿는 프록시의 단 수 — {@code X-Forwarded-For} 의 <b>뒤에서</b> 이 번째 값을 요청자의 IP 로 쓴다(프록시 하나면 1).
 *                    앞쪽 값은 요청자가 꾸밀 수 있어 쓰지 않는다. 0 이하면 헤더를 믿지 않고 접속 주소만 쓴다. 설정이 없으면 1
 */
@ConfigurationProperties(prefix = "roboyam.proxy")
public record ProxyProperties(@DefaultValue("1") int trustedHops) {
}
