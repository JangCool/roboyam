package com.community.social.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 경로의 공급자 이름이 카카오·네이버·구글이 아니다(404) — 코드는 공개 서버와 같은 {@code PROVIDER_NOT_FOUND} */
public class SocialProviderUnknownException extends CommonException {

    public SocialProviderUnknownException() {
        super(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", new MessageRef("socialSetting.provider.notFound"));
    }
}
