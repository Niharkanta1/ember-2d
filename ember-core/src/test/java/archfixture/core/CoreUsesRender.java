package archfixture.core;

import archfixture.render.RenderThing;

/** Violates rule 1: core depends on render. */
public class CoreUsesRender {
    public RenderThing thing = new RenderThing();
}
