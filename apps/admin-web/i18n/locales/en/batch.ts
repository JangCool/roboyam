/**
 * Batch jobs and batch logs (ported from hamonica3 D-89). Both screens share the result tags. Keys are `batch.what` — no screen names.
 * Same keys and order as `ko/batch.ts`.
 * ⚠ Job names, descriptions and the last-run message come from the server already in the request language, so they are not kept here.
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
    schedule: 'Schedule',
    lastRun: 'Last run',
    nextRun: 'Next run',
    neverRan: 'Has not run yet',
    nextRunOff: 'Off, so nothing is planned',
    nextRunNone: 'Nothing planned',
    enabled: 'Enabled',
    toggleLabel: 'Turn {name} on or off',
    runNow: 'Run now',
    running: 'Running',
    viewLogs: 'View logs',
    viewLogsLabel: 'View run logs of {name}',
    noManageReason: 'You do not have permission to turn jobs on or off.',
    noRunReason: 'You do not have permission to run jobs now.',
    turnedOn: 'Turned on',
    turnedOff: 'Turned off',
    runNowNote: 'Run now works even when the job is off.',
    cronZoneNote: 'Schedules follow the server time zone (Asia/Seoul).',

    runConfirmTitle: 'Run this job now?',
    runConfirmMessage: '"{name}" — it waits until the job finishes and runs even when the job is off. The outcome cannot be undone and is kept in the batch logs.',
    runConfirmLabel: 'Run now',

    emptyJobs: 'No jobs are scheduled',
    emptyJobsHint: 'Jobs registered on the server show up here.',
    emptyLogs: 'Nothing recorded yet',
    emptyLogsHint: 'Once a job runs, its history collects here.',
    jobsLoadFailed: 'Could not load the jobs',
    logsLoadFailed: 'Could not load the batch logs',

    startedAt: 'Started',
    result: 'Result',
    trigger: 'Started by',
    elapsed: 'Took',
    affected: 'Rows',
    message: 'Message',
    countCases: '{n} rows',

    statusRunning: 'Running',
    statusOk: 'Succeeded',
    statusFail: 'Failed',
    statusSkip: 'Nothing to do',
    triggerScheduled: 'Scheduled',
    triggerManual: 'Manual',
    runBySeconds: '{n} s',
    manualRunNote: 'Someone ran it by hand, so it has nothing to do with the schedule. The result is kept in',
    batchLogsLink: 'Batch logs',
    remainsIn: '.',

    // ── Schedule (cron) in plain words ──
    cronDaily: 'Every day at {time}',
    cronWeekly: 'Every {day} at {time}',
    cronMonthly: 'Day {day} of every month at {time}',
    cronHourly: 'Every hour at minute {minute}',
    cronEveryMinutes: 'Every {n} minutes',
    cronDow0: 'Sunday',
    cronDow1: 'Monday',
    cronDow2: 'Tuesday',
    cronDow3: 'Wednesday',
    cronDow4: 'Thursday',
    cronDow5: 'Friday',
    cronDow6: 'Saturday',
};
