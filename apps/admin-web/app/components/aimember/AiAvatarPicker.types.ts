/** `AiAvatarPicker` 이 고를 수 있는 아바타 하나 — AI 회원 고르기 목록(`options.avatars`)에서 부르는 쪽이 만든다 */
export interface AiAvatarChoice {
    /** 카탈로그 아바타 ID */
    avatarId: string;
    name: string;
    /** 카탈로그 카테고리 ID(`job` …) */
    category: string | null;
}
