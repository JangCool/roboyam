/**
 * Sign-up / log-in input hints: nickname and email problems found on the screen, and nickname rejection reasons from the server. Same keys and order as `ko/auth.ts`.
 */
export default {
    // Nickname
    nicknameLength: 'Use 2 to 12 characters for your nickname.',
    nicknameTooShort: 'Use at least 2 characters for your nickname.',
    nicknameChars: 'Only Korean and English letters, numbers, underscores (_) and spaces between characters are allowed.',
    nicknameDuplicate: 'This nickname is already taken. Please enter another one.',
    nicknameReserved: 'This nickname is not allowed. Please enter another one.',
    nicknameRequired: 'Enter a nickname.',

    // Email
    emailRequired: 'Enter your email.',
    emailLength: 'The email must be 254 characters or fewer.',
    emailFormat: 'Check the email address format.',
};
