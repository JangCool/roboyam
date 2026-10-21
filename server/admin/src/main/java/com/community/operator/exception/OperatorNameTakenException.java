package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 이미 그 이름(대소문자 무시)을 쓰는 다른 운영자가 있다(409). 내 정보 닉네임·초대 수락과 같은 코드 {@code NICKNAME_TAKEN} 이고,
 * 운영자 추가·수정에서는 이름 칸({@code fields.operatorName})에 문구가 붙는다. 문구에 입력한 이름을 싣지 않는다.
 */
public class OperatorNameTakenException extends CommonException {

    public OperatorNameTakenException() {
        super(HttpStatus.CONFLICT, "NICKNAME_TAKEN", new MessageRef("operator.name.duplicated"), Map.of("operatorName", new MessageRef("operator.name.duplicated")), null);
    }
}
