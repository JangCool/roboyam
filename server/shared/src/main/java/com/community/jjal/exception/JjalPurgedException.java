package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 불법촬영물로 확정해 파일을 지운 짤이라 되돌릴 수 없다(관리자 짤 되돌림 — 해시 행만 남아 같은 해시 다시 올리기를 거절한다) */
public class JjalPurgedException extends CommonException {

    public JjalPurgedException() {
        super(HttpStatus.CONFLICT, "JJAL_PURGED", new MessageRef("error.JJAL_PURGED"));
    }
}
