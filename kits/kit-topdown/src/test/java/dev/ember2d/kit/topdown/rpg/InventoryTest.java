package dev.ember2d.kit.topdown.rpg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.kit.common.ZoneEntered;
import dev.ember2d.save.SaveData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Items and inventory (F-017, I-410). */
class InventoryTest {

    static final ItemCatalog CATALOG = ItemCatalog.parse(new JsonReader().parse("""
            { "version": 1, "items": {
                "potion": { "name": "Potion", "icon": "atlas.rpg/potion", "stack": 5, "tags": ["consumable"] },
                "key": { "name": "Cellar key", "tags": ["key"] },
                "coin": { "name": "Coin", "stack": 99 } } }
            """), "items.json");

    final Inventory inv = new Inventory();

    @Test
    void stacksFillFirstThenNewSlotsUpToCapacity() {
        inv.capacity = 3;
        assertThat(inv.add(CATALOG, "potion", 3)).isEqualTo(3);
        assertThat(inv.add(CATALOG, "potion", 4)).isEqualTo(4);   // 5 + 2
        assertThat(inv.size()).isEqualTo(2);
        assertThat(inv.count(0)).isEqualTo(5);
        assertThat(inv.add(CATALOG, "key", 2)).as("one slot left, key stacks to 1").isEqualTo(1);
        assertThat(inv.add(CATALOG, "potion", 10)).as("only the second potion stack has room").isEqualTo(3);
        assertThat(inv.count("potion")).isEqualTo(10);
        assertThat(inv.has("key", 1)).isTrue();
        assertThat(inv.hasTag(CATALOG, "key")).isTrue();
        assertThat(inv.hasTag(CATALOG, "weapon")).isFalse();
        assertThatThrownBy(() -> inv.add(CATALOG, "sword", 1)).hasMessageContaining("known: [potion, key, coin]");
    }

    @Test
    void removingIsAllOrNothingAndClosesUpSlots() {
        inv.add(CATALOG, "potion", 7);   // 5 + 2
        inv.add(CATALOG, "key", 1);
        assertThat(inv.remove("potion", 8)).isFalse();
        assertThat(inv.count("potion")).isEqualTo(7);
        assertThat(inv.remove("potion", 3)).isTrue();   // takes the 2, then 1 from the first stack
        assertThat(inv.size()).isEqualTo(2);
        assertThat(inv.item(0)).isEqualTo("potion");
        assertThat(inv.count(0)).isEqualTo(4);
        assertThat(inv.item(1)).isEqualTo("key");
        assertThat(inv.remove("key", 1)).isTrue();
        assertThat(inv.size()).isEqualTo(1);
    }

    @Test
    void saveAndLoadRoundTripSkippingUnknownItems() {
        inv.add(CATALOG, "coin", 40);
        inv.add(CATALOG, "potion", 6);
        SaveData data = new SaveData();
        inv.save(data);
        Inventory back = new Inventory();
        back.load(SaveData.parse(data.toJson()), CATALOG);
        assertThat(back.count("coin")).isEqualTo(40);
        assertThat(back.count("potion")).isEqualTo(6);
        assertThat(back.size()).isEqualTo(3);

        data.list("slots").add(new SaveData().set("item", "sword").set("count", 1));
        data.list("slots").add(new SaveData().set("item", "key").set("count", 9));
        back.load(data, CATALOG);
        assertThat(back.count("key")).as("clamped to the stack size").isEqualTo(1);
        assertThat(back.size()).isEqualTo(4);
    }

    @Test
    void badCatalogsAreReported() {
        assertThatThrownBy(() -> ItemCatalog.parse(new JsonReader().parse(
                "{ \"version\": 1, \"items\": { \"a\": { \"stack\": 0, \"weight\": 2, \"tags\": \"x\" } } }"), "i.json"))
                .hasMessageContaining("i.json: item \"a\": unknown field \"weight\"")
                .hasMessageContaining("\"name\" is required").hasMessageContaining("\"stack\" must be")
                .hasMessageContaining("\"tags\" must be a list");
    }

    @Test
    void pickupsGiveWhatFitsAndDespawnWhenTaken() {
        World world = new World(false);
        DefaultEventBus events = new DefaultEventBus(false);
        world.addSystem(new ItemPickupSystem(world, CATALOG, events));
        List<ItemPickedUp> seen = new ArrayList<>();
        events.onGlobal(ItemPickedUp.class, seen::add);
        int player = world.create();
        Inventory bag = world.add(player, new Inventory());
        bag.capacity = 1;
        int bystander = world.create();

        int potions = world.create();
        world.add(potions, new Transform());
        ItemPickup p = world.add(potions, new ItemPickup());
        p.item = "potion";
        p.count = 8;
        events.publish(new ZoneEntered(potions, bystander, "potions"));
        assertThat(seen).as("no inventory: ignored").isEmpty();
        events.publish(new ZoneEntered(potions, player, "potions"));
        assertThat(seen).containsExactly(new ItemPickedUp(player, potions, "potion", 5));
        assertThat(p.count).isEqualTo(3);
        assertThat(world.isAlive(potions)).isTrue();
        events.publish(new ZoneEntered(potions, player, "potions"));
        assertThat(seen).as("full: nothing taken, nothing published").hasSize(1);

        bag.capacity = 2;
        events.publish(new ZoneEntered(potions, player, "potions"));
        assertThat(seen).hasSize(2);
        assertThat(world.isAlive(potions)).as("taken whole").isFalse();
    }
}
