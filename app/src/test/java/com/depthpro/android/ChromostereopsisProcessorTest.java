package com.depthpro.android;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ChromostereopsisProcessorTest {

    private ChromostereopsisProcessor.EffectParams defaultParams() {
        ChromostereopsisProcessor.EffectParams p = new ChromostereopsisProcessor.EffectParams();
        p.threshold = 50f;
        p.depthScale = 100f;
        p.feather = 0f;
        p.redBrightness = 100f;
        p.blueBrightness = 100f;
        p.gamma = 50f;
        p.blackLevel = 0f;
        p.whiteLevel = 100f;
        p.smoothing = 0f;
        return p;
    }

    @Test
    public void applyEffectUsesLastDepthPixel() {
        Bitmap original = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888);
        original.eraseColor(Color.WHITE);

        float[][] depth = new float[8][5];
        depth[7][4] = 1f; // bottom-right depth

        ChromostereopsisProcessor processor = new ChromostereopsisProcessor();
        Bitmap result = processor.applyEffect(original, depth, defaultParams());

        int topLeft = result.getPixel(0, 0);
        int bottomRight = result.getPixel(2, 3);

        assertEquals(255, Color.blue(topLeft));
        assertEquals(0, Color.red(topLeft));

        assertEquals(255, Color.red(bottomRight));
        assertEquals(0, Color.blue(bottomRight));
    }

    @Test
    public void applyEffectHandlesSmallDepthMap() {
        Bitmap original = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        original.eraseColor(Color.WHITE);

        float[][] depth = new float[1][1]; // minimal depth map

        ChromostereopsisProcessor processor = new ChromostereopsisProcessor();
        Bitmap result = processor.applyEffect(original, depth, defaultParams());

        assertNotNull(result);
        assertEquals(10, result.getWidth());
        assertEquals(10, result.getHeight());
    }
}
