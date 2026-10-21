/**
 * Mail server inputs (install wizard step 4). Same keys and order as `ko/mail.ts`.
 */
export default {
    // Field names and hints
    hostLabel: 'Server address',
    securityLabel: 'Security type',
    portLabel: 'Port',
    usernameLabel: 'Username',
    passwordLabel: 'Password',
    fromLabel: 'From address',
    optionalHint: 'Optional',
    fromHint: 'Email sender',
    securityOption: '{label} · usually {port}',

    // Help
    checkButton: 'Test connection',
    checkNote: 'Only checks the connection and authentication. No email is sent.',
    noMailNote: 'Without a mail server, sign-up verification and password reset emails are not sent. Sign-up still works, and you can add one later in the settings.',
    usernameHelp: 'Enter this only if the mail server requires a login. If no authentication is needed, leave both the username and password empty.',
    passwordHelpRegistered: 'Registered — enter a new value to change it. Leave it empty to keep the current one.',
    passwordHelpNew: 'It will not be shown again once saved.',
    passwordPlaceholderRegistered: 'Registered · enter a new value to change',

    // Checks done on the screen
    hostRequired: 'Enter the mail server address.',
    portRequired: 'Enter the port.',
    securityRequired: 'Choose a security type.',
    usernameRequired: 'Enter the username.',
    passwordRequired: 'Enter the password.',
    fromRequired: 'Enter the from address.',
    pairUsername: 'Enter the username along with the password. If no authentication is needed, leave both empty.',
    pairPassword: 'To use authentication, enter the password along with the username. If no authentication is needed, leave both empty.',
    hostLength: 'The server address must be {max} characters or fewer.',
    hostSpace: 'The server address cannot contain spaces. e.g. smtp.example.com',
    portRange: 'Enter a number between {min} and {max} for the port.',
    fromLength: 'The from address must be 254 characters or fewer.',
    fromFormat: "Check the email format of the from address. e.g. no-reply{'@'}example.com",

    // Connection test result
    checkConnected: 'Connected to the mail server. Both the connection and authentication succeeded.',
    checkFailed: 'Could not connect to the mail server. Check what you entered.',
    reasonConnectFailed: 'Could not connect to the mail server. Check that the address and port are correct and that a firewall is not blocking it.',
    reasonAuthFailed: 'The username or password is incorrect. Please check them again.',
    reasonTlsFailed: 'The secure (TLS) connection failed. Check that the security type and port match, e.g. 587 for STARTTLS and 465 for SSL/TLS.',
    reasonTimeout: 'The mail server did not respond in time. Check the address and port, then try again in a moment.',
};
