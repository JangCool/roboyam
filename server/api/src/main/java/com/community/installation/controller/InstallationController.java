package com.community.installation.controller;

import com.community.installation.dto.request.CompleteInstallationRequest;
import com.community.installation.dto.request.VerifyTokenRequest;
import com.community.installation.dto.response.BoardPresetsResponse;
import com.community.installation.dto.response.InstallAuthorizedResponse;
import com.community.installation.dto.response.InstallChecksResponse;
import com.community.installation.dto.response.InstallCompletedResponse;
import com.community.installation.dto.response.InstallStatusResponse;
import com.community.installation.service.InstallationService;
import com.community.mail.dto.MailCheckResult;
import com.community.mail.dto.MailInput;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설치 마법사 API. <b>로그인 없이 쓰는 구간</b>이다 — 설치 토큰을 맞힌 세션만 {@code status}·{@code verify-token} 밖의 호출을 할 수 있고,
 * 설치가 끝나면 {@code status} 말고는 모두 {@code 409 ALREADY_INSTALLED} 로 닫힌다(판단은 서비스가 한다). 응답은 모두 {@code Cache-Control: no-store} 이고
 * 비밀번호·시크릿·토큰은 어떤 응답에도 없다. 쓰기는 CSRF 헤더가 필요하다 — 쿠키는 {@code status} 응답이 내려 준다.
 *
 * <p>이 컨트롤러는 HTTP 와 세션만 다룬다 — 요청의 IP 와 "세션이 인증됐는가"를 뽑아 서비스에 넘기고, 서비스가 성공하면 세션을 인증 상태로 바꾸거나 끝낸다.
 * 요청 본문은 {@code @Valid} 가 아니라 서비스가 검사한다(모든 위반을 점·대괄호 경로 키로 한꺼번에 돌려주고 검사 순서가 409 → 401 → 503 → 400 으로 계약이라서).
 */
@RestController
@RequestMapping("/api/system/installation")
public class InstallationController {

    private final InstallationService service;
    private final InstallSession session;
    private final ClientIpResolver clientIps;

    public InstallationController(InstallationService service, InstallSession session, ClientIpResolver clientIps) {
        this.service = service;
        this.session = session;
        this.clientIps = clientIps;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<InstallStatusResponse>> status(HttpServletRequest request) {
        return noStore(HttpStatus.OK, service.status(session.isAuthorized(request)));
    }

    @PostMapping("/verify-token")
    public ResponseEntity<ApiResponse<InstallAuthorizedResponse>> verifyToken(@RequestBody VerifyTokenRequest body, HttpServletRequest request) {
        InstallAuthorizedResponse response = service.verifyToken(clientIps.resolve(request), body.token());
        session.authorize(request); // 맞았을 때만 — 세션 ID 를 새로 바꾼다
        return noStore(HttpStatus.OK, response);
    }

    @GetMapping("/checks")
    public ResponseEntity<ApiResponse<InstallChecksResponse>> checks(HttpServletRequest request) {
        return noStore(HttpStatus.OK, service.checks(session.isAuthorized(request)));
    }

    @GetMapping("/board-presets")
    public ResponseEntity<ApiResponse<BoardPresetsResponse>> boardPresets(HttpServletRequest request) {
        return noStore(HttpStatus.OK, service.boardPresets(session.isAuthorized(request)));
    }

    @PostMapping("/mail-check")
    public ResponseEntity<ApiResponse<MailCheckResult>> mailCheck(@RequestBody MailInput body, HttpServletRequest request) {
        return noStore(HttpStatus.OK, service.mailCheck(session.isAuthorized(request), body));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<InstallCompletedResponse>> complete(@RequestBody CompleteInstallationRequest body, HttpServletRequest request) {
        InstallCompletedResponse response = service.complete(session.isAuthorized(request), body);

        session.end(request); // 설치는 이미 커밋됐다 — end 는 던지지 않는다
        return noStore(HttpStatus.CREATED, response);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
