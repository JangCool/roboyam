package com.community.installation.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 설치가 끝났다(409 {@code ALREADY_INSTALLED}). 설치가 끝나면 마법사 API 는 {@code status} 를 뺀 모두 이 오류로 닫힌다(동시에 두 번 설치해도 두 번째는 이 오류) */
public class InstallationAlreadyInstalledException extends CommonException {

    public InstallationAlreadyInstalledException() {
        super(HttpStatus.CONFLICT, "ALREADY_INSTALLED", new MessageRef("error.ALREADY_INSTALLED"));
    }
}
