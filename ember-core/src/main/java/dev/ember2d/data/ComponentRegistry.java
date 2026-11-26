package dev.ember2d.data;

import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import dev.ember2d.animation.Animator;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Solid;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.Transform;
import dev.ember2d.render.Sprite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Every component type usable from data, with its JSON name (F-010): the single source for prefabs, Tiled overrides,
 * the dev-tools inspector, the Tiled type generator and generated R8 keep rules. The engine components
 * ({@code Transform}, {@code Sprite}, {@code Collider}, {@code Actor}, {@code Solid}) are registered from the start. Components are created with their factory (no reflective construction); their public
 * fields of supported types are found once per class.
 */
@PublicApi
public final class ComponentRegistry {

    /** One registered component type. */
    static final class Type {
        final String name;
        final Class<?> type;
        final Supplier<?> factory;
        final Map<String, FieldSetter> fields;
        final Map<String, String> unsupported;   // field name -> type name

        Type(String name, Class<?> type, Supplier<?> factory) {
            this.name = name;
            this.type = type;
            this.factory = factory;
            Map<String, FieldSetter> found = new LinkedHashMap<>();
            Map<String, String> skipped = new LinkedHashMap<>();
            for (Field field : ClassReflection.getFields(type)) {
                if (field.isStatic()) {
                    continue;
                }
                FieldSetter setter = FieldSetter.of(field);
                if (setter != null) {
                    found.put(field.getName(), setter);
                } else {
                    String typeName = field.getType().getSimpleName();
                    skipped.put(field.getName(), field.isFinal() && FieldSetter.supported(field.getType())
                            ? typeName + " (final)" : typeName);
                }
            }
            this.fields = Collections.unmodifiableMap(found);
            this.unsupported = Collections.unmodifiableMap(skipped);
        }

        Object create() {
            Object instance = factory.get();
            if (instance == null || instance.getClass() != type) {
                throw new IllegalStateException("The factory of component " + name + " returned "
                        + (instance == null ? "null" : instance.getClass().getName()) + ", not " + type.getName());
            }
            return instance;
        }
    }

    private final Map<String, Type> byName = new LinkedHashMap<>();
    private final Map<Class<?>, Type> byClass = new LinkedHashMap<>();

    /** A registry with the engine components. */
    public ComponentRegistry() {
        register(Transform.class, Transform::new);
        register(Sprite.class, Sprite::new);
        register(Collider.class, Collider::new);
        register(Actor.class, Actor::new);
        register(Solid.class, Solid::new);
        register(Animator.class, Animator::new);
    }

    /** Registers a component under its simple class name. Returns this. */
    public <T> ComponentRegistry register(Class<T> type, Supplier<T> factory) {
        return register(Objects.requireNonNull(type, "type").getSimpleName(), type, factory);
    }

    /**
     * Registers a component under a JSON name. Returns this.
     *
     * @throws IllegalArgumentException if the name or the class is already registered
     */
    public <T> ComponentRegistry register(String name, Class<T> type, Supplier<T> factory) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Component name must not be blank");
        }
        if (byName.containsKey(name)) {
            throw new IllegalArgumentException("A component named \"" + name + "\" is already registered ("
                    + byName.get(name).type.getName() + ")");
        }
        if (byClass.containsKey(type)) {
            throw new IllegalArgumentException(type.getName() + " is already registered as \""
                    + byClass.get(type).name + "\"");
        }
        Type t = new Type(name, type, factory);
        byName.put(name, t);
        byClass.put(type, t);
        return this;
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    /** Registered names in registration order. */
    public Set<String> names() {
        return Collections.unmodifiableSet(byName.keySet());
    }

    /** Registered classes in registration order. */
    public List<Class<?>> types() {
        return List.copyOf(byClass.keySet());
    }

    /**
     * The fields data can set, with their values on a new instance.
     *
     * @throws IllegalArgumentException if the component is not registered
     */
    public List<FieldInfo> fields(String component) {
        Type t = type(component);
        Object instance = t.create();
        List<FieldInfo> out = new ArrayList<>();
        for (FieldSetter setter : t.fields.values()) {
            out.add(new FieldInfo(setter.name, setter.type, setter.get(instance)));
        }
        return out;
    }

    Type type(String name) {
        Type t = byName.get(name);
        if (t == null) {
            throw new IllegalArgumentException("Unknown component \"" + name + "\"; registered: " + byName.keySet());
        }
        return t;
    }

    Type find(String name) {
        return byName.get(name);
    }
}
