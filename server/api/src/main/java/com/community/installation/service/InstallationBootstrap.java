package com.community.installation.service;

import com.community.installation.config.InstallProperties;
import com.community.installation.exception.InstallationAlreadyInstalledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 <b>설치 전이면 설치 토큰을 준비</b>하고 설치 주소를 알린다.
 *
 * <ul>
 *   <li>환경 변수 {@code ROBOYAM_INSTALL_TOKEN}({@code roboyam.install.token})이 있으면 그 값이 설치 토큰이다 — <b>값은 로그에 찍지 않는다</b>
 *       (자동화·컨테이너·앱 서버 여러 대는 환경 변수로 같은 토큰을 준다).</li>
 *   <li>없으면 <b>무작위 토큰을 만들어 로그에 한 번 출력</b>한다(설치 주소와 함께). DB 에는 해시만 둔다. 설치 전에 서버를 다시 띄우면 새 토큰이 만들어지고
 *       이전 토큰은 무효다 — 앱 서버를 여러 대 띄우면 마지막에 뜬 서버의 토큰만 유효하므로 환경 변수로 같은 토큰을 준다.</li>
 *   <li>이미 설치가 끝났으면 아무것도 하지 않는다.</li>
 * </ul>
 * 웹 서버가 요청을 받기 시작하기 전에({@link SmartInitializingSingleton}) 준비한다 — 토큰이 없는 채로 설치 화면이 열리는 틈이 없다.
 * 토큰 발급은 공개 사이트(api)만 한다 — 관리자 앱에는 이 클래스가 없어, 어느 앱이 먼저 떠도 설치 전 상태·토큰은 하나다.
 */
@Component
public class InstallationBootstrap implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(InstallationBootstrap.class);

    private static final String BAR = "=".repeat(72);

    private final InstallProperties props;
    private final InstallationState state;
    private final InstallTokenService tokens;
    private final Environment env;

    public InstallationBootstrap(InstallProperties props, InstallationState state, InstallTokenService tokens, Environment env) {
        this.props = props;
        this.state = state;
        this.tokens = tokens;
        this.env = env;
    }

    @Override
    public void afterSingletonsInstantiated() {
        prepare();
    }

    /** 설치 전이면 토큰을 준비하고 안내를 출력한다. 설치가 끝났으면 아무것도 하지 않는다 */
    void prepare() {
        if (state.isInstalled()) {
            return;
        }
        String address = installAddress();
        try {
            if (props.hasConfiguredToken()) {
                tokens.useConfigured(props.token().strip());
                log.info("\n{}\n 설치가 필요합니다. 브라우저로 아래 주소를 열고 설치 토큰을 입력해 주세요.\n   설치 주소 : {}\n"
                        + " 설치 토큰은 환경 변수 ROBOYAM_INSTALL_TOKEN 으로 정해져 있습니다(값은 로그에 남기지 않습니다).\n{}", BAR, address, BAR);
                return;
            }
            String token = tokens.issueRandom();
            log.info("\n{}\n 설치가 필요합니다. 브라우저로 아래 주소를 열고 설치 토큰을 입력해 주세요.\n   설치 주소 : {}\n   설치 토큰 : {}\n"
                    + " 이 토큰은 지금 한 번만 출력됩니다. 서버를 다시 띄우면 새 토큰이 만들어지고 이전 토큰은 쓸 수 없습니다.\n"
                    + " (자동 설치이거나 서버를 여러 대 띄운다면 환경 변수 ROBOYAM_INSTALL_TOKEN 으로 토큰을 정해 두세요.)\n{}",
                    BAR, address, token, BAR);
        } catch (InstallationAlreadyInstalledException e) {
            // 상태를 읽은 뒤 다른 서버가 그사이 설치를 끝냈다 — 기동을 멈출 일이 아니다
            state.markCompleted();
            log.info("다른 서버가 설치를 끝냈다 — 설치 토큰을 만들지 않는다");
        }
    }

    /** 설치 화면 주소 — 이 서버의 포트 기준(공개 주소는 설치 때 입력받는 값이라 아직 없다) */
    private String installAddress() {
        return "http://localhost:" + env.getProperty("server.port", "8080") + "/install";
    }
}
