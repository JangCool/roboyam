package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code WITHDRAWAL_EMOTICON_EARNINGS_UNPAID} — 아직 지급되지 않은 로보티콘 수익(양수)이 있는데 정산 정보가 없고 포기 동의({@code waiveEmoticonEarnings: true})도 없다(V148).
 * 회원은 정산 정보를 먼저 등록하거나(그러면 탈퇴 뒤 다음 확정에서 최소 지급액과 상관없이 한 번에 지급) 남은 수익 포기에 동의해야 한다.
 * 오류 본문 최상위 {@code unpaidEarnings} = 그 금액(원천징수 전, 원) — 화면은 이 금액을 {@code forfeitConsent.emoticonEarnings} 로 다시 보낸다.
 */
public class WithdrawalEmoticonEarningsUnpaidException extends CommonException {

    public static final String CODE = "WITHDRAWAL_EMOTICON_EARNINGS_UNPAID";

    public WithdrawalEmoticonEarningsUnpaidException(long unpaidEarnings) {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error." + CODE, unpaidEarnings));
        extra("unpaidEarnings", unpaidEarnings);
    }
}
