/**
 * 가입·로그인 입력 안내 — 화면이 미리 검사한 닉네임·이메일 문제와 서버가 알려 준 닉네임 불가 사유의 문구(`utils/util-auth-messages.ts` 가 코드로 고른다).
 * 서버 오류 코드의 문구는 `error.ts`. 키는 `auth.무엇` 두 마디. `en/auth.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 닉네임 — 서버 사유(`nickname-available` 의 reason)와 화면 검사가 같은 말을 쓴다
    nicknameLength: '닉네임은 2자 이상 12자 이하로 입력해 주세요.',
    nicknameTooShort: '닉네임은 2자 이상 입력해 주세요.',
    nicknameChars: '한글·영문·숫자·밑줄(_)과 글자 사이의 공백만 쓸 수 있어요.',
    nicknameDuplicate: '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 입력해 주세요.',
    nicknameReserved: '쓸 수 없는 닉네임이에요. 다른 닉네임을 입력해 주세요.',
    nicknameRequired: '닉네임을 입력해 주세요.',

    // 이메일
    emailRequired: '이메일을 입력해 주세요.',
    emailLength: '이메일은 254자 이하로 입력해 주세요.',
    emailFormat: '이메일 주소 형식을 확인해 주세요.',
};
