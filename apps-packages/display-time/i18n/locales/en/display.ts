/**
 * Labels for the display-value form (`DisplayValueForm`): language, time zone, time format, date format. Same keys and order as `ko/display.ts`.
 */
export default {
    // Field names and hints
    language: 'Language',
    zoneLabel: 'Time zone',
    zoneHelp: 'Used for display only. Times are stored in UTC, so changing this does not alter existing records.',
    zoneFilter: 'Search by city or region (e.g. New_York, Seoul)',
    zoneEmpty: 'No matching time zone',
    zonePlaceholder: 'Select a time zone',
    timeFormat: 'Time format',
    dateFormat: 'Date format',
    previewTitle: 'Preview',
    previewPast: 'Before yesterday',

    // Time zone group names
    zoneGroupMajor: 'Common time zones',
    zoneGroupAll: 'All time zones',

    // Format names — a sample in the chosen time zone follows in parentheses (`24-hour (14:32)`)
    timeFormat_H24: '24-hour',
    timeFormat_H12: '12-hour',
    dateFormat_YMD_DOT: 'Year.Month.Day',
    dateFormat_YMD_DASH: 'Year-Month-Day',
    dateFormat_DMY_SLASH: 'Day/Month/Year',
    dateFormat_MDY_SLASH: 'Month/Day/Year',
};
