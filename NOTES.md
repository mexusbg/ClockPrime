# Release notes

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
