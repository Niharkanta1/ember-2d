package archfixture.input;

import archfixture.ecs.EcsThing;

/** Other half of the package cycle ecs <-> input (rule 12); also violates rule 2 (input uses ecs). */
public class InputThing {
    public EcsThing ecs;
}
