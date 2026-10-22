package com.community.mail.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 메일 서버 설정 이력 한 줄.
 *
 * @param id              Snowflake(문자열)
 * @param source          {@code INSTALL}(설치 마법사) · {@code ADMIN}(관리자 화면)
 * @param operatorId      바꾼 운영자 ID(문자열). 지워진 운영자는 null
 * @param operatorName    바꾼 운영자 이름. 지워진 운영자는 대체 문구(「삭제된 운영자」)
 * @param operatorDeleted 운영자가 지워졌는가
 * @param changes         바뀐 칸 — 칸 차례(host · port · security · username · password · fromAddress)
 */
public record MailSettingHistoryItemView(String id, Instant changedAt, String source, String operatorId, String operatorName, boolean operatorDeleted,
                                         List<MailSettingChangeView> changes) {
}
