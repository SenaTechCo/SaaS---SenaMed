import { useState } from 'react';
import type { MouseEvent } from 'react';
import type { Appointment } from '../types/appointment';
import type { AvailabilitySlot, Doctor } from '../types/doctor';

interface AppointmentCalendarProps {
  view: 'mes' | 'semana' | 'dia';
  appointments: Appointment[];
  availability: AvailabilitySlot[];
  referenceDate: Date;
  onAppointmentClick: (appointment: Appointment) => void;
  doctorsForDayColumns?: Doctor[];
}

const MONTH_WEEKDAY_LABELS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
const WEEKDAY_ABBR = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];

const DAY_START_MINUTES = 7 * 60;
const DAY_END_MINUTES = 20 * 60;
const SLOT_MINUTES = 30;
const ROW_HEIGHT = 40;
const PIXELS_PER_MINUTE = ROW_HEIGHT / SLOT_MINUTES;
const MAX_CHIPS = 3;
const TOTAL_TIMELINE_HEIGHT = ((DAY_END_MINUTES - DAY_START_MINUTES) / SLOT_MINUTES) * ROW_HEIGHT;

function timeToMinutes(time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + (minutes || 0);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  const result = startOfDay(date);
  result.setDate(result.getDate() + days);
  return result;
}

function startOfWeek(date: Date): Date {
  const day = startOfDay(date);
  const jsDay = day.getDay(); // 0 = Sunday
  const isoDay = jsDay === 0 ? 7 : jsDay; // 1 = Monday ... 7 = Sunday
  return addDays(day, 1 - isoDay);
}

function isoDayOfWeek(date: Date): number {
  const jsDay = date.getDay();
  return jsDay === 0 ? 7 : jsDay;
}

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function formatDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function buildMonthGrid(referenceDate: Date): Date[] {
  const firstOfMonth = new Date(referenceDate.getFullYear(), referenceDate.getMonth(), 1);
  const lastOfMonth = new Date(referenceDate.getFullYear(), referenceDate.getMonth() + 1, 0);
  const gridStart = startOfWeek(firstOfMonth);
  const gridEnd = addDays(startOfWeek(lastOfMonth), 6);

  const days: Date[] = [];
  let cursor = gridStart;
  while (cursor.getTime() <= gridEnd.getTime()) {
    days.push(cursor);
    cursor = addDays(cursor, 1);
  }
  return days;
}

function buildTimeSlots(): number[] {
  const slots: number[] = [];
  for (let minutes = DAY_START_MINUTES; minutes < DAY_END_MINUTES; minutes += SLOT_MINUTES) {
    slots.push(minutes);
  }
  return slots;
}

function formatHourLabel(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  return `${String(hours).padStart(2, '0')}:00`;
}

function statusClasses(status: Appointment['status']): string {
  return status === 'CONFIRMED' ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500';
}

function isWithinAvailability(minutes: number, daySlots: AvailabilitySlot[]): boolean {
  return daySlots.some((slot) => minutes >= timeToMinutes(slot.startTime) && minutes < timeToMinutes(slot.endTime));
}

