package com.community.title.service;

import com.community.title.entity.TitleEntity;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 칭호 표시 이름 — 이달의 작가는 받은 달로 「11월의 작가」(영어 「November Writer」), 그 밖은 칭호 이름 그대로(계약 「화면」·「구현 결정」 7).
 */
public final class TitleLabels {

    private TitleLabels() {
    }

    /** 한 번 받은 것의 표시 이름(기간을 안다) */
    public static String label(TitleEntity title, String period) {
        Integer month = month(title, period);
        return month == null ? title.getName() : month + "월의 작가";
    }

    /** 영어 표시 이름 — 운영자가 영어 이름을 넣지 않았으면 null(이달의 작가는 달 이름으로 만든다) */
    public static String labelEn(TitleEntity title, String period) {
        Integer month = month(title, period);
        if (month != null) {
            return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " Writer";
        }
        return title.getNameEn();
    }

    private static Integer month(TitleEntity title, String period) {
        if (!BuiltinTitles.MONTHLY_WRITER.equals(title.getTitleKey()) || period == null || !period.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            return null;
        }
        return Integer.parseInt(period.substring(5));
    }
}
