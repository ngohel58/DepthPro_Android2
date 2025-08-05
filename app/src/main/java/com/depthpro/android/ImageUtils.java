package com.depthpro.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    // ImageNet normalization values
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    public Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null) {
            return null;
        }

        // Calculate scale to maintain aspect ratio
        float scaleX = (float) targetWidth / bitmap.getWidth();
        float scaleY = (float) targetHeight / bitmap.getHeight();
        float scale = Math.min(scaleX, scaleY);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        Bitmap scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Create final bitmap with exact target dimensions (pad if necessary)
        Bitmap finalBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);

        // Center the scaled bitmap
        float dx = (targetWidth - scaledBitmap.getWidth()) / 2f;
        float dy = (targetHeight - scaledBitmap.getHeight()) / 2f;

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(scaledBitmap, dx, dy, paint);

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }

        Log.d(TAG, String.format("Resized bitmap: %dx%d -> %dx%d",
                bitmap.getWidth(), bitmap.getHeight(), targetWidth, targetHeight));

        return finalBitmap;
    }

    public float[][][] preprocessImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float[][][] preprocessedImage = new float[height][width][3];
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];

                // Extract RGB values
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Normalize to [0, 1]
                float rNorm = r / 255.0f;
                float gNorm = g / 255.0f;
                float bNorm = b / 255.0f;

                // Apply ImageNet normalization
                preprocessedImage[y][x][0] = (rNorm - MEAN[0]) / STD[0]; // R
                preprocessedImage[y][x][1] = (gNorm - MEAN[1]) / STD[1]; // G
                preprocessedImage[y][x][2] = (bNorm - MEAN[2]) / STD[2]; // B
            }
        }

        Log.d(TAG, String.format("Preprocessed image: %dx%d", width, height));
        return preprocessedImage;
    }

    public Bitmap cropBitmap(Bitmap bitmap, int x, int y, int width, int height) {
        if (bitmap == null) return null;

        // Ensure crop dimensions are within bitmap bounds
        x = Math.max(0, Math.min(x, bitmap.getWidth() - 1));
        y = Math.max(0, Math.min(y, bitmap.getHeight() - 1));
        width = Math.min(width, bitmap.getWidth() - x);
        height = Math.min(height, bitmap.getHeight() - y);

        return Bitmap.createBitmap(bitmap, x, y, width, height);
    }

    public Bitmap rotateBitmap(Bitmap bitmap, float degrees) {
        if (bitmap == null || degrees == 0) return bitmap;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public float calculateAspectRatio(Bitmap bitmap) {
        if (bitmap == null) return 1.0f;
        return (float) bitmap.getWidth() / bitmap.getHeight();
    }

    public Bitmap createScaledBitmap(Bitmap bitmap, int maxWidth, int maxHeight, boolean maintainAspectRatio) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap; // No scaling needed
        }

        float scaleX = (float) maxWidth / width;
        float scaleY = (float) maxHeight / height;
        float scale;

        if (maintainAspectRatio) {
            scale = Math.min(scaleX, scaleY);
        } else {
            // Use different scales for X and Y (may distort image)
            Matrix matrix = new Matrix();
            matrix.postScale(scaleX, scaleY);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }

        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    public boolean isValidBitmap(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0;
    }

    public void logBitmapInfo(Bitmap bitmap, String tag) {
        if (bitmap != null) {
            Log.d(TAG, String.format("%s - Bitmap: %dx%d, Config: %s, Bytes: %d",
                    tag, bitmap.getWidth(), bitmap.getHeight(),
                    bitmap.getConfig(), bitmap.getByteCount()));
        } else {
            Log.d(TAG, tag + " - Bitmap is null");
        }
    }
}