"""Generates the shooter demo's sound effects and boss music (I-340).

Original, synthesised sounds (square, triangle and noise voices), written as 22 050 Hz mono 16-bit WAV files into
demos/demo-shooter/core/assets/sfx and .../music. Standard library only:

    python assets-src/demo-shooter/sound/make_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 22050
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'demos', 'demo-shooter', 'core',
                    'assets')


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


def sfx():
    write('sfx/shoot.wav', sweep(900, 300, 0.06, 0.25, duty=0.25, noise=0.2, seed=4, power=2))
    write('sfx/hit.wav', sweep(400, 150, 0.07, 0.3, duty=0.5, noise=0.5, seed=5))
    write('sfx/kill.wav', sweep(300, 60, 0.25, 0.45, duty=0.5, noise=0.55, seed=6))
    hurt = sweep(240, 80, 0.3, 0.45, duty=0.5, noise=0.25, seed=7)
    write('sfx/hurt.wav', [s * (0.6 + 0.4 * math.sin(i / RATE * 2 * math.pi * 30)) for i, s in enumerate(hurt)])
    write('sfx/pickup.wav', tone(note('E6'), 0.06, 0.3, 0.25) + tone(note('B6'), 0.12, 0.3, 0.25))
    write('sfx/reload.wav', tone(note('C4'), 0.05, 0.3, 0.5) + [0.0] * 1500 + tone(note('G4'), 0.07, 0.3, 0.5))
    write('sfx/boss_phase.wav', sweep(80, 600, 0.6, 0.5, duty=0.5, noise=0.3, seed=8, power=0.8))
    write('sfx/graze.wav', tone(note('A6'), 0.03, 0.12, 0.125, release=1.5))


def music():
    """A tense 12-second loop in D minor, 140 bpm: driving bass and a square arpeggio."""
    beat = 60 / 140
    chords = [(['D3', 'A3'], ['D5', 'F5', 'A5', 'F5']), (['A#2', 'F3'], ['D5', 'F5', 'A#5', 'F5']),
              (['C3', 'G3'], ['C5', 'E5', 'G5', 'E5']), (['A2', 'E3'], ['C#5', 'E5', 'A5', 'E5'])] * 2
    total = int(len(chords) * 4 * beat * RATE)
    out = [0.0] * total
    lead_phase = bass_phase = 0.0
    sixteenth = int(beat / 4 * RATE)
    for b, (bass, arp) in enumerate(chords):
        start = int(b * 4 * beat * RATE)
        for k in range(16):
            f = note(arp[k % 4])
            for i in range(sixteenth):
                j = start + k * sixteenth + i
                if j >= total:
                    break
                lead_phase += f / RATE
                env = min(1.0, i / 40) * (1 - i / sixteenth) ** 0.5
                out[j] += square(lead_phase, 0.25) * env * 0.12
        for k in range(8):
            f = note(bass[k % 2])
            for i in range(2 * sixteenth):
                j = start + k * 2 * sixteenth + i
                if j >= total:
                    break
                bass_phase += f / RATE
                env = min(1.0, i / 40) * (1 - i / (2 * sixteenth)) ** 0.3
                out[j] += triangle(bass_phase) * env * 0.35
    write('music/boss.wav', out)


if __name__ == '__main__':
    sfx()
    music()
