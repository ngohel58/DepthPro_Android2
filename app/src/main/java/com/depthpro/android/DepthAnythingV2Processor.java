package com.depthpro.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

public class DepthAnythingV2Processor {
    private static final String TAG = "DepthAnythingV2Processor";
    private static final String MODEL_FILENAME = "depth_anything_v2_large.onnx";

    // Model input dimensions
    private static final int INPUT_HEIGHT = 518;
    private static final int INPUT_WIDTH = 518;
    private static final int CHANNELS = 3;

    // Model input/output names
    private static final String INPUT_NAME = "pixel_values";
    private static final String DEPTH_OUTPUT_NAME = "depth";

    private final Context context;
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;

    private final ImageUtils imageUtils;
    private final TensorUtils tensorUtils;
    private final DepthMapRenderer depthMapRenderer;

    // Track preprocessing details for post-processing
    public static class PreprocessInfo {
        public final int originalWidth;
        public final int originalHeight;
        public final float scale;
        public final int cropX;
        public final int cropY;

        public PreprocessInfo(int originalWidth, int originalHeight,
                               float scale, int cropX, int cropY) {
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.scale = scale;
            this.cropX = cropX;
            this.cropY = cropY;
        }
    }

    public DepthAnythingV2Processor(Context context) throws OrtException, IOException {
        this.context = context;
        this.imageUtils = new ImageUtils();
        this.tensorUtils = new TensorUtils();
        this.depthMapRenderer = new DepthMapRenderer();

        initializeModel();
    }

    private void initializeModel() throws OrtException, IOException {
        Log.d(TAG, "Initializing Depth Anything V2 ONNX model...");

        // Initialize ONNX Runtime environment
        ortEnvironment = OrtEnvironment.getEnvironment();

        // Copy model to internal storage for direct file access
        String modelPath = copyModelToInternalStorage();

        // Create session options
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

        // Enable CPU optimizations
        sessionOptions.setIntraOpNumThreads(4);
        sessionOptions.setInterOpNumThreads(4);

        // Create session from file path
        ortSession = ortEnvironment.createSession(modelPath, sessionOptions);

        Log.d(TAG, "Model initialized successfully");
        logModelInfo();
    }

    private String copyModelToInternalStorage() throws IOException {
        File modelFile = new File(context.getFilesDir(), MODEL_FILENAME);

        // Only copy if not exists
        if (!modelFile.exists()) {
            Log.d(TAG, "Copying model to internal storage...");

            try (InputStream inputStream = context.getAssets().open(MODEL_FILENAME);
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(modelFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;

                    // Log progress every 50MB
                    if (totalBytes % (50 * 1024 * 1024) == 0) {
                        Log.d(TAG, "Copied " + (totalBytes / (1024 * 1024)) + " MB");
                    }
                }

                Log.d(TAG, "Model copied successfully: " + (totalBytes / (1024 * 1024)) + " MB");
            }
        }

        return modelFile.getAbsolutePath();
    }

    private void logModelInfo() {
        try {
            Log.d(TAG, "Model inputs: " + ortSession.getInputNames());
            Log.d(TAG, "Model outputs: " + ortSession.getOutputNames());
        } catch (Exception e) {
            Log.w(TAG, "Could not log model info", e);
        }
    }

