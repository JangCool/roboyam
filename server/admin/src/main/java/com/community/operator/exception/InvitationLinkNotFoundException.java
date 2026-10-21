package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 초대 링크의 토큰을 모른다(404 {@code INVITATION_NOT_FOUND}) — 모르는 토큰, 그리고 취소된 초대(취소는 행을 지우므로 구분되지 않는다).
 * 코드는 {@link InvitationNotFoundException}(운영자가 목록에서 취소하려는 초대가 없다)과 같지만 링크를 여는 사람에게 맞는 문구를 쓴다.
 */
public class InvitationLinkNotFoundException extends CommonException {

    public InvitationLinkNotFoundException() {
        super(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", new MessageRef("error.INVITATION_LINK_NOT_FOUND"));
    }
}
