package com.community.pointgame.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 남의 번호 줄이거나 ID 모양이 틀렸다(이미 없는 내 줄은 오류가 아니라 204 — point-games-contract 「API 후보」) */
public class LottoDrawNotFoundException extends CommonException {

    public LottoDrawNotFoundException() {
        super(HttpStatus.NOT_FOUND, "LOTTO_DRAW_NOT_FOUND", new MessageRef("error.LOTTO_DRAW_NOT_FOUND"));
    }
}