    public DepthResult processImage(Bitmap inputBitmap) throws OrtException {
        Log.d(TAG, "Processing image for depth estimation...");

        // Preprocess with aspect ratio tracking
        PreprocessResult preprocessResult = preprocessImageWithAspectRatio(inputBitmap);
        Bitmap resizedBitmap = preprocessResult.bitmap;
        PreprocessInfo preprocessInfo = preprocessResult.info;

        float[][][] preprocessedImage = preprocessImageForDepthAnything(resizedBitmap);

        // Convert to FloatBuffer in NCHW format
        FloatBuffer buffer = FloatBuffer.allocate(CHANNELS * INPUT_HEIGHT * INPUT_WIDTH);
        for (int c = 0; c < CHANNELS; c++) {
            for (int h = 0; h < INPUT_HEIGHT; h++) {
                for (int w = 0; w < INPUT_WIDTH; w++) {
                    buffer.put(preprocessedImage[h][w][c]);
                }
            }
        }
        buffer.rewind();

        // Create input tensor
        OnnxTensor inputTensor = OnnxTensor.createTensor(
                ortEnvironment, buffer, new long[]{1, CHANNELS, INPUT_HEIGHT, INPUT_WIDTH});

        // Run inference
        Map<String, OnnxTensor> inputs = Collections.singletonMap(INPUT_NAME, inputTensor);
        OrtSession.Result result = ortSession.run(inputs);

        // Extract depth output
        OnnxTensor depthTensor = null;
        String[] possibleNames = {"depth", "output", "logits", "predicted_depth"};
        for (String name : possibleNames) {
            depthTensor = (OnnxTensor) result.get(name).orElse(null);
            if (depthTensor != null) {
                Log.d(TAG, "Found output with name: " + name);
                break;
            }
        }

        if (depthTensor == null && result.size() > 0) {
            String firstOutputName = result.iterator().next().getKey();
            depthTensor = (OnnxTensor) result.get(firstOutputName).orElse(null);
            Log.d(TAG, "Using first output: " + firstOutputName);
        }

        if (depthTensor == null) {
            StringBuilder availableOutputs = new StringBuilder("Available outputs: ");
            for (Map.Entry<String, ?> entry : result) {
                availableOutputs.append(entry.getKey()).append(" ");
            }
            Log.e(TAG, availableOutputs.toString());
            throw new RuntimeException("No depth output found. Available: " + availableOutputs.toString());
        }

        // Process depth map with Python-exact processing
        float[][] depthMap = extractAndResizeDepthMapPythonExact(depthTensor, preprocessInfo);

        // Generate depth map bitmap using Python-exact rendering
        Bitmap depthMapBitmap = depthMapRenderer.renderDepthMapPythonExact(depthMap, inputBitmap.getWidth(), inputBitmap.getHeight());

        // Cleanup
        inputTensor.close();
        result.close();

        Log.d(TAG, "Depth estimation completed");

        return new DepthResult(depthMapBitmap, depthMap);
    }

    private static class PreprocessResult {
        final Bitmap bitmap;
        final PreprocessInfo info;

        PreprocessResult(Bitmap bitmap, PreprocessInfo info) {
            this.bitmap = bitmap;
            this.info = info;
        }
    }

    private PreprocessResult preprocessImageWithAspectRatio(Bitmap inputBitmap) {
        int originalWidth = inputBitmap.getWidth();
        int originalHeight = inputBitmap.getHeight();

        // Scale so that the shorter side equals 518
        float scale = (float) INPUT_WIDTH / Math.min(originalWidth, originalHeight);
        int scaledWidth = Math.round(originalWidth * scale);
        int scaledHeight = Math.round(originalHeight * scale);

        // Scale the image
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, scaledWidth, scaledHeight, true);

        // Center-crop to 518x518
        int cropX = (scaledWidth - INPUT_WIDTH) / 2;
        int cropY = (scaledHeight - INPUT_HEIGHT) / 2;
        Bitmap finalBitmap = Bitmap.createBitmap(scaledBitmap, cropX, cropY, INPUT_WIDTH, INPUT_HEIGHT);

        // Create preprocessing info for later use
        PreprocessInfo info = new PreprocessInfo(
                originalWidth, originalHeight, scale, cropX, cropY);

        Log.d(TAG, String.format(
                "Preprocessed (crop): %dx%d -> %dx%d (scale=%.3f, crop=%d,%d)",
                originalWidth, originalHeight, scaledWidth, scaledHeight, scale, cropX, cropY));

