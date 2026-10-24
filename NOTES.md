# Release notes

## 0.13.3
- Alarm editor dropdowns: the arrow sits at the right edge.

## 0.13.2
- Alarm editor dropdowns show their value on the left.

## 0.13.1
- Deleting an alarm, timer or world clock shows Undo; delete actions are styled as destructive.
- The alarm editor follows the 12-hour format with an AM/PM wheel.
- Large text: page titles, editor rows and timer presets no longer cut off or break words.
- Alarm cards and headers use relative dates (Today, Tomorrow, weekday).
- Timers: compact rows, running timers first with a progress bar, "5 min" presets.
- Settings: values in a quieter colour; problems marked with a warning; done setup steps shown with a check.
- Group menu grouped, showing only the actions that apply; groups use a "more" icon.
- Ringtone list shows the current choice; "Alarm sound" is the one way to silence an alarm.
- Stopwatch laps marked "fastest" and "slowest" and use the main display format.
- Accessibility: stronger light-theme contrast (text, switches, selected tab), larger touch targets, tabs announced as selected, puzzle feedback announced, named timer buttons.

## 0.13.0
- More schedules: one-time alarms on a chosen date, every few days, every few weeks on chosen weekdays, monthly (a day or the last day), with optional start and end dates.
- Holidays: a shared list of dates in Settings; alarms with "Skip holidays" don't ring on them.
- Pause an alarm, or a whole group, until a date or for the next few rings; it resumes by itself.
- Wake-up puzzles (math, retype a code, shake the phone) that must be solved to dismiss an alarm; snooze still works.
- Create several alarms at once, spread evenly up to a last time (for example 5 alarms from 7:00 to 8:00).
- New alarms start from the last alarm's settings.
- Timers: +1 min and −1 min while running, +1 min on the ring screen and in the notification.
- Optional automation events and control for apps like Tasker (Settings > Automation).

## 0.12.4
- The built-in ClockPrime tone can be chosen as a ringtone, per alarm and as the default for alarms or timers.
- Typed hours, minutes and seconds out of range: the digits turn gold, shake, and the valid range appears below, instead of Android's red error popup.

## 0.12.3
- Ready for translation: translator notes on the English strings, a checker for every language (`tools/verify-localization.py`), and partial translations fall back to English.
- Right-to-left languages: paddings and arrows follow the reading direction.
- Debug builds include the pseudo-locales English (XA) and Arabic (XB) for layout checks.

## 0.12.2
- ClockPrime is now free software under GPL-3.0-or-later.
- Settings > About: version, license, source code and a Ko-fi support button.
- Release build for F-Droid.
- First start: a "Get alarms ready" checklist for notifications, full-screen alerts, the alarm screen over apps, unrestricted battery and, on brands that have one, the maker's autostart setting. Statuses update as you come back. Also in Settings > Reliability.
- The battery row now opens Android's exemption dialog; it used to do nothing.
- Timers: the Start timer button sits as far below the presets as the presets sit below the Label field.

## 0.11.4
- Selection bar: Skip day is greyed out unless a selected alarm repeats and is on. Skipping a one-time alarm is the same as turning it off.

## 0.11.3
- Alarm cards no longer show the yellow "Automatically silenced" / "Previous alarm missed" lines.
- Switching an alarm on, or saving one that is on, shows when it rings, for example "Alarm set for Tomorrow 07:30 · in 8 h 12 min".

## 0.11.2
- The Timer widget preview shows the idle Timer state.

## 0.11.1
- Widgets: every update now refreshes all parts, so a running timer no longer keeps the idle hint. Text sizes now fit every widget size.
- Picker previews no longer clip. Stopwatch has its own preview, and the list widgets preview sample rows.

## 0.11.0
- New widgets: Analog clock, Stacked clock, Dual clock, Timer (1/5/10 min quick starts), Stopwatch and Alarm groups.
- Fix: starting a timer and cancelling it at once could crash the app.

## 0.10.1
- Widgets: Clock & alarm, Clock (time and date only) and Next alarm.

## 0.10.0
- Home-screen widgets: Clock, Next alarm (countdown, Skip, Turn off), Alarms (a switch per alarm) and World clock.

## 0.9.9
- A newer alarm replaces one still ringing, instead of being skipped, so backup alarms always sound.
- *Ring during Do Not Disturb*: when Do Not Disturb would silence an alarm, it plays at media volume.
- The debug log records whether alarm audio was muted.

## 0.9.8
- The snooze duration in the alarm editor is a dropdown.

## 0.9.7
- Refined alarm editor (Schedule, Sound and Snooze cards) and ring screen.

## 0.9.6
- The overall next-alarm line shows only when some alarms are ungrouped.

## 0.9.5
- Alarms survive a reboot before the first unlock (Direct Boot).

## 0.9.4
- Upcoming-alarm notification lead time: 1–59 minutes, 1–24 hours, or never.

## 0.9.0 – 0.9.3
- Alarm volume is linked to Android's alarm volume.
- Stopwatch in the HyperOS Super Island.
- Settings reorganized.
- Built-in fallback tone, audibility warnings, force-stop detection, and alarm backup export/import.

## 0.8.x
- HyperOS Super Island for alarms (Dismiss / Snooze) and timers.
- The ring screen opens while the phone is unlocked (overlay permission).
- Debug log export.
- Only one alarm rings at a time.

## 0.1 – 0.7
- Core app: alarms and alarm groups, world clocks, stopwatch with laps, multiple timers, settings, themes, and English and Bulgarian.
