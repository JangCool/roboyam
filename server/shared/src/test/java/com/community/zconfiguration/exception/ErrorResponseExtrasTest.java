package com.community.zconfiguration.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.identity.exception.IdentityReverifyBlockedException;
import com.community.identity.exception.IdentityUnderageException;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.response.ApiResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 오류의 추가 값은 오류 본문 <b>최상위 칸</b>으로 나간다(계약 「0-4」 API 표 머리말 — 화면은 모르는 최상위 칸을 {@code ApiError.extras} 로 읽는다). 추가 값이 없는 오류의 모양은 그대로다.
 */
class ErrorResponseExtrasTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode body(CommonException e) {
        ErrorResponse response = new ErrorResponse(e.getErrorCode(), "문구", Map.of(), e.getRetryAfterSeconds(), e.getRemainingAttempts(), e.getExtras());
        return MAPPER.readTree(MAPPER.writeValueAsString(ApiResponse.failure(response))).path("error");
    }

    @Test
    void 재인증_제한은_retryAfter_날짜를_최상위에_싣는다() {
        JsonNode error = body(new IdentityReverifyBlockedException(LocalDate.of(2026, 11, 24)));
        assertThat(error.path("code").asString()).isEqualTo("IDENTITY_REVERIFY_BLOCKED");
        assertThat(error.path("retryAfter").asString()).isEqualTo("2026-11-24");
        assertThat(error.has("extras")).isFalse();
        assertThat(error.has("extraFields")).isFalse();
        assertThat(error.has("fields")).isFalse();
        assertThat(error.has("retryAfterSeconds")).isFalse();
    }

    @Test
    void 미성년은_adultFrom_을_싣는다() {
        assertThat(body(new IdentityUnderageException(LocalDate.of(2027, 3, 1))).path("adultFrom").asString()).isEqualTo("2027-03-01");
    }

    @Test
    void 기능_제한은_restriction_객체를_싣고_무기한이면_until_은_null() {
        JsonNode error = body(new MemberRestrictedException(RestrictionType.GROUP_BUY_BAN, Instant.parse("2026-10-25T05:00:00Z"), "공개 사유"));
        JsonNode restriction = error.path("restriction");
        assertThat(restriction.path("type").asString()).isEqualTo("GROUP_BUY_BAN");
        assertThat(restriction.path("until").asString()).isEqualTo("2026-10-25T05:00:00Z");
        assertThat(restriction.path("reason").asString()).isEqualTo("공개 사유");
        assertThat(restriction.propertyNames()).containsExactlyInAnyOrder("type", "until", "reason");

        JsonNode indefinite = body(new MemberRestrictedException(RestrictionType.CHARGE_BAN, null, "사유")).path("restriction");
        assertThat(indefinite.has("until")).isTrue();
        assertThat(indefinite.path("until").isNull()).isTrue();
    }

    @Test
    void 추가_값이_없는_오류는_예전_모양_그대로다() throws Exception {
        String json = MAPPER.writeValueAsString(ApiResponse.failure(new ErrorResponse("VALIDATION", "문구", Map.of("root.email", "칸 문구"), 600L, 3)));
        JsonNode error = MAPPER.readTree(json).path("error");
        assertThat(error.propertyNames()).containsExactlyInAnyOrder("code", "message", "fields", "retryAfterSeconds", "remainingAttempts");
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(ErrorResponse.of("NOT_FOUND", "없음"))).propertyNames()).containsExactlyInAnyOrder("code", "message");
    }

    @Test
    void 정해진_칸_이름은_추가_값으로_쓸_수_없다() {
        assertThatThrownBy(() -> new CommonException(HttpStatus.CONFLICT, "X", new MessageRef("error.X")) {
            {
                extra("code", "덮어쓰기");
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }
}
