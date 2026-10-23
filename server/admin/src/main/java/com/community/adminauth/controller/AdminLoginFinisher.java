package com.community.adminauth.controller;

import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.adminauth.service.AdminMeService;
import com.community.mfa.controller.MfaTrustCookie;
import com.community.mfa.dto.MfaDecision;
import com.community.mfa.dto.response.AdminLoginMfa;
import com.community.mfa.service.MfaLoginService;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * 비밀번호가 맞은 뒤의 세션 마무리 — 웹 계층의 도우미(로그인·비밀번호 재설정이 같이 쓴다).
 *
 * <ul>
 *   <li>관리자 2차 인증 판정({@link MfaLoginService#decide})이 VERIFY·ENROLL 이면 <b>로그인 세션을 만들지 않고</b> 2차 대기 세션을 만든다 — 응답은 {@code authenticated:false} + {@code mfa}.</li>
 *   <li>바로 로그인(꺼짐·신뢰 기기·유예 안)이면 로그인을 완성하고(감사 {@code LOGIN_SUCCESS}·마지막 로그인 시각) 세션을 만든다 — 유예 안이면 {@code mfa.stage = GRACE}.</li>
 *   <li>2단계를 통과한 뒤({@link #establish})는 세션 ID 를 다시 바꿔 정상 로그인 세션을 만든다.</li>
 * </ul>
 */
@Component
public class AdminLoginFinisher {

    private final MfaLoginService mfaLogins;
    private final AdminSession adminSession;
    private final AdminMeService meService;
    private final MfaTrustCookie trustCookie;
    private final ClientIpResolver clientIps;

    public AdminLoginFinisher(MfaLoginService mfaLogins, AdminSession adminSession, AdminMeService meService, MfaTrustCookie trustCookie,
                              ClientIpResolver clientIps) {
        this.mfaLogins = mfaLogins;
        this.adminSession = adminSession;
        this.meService = meService;
        this.trustCookie = trustCookie;
        this.clientIps = clientIps;
    }

    /**
     * @param via 다른 흐름으로 들어왔을 때 로그인 감사에 남길 값(PASSWORD_RESET). 일반 로그인은 null
     */
    public AdminMeResponse afterPassword(HttpServletRequest request, HttpServletResponse response, OperatorPrincipal principal, String via) {
        MfaDecision decision = mfaLogins.decide(principal.id(), trustCookie.read(request));
        AdminLoginMfa mfa = mfaLogins.view(decision);
        if (decision.isPending()) {
            adminSession.beginMfaPending(request, response, principal.id(), decision.stage());
            return meService.of(java.util.Optional.empty(), mfa, null);
        }
        mfaLogins.complete(principal.id(), decision.method(), clientIps.resolve(request), request.getHeader("User-Agent"), via);
        return establish(request, response, principal.id(), mfa, null);
    }

    /** 로그인 세션을 만든다(옛 세션 — 2차 대기 포함 — 을 버리고 새 ID). 로그인 완성 감사는 부르는 쪽이 이미 남겼다 */
    public AdminMeResponse establish(HttpServletRequest request, HttpServletResponse response, long operatorId, AdminLoginMfa mfa, Integer recoveryCodesLeft) {
        adminSession.establish(request, response, new OperatorPrincipal(operatorId));
        return meService.of(adminSession.resolve(request), mfa, recoveryCodesLeft);
    }
}
