package com.community.home.dto.response.gadget;

import java.util.List;

/** 목록 하나만 싣는 가젯 data — {@code GALLERY}·{@code GROUPS}·{@code GROUP_BUY}·{@code QUICK_LINKS} 의 {@code { items }} */
public record ItemsDataView(List<?> items) {
}
