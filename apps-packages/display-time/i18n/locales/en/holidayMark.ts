/**
 * Day-off marks on calendar cells (`UtilHolidayMessages`) — shared by every calendar. Same keys and order as `ko/holidayMark.ts`.
 */
export default {
    // Accessible name — "9/25/2026, Chuseok"
    cellLabel: '{month}/{day}/{year}, {names}',
    // Suffix after a company day-off name (admin app only)
    company: '{name} (company day off)',
    // When the name is empty
    companyOnly: 'Company day off',
    holidayOnly: 'Public holiday',
};
