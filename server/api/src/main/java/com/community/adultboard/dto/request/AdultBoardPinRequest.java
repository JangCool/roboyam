package com.community.adultboard.dto.request;

/** {@code PUT /api/me/adult-board/pin} — 핀번호 바꾸기·잊었을 때 다시 정하기 {@code { password, pin }}. 검사는 서비스가 한다 */
public record AdultBoardPinRequest(String password, String pin) {

    @Override
    public String toString() {
        return "AdultBoardPinRequest[password=(감춤), pin=(감춤)]";
    }
}
