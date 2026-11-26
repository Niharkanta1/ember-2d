"""Generates the platformer demo's sound effects and level music (I-210).

Original, synthesised sounds (square, triangle and noise voices), written as 22 050 Hz mono 16-bit WAV files into
demos/demo-platformer/core/assets/sfx and .../music. Standard library only:

    python assets-src/demo-platformer/sound/make_sounds.py

Replace any file with your own sound under the same name to change it; the manifest IDs stay the same.
"""
import math
import os
import random
import struct
import wave

RATE = 22050
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'demos', 'demo-platformer', 'core',
                    'assets')


def note(name):
    """Frequency of a note name like 'C5' or 'F#4' (A4 = 440 Hz)."""
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


def sweep(f0, f1, seconds, volume, duty=0.5, noise=0.0, seed=1):
    rnd = random.Random(seed)
    n = int(seconds * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        f = f0 * (f1 / f0) ** t
        phase += f / RATE
        env = (1 - t) ** 1.5
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
    write('sfx/jump.wav', sweep(320, 760, 0.13, 0.35, duty=0.25))
    write('sfx/stomp.wav', sweep(520, 110, 0.14, 0.45, duty=0.5, noise=0.35, seed=2))
    hurt = sweep(260, 90, 0.32, 0.45, duty=0.5, noise=0.25, seed=3)
    write('sfx/hurt.wav', [s * (0.6 + 0.4 * math.sin(i / RATE * 2 * math.pi * 30)) for i, s in enumerate(hurt)])
    write('sfx/checkpoint.wav', tone(note('E6'), 0.09, 0.3, 0.25) + tone(note('A6'), 0.22, 0.3, 0.25))
    fanfare = []
    for name, length in [('C5', 0.11), ('E5', 0.11), ('G5', 0.11), ('C6', 0.5)]:
        fanfare += tone(note(name), length, 0.35, 0.25, release=0.4)
    write('sfx/goal.wav', fanfare)


def music():
    """A 16-second loop: C - Am - F - G twice, 120 bpm, square lead over triangle bass."""
    beat = 0.5
    bars = [('C', ['C3', 'G3'], ['E5', 'G5', 'C6', 'G5', 'E5', 'G5', 'D5', 'E5']),
            ('Am', ['A2', 'E3'], ['C5', 'E5', 'A5', 'E5', 'C5', 'E5', 'B4', 'C5']),
            ('F', ['F2', 'C3'], ['A4', 'C5', 'F5', 'C5', 'A4', 'C5', 'G4', 'A4']),
            ('G', ['G2', 'D3'], ['B4', 'D5', 'G5', 'D5', 'B4', 'D5', 'A4', 'B4']),
            ('C', ['C3', 'G3'], ['G5', 'E5', 'C5', 'E5', 'G5', 'C6', 'B5', 'G5']),
            ('Am', ['A2', 'E3'], ['A5', 'E5', 'C5', 'E5', 'A5', 'C6', 'B5', 'A5']),
            ('F', ['F2', 'C3'], ['F5', 'A5', 'C6', 'A5', 'F5', 'E5', 'D5', 'C5']),
            ('G', ['G2', 'D3'], ['D5', 'B4', 'G4', 'B4', 'D5', 'F5', 'E5', 'D5'])]
    total = int(len(bars) * 4 * beat * RATE)
    out = [0.0] * total
    lead_phase = bass_phase = 0.0
    eighth = int(beat / 2 * RATE)
    for b, (_, bass, lead) in enumerate(bars):
        start = int(b * 4 * beat * RATE)
        for k, name in enumerate(lead):
            f = note(name)
            for i in range(eighth):
                j = start + k * eighth + i
                lead_phase += f / RATE
                env = min(1.0, i / 60) * (1 - i / eighth) ** 0.35
                out[j] += square(lead_phase, 0.25) * env * 0.16
        for k in range(8):
            f = note(bass[k % 2])
            for i in range(eighth):
                j = start + k * eighth + i
                bass_phase += f / RATE
                env = min(1.0, i / 40) * (1 - i / eighth) ** 0.2
                out[j] += triangle(bass_phase) * env * 0.32
    write('music/level.wav', out)


if __name__ == '__main__':
    sfx()
    music()
