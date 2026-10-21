package com.community.installation.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 아직 설치가 끝나지 않았다(503 {@code NOT_INSTALLED}). 설치 전에는 설치 마법사 API({@code /api/system/installation/**}) 말고 모든 {@code /api/**} 가
 * 이 오류다. 화면은 이 응답을 보면 {@code /install} 로 보낸다.
 */
public class InstallationNotInstalledException extends CommonException {

    public InstallationNotInstalledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "NOT_INSTALLED", new MessageRef("error.NOT_INSTALLED"));
    }
}
