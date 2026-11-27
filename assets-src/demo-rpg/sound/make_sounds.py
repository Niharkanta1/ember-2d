"""Generates the RPG demo's sound effects and town music (I-450).

Original, synthesised sounds written as 22 050 Hz mono 16-bit WAV files into demos/demo-rpg/core/assets/sfx and
.../music. Reuses the voices of the shooter demo's generator. Standard library only:

    python assets-src/demo-rpg/sound/make_sounds.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'demo-shooter', 'sound'))
import make_sounds as synth  # noqa: E402  (the shooter demo's voices)

synth.ROOT = os.path.join(HERE, '..', '..', '..', 'demos', 'demo-rpg', 'core', 'assets')
note, tone, sweep, square, triangle, write, RATE = (synth.note, synth.tone, synth.sweep, synth.square,
                                                     synth.triangle, synth.write, synth.RATE)


def sfx():
    write('sfx/blip.wav', tone(note('A5'), 0.025, 0.12, 0.25, release=1.5))
    write('sfx/door.wav', sweep(120, 60, 0.5, 0.4, duty=0.5, noise=0.45, seed=3, power=0.7))
    write('sfx/chest.wav', sweep(200, 90, 0.18, 0.35, duty=0.5, noise=0.3, seed=4)
          + tone(note('C6'), 0.07, 0.25, 0.25) + tone(note('E6'), 0.07, 0.25, 0.25) + tone(note('G6'), 0.16, 0.25, 0.25))
    write('sfx/item.wav', tone(note('G5'), 0.07, 0.3, 0.25) + tone(note('D6'), 0.14, 0.3, 0.25))
    write('sfx/rumble.wav', sweep(70, 40, 1.2, 0.6, duty=0.5, noise=0.7, seed=9, power=0.6))
    write('sfx/step.wav', sweep(180, 120, 0.03, 0.12, duty=0.5, noise=0.6, seed=2, power=2))


def music():
    """A calm 16-second loop in G major, 90 bpm: a triangle bass and a soft square melody."""
    beat = 60 / 90
    bars = [('G2', ['B4', 'D5', 'G5', 'D5']), ('C3', ['C5', 'E5', 'G5', 'E5']),
            ('D3', ['A4', 'D5', 'F#5', 'D5']), ('G2', ['B4', 'D5', 'G5', 'B5'])] * 2
    eighth = int(beat / 2 * RATE)
    total = len(bars) * 8 * eighth
    out = [0.0] * total
    lead = bass = 0.0
    for b, (root, arp) in enumerate(bars):
        start = b * 8 * eighth
        for k in range(8):
            f = note(arp[k % 4])
            for i in range(eighth):
                j = start + k * eighth + i
                lead += f / RATE
                env = min(1.0, i / 60) * (1 - i / eighth) ** 0.8
                out[j] += square(lead, 0.125) * env * 0.07
        f = note(root)
        for i in range(8 * eighth):
            j = start + i
            bass += f / RATE
            env = min(1.0, i / 200) * (1 - (i % (4 * eighth)) / (4 * eighth)) ** 0.5
            out[j] += triangle(bass) * env * 0.3
    write('music/town.wav', out)


if __name__ == '__main__':
    sfx()
    music()
