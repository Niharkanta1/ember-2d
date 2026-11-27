"""Generates the novel demo's sound effects and music (I-470).

Original, synthesised sounds written as 22 050 Hz mono 16-bit WAV files into demos/demo-novel/core/assets/sfx and
.../music. Reuses the voices of the shooter demo's generator. Standard library only:

    python assets-src/demo-novel/sound/make_sounds.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'demo-shooter', 'sound'))
import make_sounds as synth  # noqa: E402  (the shooter demo's voices)

synth.ROOT = os.path.join(HERE, '..', '..', '..', 'demos', 'demo-novel', 'core', 'assets')
note, tone, sweep, square, triangle, write, RATE = (synth.note, synth.tone, synth.sweep, synth.square,
                                                     synth.triangle, synth.write, synth.RATE)


def sfx():
    write('sfx/pop.wav', tone(note('C6'), 0.04, 0.25, 0.5) + tone(note('G6'), 0.08, 0.25, 0.5))
    write('sfx/rustle.wav', sweep(900, 400, 0.45, 0.3, duty=0.5, noise=0.85, seed=11, power=0.8))
    write('sfx/blip.wav', tone(note('E5'), 0.025, 0.1, 0.25, release=1.5))


def loop(bars, bpm, lead_volume, bass_volume, duty, path):
    beat = 60 / bpm
    eighth = int(beat / 2 * RATE)
    total = len(bars) * 8 * eighth
    out = [0.0] * total
    lead = bass = 0.0
    for b, (root, arp) in enumerate(bars):
        start = b * 8 * eighth
        for k in range(8):
            f = note(arp[k % len(arp)])
            for i in range(eighth):
                j = start + k * eighth + i
                lead += f / RATE
                env = min(1.0, i / 60) * (1 - i / eighth) ** 0.8
                out[j] += square(lead, duty) * env * lead_volume
        f = note(root)
        for i in range(8 * eighth):
            j = start + i
            bass += f / RATE
            env = min(1.0, i / 200) * (1 - (i % (4 * eighth)) / (4 * eighth)) ** 0.5
            out[j] += triangle(bass) * env * bass_volume
    write(path, out)


def music():
    # A light meadow tune in C major, 100 bpm.
    loop([('C3', ['E5', 'G5', 'C6', 'G5']), ('F2', ['F5', 'A5', 'C6', 'A5']),
          ('G2', ['D5', 'G5', 'B5', 'G5']), ('C3', ['E5', 'G5', 'C6', 'E6'])] * 2, 100, 0.06, 0.28, 0.25,
         'music/meadow.wav')
    # A warm, slower sunset tune in F major, 76 bpm.
    loop([('F2', ['A4', 'C5', 'F5', 'C5']), ('D3', ['A4', 'D5', 'F5', 'D5']),
          ('A#2', ['A#4', 'D5', 'F5', 'D5']), ('C3', ['G4', 'C5', 'E5', 'C5'])] * 2, 76, 0.06, 0.3, 0.125,
         'music/sunset.wav')


if __name__ == '__main__':
    sfx()
    music()
