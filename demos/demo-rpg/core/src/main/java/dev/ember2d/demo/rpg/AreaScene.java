package dev.ember2d.demo.rpg;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.animation.AnimationLibrary;
import dev.ember2d.animation.AnimationSystem;
import dev.ember2d.animation.Animator;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.audio.Audio;
import dev.ember2d.camera.CameraFollow;
import dev.ember2d.camera.CameraFollowSystem;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionDebugSystem;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.Solid;
import dev.ember2d.core.Log;
import dev.ember2d.data.MapSpawner;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.kit.common.TriggerZoneSystem;
import dev.ember2d.kit.common.ZoneEntered;
import dev.ember2d.kit.novel.NovelCharacters;
import dev.ember2d.kit.novel.NovelDirector;
import dev.ember2d.kit.novel.NovelSettings;
import dev.ember2d.kit.novel.NovelView;
import dev.ember2d.kit.topdown.TopDownBody;
import dev.ember2d.kit.topdown.TopDownSystem;
import dev.ember2d.kit.topdown.rpg.Interactable;
import dev.ember2d.kit.topdown.rpg.Interacted;
import dev.ember2d.kit.topdown.rpg.InteractionFocus;
import dev.ember2d.kit.topdown.rpg.InteractionSystem;
import dev.ember2d.kit.topdown.rpg.ItemCatalog;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.save.FlagChanged;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;
import dev.ember2d.ui.ConfirmDialog;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * An area of the RPG demo (the town or the cellar), built from its Tiled map.
 * <ul>
 *   <li>The knight walks with kit-topdown and talks, opens and searches through kit-topdown's interaction
 *       ({@code Interactable.action} names a knot of the town story, played by {@link Conversation}).</li>
 *   <li>Doors and chests follow their world flags: the story sets a flag, the scene opens the door; on entering an
 *       area they are shown as the flags say, so they stay open after travelling and after loading.</li>
 *   <li>Travel zones lead to the other area; the game is saved on arrival and from the pause menu.</li>
 *   <li>The first visit to the cellar plays a cutscene staged with kit-novel over the game world.</li>
 * </ul>
 */
final class AreaScene extends Scene {

    static final String CUTSCENE_FLAG = "cutscene.cellar";
    private static final Log LOG = Log.get("rpg");

    private final Game game;
    private World world;
    private Input input;
    private Audio audio;
    private int player;
    private Family doors;
    private Family chests;
    private Conversation talk;
    private NovelView cutscene;
    private Label prompt;
    private Table items;
    private int shownItems = -1;
    private Label notice;
    private float noticeLeft;
    private Table pausePanel;
    private boolean paused;
    private boolean travelling;
    private boolean cutsceneDue;
    private boolean touch;
    private int focused = World.NONE;

    AreaScene(Game game) {
        this.game = game;
    }

    @Override
    protected String assetGroup() {
        return game.area;
    }

