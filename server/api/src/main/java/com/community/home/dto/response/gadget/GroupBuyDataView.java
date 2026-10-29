package com.community.home.dto.response.gadget;

import com.community.groupbuy.dto.response.GroupBuyMemberView;
import java.util.List;

/**
 * {@code GROUP_BUY} 가젯 data — 줄은 회원 공구 목록({@code GET /api/group-buys})과 같은 모양이다.
 *
 * @param salesOpen 판매 스위치(꺼져도 목록처럼 공구는 보이고, 화면이 「지금은 주문을 받지 않는다」를 덧붙인다)
 */
public record GroupBuyDataView(List<GroupBuyMemberView.ListItem> items, boolean salesOpen) {
}
