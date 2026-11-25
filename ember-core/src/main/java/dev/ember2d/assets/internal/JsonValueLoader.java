package dev.ember2d.assets.internal;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Loads {@code json} manifest entries as libGDX {@link JsonValue} trees. */
final class JsonValueLoader extends SynchronousAssetLoader<JsonValue, JsonValueLoader.Parameters> {

    static final class Parameters extends AssetLoaderParameters<JsonValue> {
    }

    JsonValueLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    @Override
    public JsonValue load(AssetManager manager, String fileName, FileHandle file, Parameters parameters) {
        return new JsonReader().parse(file);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, Parameters parameters) {
        return null;
    }
}
