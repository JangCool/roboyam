package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 지금 상태에서는 할 수 없다 — 이미 종료(종료)·이미 진행 중(재개)·이미 무시함(의심 후보)(409) */
public class HotdealStateChangedException extends CommonException {

    public HotdealStateChangedException() {
        super(HttpStatus.CONFLICT, "HOTDEAL_STATE_CHANGED", new MessageRef("error.HOTDEAL_STATE_CHANGED"), Map.of(), null);
    }
}
