package dev.ember2d.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of Ember2D's public API.
 *
 * <p>Public API types are documented, kept backward compatible once a phase gate has passed, and must not
 * expose hidden types (Box2D, Spine, platform backends, file types or {@code internal} packages) in their
 * public signatures. Types in packages named {@code internal} are never public API. These rules are checked
 * by the architecture tests (documents/ARCHITECTURE.md, rule 11).
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface PublicApi {
}
