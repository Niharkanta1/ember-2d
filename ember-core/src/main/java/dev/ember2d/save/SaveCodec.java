package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** Writes a game object into {@link SaveData} and reads it back (F-013); saves use no reflection. */
@PublicApi
public interface SaveCodec<T> {

    void write(T value, SaveData out);

    T read(SaveData in);
}
