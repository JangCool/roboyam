package com.community.mail.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 메일 서버 설정 이력 한 줄(소셜 로그인 설정 이력 {@code SocialSettingHistoryView} 와 같은 모양, 공급자 칸만 없다).
 *
 * @param id              Snowflake(문자열)
 * @param action          {@code CREATE}(처음 만듦) · {@code UPDATE}(고침). {@code DELETE} 는 아직 없다
 * @param source          {@code INSTALL}(설치 마법사) · {@code ADMIN}(관리자 화면)
 * @param operatorId      바꾼 운영자 ID(문자열). 모르면 null
 * @param operatorName    바꾼 운영자 이름 — 운영자가 지워졌거나 모르면 대체 문구(「삭제된 운영자」, 요청 언어)
 * @param operatorDeleted 운영자를 찾을 수 없어 대체 문구를 넣었는가
 * @param changes         바뀐 칸 — 칸 차례 host · port · security · username · password · fromAddress
 */
public record MailSettingHistoryItemView(String id, String action, String source, String operatorId, String operatorName, boolean operatorDeleted,
                                         Instant changedAt, List<MailSettingChangeView> changes) {
}
