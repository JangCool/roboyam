package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * 403 — 19금 글인데 보는 사람이 성인이 아니다(adult-content-contract). 오류 본문 최상위 {@code reason}: {@code LOGIN}(비회원) · {@code IDENTITY}(본인인증 전) ·
 * {@code UNDERAGE}(만 19세 미만 — {@code adultFrom} = 만 19세가 되는 서비스 날짜 {@code YYYY-MM-DD}). 화면은 사유마다 로그인·본인인증 안내를 보인다.
 */
public class PostAdultOnlyException extends CommonException {

    public static final String LOGIN = "LOGIN";
    public static final String IDENTITY = "IDENTITY";
    public static final String UNDERAGE = "UNDERAGE";

    public PostAdultOnlyException(String reason, LocalDate adultFrom) {
        super(HttpStatus.FORBIDDEN, "POST_ADULT_ONLY", new MessageRef("error.POST_ADULT_ONLY"));
        extra("reason", reason);
        if (adultFrom != null) {
            extra("adultFrom", adultFrom.toString());
        }
    }
}
