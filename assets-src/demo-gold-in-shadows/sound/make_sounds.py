"""Generates Gold in Shadows' sound effects and music (F-029, I-600).

The original game has no audio, so these are new, original, synthesised sounds (square, triangle and noise voices),
written as 22 050 Hz mono 16-bit WAV files into demos/demo-gold-in-shadows/core/assets/sfx and .../music.
Standard library only:

    python assets-src/demo-gold-in-shadows/sound/make_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 22050
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'demos', 'demo-gold-in-shadows',
                    'core', 'assets')


def note(name):
    names = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1,
             'B': 2}
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[pitch] + (octave - 4) * 12) / 12)


def square(phase, duty=0.5):
    return 1.0 if (phase % 1.0) < duty else -1.0


def triangle(phase):
    p = phase % 1.0
    return 4 * p - 1 if p < 0.5 else 3 - 4 * p


def write(path, samples):
    full = os.path.normpath(os.path.join(ROOT, path))
    os.makedirs(os.path.dirname(full), exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = min(1.0, 0.9 / peak)
    with wave.open(full, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1.0, min(1.0, s * scale)) * 32767)) for s in samples))
    print(f'{path}: {len(samples) / RATE:.2f} s')


def sweep(f0, f1, seconds, volume, duty=0.5, noise=0.0, seed=1, power=1.5):
    rnd = random.Random(seed)
    n = int(seconds * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        f = f0 * (f1 / f0) ** t
        phase += f / RATE
        env = (1 - t) ** power
        s = square(phase, duty) * (1 - noise) + (rnd.uniform(-1, 1) * noise)
        out.append(s * env * volume)
    return out


def tone(freq, seconds, volume, duty=0.5, release=0.6):
    n = int(seconds * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        phase += freq / RATE
        env = min(1.0, i / (0.004 * RATE)) * (1 - t) ** release
        out.append(square(phase, duty) * env * volume)
    return out


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def sfx():
    # The rifle: a short, bright crack with a noise tail; quiet enough for 600 rounds per minute.
    write('sfx/shoot.wav', sweep(1100, 260, 0.05, 0.22, duty=0.25, noise=0.35, seed=4, power=2.2))
    write('sfx/empty.wav', tone(note('C6'), 0.025, 0.2, 0.125, release=2))
    write('sfx/reload.wav', tone(note('D4'), 0.04, 0.3, 0.5) + silence(0.12) + tone(note('A4'), 0.06, 0.3, 0.5))
    write('sfx/impact.wav', sweep(500, 200, 0.04, 0.2, duty=0.5, noise=0.6, seed=9))
    write('sfx/enemy_hit.wav', sweep(420, 160, 0.07, 0.3, duty=0.5, noise=0.45, seed=5))
    write('sfx/enemy_die.wav', sweep(320, 55, 0.3, 0.45, duty=0.5, noise=0.5, seed=6))
    hurt = sweep(260, 70, 0.32, 0.45, duty=0.5, noise=0.25, seed=7)
    write('sfx/hurt.wav', [s * (0.6 + 0.4 * math.sin(i / RATE * 2 * math.pi * 28)) for i, s in enumerate(hurt)])
    write('sfx/death.wav', sweep(300, 40, 1.2, 0.5, duty=0.5, noise=0.3, seed=12, power=0.9))
    write('sfx/coin.wav', tone(note('B5'), 0.05, 0.28, 0.25) + tone(note('E6'), 0.1, 0.28, 0.25))
    write('sfx/gold.wav', tone(note('E5'), 0.05, 0.28, 0.25) + tone(note('G#5'), 0.05, 0.28, 0.25)
          + tone(note('B5'), 0.05, 0.28, 0.25) + tone(note('E6'), 0.14, 0.3, 0.25))
    write('sfx/heal.wav', tone(note('C5'), 0.08, 0.3, 0.5) + tone(note('E5'), 0.08, 0.3, 0.5)
          + tone(note('G5'), 0.16, 0.3, 0.5))
    write('sfx/key.wav', tone(note('A5'), 0.06, 0.3, 0.125) + tone(note('D6'), 0.06, 0.3, 0.125)
          + tone(note('A6'), 0.2, 0.3, 0.125))
    write('sfx/chest.wav', sweep(180, 90, 0.12, 0.4, duty=0.5, noise=0.4, seed=10) + silence(0.03)
          + tone(note('G5'), 0.08, 0.25, 0.25) + tone(note('D6'), 0.12, 0.25, 0.25))
    write('sfx/door.wav', sweep(70, 45, 0.6, 0.5, duty=0.5, noise=0.35, seed=11, power=0.7))
    write('sfx/locked.wav', tone(note('C3'), 0.08, 0.35, 0.5) + silence(0.04) + tone(note('C3'), 0.08, 0.35, 0.5))
    write('sfx/spikes.wav', sweep(1400, 700, 0.07, 0.25, duty=0.125, noise=0.5, seed=13, power=2))
    write('sfx/alert.wav', tone(note('E5'), 0.05, 0.25, 0.25) + tone(note('A5'), 0.09, 0.25, 0.25))
    write('sfx/spit.wav', sweep(200, 420, 0.12, 0.3, duty=0.5, noise=0.5, seed=14, power=1.2))
    write('sfx/bolt.wav', sweep(700, 1400, 0.1, 0.22, duty=0.25, noise=0.1, seed=15, power=1.2))
    write('sfx/dash.wav', sweep(150, 600, 0.18, 0.35, duty=0.5, noise=0.6, seed=16, power=1.0))
    write('sfx/bite.wav', sweep(600, 250, 0.05, 0.3, duty=0.5, noise=0.7, seed=17))
    write('sfx/menu.wav', tone(note('A5'), 0.03, 0.2, 0.25, release=1.5))
    write('sfx/level_clear.wav', tone(note('C5'), 0.1, 0.3, 0.25) + tone(note('E5'), 0.1, 0.3, 0.25)
          + tone(note('G5'), 0.1, 0.3, 0.25) + tone(note('C6'), 0.3, 0.3, 0.25))


def music():
    """A slow, dark 16-second loop in A minor, 75 bpm: a droning bass, a sparse bell melody and a soft pulse."""
    beat = 60 / 75
    bars = [('A1', ['A4', None, 'C5', None]), ('F1', ['A4', None, 'E4', None]),
            ('D2', ['F4', None, 'A4', 'G4']), ('E1', ['G#4', None, None, None])] * 1
    bar = 4 * beat
    total = int(len(bars) * bar * RATE)
    out = [0.0] * total
    rnd = random.Random(3)
    for b, (bass, melody) in enumerate(bars):
        start = int(b * bar * RATE)
        f = note(bass)
        phase = 0.0
        n = int(bar * RATE)
        for i in range(n):
            j = start + i
            if j >= total:
                break
            phase += f / RATE
            env = min(1.0, i / (0.2 * RATE)) * (1 - i / n) ** 0.2
            out[j] += triangle(phase) * env * 0.33
        for k, m in enumerate(melody):
            if m is None:
                continue
            f = note(m)
            phase = 0.0
            m_start = start + int(k * beat * RATE)
            m_len = int(beat * 1.6 * RATE)
            for i in range(m_len):
                j = m_start + i
                if j >= total:
                    break
                phase += f / RATE
                env = min(1.0, i / 60) * math.exp(-3.2 * i / m_len)
                out[j] += (square(phase, 0.125) * 0.5 + triangle(phase * 2) * 0.5) * env * 0.1
        for k in range(4):   # a quiet noise pulse on each beat, like a distant drum
            p_start = start + int(k * beat * RATE)
            for i in range(int(0.08 * RATE)):
                j = p_start + i
                if j >= total:
                    break
                out[j] += rnd.uniform(-1, 1) * (1 - i / (0.08 * RATE)) ** 3 * 0.06
    write('music/dungeon.wav', out)


if __name__ == '__main__':
    sfx()
    music()
