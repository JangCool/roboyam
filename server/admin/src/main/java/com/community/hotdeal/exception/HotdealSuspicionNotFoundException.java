package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 없는 의심 후보(404) */
public class HotdealSuspicionNotFoundException extends CommonException {

    public HotdealSuspicionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "HOTDEAL_SUSPICION_NOT_FOUND", new MessageRef("error.HOTDEAL_SUSPICION_NOT_FOUND"), Map.of(), null);
    }
}
