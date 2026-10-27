package com.usagi.alarm;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import static org.junit.Assert.*;

public class AudioAssetsTest {
    private short[] load(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src/main/assets/" + name + ".pcm"));
        assertEquals(0, bytes.length % 4);
        short[] samples = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        return samples;
    }

    @Test public void packagedClipsHaveExpectedDurations() throws Exception {
        assertEquals(176400, load("type_1").length);
        assertEquals(176400, load("type_2").length);
        assertEquals(220500, load("alarm_start").length);
        assertEquals(88200, load("alarm_chain").length);
    }

    @Test public void realIntroAndChainAreConcatenatedWithoutAddedOrMissingSamples() throws Exception {
        short[] start = load("alarm_start");
        short[] chain = load("alarm_chain");
        PcmSequence sequence = new PcmSequence(load("type_1"), load("type_2"), start, chain, 0, 0, new Random(7));
        short[] actual = new short[start.length + chain.length * 3];
        int position = 0;
        while (position < actual.length) {
            short[] block = new short[Math.min(882, actual.length - position)];
            sequence.read(block);
            System.arraycopy(block, 0, actual, position, block.length);
            position += block.length;
        }
        for (int i = 0; i < actual.length; i++) {
            assertEquals("sample " + i, i < start.length ? start[i] : chain[(i - start.length) % chain.length], actual[i]);
        }
    }
}
