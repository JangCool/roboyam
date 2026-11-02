package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 판매자(광고주)가 일시 정지·중단해 숨긴 사업자 핫딜 글을 직접 열었다(403 {@code DEAL_SELLER_HIDDEN} — V127, biz-pause-contract 규칙 9). 404 가 아니라 안내만:
 * extras {@code state}(PAUSED·STOPPED) · {@code boardSlug} · {@code boardName} · {@code boardNameEn} · {@code applicationId}(보는 사람이 그 사업자일 때만, 아니면 칸 없음).
 * 본문·댓글·딜 칸은 싣지 않는다.
 */
public class DealSellerHiddenException extends CommonException {

    public DealSellerHiddenException(String state, String boardSlug, String boardName, String boardNameEn, String applicationId) {
        super(HttpStatus.FORBIDDEN, "DEAL_SELLER_HIDDEN", new MessageRef("error.DEAL_SELLER_HIDDEN"));
        extra("state", state);
        extra("boardSlug", boardSlug);
        extra("boardName", boardName);
        extra("boardNameEn", boardNameEn);
        if (applicationId != null) {
            extra("applicationId", applicationId);
        }
    }
}
