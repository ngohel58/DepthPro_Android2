package com.depthpro.android;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class ChromostereopsisProcessor {
    private static final String TAG = "ChromostereopsisProcessor";

    public static class EffectParams {
        public float threshold = 50f;
        public float depthScale = 50f;
        public float feather = 10f;
        public float redBrightness = 50f;
        public float blueBrightness = 50f;
        public float gamma = 50f;
        public float blackLevel = 0f;
        public float whiteLevel = 100f;
        public float smoothing = 0f;
    }

    public Bitmap applyEffect(Bitmap original, float[][] depthMap, EffectParams params) {
        if (original == null || depthMap == null) return null;

        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        // Resize depth map using exact Python method
        double[][] depthMapDouble = convertToDouble(depthMap);
        double[][] resizedDepth = resizeDepthMapPythonExact(depthMapDouble, originalWidth, originalHeight);

        // Apply smoothing exactly like Python bilateral filter
        double[][] smoothedDepth = applyPythonSmoothing(resizedDepth, params.smoothing);

        // Convert original to grayscale exactly like Python PIL
        double[] grayDouble = convertToGrayscalePythonExact(original);

        // Apply levels adjustment with exact Python precision
        applyLevelsAdjustmentPython(grayDouble, params.blackLevel, params.whiteLevel);

        // Apply gamma correction exactly like Python
        applyGammaCorrectionPython(grayDouble, params.gamma);

        // Apply chromostereopsis effect with exact Python parameters
        Bitmap result = applyChromostereopsisEffectPython(
                grayDouble, smoothedDepth, params, originalWidth, originalHeight);

        Log.d(TAG, "Applied Python-exact chromostereopsis effect");
        return result;
    }

    private double[][] convertToDouble(float[][] floatArray) {
        int height = floatArray.length;
        int width = floatArray[0].length;
        double[][] doubleArray = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                doubleArray[y][x] = (double) floatArray[y][x];
            }
        }
        return doubleArray;
    }

    private double[][] resizeDepthMapPythonExact(double[][] depthMap, int targetWidth, int targetHeight) {
        int sourceHeight = depthMap.length;
        int sourceWidth = depthMap[0].length;

        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return depthMap;
        }

        double[][] resized = new double[targetHeight][targetWidth];
        double scaleX = (double) sourceWidth / targetWidth;
        double scaleY = (double) sourceHeight / targetHeight;

        // Use exact PIL-style resampling
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                double sourceY = (y + 0.5) * scaleY - 0.5;
                double sourceX = (x + 0.5) * scaleX - 0.5;

                // PIL-style clamping and interpolation
                sourceX = Math.max(0, Math.min(sourceWidth - 1, sourceX));
                sourceY = Math.max(0, Math.min(sourceHeight - 1, sourceY));

                resized[y][x] = bilinearInterpolateDouble(depthMap, sourceX, sourceY);
            }
        }

        return resized;
    }

    private double bilinearInterpolateDouble(double[][] data, double x, double y) {
        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = Math.min(x1 + 1, data[0].length - 1);
        int y2 = Math.min(y1 + 1, data.length - 1);

        double dx = x - x1;
        double dy = y - y1;

        double v11 = data[y1][x1];
        double v12 = data[y1][x2];
        double v21 = data[y2][x1];
        double v22 = data[y2][x2];

        return v11 * (1 - dx) * (1 - dy) +
                v12 * dx * (1 - dy) +
                v21 * (1 - dx) * dy +
                v22 * dx * dy;
    }

    private double[][] applyPythonSmoothing(double[][] depthMap, float smoothingPercent) {
        double smoothingRadius = smoothingPercent / 10.0;
        if (smoothingRadius <= 0.0) {
            return depthMap;
        }

        double sigma = Math.max(smoothingRadius * 10.0, 1.0);
        int diameter = 5;
        int radius = diameter / 2;
        int height = depthMap.length;
        int width = depthMap[0].length;
        double[][] result = new double[height][width];

        double twoSigmaSpace2 = 2.0 * sigma * sigma;
        double twoSigmaColor2 = twoSigmaSpace2;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double center = depthMap[y][x] * 255.0;
                double weightSum = 0.0;
                double valueSum = 0.0;

                for (int dy = -radius; dy <= radius; dy++) {
                    int yy = Math.min(Math.max(y + dy, 0), height - 1);
                    double spatialY = dy * dy;

                    for (int dx = -radius; dx <= radius; dx++) {
                        int xx = Math.min(Math.max(x + dx, 0), width - 1);
                        double spatial = Math.exp(-(spatialY + dx * dx) / twoSigmaSpace2);

                        double neighbor = depthMap[yy][xx] * 255.0;
                        double range = Math.exp(-((neighbor - center) * (neighbor - center)) / twoSigmaColor2);

                        double weight = spatial * range;
                        weightSum += weight;
                        valueSum += neighbor * weight;
                    }
                }

                if (weightSum > 0.0) {
                    result[y][x] = (valueSum / weightSum) / 255.0;
                } else {
                    result[y][x] = depthMap[y][x];
                }
            }
        }

        return result;
    }

    private double[] convertToGrayscalePythonExact(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        double[] gray = new double[pixels.length];

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];

            // Extract RGB with exact precision
            double r = (double) ((pixel >> 16) & 0xFF);
            double g = (double) ((pixel >> 8) & 0xFF);
            double b = (double) (pixel & 0xFF);

            // Exact PIL luminance formula (ITU-R BT.601)
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b;
        }

        return gray;
    }

    private void applyLevelsAdjustmentPython(double[] gray, float blackLevelPercent, float whiteLevelPercent) {
        // Exact Python mapping: 0-100% -> 0-255
        double blackLevel = blackLevelPercent * 2.55;
        double whiteLevel = whiteLevelPercent * 2.55;
        double denominator = Math.max(whiteLevel - blackLevel, 1e-10); // Python precision

        for (int i = 0; i < gray.length; i++) {
            double adjustedValue = (gray[i] - blackLevel) / denominator;
            gray[i] = Math.max(0.0, Math.min(1.0, adjustedValue));
        }
    }

    private void applyGammaCorrectionPython(double[] gray, float gammaPercent) {
        // Exact Python gamma mapping: 0-100% -> 0.1-3.0
        double gammaValue = 0.1 + (gammaPercent / 100.0) * 2.9;

        for (int i = 0; i < gray.length; i++) {
            gray[i] = Math.pow(gray[i], gammaValue);
        }
    }

    private Bitmap applyChromostereopsisEffectPython(double[] gray, double[][] depthMap,
                                                     EffectParams params, int width, int height) {

        // Exact Python parameter mapping
        double thresholdNorm = params.threshold / 100.0;
        double steepness = Math.max(params.depthScale, 1e-3); // Avoid division by zero
        double featherNorm = params.feather / 100.0;
        double steepnessAdjusted = steepness / (featherNorm * 10.0 + 1.0);

        // Exact Python brightness mapping: 0-100% -> 0-2
        double redFactor = params.redBrightness / 50.0;
        double blueFactor = params.blueBrightness / 50.0;

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] outputPixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                double depth = depthMap[y][x];

                // Exact Python logistic function
                double exponent = -steepnessAdjusted * (depth - thresholdNorm);
                double blend = 1.0 / (1.0 + Math.exp(exponent));

                double grayValue = gray[pixelIndex];

                // Exact Python color calculation
                double redOutput = redFactor * grayValue * blend;
                double blueOutput = blueFactor * grayValue * (1.0 - blend);

                // Convert to integer with Python's truncation behavior
                int redInt = (int) Math.max(0.0, Math.min(255.0, redOutput * 255.0));
                int blueInt = (int) Math.max(0.0, Math.min(255.0, blueOutput * 255.0));

                outputPixels[pixelIndex] = Color.rgb(redInt, 0, blueInt);
            }
        }

        output.setPixels(outputPixels, 0, width, 0, 0, width, height);
        return output;
    }

    public Bitmap createGrayscaleDepthMap(float[][] depthMap, int targetWidth, int targetHeight) {
        if (depthMap == null) return null;

        // Convert to double precision
        double[][] depthDouble = convertToDouble(depthMap);
        double[][] resizedDepth = resizeDepthMapPythonExact(depthDouble, targetWidth, targetHeight);

        Bitmap grayscaleBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[targetWidth * targetHeight];

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int pixelIndex = y * targetWidth + x;
                // Exact Python grayscale conversion with truncation
                int grayValue = (int) Math.max(0.0, Math.min(255.0, resizedDepth[y][x] * 255.0));
                pixels[pixelIndex] = Color.rgb(grayValue, grayValue, grayValue);
            }
        }

        grayscaleBitmap.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        return grayscaleBitmap;
    }
}