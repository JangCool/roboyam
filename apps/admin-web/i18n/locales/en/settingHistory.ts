/**
 * Setting change history (the drawer opened by the "Change history" button next to the social login and mail server screen titles — `SettingHistoryDrawer` + `SettingHistoryTable`).
 * `field.<name>` keys are the server's `labelKey` (`settingHistory.field.<field>`). Same keys and order as `ko/settingHistory.ts`.
 */
export default {
    title: 'Change history',
    open: 'Change history',
    loadFailed: "Couldn't load the change history",
    emptyTitle: 'No changes yet',
    emptyHint: 'Changes from installation and from this screen show up here.',
    changedAt: 'Changed at',
    sourceHeader: 'Source · action',
    provider: 'Provider',
    operator: 'Operator',
    changes: 'Changed fields',
    none: '(none)',
    allProviders: 'All providers',

    // Secret value codes
    secretSet: 'Set',
    secretChanged: 'Changed',
    secretCleared: 'Removed',

    // Source · action
    sourceInstall: 'Install',
    sourceAdmin: 'Admin',
    actionCreate: 'First set',
    actionUpdate: 'Changed',
    actionDelete: 'Deleted',

    // Field names — the server's `labelKey`
    field: {
        clientId: 'Client ID',
        clientSecret: 'Client secret',
        enabled: 'Turn on',
        host: 'Server address',
        port: 'Port',
        security: 'Security',
        username: 'Username',
        password: 'Password',
        fromAddress: 'From address',
        senderName: 'Sender name',
        subjectPrefix: 'Subject prefix',
        // Mail template and layout history (mock — the server will send the same labelKey)
        name: 'Name',
        locale: 'Language',
        layoutId: 'Layout',
        audience: 'Used for',
        subject: 'Subject',
        body: 'Body',
        showLogo: 'Show logo',
        showSiteName: 'Show site name',
        headerColor: 'Header band color',
        footerKo: 'Footer (Korean)',
        footerEn: 'Footer (English)',
        frameKo: 'Frame (Korean)',
        frameEn: 'Frame (English)',
    },
};
