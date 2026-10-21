/**
 * Batch jobs and batch logs (ported from hamonica3 D-89). Both screens share the result tags. Keys are `batch.what` — no screen names.
 * Same keys and order as `ko/batch.ts`.
 * ⚠ The "Java batch" names and descriptions (`auditRetention` …) are texts for jobs the server sends as message keys (`i18n`, `descI18n`) — a job without a key (made by the customer) shows the text the server wrote.
 */
export default {
    title: 'Batch jobs',
    subtitle: 'See and manage the jobs that run on a schedule.',
    logsTitle: 'Batch logs',
    logsSubtitle: 'When each job ran and how it ended.',

    // hamonica3 common texts "Reload" / "Clear filters" — not in the app-wide texts (`@roboyam/i18n`) yet, so they live here
    reload: 'Reload',
    clearFilter: 'Clear filters',

    batch: 'Job',
    where: 'Runs in',
    whereApp: 'Application',
    whereDb: 'Database',
    schedule: 'Schedule',
    lastRun: 'Last run',
    neverRan: 'Has not run yet',
    enabled: 'Enabled',
    toggleLabel: 'Turn {name} on or off',
    runNow: 'Run now',
    dbRunsTooltip: 'pg_cron commands are run by the database itself',
    turnedOn: 'Turned on',
    turnedOff: 'Turned off',
    runNowNote: 'Run now works even when the job is off.',

    emptyJobs: 'No jobs are scheduled',
    emptyJobsHint: "Add {'@'}Scheduled in code, or register a pg_cron job, and it shows up here.",
    emptyLogs: 'Nothing recorded yet',
    emptyLogsHint: 'Once a job runs, its history collects here.',
    jobsLoadFailed: 'Could not load the jobs',
    logsLoadFailed: 'Could not load the batch logs',

    startedAt: 'Started',
    result: 'Result',
    elapsed: 'Took',
    affected: 'Rows',
    message: 'Message',

    statusOk: 'Succeeded',
    statusFail: 'Failed',
    statusSkip: 'Nothing to do',
    dbBadge: 'App',
    dbBadgeDb: 'DB',
    runBySeconds: '{n} s',
    runByDb: 'Run by the database',
    manualRunNote: 'Someone ran it by hand, so it has nothing to do with the schedule. The result is kept in',
    batchLogsLink: 'Batch logs',
    remainsIn: '.',

    // ── Java batch jobs — sent by the server as keys ──
    auditRetention: 'Audit log cleanup',
    auditRetentionDesc: 'Drops whole chunks of audit log past the retention period.',
    accountExpiry: 'Account expiry',
    accountExpiryDesc: 'Marks accounts past their valid period as expired. Blocking is done by sign-in itself.',
};
