package com.depthpro.android;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_IMAGE_PICK = 101;
    private static final int REQUEST_CAMERA = 102;

    // UI Components
    private ImageView originalImageView;
    private ImageView depthMapImageView;
    private ImageView chromoImageView;
    private MaterialButton selectImageButton;
    private MaterialButton captureImageButton;
    private MaterialButton processButton;
    private MaterialButton downloadDepthButton;
    private MaterialButton downloadEffectButton;
    private LinearProgressIndicator progressBar;
    private TextView statusText;
    private TextView processingTimeText;

    // Core Components
    private DepthAnythingV2Processor depthProcessor;
    private ChromostereopsisProcessor chromoProcessor;
    private ExecutorService executorService;

    // Current data
    private Bitmap currentBitmap;
    private Bitmap depthMapBitmap;
    private Bitmap chromoBitmap;
    private float[][] currentDepthMap;
    private Uri currentImageUri;

    // Effect parameters
    private ChromostereopsisProcessor.EffectParams effectParams = new ChromostereopsisProcessor.EffectParams();

    // Sliders
    private Slider thresholdSlider;
    private Slider depthScaleSlider;
    private Slider featherSlider;
    private Slider redSlider;
    private Slider blueSlider;
    private Slider gammaSlider;
    private Slider blackSlider;
    private Slider whiteSlider;
    private Slider smoothingSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeComponents();
        checkPermissions();
        handleSharedIntent();
    }

    private void initializeViews() {
        originalImageView = findViewById(R.id.originalImageView);
        depthMapImageView = findViewById(R.id.depthMapImageView);
        chromoImageView = findViewById(R.id.chromoImageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        captureImageButton = findViewById(R.id.captureImageButton);
        processButton = findViewById(R.id.processButton);
        downloadDepthButton = findViewById(R.id.downloadDepthButton);
        downloadEffectButton = findViewById(R.id.downloadEffectButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        processingTimeText = findViewById(R.id.processingTimeText);

        // Initialize sliders
        thresholdSlider = findViewById(R.id.thresholdSlider);
        depthScaleSlider = findViewById(R.id.depthScaleSlider);
        featherSlider = findViewById(R.id.featherSlider);
        redSlider = findViewById(R.id.redSlider);
        blueSlider = findViewById(R.id.blueSlider);
        gammaSlider = findViewById(R.id.gammaSlider);
        blackSlider = findViewById(R.id.blackSlider);
        whiteSlider = findViewById(R.id.whiteSlider);
        smoothingSlider = findViewById(R.id.smoothingSlider);

        // Set click listeners
        selectImageButton.setOnClickListener(v -> selectImage());
        captureImageButton.setOnClickListener(v -> captureImage());
        processButton.setOnClickListener(v -> processCurrentImage());
        downloadDepthButton.setOnClickListener(v -> downloadDepthMap());
        downloadEffectButton.setOnClickListener(v -> downloadChromoEffect());

        // Initially disable buttons
        processButton.setEnabled(false);
        downloadDepthButton.setEnabled(false);
        downloadEffectButton.setEnabled(false);

        setupSliders();
    }

    private void setupSliders() {
        // Set initial values to match Python defaults
        thresholdSlider.setValue(50f);
        depthScaleSlider.setValue(50f);
        featherSlider.setValue(10f);
        redSlider.setValue(50f);
        blueSlider.setValue(50f);
        gammaSlider.setValue(50f);
        blackSlider.setValue(0f);
        whiteSlider.setValue(100f);
        smoothingSlider.setValue(0f);

        // Add listeners for real-time updates
        thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.threshold = value;
                updateEffectRealTime();
            }
        });

        depthScaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.depthScale = value;
                updateEffectRealTime();
            }
        });

        featherSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.feather = value;
                updateEffectRealTime();
            }
        });

        redSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.redBrightness = value;
                updateEffectRealTime();
            }
        });

        blueSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.blueBrightness = value;
                updateEffectRealTime();
            }
        });

        gammaSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.gamma = value;
                updateEffectRealTime();
            }
        });

        blackSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.blackLevel = value;
                updateEffectRealTime();
            }
        });

        whiteSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.whiteLevel = value;
                updateEffectRealTime();
            }
        });

        smoothingSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                effectParams.smoothing = value;
                updateEffectRealTime();
            }
        });
    }

    private void initializeComponents() {
        executorService = Executors.newSingleThreadExecutor();

        // Initialize ChromostereopsisProcessor
        chromoProcessor = new ChromostereopsisProcessor();

        // Initialize Depth Anything V2 processor
        try {
            depthProcessor = new DepthAnythingV2Processor(this);
            updateStatus("ChromoStereoizer ready");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Depth Anything V2 processor", e);
            updateStatus("Failed to load AI model: " + e.getMessage());
            Toast.makeText(this, "Failed to initialize model", Toast.LENGTH_LONG).show();
        }
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        };

        boolean hasAllPermissions = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                break;
            }
        }

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "Permissions required for app functionality", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleSharedIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                loadImageFromUri(imageUri);
            }
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CAMERA);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        switch (requestCode) {
            case REQUEST_IMAGE_PICK:
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    loadImageFromUri(selectedImageUri);
                }
                break;

            case REQUEST_CAMERA:
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap photo = (Bitmap) extras.get("data");
                    if (photo != null) {
                        loadImageFromBitmap(photo);
                    }
                }
                break;
        }
    }

    private void loadImageFromUri(Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            if (bitmap != null) {
                currentImageUri = uri;
                loadImageFromBitmap(bitmap);
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Image file not found", e);
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImageFromBitmap(Bitmap bitmap) {
        currentBitmap = bitmap;

        // Display original image with proper aspect ratio
        originalImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this)
                .load(bitmap)
                .into(originalImageView);

        // Set click listener for fullscreen view
        originalImageView.setOnClickListener(v -> showFullscreenImage(bitmap, "Original Image"));

        // Clear previous results
        depthMapImageView.setImageBitmap(null);
        chromoImageView.setImageBitmap(null);
        depthMapBitmap = null;
        chromoBitmap = null;
        currentDepthMap = null;
        downloadDepthButton.setEnabled(false);
        downloadEffectButton.setEnabled(false);

        // Enable process button
        processButton.setEnabled(true);
        updateStatus("Image loaded. Ready to generate depth map.");

        // Clear previous results
        processingTimeText.setText("");

        Log.d(TAG, String.format("Loaded image: %dx%d", bitmap.getWidth(), bitmap.getHeight()));
    }

    private void processCurrentImage() {
        if (currentBitmap == null || depthProcessor == null) {
            Toast.makeText(this, "No image selected or model not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        processButton.setEnabled(false);
        updateStatus("Generating depth map...");

        // Process in background thread
        executorService.execute(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Process image with Depth Anything V2
                DepthAnythingV2Processor.DepthResult result = depthProcessor.processImage(currentBitmap);

                long processingTime = System.currentTimeMillis() - startTime;

                // Update UI on main thread
                runOnUiThread(() -> {
                    displayResults(result, processingTime);
                    progressBar.setVisibility(View.GONE);
                    processButton.setEnabled(true);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                runOnUiThread(() -> {
                    updateStatus("Error processing image: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                    processButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Processing failed", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void displayResults(DepthAnythingV2Processor.DepthResult result, long processingTime) {
        // Store depth map data
        currentDepthMap = result.depthMap;

        // Create and display grayscale depth map at original image size
        depthMapBitmap = chromoProcessor.createGrayscaleDepthMap(
                currentDepthMap,
                currentBitmap.getWidth(),
                currentBitmap.getHeight()
        );

        if (depthMapBitmap != null) {
            depthMapImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Glide.with(this)
                    .load(depthMapBitmap)
                    .into(depthMapImageView);

            // Set click listener for fullscreen view
            depthMapImageView.setOnClickListener(v -> showFullscreenImage(depthMapBitmap, "Depth Map"));
        }

        // Generate initial chromostereopsis effect with default parameters
        updateEffect();

        // Enable download buttons
        downloadDepthButton.setEnabled(true);
        downloadEffectButton.setEnabled(true);

        // Display processing time
        processingTimeText.setText(String.format("Processing Time: %d ms", processingTime));

        updateStatus("Processing completed successfully");
        Toast.makeText(this, "Depth map generated!", Toast.LENGTH_SHORT).show();

        Log.d(TAG, String.format("Processing completed in %d ms", processingTime));
    }

    private void updateEffect() {
        if (currentBitmap == null || currentDepthMap == null) return;

        executorService.execute(() -> {
            Bitmap effect = chromoProcessor.applyEffect(currentBitmap, currentDepthMap, effectParams);

            runOnUiThread(() -> {
                if (effect != null) {
                    chromoBitmap = effect;
                    chromoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    Glide.with(this)
                            .load(effect)
                            .into(chromoImageView);

                    // Set click listener for fullscreen view
                    chromoImageView.setOnClickListener(v -> showFullscreenImage(chromoBitmap, "ChromoStereoizer Result"));

                    downloadEffectButton.setEnabled(true);
                }
            });
        });
    }

    private void updateEffectRealTime() {
        // Debounced real-time update
        if (currentBitmap == null || currentDepthMap == null) return;

        // Cancel previous task and start new one
        executorService.execute(() -> {
            try {
                Thread.sleep(50); // Small delay to prevent too frequent updates

                Bitmap effect = chromoProcessor.applyEffect(currentBitmap, currentDepthMap, effectParams);

                runOnUiThread(() -> {
                    if (effect != null) {
                        chromoBitmap = effect;
                        chromoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        Glide.with(this)
                                .load(effect)
                                .into(chromoImageView);

                        // Set click listener for fullscreen view
                        chromoImageView.setOnClickListener(v -> showFullscreenImage(chromoBitmap, "ChromoStereoizer Result"));
                    }
                });
            } catch (InterruptedException e) {
                // Thread interrupted, ignore
            }
        });
    }

    private void showFullscreenImage(Bitmap bitmap, String title) {
        if (bitmap == null) return;

        // Create fullscreen dialog
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.fullscreen_image_viewer);

        ImageView fullscreenImageView = dialog.findViewById(R.id.fullscreenImageView);
        TextView fullscreenTitle = dialog.findViewById(R.id.fullscreenTitle);
        MaterialButton closeButton = dialog.findViewById(R.id.closeButton);

        fullscreenTitle.setText(title);

        Glide.with(this)
                .load(bitmap)
                .into(fullscreenImageView);

        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Allow tap to close
        fullscreenImageView.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void downloadDepthMap() {
        if (depthMapBitmap != null) {
            saveBitmapToGallery(depthMapBitmap, "depth_map_" + System.currentTimeMillis());
        } else {
            Toast.makeText(this, "No depth map to download", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadChromoEffect() {
        if (chromoBitmap != null) {
            saveBitmapToGallery(chromoBitmap, "chromostereoizer_" + System.currentTimeMillis());
        } else {
            Toast.makeText(this, "No effect to download", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBitmapToGallery(Bitmap bitmap, String filename) {
        try {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ChromoStereoizer");

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = resolver.openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    runOnUiThread(() ->
                            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Save failed", e);
            runOnUiThread(() ->
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void updateStatus(String message) {
        statusText.setText(message);
        Log.d(TAG, message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        if (depthProcessor != null) {
            depthProcessor.cleanup();
        }
    }
}