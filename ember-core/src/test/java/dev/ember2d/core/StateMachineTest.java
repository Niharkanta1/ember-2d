package dev.ember2d.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StateMachineTest {

    enum Guard { IDLE, PATROL, CHASE }

    private final List<String> log = new ArrayList<>();
    private final StateMachine<Guard> machine = new StateMachine<>(Guard.IDLE);

    private void record() {
        for (Guard state : Guard.values()) {
            machine.onEnter(state, () -> log.add("enter " + state));
            machine.onExit(state, () -> log.add("exit " + state));
        }
    }

    @Test
    void changeRunsExitThenEnterAndResetsTimeInState() {
        record();
        machine.update(1.5f);

        machine.change(Guard.PATROL);

        assertThat(log).containsExactly("exit IDLE", "enter PATROL");
        assertThat(machine.current()).isEqualTo(Guard.PATROL);
        assertThat(machine.timeInState()).isZero();
        machine.update(0.25f);
        machine.update(0.25f);
        assertThat(machine.timeInState()).isCloseTo(0.5f, within(1e-6f));
    }

    @Test
    void changingToTheCurrentStateDoesNothing() {
        record();
        machine.update(1f);

        machine.change(Guard.IDLE);

        assertThat(log).isEmpty();
        assertThat(machine.timeInState()).isEqualTo(1f);
    }

    @Test
    void initialStateEnterActionIsNotRun() {
        record();

        assertThat(log).isEmpty();
        assertThat(machine.current()).isEqualTo(Guard.IDLE);
    }

    @Test
    void changeFromInsideAnEnterActionIsAppliedAfterwards() {
        record();
        machine.onEnter(Guard.PATROL, () -> machine.change(Guard.CHASE));

        machine.change(Guard.PATROL);

        assertThat(log).containsExactly("exit IDLE", "enter PATROL", "exit PATROL", "enter CHASE");
        assertThat(machine.current()).isEqualTo(Guard.CHASE);
    }

    @Test
    void multipleActionsPerStateRunInRegistrationOrder() {
        machine.onEnter(Guard.CHASE, () -> log.add("first"));
        machine.onEnter(Guard.CHASE, () -> log.add("second"));

        machine.change(Guard.CHASE);

        assertThat(log).containsExactly("first", "second");
    }
}
