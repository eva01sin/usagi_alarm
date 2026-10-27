package com.usagi.alarm;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class PcmAssets {
    private PcmAssets() {}
    static short[] load(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name + ".pcm")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            byte[] bytes = output.toByteArray();
            if (bytes.length % 4 != 0) throw new IOException("Invalid stereo PCM: " + name);
            short[] samples = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
            return samples;
        }
    }
}
