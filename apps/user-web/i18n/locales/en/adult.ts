/**
 * Age check (`/settings/adult`) — self-reported birth year. The adult label is the service term `ADULT_LABEL` (`{label}`). Same keys and order as `ko/adult.ts`.
 */
export default {
    seo: 'Confirm your age to see {label} posts.',
    loading: 'Loading age check status',
    loadFailed: 'Couldn’t load your age check status',
    stateLabel: 'Age check status',
    stateNone: 'Not confirmed yet',
    stateConfirmed: 'Adult confirmed',
    stateConfirmedYear: 'Adult confirmed · born {y}',
    stateMinor: 'Not an adult yet · born {y}',
    stateMinorNote: 'You can see {label} posts from {y}.',
    birthLabel: 'Birth year',
    birthPlaceholder: 'Choose your birth year',
    yearOption: '{y}',
    confirmLabel: 'I confirm that this birth year is accurate',
    birthRequired: 'Choose your birth year.',
    birthTooYoung: 'You must be at least {age} to use this service.',
    birthOutOfRange: 'Check your birth year.',
    confirmRequired: 'Confirm that the birth year is accurate.',
    saved: 'Birth year saved',
    savedAdult: 'Adult confirmed',
    saveFailed: 'Couldn’t save. {reason}',
    note1: '{label} posts are shown only to members who confirmed their age.',
    note2: 'We use the birth year you entered. You count as an adult from January 1 of the year you turn {age}.',
    note3: 'Entering a false year may restrict your account.',
};
