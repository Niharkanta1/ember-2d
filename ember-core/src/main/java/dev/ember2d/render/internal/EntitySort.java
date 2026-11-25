package dev.ember2d.render.internal;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

/**
 * Stable merge sort of entities by float keys, ascending, without per-frame allocation (the scratch arrays grow to
 * the largest size seen and are reused).
 */
public final class EntitySort {

    private int[] scratchEntities = new int[64];
    private float[] scratchKeys = new float[64];

    /** Sorts {@code entities} (and {@code keys} with them) by key, ascending; equal keys keep their order. */
    public void sort(IntArray entities, FloatArray keys) {
        int n = entities.size;
        if (n < 2) {
            return;
        }
        if (scratchEntities.length < n) {
            scratchEntities = new int[Math.max(n, scratchEntities.length * 2)];
            scratchKeys = new float[scratchEntities.length];
        }
        mergeSort(entities.items, keys.items, 0, n);
    }

    private void mergeSort(int[] entities, float[] keys, int from, int to) {
        if (to - from < 2) {
            return;
        }
        if (to - from <= 16) {
            insertionSort(entities, keys, from, to);
            return;
        }
        int middle = (from + to) >>> 1;
        mergeSort(entities, keys, from, middle);
        mergeSort(entities, keys, middle, to);
        if (keys[middle - 1] <= keys[middle]) {
            return;   // already ordered
        }
        System.arraycopy(entities, from, scratchEntities, from, to - from);
        System.arraycopy(keys, from, scratchKeys, from, to - from);
        int left = from;
        int right = middle;
        for (int out = from; out < to; out++) {
            if (right >= to || (left < middle && scratchKeys[left] <= scratchKeys[right])) {
                entities[out] = scratchEntities[left];
                keys[out] = scratchKeys[left++];
            } else {
                entities[out] = scratchEntities[right];
                keys[out] = scratchKeys[right++];
            }
        }
    }

    private static void insertionSort(int[] entities, float[] keys, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            float key = keys[i];
            int entity = entities[i];
            int j = i - 1;
            while (j >= from && keys[j] > key) {
                keys[j + 1] = keys[j];
                entities[j + 1] = entities[j];
                j--;
            }
            keys[j + 1] = key;
            entities[j + 1] = entity;
        }
    }
}
