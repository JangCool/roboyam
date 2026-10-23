package com.community.commerce.support;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.dto.StoredResponse;
import com.community.commerce.guard.exception.IdempotencyKeyRequiredException;
import com.community.commerce.guard.service.IdempotencyService;
import com.community.commerce.guard.service.RequestHashes;
import com.community.commerce.guard.service.RequestRateLimiter;
import com.community.zconfiguration.response.ApiResponse;
import java.util.function.Supplier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 돈이 움직이는 회원 API(충전 요청·공구 참여·상점 구매·환불 신청)의 입구 도우미 — 컨트롤러가 이것으로 업무를 감싼다(docs/domain/commerce.md 3절·12-4).
 *
 * <pre>{@code
 * @PostMapping("/api/shop/purchases")
 * public ResponseEntity<ApiResponse<Object>> purchase(@RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String key,
 *                                                     @RequestBody PurchaseRequest body, Authentication auth) {
 *     long userId = ...;
 *     return guard.handle(userId, key, RequestAction.SHOP_PURCHASE, body, () -> purchases.purchase(userId, body));
 * }
 * }</pre>
 *
 * <ol>
 *   <li>{@value #HEADER} 헤더가 없으면 400 {@code IDEMPOTENCY_KEY_REQUIRED}, 형식이 틀리면 400 {@code IDEMPOTENCY_KEY_INVALID}.</li>
 *   <li>속도 제한({@link RequestRateLimiter}) — 업무와 따로 센다. 넘으면 429 + {@code Retry-After}. 같은 키로 다시 보낸 요청(응답 다시 받기)도 한 번으로 센다.</li>
 *   <li>멱등 키({@link IdempotencyService}) — 같은 키·같은 본문이면 업무 없이 처음 응답({@code data})을 다시 주고 머리글 {@value #REPLAYED_HEADER}: true.
 *       같은 키·다른 본문 409 {@code IDEMPOTENCY_KEY_REUSED}, 처리 중 409 {@code IDEMPOTENCY_IN_PROGRESS}. 업무가 실패하면 같은 키로 다시 할 수 있다.</li>
 * </ol>
 * 저장하는 것은 응답의 {@code data} 뿐이다 — 다시 줄 때 {@code meta}(요청 번호·시각)는 그 요청의 것으로 새로 만든다. 응답은 {@code Cache-Control: no-store}.
 */
@Component
public class MoneyRequestGuard {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RequestRateLimiter limiter;
    private final IdempotencyService idempotency;

    public MoneyRequestGuard(RequestRateLimiter limiter, IdempotencyService idempotency) {
        this.limiter = limiter;
        this.idempotency = idempotency;
    }

    /**
     * @param body 요청 본문(해시에 쓴다 — 같은 키에 다른 본문이면 409)
     * @param work 업무 — 업무 트랜잭션 안에서 불린다. 돌려준 값이 응답의 {@code data} 다(200)
     */
    public ResponseEntity<ApiResponse<Object>> handle(long userId, String idempotencyKey, RequestAction action, Object body, Supplier<?> work) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
        String key = idempotencyKey.strip();
        limiter.check(userId, action);
        StoredResponse response = idempotency.execute(userId, key, action, RequestHashes.of(action, body),
                () -> StoredResponse.fresh(200, MAPPER.writeValueAsString(work.get())));
        JsonNode data = response.body() == null ? null : MAPPER.readTree(response.body());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status()).cacheControl(CacheControl.noStore());
        if (response.replayed()) {
            builder.header(REPLAYED_HEADER, "true");
        }
        return builder.body(ApiResponse.success(data));
    }
}