export function AppointmentCalendar({
  view,
  appointments,
  availability,
  referenceDate,
  onAppointmentClick,
  doctorsForDayColumns,
}: AppointmentCalendarProps) {
  const [expandedDays, setExpandedDays] = useState<Record<string, boolean>>({});

  function toggleExpanded(dateKey: string, event: MouseEvent) {
    event.stopPropagation();
    setExpandedDays((prev) => ({ ...prev, [dateKey]: !prev[dateKey] }));
  }

  if (view === 'mes') {
    const today = new Date();
    const monthDays = buildMonthGrid(referenceDate);
    const appointmentsByDate = new Map<string, Appointment[]>();
    appointments.forEach((appointment) => {
      const list = appointmentsByDate.get(appointment.date) ?? [];
      list.push(appointment);
      appointmentsByDate.set(appointment.date, list);
    });

    return (
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        <div className="grid grid-cols-7 border-b border-slate-100 bg-slate-50/80">
          {MONTH_WEEKDAY_LABELS.map((label) => (
            <div key={label} className="px-2 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider text-center">
              {label}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7">
          {monthDays.map((day) => {
            const dateKey = formatDateKey(day);
            const isCurrentMonth = day.getMonth() === referenceDate.getMonth();
            const isToday = isSameDay(day, today);
            const dayAppointments = (appointmentsByDate.get(dateKey) ?? [])
              .slice()
              .sort((a, b) => a.startTime.localeCompare(b.startTime));
            const isExpanded = !!expandedDays[dateKey];
            const visibleAppointments = isExpanded ? dayAppointments : dayAppointments.slice(0, MAX_CHIPS);
            const hiddenCount = dayAppointments.length - MAX_CHIPS;

            return (
              <div
                key={dateKey}
                className={`relative min-h-[110px] border-b border-r border-slate-100 p-1.5 ${
                  isCurrentMonth ? 'bg-white' : 'bg-slate-50/40'
                }`}
              >
                <div
                  className={`inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-medium mb-1 ${
                    isToday ? 'bg-primary-600 text-white' : isCurrentMonth ? 'text-slate-700' : 'text-slate-300'
                  }`}
                >
                  {day.getDate()}
                </div>
                <div className="space-y-1">
                  {visibleAppointments.map((appointment) => (
                    <button
                      key={appointment.id}
                      type="button"
                      onClick={(event) => {
                        event.stopPropagation();
                        onAppointmentClick(appointment);
                      }}
                      className={`w-full truncate text-left rounded px-1.5 py-0.5 text-[11px] font-medium ${statusClasses(
                        appointment.status,
                      )}`}
                      title={`${appointment.startTime} ${appointment.patientName}`}
                    >
                      {appointment.startTime} {appointment.patientName}
                    </button>
                  ))}
                  {hiddenCount > 0 && (
                    <button
                      type="button"
                      onClick={(event) => toggleExpanded(dateKey, event)}
                      className="text-[11px] font-medium text-primary-600 hover:text-primary-700"
                    >
                      {isExpanded ? 'Ver menos' : `+${hiddenCount} mais`}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  const timeSlots = buildTimeSlots();

  if (view === 'semana') {
    const weekStart = startOfWeek(referenceDate);
    const columns = Array.from({ length: 7 }, (_, index) => addDays(weekStart, index));
    const today = new Date();

    return (
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <div className="flex" style={{ minWidth: 760 }}>
            <TimeAxisColumn timeSlots={timeSlots} />
            {columns.map((day) => {
              const dateKey = formatDateKey(day);
              const isToday = isSameDay(day, today);
              const dayOfWeek = isoDayOfWeek(day);
              const daySlots = availability.filter((slot) => slot.dayOfWeek === dayOfWeek);
              const dayAppointments = appointments.filter((appointment) => appointment.date === dateKey);

              return (
                <DayColumn
                  key={dateKey}
                  headerLine1={WEEKDAY_ABBR[dayOfWeek - 1]}
                  headerLine2={String(day.getDate())}
                  isToday={isToday}
                  timeSlots={timeSlots}
                  availabilitySlots={daySlots}
                  hasAvailabilityFilter={availability.length > 0}
                  dayAppointments={dayAppointments}
                  onAppointmentClick={onAppointmentClick}
                />
              );
            })}
          </div>
        </div>
      </div>
    );
  }

  // view === 'dia'
  const today = new Date();
  const dateKey = formatDateKey(referenceDate);

  if (doctorsForDayColumns && doctorsForDayColumns.length > 0) {
    return (
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <div className="flex" style={{ minWidth: doctorsForDayColumns.length * 180 + 56 }}>
            <TimeAxisColumn timeSlots={timeSlots} />
            {doctorsForDayColumns.map((doctor) => {
              const doctorAppointments = appointments.filter(
                (appointment) => appointment.date === dateKey && appointment.doctorId === Number(doctor.id),
              );
              return (
                <DayColumn
                  key={doctor.id}
                  headerLine1={doctor.name}
                  headerLine2=""
                  isToday={isSameDay(referenceDate, today)}
                  timeSlots={timeSlots}
                  availabilitySlots={[]}
                  hasAvailabilityFilter={false}
                  dayAppointments={doctorAppointments}
                  onAppointmentClick={onAppointmentClick}
                />
              );
            })}
          </div>
        </div>
      </div>
    );
  }

  const dayOfWeek = isoDayOfWeek(referenceDate);
  const daySlots = availability.filter((slot) => slot.dayOfWeek === dayOfWeek);
  const dayAppointments = appointments.filter((appointment) => appointment.date === dateKey);

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <div className="flex" style={{ minWidth: 360 }}>
          <TimeAxisColumn timeSlots={timeSlots} />
          <DayColumn
            headerLine1={WEEKDAY_ABBR[dayOfWeek - 1]}
            headerLine2={String(referenceDate.getDate())}
            isToday={isSameDay(referenceDate, today)}
            timeSlots={timeSlots}
            availabilitySlots={daySlots}
            hasAvailabilityFilter={availability.length > 0}
            dayAppointments={dayAppointments}
            onAppointmentClick={onAppointmentClick}
          />
        </div>
      </div>
    </div>
  );
}

function TimeAxisColumn({ timeSlots }: { timeSlots: number[] }) {
  return (
    <div className="w-14 shrink-0 sticky left-0 bg-white z-10">
      <div className="h-12 border-b border-slate-100" />
      {timeSlots.map((minutes) => (
        <div
          key={minutes}
          className="border-t border-slate-100 text-[10px] text-slate-400 text-right pr-1.5"
          style={{ height: ROW_HEIGHT }}
        >
          {minutes % 60 === 0 ? formatHourLabel(minutes) : ''}
        </div>
      ))}
    </div>
  );
}

interface DayColumnProps {
  headerLine1: string;
  headerLine2: string;
  isToday: boolean;
  timeSlots: number[];
  availabilitySlots: AvailabilitySlot[];
  hasAvailabilityFilter: boolean;
  dayAppointments: Appointment[];
  onAppointmentClick: (appointment: Appointment) => void;
}

function DayColumn({
  headerLine1,
  headerLine2,
  isToday,
  timeSlots,
  availabilitySlots,
  hasAvailabilityFilter,
  dayAppointments,
  onAppointmentClick,
}: DayColumnProps) {
  return (
    <div className="flex-1 min-w-[140px] border-l border-slate-100">
      <div
        className={`h-12 border-b border-slate-100 flex flex-col items-center justify-center px-1 ${
          isToday ? 'bg-primary-50' : ''
        }`}
      >
        <span className="text-xs font-medium text-slate-500 truncate max-w-full">{headerLine1}</span>
        {headerLine2 && <span className="text-sm font-semibold text-slate-800">{headerLine2}</span>}
      </div>
      <div className={`relative ${isToday ? 'bg-primary-50/30' : ''}`} style={{ height: TOTAL_TIMELINE_HEIGHT }}>
        {timeSlots.map((minutes, index) => {
          const shaded = hasAvailabilityFilter && !isWithinAvailability(minutes, availabilitySlots);
          return (
            <div
              key={minutes}
              className={`absolute left-0 right-0 border-t border-slate-100 ${shaded ? 'bg-slate-100/70' : ''}`}
              style={{ top: index * ROW_HEIGHT, height: ROW_HEIGHT }}
            />
          );
        })}
        {dayAppointments.map((appointment) => {
          const startMinutes = clamp(timeToMinutes(appointment.startTime), DAY_START_MINUTES, DAY_END_MINUTES);
          const endMinutes = clamp(timeToMinutes(appointment.endTime), DAY_START_MINUTES, DAY_END_MINUTES);
          const top = (startMinutes - DAY_START_MINUTES) * PIXELS_PER_MINUTE;
          const height = Math.max((endMinutes - startMinutes) * PIXELS_PER_MINUTE, 18);

          return (
            <button
              key={appointment.id}
              type="button"
              onClick={() => onAppointmentClick(appointment)}
              className={`absolute left-1 right-1 rounded-md px-1.5 py-0.5 text-left overflow-hidden text-[11px] font-medium leading-tight ${statusClasses(
                appointment.status,
              )}`}
              style={{ top, height }}
              title={`${appointment.startTime} — ${appointment.endTime} ${appointment.patientName}`}
            >
              <span className="block truncate">{appointment.patientName}</span>
              <span className="block truncate opacity-80">
                {appointment.startTime}–{appointment.endTime}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
