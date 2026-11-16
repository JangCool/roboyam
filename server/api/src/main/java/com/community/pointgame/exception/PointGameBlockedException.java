package com.community.pointgame.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 포인트 게임을 할 자격이 없다(point-games-contract 「공통 거절」). 코드는 까닭 그대로({@link #ACCOUNT_RESTRICTED} 정지·영구 정지·탈퇴 진행 중 ·
 * {@link #BUSINESS_ACCOUNT_RESTRICTED} 사업자 회원 — 글쓰기·쪽지와 같은 코드). 문구 키는 {@code pointGame.blocked.<코드>}.
 * 포인트 동결(기능 제한)은 이 예외가 아니라 {@code 403 MEMBER_RESTRICTED}({@code MemberRestrictedException} — 포인트 상점 구매와 같다)다.
 */
public class PointGameBlockedException extends CommonException {

    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String BUSINESS_ACCOUNT_RESTRICTED = "BUSINESS_ACCOUNT_RESTRICTED";

    public PointGameBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("pointGame.blocked." + reason));
    }
}
