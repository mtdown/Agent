package com.et.cloud.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Serializes embedding vectors to/from the wiki_chunk.embedding BLOB
 * (float32 little-endian).
 */
public final class VectorCodec {

    private VectorCodec() {
    }

    public static byte[] encode(float[] vector) {
        if (vector == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * vector.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    public static float[] decode(byte[] blob) {
        if (blob == null || blob.length == 0 || blob.length % Float.BYTES != 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[blob.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
