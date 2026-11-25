package archfixture.render;

import archfixture.render.internal.InternalPublic;
import dev.ember2d.core.PublicApi;

/** Violates rule 11: a public API type exposes an internal type. */
@PublicApi
public class ExposesInternal {
    public InternalPublic leak;
}
