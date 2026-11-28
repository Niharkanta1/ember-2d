package dev.ember2d.demo.gold;

import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;

/**
 * Every component your prefabs and maps create. Register your own components here (and the kits', for example
 * {@code CommonKit.register(registry)}); the Android release build keeps exactly these through R8.
 */
public final class GoldComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        // registry.register(MyComponent.class, MyComponent::new);
    }
}
