package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 준비 중인 채널(카카오톡)의 문구를 만들거나 고치거나 지우려 했다(409) */
public class ChannelPreparingException extends CommonException {

    /** @param channelName 요청 언어의 채널 이름 */
    public ChannelPreparingException(String channelName) {
        super(HttpStatus.CONFLICT, "CHANNEL_PREPARING", new MessageRef("error.CHANNEL_PREPARING", channelName));
    }
}
