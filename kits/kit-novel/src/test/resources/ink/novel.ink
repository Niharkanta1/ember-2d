// kit-novel test story (I-460): every staging tag.
# bg: park [transition=fade 1.0]
# music: theme_calm fade=2
# show: alice happy left
Alice: What a lovely evening. #voice:alice_001
# show: bob right transition=slide
# wait: 0.5
# wait: 0.25
Bob: It is. # expr: alice sad
* [Agree]
    # sfx: door_knock
    # shake: 0.6
    # flash: red 0.3
    Narrator: Someone knocks.
    -> ending
* [Leave]
    # hide: bob transition=cut
    # stopmusic
    Alice: Goodbye then.
    -> ending

=== ending ===
# cg: sunset
# hidecg
The end. #mood:quiet #chapter_end
-> END
