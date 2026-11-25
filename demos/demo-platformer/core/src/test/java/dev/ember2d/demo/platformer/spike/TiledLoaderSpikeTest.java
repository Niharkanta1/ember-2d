package dev.ember2d.demo.platformer.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** SPIKE S-02: what libGDX's TMX and TMJ loaders resolve. Prints one "S02-TILED" line per feature and format. */
class TiledLoaderSpikeTest {

    @BeforeAll
    static void gdx() {
        SpikeGdx.install();
    }

    @Test
    void tmx() {
        report("tmx", SpikeGdx::tmx);
    }

    @Test
    void tmj() {
        report("tmj", SpikeGdx::tmj);
    }

    static void report(String fmt, Supplier<TiledMap> loader) {
        TiledMap map = loader.get();
        MapObjects entities = map.getLayers().get("entities").getObjects();
        MapObjects platforms = map.getLayers().get("platforms").getObjects();

        line(fmt, "map property gravity", describe(map.getProperties().get("gravity")));
        TiledMapTileLayer ground = (TiledMapTileLayer) map.getLayers().get("ground");
        line(fmt, "tile property solid (0,0)", describe(ground.getCell(0, 0).getTile().getProperties().get("solid")));
        line(fmt, "tile property oneWay (7,4)", describe(ground.getCell(7, 4).getTile().getProperties().get("oneWay")));

        for (String name : new String[] {"slime_1", "slime_2"}) {
            MapObject o = entities.get(name);
            line(fmt, name + " (template) object", o == null ? "MISSING" : o.getClass().getSimpleName());
            if (o != null) {
                line(fmt, name + " type", describe(o.getProperties().get("type")));
                line(fmt, name + " Health.max", describe(o.getProperties().get("Health.max")));
                line(fmt, name + " speed", describe(o.getProperties().get("speed")));
                line(fmt, name + " x,y,w,h", xywh(o));
                if (o instanceof TiledMapTileMapObject t) {
                    line(fmt, name + " tile id", String.valueOf(t.getTile().getId()));
                }
            }
        }
        line(fmt, "coin_a type= attribute", describe(entities.get("coin_a").getProperties().get("type")));
        line(fmt, "coin_b class= attribute", describe(entities.get("coin_b").getProperties().get("type"))
                + " / props " + keys(entities.get("coin_b").getProperties()));
        line(fmt, "coin_a x,y,w,h", xywh(entities.get("coin_a")));

        MapObject door = entities.get("door");
        Object stats = door.getProperties().get("stats");
        line(fmt, "door class property stats", stats instanceof MapProperties mp
                ? "MapProperties " + keys(mp) + " cost=" + describe(mp.get("cost")) + " locked=" + describe(mp.get("locked"))
                        + " label(default)=" + describe(mp.get("label")) + " type=" + describe(mp.get("type"))
                : describe(stats));
        Object ref = door.getProperties().get("switch");
        line(fmt, "door object reference switch", ref instanceof MapObject mo
                ? "MapObject '" + mo.getName() + "' same instance=" + (mo == entities.get("switch"))
                : describe(ref));

        MapObject sw = entities.get("switch");
        line(fmt, "point object", sw.getClass().getSimpleName() + " " + xywh(sw));
        MapObject patrol = entities.get("patrol");
        line(fmt, "polyline object", patrol instanceof PolylineMapObject pl
                ? "PolylineMapObject vertices=" + Arrays.toString(pl.getPolyline().getVertices()) + " pos="
                        + pl.getPolyline().getX() + "," + pl.getPolyline().getY()
                : patrol.getClass().getSimpleName());
        MapObject flipped = entities.get("flipped_slime");
        line(fmt, "flipped tile object", flipped instanceof TiledMapTileMapObject t
                ? "flipH=" + t.isFlipHorizontally() + " flipV=" + t.isFlipVertically() + " tile id=" + t.getTile().getId()
                : flipped.getClass().getSimpleName());
        MapObject platform = platforms.get("platform_1");
        line(fmt, "rectangle object", platform.getClass().getSimpleName() + " " + xywh(platform)
                + (platform instanceof RectangleMapObject r ? " rect=" + r.getRectangle() : "")
                + " distance=" + describe(platform.getProperties().get("distance")));

        assertThat(map.getProperties().get("gravity")).isNotNull();
        map.dispose();
    }

    static String xywh(MapObject o) {
        MapProperties p = o.getProperties();
        return p.get("x") + "," + p.get("y") + "," + p.get("width") + "," + p.get("height");
    }

    static String keys(MapProperties p) {
        StringBuilder b = new StringBuilder("[");
        for (Iterator<String> it = p.getKeys(); it.hasNext();) {
            b.append(it.next()).append(it.hasNext() ? "," : "");
        }
        return b.append(']').toString();
    }

    static String describe(Object value) {
        return value == null ? "null" : value + " (" + value.getClass().getSimpleName() + ")";
    }

    static void line(String fmt, String feature, String result) {
        System.out.println("S02-TILED " + fmt + " | " + feature + " | " + result);
    }
}