    @Override
    protected void enter(EngineContext context) {
        world = world();
        input = context.input();
        audio = context.audio();
        touch = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        context.renderer().setLayers(new RenderLayer("floor", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT));
        float ppu = context.renderer().pixelsPerUnit();
        TileMap map = TileMap.load(context.assets(), "map." + game.area, ppu);
        Rectangle bounds = map.bounds(new Rectangle());
        Collision collision = new Collision(world, context.events());
        collision.addTileGrid(map.collision(), 0);

        new MapSpawner(context.prefabs()).spawn(world, map);
        player = world.findByName("player");
        world.tag(player, "player");
        world.add(player, game.inventory);
        placePlayer();
        world.get(world.findByName("camera"), CameraFollow.class).bounds = bounds;
        context.renderer().camera().position.set(world.get(player, Transform.class).position);

        AnimationLibrary clips = new AnimationLibrary(context.assets());
        world.addSystem(new TopDownSystem(world, collision, input, context.events()));
        world.addSystem(new InteractionSystem(world, input, context.events()));
        world.addSystem(new TriggerZoneSystem(world, context.events()));
        world.addSystem(new CollisionSystem(world, collision));
        world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));
        world.addSystem(new AnimationSystem(world, clips, context.events()));
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        world.addSystem(new CollisionDebugSystem(world, collision, context.debug(), context.renderer()));

        doors = world.family(Door.class);
        chests = world.family(Chest.class);
        showFlags(false);
        RpgUi.useSkin(ui(), context.assets());   // before any widget is made
        talk = new Conversation(ui(), input, game.story, audio);
        wireEvents(context);
        buildHud(context);
        if ("town".equals(game.area)) {
            audio.playMusic("music.town", 1f);
        } else {
            audio.stopMusic(1f);
        }
        cutsceneDue = "cellar".equals(game.area) && !game.flags.getBool(CUTSCENE_FLAG, false);
        context.saves().setSuspendSave(this::save);   // Android may stop the app in the background
        save();   // arriving in an area saves the game
        LOG.info("Area " + game.area + " ready: " + world.entityCount() + " entities");
    }

    private void placePlayer() {
        Transform t = world.get(player, Transform.class);
        if (game.hasPosition) {
            t.position.set(game.x, game.y);
            game.hasPosition = false;
            return;
        }
        int marker = world.findByName(game.spawn);
        if (marker == World.NONE) {
            LOG.warn("No marker \"" + game.spawn + "\" in " + game.area + "; starting at the map's player");
            return;
        }
        t.position.set(world.get(marker, Transform.class).position);
    }

    /** Shows the doors and chests as their flags say; {@code animate} plays the opening. */
    private void showFlags(boolean animate) {
        IntArray all = doors.entities();
        for (int i = 0; i < all.size; i++) {
            int door = all.get(i);
            if (game.flags.getBool(world.get(door, Door.class).flag, false) && world.get(door, Solid.class) != null) {
                world.remove(door, Solid.class);
                world.get(door, Interactable.class).enabled = false;
                world.get(door, Animator.class).play(animate ? "door_open" : "door_opened");
                if (animate) {
                    audio.play("sfx.door");
                }
            }
        }
        all = chests.entities();
        for (int i = 0; i < all.size; i++) {
            int chest = all.get(i);
            if (game.flags.getBool(world.get(chest, Chest.class).flag, false)) {
                world.get(chest, Sprite.class).tint.set(0.55f, 0.5f, 0.5f, 1f);   // opened: dull
                world.get(chest, Interactable.class).prompt = "Search";
            }
        }
    }

    private void wireEvents(EngineContext context) {
        context.events().on(Interacted.class, e -> {
            if (e.interactor() != player || talk.active() || paused) {
                return;
            }
            String knot = e.action();
            Chest chest = world.get(e.target(), Chest.class);
            if (chest != null && game.flags.getBool(chest.flag, false)) {
                knot = "chest_empty";
            } else if (chest != null) {
                audio.play("sfx.chest");
            }
            converse(knot);
        });
        context.events().on(FlagChanged.class, e -> showFlags(true));
        context.events().on(InteractionFocus.class, e -> {
            if (e.interactor() == player) {
                prompt.setText(e.prompt().isEmpty() ? "" : (touch ? "A: " : "E: ") + e.prompt());
                outline(focused, false);
                focused = e.target();
                outline(focused, true);
            }
        });
        context.events().on(ZoneEntered.class, e -> {
            Travel travel = world.get(e.zone(), Travel.class);
            if (travel != null && e.entity() == player && !travelling) {
                travelling = true;
                world.get(player, TopDownBody.class).locked = true;
                game.area = travel.area;
                game.spawn = travel.spawn;
                game.hasPosition = false;
                context.scenes().replace(new AreaScene(game), Transition.fade(0.6f));
            }
        });
    }

    /** Outlines the focused interactable (F-026). */
    private void outline(int entity, boolean on) {
        Sprite sprite = entity == World.NONE || !world.isAlive(entity) ? null : world.get(entity, Sprite.class);
        if (sprite != null) {
            sprite.outline.set(1f, 0.85f, 0.3f, on ? 1f : 0f);
        }
    }

    private void converse(String knot) {
        TopDownBody body = world.get(player, TopDownBody.class);
        body.locked = true;
        int before = game.itemsVersion;
        talk.start(knot, () -> {
            body.locked = false;
            if (game.itemsVersion != before) {
                audio.play("sfx.item");
            }
        });
    }

    private void startCutscene(EngineContext context) {
        cutsceneDue = false;
        NovelCharacters cast = NovelCharacters.parse(context.assets().get("cast", JsonValue.class),
                context.assets().path("cast"));
        cutscene = new NovelView(ui(), context.assets(), audio, input, context.renderer().camera(),
                new NovelSettings().load(context.saves().settings()));
        cutscene.setCharacterArea(0.33f, 0.5f);   // portraits on the box, over the world
        NovelDirector director = new NovelDirector(DialogueRunner.fromAsset(context.assets(), "ink.cellar"), cast,
                cutscene, null, context.config().devMode);
        world.get(player, TopDownBody.class).locked = true;
        input.pushContext("ui");
        prompt.setVisible(false);
        cutscene.play(director, () -> {
            game.flags.set(CUTSCENE_FLAG, true);
            input.popContext();
            prompt.setVisible(true);
            world.get(player, TopDownBody.class).locked = false;
            save();
        });
    }

    private void buildHud(EngineContext context) {
        Ui ui = ui();
        Table root = ui.root();
        items = new Table(ui.skin());
        notice = new Label("", ui.skin(), "title");
        prompt = new Label("", ui.skin());
        root.add(items).expandX().top().left().pad(4f).row();
        root.add(notice).expand().top().padTop(6f).row();
        root.add(prompt).padBottom(touch ? 6f : 8f);
        showNotice("cellar".equals(game.area) ? "The cellar" : "Millbrook");
    }

    private void showNotice(String text) {
        notice.setText(text);
        notice.setVisible(true);
        noticeLeft = 2.5f;
    }

    private void refreshItems() {
        shownItems = game.itemsVersion;
        items.clearChildren();
        ItemCatalog catalog = game.items;
        for (int slot = 0; slot < game.inventory.size(); slot++) {
            ItemCatalog.Item item = catalog.get(game.inventory.item(slot));
            items.add(new Image(context().assets().region(item.icon()))).size(16f);
            int count = game.inventory.count(slot);
            items.add(new Label(count > 1 ? "x" + count : "", ui().skin())).padRight(6f);
        }
    }

    @Override
    protected void update(float delta) {
        if (cutsceneDue && !context().scenes().inTransition()) {
            startCutscene(context());
        }
        if (cutscene != null) {
            cutscene.update(delta);
        }
        boolean busy = talk.active() || (cutscene != null && cutscene.isPlaying()) || travelling;
        if (!busy && !paused && "gameplay".equals(input.topContext())
                && (input.justPressed("pause") || input.justPressed("menu"))) {
            openPause();
        }
        if (!paused) {
            game.playSeconds += delta;
        }
        TopDownBody body = world.get(player, TopDownBody.class);
        world.get(player, Animator.class).playIfNotPlaying(body.moving && !body.locked ? "knight_run" : "knight_idle");
        if (shownItems != game.itemsVersion) {
            refreshItems();
        }
        if (noticeLeft > 0f) {
            noticeLeft -= delta;
            notice.setVisible(noticeLeft > 0f);
        }
    }

    /** Writes slot 0 with the knight's position. */
    private void save() {
        Transform t = world.get(player, Transform.class);
        game.x = t.position.x;
        game.y = t.position.y;
        game.save(context().saves());
    }

    // ---- pause ----

    private void openPause() {
        paused = true;
        context().time().setPaused(true);
        input.pushContext("ui");
        Ui ui = ui();
        pausePanel = RpgUi.overlay(ui);
        MenuList menu = new MenuList(ui);
        menu.add("Resume", this::closePause);
        menu.add("Save", () -> {
            save();
            closePause();
            showNotice("Saved");
        });
        menu.add("Main menu", () -> ConfirmDialog.show(ui, "Quit to the main menu?", "Yes", "No",
                () -> context().scenes().replace(new MenuScene(), Transition.fade(0.5f))));
        menu.onBack(this::closePause);
        RpgUi.show(pausePanel, "Paused", menu);
    }

    private void closePause() {
        if (!paused) {
            return;
        }
        paused = false;
        pausePanel.getParent().remove();
        input.popContext();
        context().time().setPaused(false);
    }

    @Override
    protected void exit() {
        context().saves().setSuspendSave(null);
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
        if (context().time().paused()) {
            context().time().setPaused(false);
        }
    }
}
