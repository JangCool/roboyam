package com.community.groupbuy.service;

import com.community.groupbuy.repository.master.GroupBuyInvariantQueries;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 공구 불변식 검사(commerce.md 18절 「불변식 추가」, 계약 결정 28·39·65·77·80) — <b>한 SQL</b>(같은 스냅샷)로 어긋난 줄만 돌려준다(읽기만). 비면 맞다.
 *
 * <ol>
 *   <li>공구 {@code current_qty} = 결제 확정된 주문의 <b>MAIN</b> 단품 줄 판매 수량 Σ(qty − canceled − restocked)(결제 중·만료 주문은 빼고 — 추가 구성상품은 공구 수량에 들지 않는다, 결정 88)</li>
 *   <li>공구 {@code reserved_qty} = 결제 중 주문의 MAIN 줄 수량 Σ</li>
 *   <li>단품별 {@code current_qty}·{@code reserved_qty} = 그 단품 줄의 같은 합(MAIN·ADDON 모두)</li>
 *   <li>Σ MAIN 단품 current = 공구 current, Σ MAIN 단품 reserved = 공구 reserved(단품 최대 합과 공구 최대는 관계 없다)</li>
 *   <li>공구 값을 적은 수량 이동 줄의 마지막(ID 순) 이후 값 = 지금 공구 값, 단품 값을 적은 마지막 줄 = 지금 단품 값</li>
 *   <li>모집을 시작한 공구: {@code max_qty = initial_max_qty + ΣADD − ΣREDUCE}(공구 값을 적은 줄만 — 추가 구성상품 입고는 빼고), {@code added_qty = ΣADD}</li>
 *   <li>받아들인 결제(이상 없음): 결제 − 취소됨 − 아직 끝나지 않은 취소 − 계좌 송금으로 돌린 취소 = 현금 − 환불 누계 − 보상 누계(결정 39·74)</li>
 *   <li>반품비 추가 결제(결정 68): 결제 − 취소됨 − 끝나지 않은 취소 = 요청의 낸 금액 − 돌려준 금액</li>
 * </ol>
 * 대상 공구를 주면 그 공구들만 본다(시험·운영자 점검), 비면 전체. SQL(native — CTE 집계)은 저장소 {@link GroupBuyInvariantQueries} 다.
 */
@Component
public class GroupBuyInvariantCheck {

    /** 어긋난 한 줄 — {@code check} 이름, 대상(공구·단품·주문) ID, 기대값, 실제값 */
    public record Violation(String check, long targetId, long expected, long actual) {
    }

    private final GroupBuyInvariantQueries queries;

    public GroupBuyInvariantCheck(GroupBuyInvariantQueries queries) {
        this.queries = queries;
    }

    /** 어긋난 줄(비면 맞다) */
    public List<Violation> violations(List<Long> groupBuyIds) {
        Long[] ids = groupBuyIds == null ? new Long[0] : groupBuyIds.toArray(Long[]::new);
        return queries.violations(ids).stream().map(v -> new Violation(v.check(), v.targetId(), v.expected(), v.actual())).toList();
    }
}