        return new PreprocessResult(finalBitmap, info);
    }

    private float[][][] preprocessImageForDepthAnything(Bitmap bitmap) {
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

                // Normalize using mean/std from Hugging Face preprocessor config
                preprocessedImage[y][x][0] = ((r / 255.0f) - 0.485f) / 0.229f; // R
                preprocessedImage[y][x][1] = ((g / 255.0f) - 0.456f) / 0.224f; // G
                preprocessedImage[y][x][2] = ((b / 255.0f) - 0.406f) / 0.225f; // B
            }
        }

        return preprocessedImage;
    }

    private float[][] extractAndResizeDepthMapPythonExact(OnnxTensor depthTensor, PreprocessInfo preprocessInfo) throws OrtException {
        long[] shape = depthTensor.getInfo().getShape();
        Log.d(TAG, "Depth tensor shape: " + java.util.Arrays.toString(shape));

        // Extract raw depth map
        int height, width;
        FloatBuffer buffer = depthTensor.getFloatBuffer();

        if (shape.length == 3) {
            height = (int) shape[1];
            width = (int) shape[2];
        } else if (shape.length == 4) {
            height = (int) shape[2];
            width = (int) shape[3];
        } else {
            throw new RuntimeException("Unexpected depth tensor shape: " + java.util.Arrays.toString(shape));
        }

        // Extract raw depth values (no normalization yet)
        double[][] rawDepthMap = new double[height][width];
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                rawDepthMap[h][w] = (double) buffer.get(h * width + w);
            }
        }

        // Reconstruct full depth canvas from cropped patch
        int scaledWidth = Math.round(preprocessInfo.originalWidth * preprocessInfo.scale);
        int scaledHeight = Math.round(preprocessInfo.originalHeight * preprocessInfo.scale);
        double[][] canvas = new double[scaledHeight][scaledWidth];

        for (int h = 0; h < height; h++) {
            System.arraycopy(rawDepthMap[h], 0,
                    canvas[preprocessInfo.cropY + h], preprocessInfo.cropX, width);
        }

        // Resize canvas back to original dimensions using exact Python method
        double[][] resizedDepth = resizeDepthMapPythonExact(canvas,
                preprocessInfo.originalWidth,
                preprocessInfo.originalHeight);

        // Apply EXACT Python normalization
        normalizeDepthMapPythonExact(resizedDepth);

        // Convert back to float for compatibility
        float[][] finalDepthMap = convertDoubleToFloat(resizedDepth);

        Log.d(TAG, "Python-exact depth processing completed");
        return finalDepthMap;
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

        // Use exact PIL-style resampling with proper coordinate system
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                // PIL coordinate system: (x+0.5)*scale - 0.5
                double sourceY = (y + 0.5) * scaleY - 0.5;
                double sourceX = (x + 0.5) * scaleX - 0.5;

                // PIL-style clamping
                sourceX = Math.max(0.0, Math.min(sourceWidth - 1.0, sourceX));
                sourceY = Math.max(0.0, Math.min(sourceHeight - 1.0, sourceY));

                resized[y][x] = bilinearInterpolateDoublePrecision(depthMap, sourceX, sourceY);
            }
        }

        Log.d(TAG, String.format("Python-exact resize: %dx%d -> %dx%d",
                sourceWidth, sourceHeight, targetWidth, targetHeight));

        return resized;
    }

    private double bilinearInterpolateDoublePrecision(double[][] data, double x, double y) {
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

        return v11 * (1.0 - dx) * (1.0 - dy) +
                v12 * dx * (1.0 - dy) +
                v21 * (1.0 - dx) * dy +
                v22 * dx * dy;
    }

    private void normalizeDepthMapPythonExact(double[][] depthMap) {
        // EXACT Python normalization sequence:
        // depth -= depth.min()
        // max_val = depth.max()
        // if max_val > 0: depth /= max_val

        // Step 1: Find minimum value with double precision
        double minDepth = Double.MAX_VALUE;
        for (double[] row : depthMap) {
            for (double value : row) {
                if (!Double.isInfinite(value) && !Double.isNaN(value)) {
                    minDepth = Math.min(minDepth, value);
                }
            }
        }

        // Step 2: Subtract minimum (depth -= depth.min())
        for (int h = 0; h < depthMap.length; h++) {
            for (int w = 0; w < depthMap[h].length; w++) {
                if (!Double.isInfinite(depthMap[h][w]) && !Double.isNaN(depthMap[h][w])) {
                    depthMap[h][w] -= minDepth;
                } else {
                    depthMap[h][w] = 0.0;
                }
            }
        }

        // Step 3: Find new maximum (max_val = depth.max())
        double maxDepth = -Double.MAX_VALUE;
        for (double[] row : depthMap) {
            for (double value : row) {
                if (!Double.isInfinite(value) && !Double.isNaN(value)) {
                    maxDepth = Math.max(maxDepth, value);
                }
            }
        }

        // Step 4: Divide by maximum if > 0 (if max_val > 0: depth /= max_val)
        if (maxDepth > 0.0) {
            for (int h = 0; h < depthMap.length; h++) {
                for (int w = 0; w < depthMap[h].length; w++) {
                    if (!Double.isInfinite(depthMap[h][w]) && !Double.isNaN(depthMap[h][w])) {
                        depthMap[h][w] /= maxDepth;
                    }
                }
            }
        }

        Log.d(TAG, String.format("Python-exact normalization: min=%.6f, max=%.6f", minDepth, maxDepth));
    }

    private float[][] convertDoubleToFloat(double[][] doubleArray) {
        int height = doubleArray.length;
        int width = doubleArray[0].length;
        float[][] floatArray = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                floatArray[y][x] = (float) doubleArray[y][x];
            }
        }
        return floatArray;
    }

    public void cleanup() {
        try {
            if (ortSession != null) {
                ortSession.close();
                ortSession = null;
            }
            if (ortEnvironment != null) {
                ortEnvironment.close();
                ortEnvironment = null;
            }
            Log.d(TAG, "DepthAnythingV2Processor cleaned up");
        } catch (OrtException e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    // Result class
    public static class DepthResult {
        public final Bitmap depthMapBitmap;
        public final float[][] depthMap;

        public DepthResult(Bitmap depthMapBitmap, float[][] depthMap) {
            this.depthMapBitmap = depthMapBitmap;
            this.depthMap = depthMap;
        }
    }
}