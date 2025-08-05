package com.depthpro.android;

public class EnhancedDepthResizer {

    /**
     * High-quality bicubic interpolation resize - matches Python PIL quality
     */
    public static float[][] resizeDepthMapBicubic(float[][] depthMap, int targetWidth, int targetHeight) {
        int sourceHeight = depthMap.length;
        int sourceWidth = depthMap[0].length;

        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return depthMap;
        }

        float[][] resized = new float[targetHeight][targetWidth];

        double scaleX = (double) sourceWidth / targetWidth;
        double scaleY = (double) sourceHeight / targetHeight;

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                double sourceY = y * scaleY;
                double sourceX = x * scaleX;

                resized[y][x] = bicubicInterpolate(depthMap, sourceX, sourceY);
            }
        }

        return resized;
    }

    private static float bicubicInterpolate(float[][] data, double x, double y) {
        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);

        double dx = x - x1;
        double dy = y - y1;

        // Get 4x4 neighborhood
        float[] p = new float[16];
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                int px = clamp(x1 - 1 + i, 0, data[0].length - 1);
                int py = clamp(y1 - 1 + j, 0, data.length - 1);
                p[j * 4 + i] = data[py][px];
            }
        }

        // Bicubic kernel
        return (float) bicubicKernel(p, dx, dy);
    }

    private static double bicubicKernel(float[] p, double x, double y) {
        double[] a = new double[4];

        // Interpolate in x direction
        for (int i = 0; i < 4; i++) {
            a[i] = cubicInterpolate(p[i*4], p[i*4+1], p[i*4+2], p[i*4+3], x);
        }

        // Interpolate in y direction
        return cubicInterpolate(a[0], a[1], a[2], a[3], y);
    }

    private static double cubicInterpolate(double p0, double p1, double p2, double p3, double x) {
        return p1 + 0.5 * x * (p2 - p0 + x * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3 +
                x * (3.0 * (p1 - p2) + p3 - p0)));
    }

    /**
     * High-precision normalization using double precision
     */
    public static void normalizeDepthMapHighPrecision(float[][] depthMap) {
        double minDepth = Double.MAX_VALUE;
        double maxDepth = -Double.MAX_VALUE;

        // Find min and max with double precision
        for (float[] row : depthMap) {
            for (float value : row) {
                if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                    double dValue = (double) value;
                    minDepth = Math.min(minDepth, dValue);
                    maxDepth = Math.max(maxDepth, dValue);
                }
            }
        }

        double range = maxDepth - minDepth;
        if (range > 0) {
            for (int h = 0; h < depthMap.length; h++) {
                for (int w = 0; w < depthMap[h].length; w++) {
                    if (!Float.isInfinite(depthMap[h][w]) && !Float.isNaN(depthMap[h][w])) {
                        // High-precision normalization
                        double normalized = ((double) depthMap[h][w] - minDepth) / range;
                        depthMap[h][w] = (float) normalized;
                    } else {
                        depthMap[h][w] = 0.0f;
                    }
                }
            }
        }
    }

    /**
     * High-precision normalization for 1D arrays using double precision
     */
    public static void normalizeArrayHighPrecision(float[] array) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        // Find min and max
        for (float value : array) {
            if (!Float.isInfinite(value) && !Float.isNaN(value)) {
                double dValue = (double) value;
                min = Math.min(min, dValue);
                max = Math.max(max, dValue);
            }
        }

        // Normalize to [0, 1]
        double range = max - min;
        if (range > 0) {
            for (int i = 0; i < array.length; i++) {
                if (!Float.isInfinite(array[i]) && !Float.isNaN(array[i])) {
                    double normalized = ((double) array[i] - min) / range;
                    array[i] = (float) normalized;
                } else {
                    array[i] = 0.0f;
                }
            }
        }
    }

    /**
     * Edge-preserving smoothing (simplified bilateral filter)
     */
    public static float[][] bilateralFilterApprox(float[][] depthMap, float spatialSigma, float intensitySigma) {
        int height = depthMap.length;
        int width = depthMap[0].length;
        float[][] filtered = new float[height][width];

        int radius = (int) Math.ceil(3 * spatialSigma);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float centerValue = depthMap[y][x];
                float weightSum = 0f;
                float valueSum = 0f;

                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int ny = clamp(y + dy, 0, height - 1);
                        int nx = clamp(x + dx, 0, width - 1);

                        float neighborValue = depthMap[ny][nx];

                        // Spatial weight
                        float spatialWeight = (float) Math.exp(-(dx*dx + dy*dy) / (2 * spatialSigma * spatialSigma));

                        // Intensity weight
                        float intensityDiff = neighborValue - centerValue;
                        float intensityWeight = (float) Math.exp(-(intensityDiff*intensityDiff) / (2 * intensitySigma * intensitySigma));

                        float totalWeight = spatialWeight * intensityWeight;
                        weightSum += totalWeight;
                        valueSum += neighborValue * totalWeight;
                    }
                }

                filtered[y][x] = weightSum > 0 ? valueSum / weightSum : centerValue;
            }
        }

        return filtered;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}