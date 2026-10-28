package com.community.home.dto.response.gadget;

import java.util.List;

/** {@code BOARD_CARDS} 가젯의 data — user-web {@code BoardCardsData} */
public record BoardCardsDataView(List<BoardCardView> cards) {
}
