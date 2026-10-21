"""Generates res/raw/fallback_alarm.wav: a loopable two-tone alarm pattern, no external assets."""
import math, struct, wave
from pathlib import Path

RATE = 22050
out = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/fallback_alarm.wav'

def tone(freq, seconds, volume=0.55):
    n = int(RATE * seconds)
    for i in range(n):
        t = i / RATE
        attack, release = min(1, t / 0.01), min(1, (seconds - t) / 0.04)
        envelope = max(0.0, min(attack, release))
        sample = math.sin(2 * math.pi * freq * t) + 0.35 * math.sin(2 * math.pi * freq * 2 * t)
        yield volume * envelope * sample / 1.35

def silence(seconds):
    yield from (0.0 for _ in range(int(RATE * seconds)))

pattern = []
for _ in range(2):
    pattern += [*tone(880, 0.16), *silence(0.07), *tone(1175, 0.16), *silence(0.07)]
pattern += [*silence(0.55)]
with wave.open(str(out), 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
    w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32767)) for s in pattern))
print(out, out.stat().st_size, 'bytes', round(len(pattern) / RATE, 2), 's')
