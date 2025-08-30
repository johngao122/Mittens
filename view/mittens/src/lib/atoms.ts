import { addDays, endOfDay, isWithinInterval, startOfDay } from "date-fns";
import { atom } from "jotai";
import type { DateRange } from "react-day-picker";
import type { TicketMetric } from "@/types/types";

// Mock data - replace with actual data source
const averageTicketsCreated = [
    { date: "2023-12-18", resolved: 5, created: 8 },
    { date: "2023-12-19", resolved: 3, created: 6 },
    { date: "2023-12-20", resolved: 7, created: 4 },
    { date: "2023-12-21", resolved: 4, created: 9 },
    { date: "2023-12-22", resolved: 6, created: 5 },
    { date: "2023-12-23", resolved: 2, created: 7 },
    { date: "2023-12-24", resolved: 8, created: 3 },
];

const defaultStartDate = new Date(2023, 11, 18);

export const dateRangeAtom = atom<DateRange | undefined>({
    from: defaultStartDate,
    to: addDays(defaultStartDate, 6),
});

export const ticketChartDataAtom = atom((get) => {
    const dateRange = get(dateRangeAtom);

    if (!dateRange?.from || !dateRange?.to) return [];

    const startDate = startOfDay(dateRange.from);
    const endDate = endOfDay(dateRange.to);

    return averageTicketsCreated
        .filter((item) => {
            const [year, month, day] = item.date.split("-").map(Number);
            const date = new Date(year, month - 1, day);
            return isWithinInterval(date, { start: startDate, end: endDate });
        })
        .flatMap((item) => {
            const res: TicketMetric[] = [
                {
                    date: item.date,
                    type: "resolved",
                    count: item.resolved,
                },
                {
                    date: item.date,
                    type: "created",
                    count: item.created,
                },
            ];
            return res;
        });
});
