package com.community.user.entity;

/**
 * 회원 등급. {@code profiles.grade}(1~4)와 같은 번호이고, 화면 응답에는 이름({@link #name()})과 요청 언어로 푼 표시 이름이 나간다.
 * 표시 이름은 메시지 번들의 {@code grade.<이름>} 키다.
 */
public enum Grade {
    SEEDLING(1),
    REGULAR(2),
    LOYAL(3),
    VETERAN(4);

    private final int level;

    Grade(int level) {
        this.level = level;
    }

    /** 표시 이름의 메시지 키({@code grade.<이름>}) */
    public String labelKey() {
        return "grade." + name();
    }

    /** 등급 번호(1~4)로 찾는다. 범위 밖이면(DB 의 CHECK 가 막는다) 가장 낮은 등급으로 본다 */
    public static Grade fromLevel(int level) {
        for (Grade grade : values()) {
            if (grade.level == level) {
                return grade;
            }
        }
        return SEEDLING;
    }
}
