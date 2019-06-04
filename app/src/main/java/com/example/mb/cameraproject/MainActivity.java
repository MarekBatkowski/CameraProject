package com.example.mb.cameraproject;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.icu.text.SimpleDateFormat;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";

    private ImageButton CaptureButton;
    private ImageButton OpenGalleryButton;
    private ImageButton ToggleCameraButton;
    private TextureView Preview;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static
    {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    //Save to FILE
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private final int CAMERABACK = 0;
    private final int CAMERAFRONT = 1;

    private int CAMERAOPENED = CAMERAFRONT;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        Preview = findViewById(R.id.Preview);
        assert Preview != null;
        Preview.setSurfaceTextureListener(textureListener);

        CaptureButton = findViewById(R.id.buttonCapture);
        OpenGalleryButton = findViewById(R.id.buttonOpenGallary);
        ToggleCameraButton = findViewById(R.id.buttonToggleCamera);

        //UpdateGalleryButton();

        CaptureButton.setOnClickListener(new View.OnClickListener()
        {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View view)
            {
                takePicture();
            }
        });

        OpenGalleryButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                OpenFolder();
            }
        });

        ToggleCameraButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ToggleCamera();
            }
        });
    }

    private void ToggleCamera()
    {
        if(CAMERAOPENED == CAMERABACK)
        {
            CAMERAOPENED = CAMERAFRONT;
            Toast.makeText(MainActivity.this, "Front", Toast.LENGTH_SHORT).show();
        }
        else
        {
            CAMERAOPENED = CAMERABACK;
            Toast.makeText(MainActivity.this, "Back", Toast.LENGTH_SHORT).show();
        }

        stopBackgroundThread();
        startBackgroundThread();
        if (Preview.isAvailable())
            openCamera();
        else
            Preview.setSurfaceTextureListener(textureListener);
    }

    private File getLatestFilefromDir(String dirPath)
    {
        File directory = new File(dirPath);
        File[] files = directory.listFiles();
        if (files == null || files.length == 0)
            return null;

        File last = files[0];
        for (int i=1; i<files.length; i++)
        {
            if(last.lastModified()<files[i].lastModified())
                last = files[i];
        }
        return last;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void UpdateGalleryButton()
    {
        Bitmap lastPhoto = BitmapFactory.decodeFile(getLatestFilefromDir(Environment.getExternalStorageDirectory() + "/DCIM/CameraProject").getPath());
        BitmapDrawable lastPhotoDrawable = new BitmapDrawable(getResources(),lastPhoto);
        OpenGalleryButton.setForeground(lastPhotoDrawable);
        //OpenGalleryButton.setBackground(lastPhotoDrawable);
    }

    private void OpenFolder()
    {
        File directory = new File(Environment.getExternalStorageDirectory() + "/DCIM/CameraProject");  // create folder if it doesnt exist
        if(!directory.exists())
            directory.mkdirs();

        File last = getLatestFilefromDir(directory.getPath());

        if(last != null)
        {
            Log.e(TAG, "path: : " + directory.getPath());
            Log.e(TAG, "last file: " + getLatestFilefromDir(directory.getPath()).getPath());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(Uri.fromFile(last), "image/*");
            startActivity(intent);
        }
        else    Toast.makeText(MainActivity.this, R.string.toast_emptyfolder, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    private String PathName;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_useIntent:
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                Date currentTime = Calendar.getInstance().getTime();
                String date = DateToString(currentTime);

                PathName = Environment.getExternalStorageDirectory() + "/DCIM/CameraProject/IMG_"+ date + ".jpg";
                File file = new File(PathName);
                Uri fileUri = Uri.fromFile(file);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

                startActivityForResult(intent,0);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private String DateToString(Date date)
    {
        String dateString = null;
        SimpleDateFormat sdfr = new SimpleDateFormat("ddMMyyyy_HHmmss");

        try
        {
            dateString = sdfr.format(date);
        }
        catch (Exception e)
        {
            System.out.println(e);
        }
        return dateString;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0)
        {
            File directory = new File(Environment.getExternalStorageDirectory() + "/DCIM/CameraProject/");  // create folder if it doesn't exist
            if(!directory.exists())
                directory.mkdirs();
            String SavedAs = getResources().getString(R.string.toast_savedas);
            Toast.makeText(MainActivity.this, SavedAs + PathName, Toast.LENGTH_SHORT).show();
            //UpdateGalleryButton();
        }
    }

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera)
        {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice)
        {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i)
        {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void takePicture()
    {
        if (cameraDevice == null) return;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try
        {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null)
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

            //Capture image with custom size
            int width = 640;
            int height = 480;
            if (jpegSizes != null && jpegSizes.length > 0)
            {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(Preview.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //Check orientation base on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            Date currentTime = Calendar.getInstance().getTime();
            String date = DateToString(currentTime);
            file = new File(Environment.getExternalStorageDirectory() + "/DCIM/CameraProject/IMG_"+ date + ".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener()
            {
                @Override
                public void onImageAvailable(ImageReader imageReader)
                {
                    Image image = null;
                    try
                    {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);

                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    finally
                    {
                        {
                            if (image != null) image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException
                {
                    OutputStream outputStream = null;
                    try
                    {
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally
                    {
                        if (outputStream != null) outputStream.close();
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback()
            {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result)
                {
                    super.onCaptureCompleted(session, request, result);
                    String SavedAs = getResources().getString(R.string.toast_savedas);
                    Toast.makeText(MainActivity.this, SavedAs + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    try
                    {
                        cameraCaptureSession.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
                {

                }
            }, mBackgroundHandler);


        } catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private void createCameraPreview()
    {
        try
        {
            SurfaceTexture texture = Preview.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth()/2, imageDimension.getHeight()/2);
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    if (cameraDevice == null) return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    Toast.makeText(MainActivity.this, "Configuration changed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private void updatePreview()
    {
        if (cameraDevice == null) Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try
        {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private void openCamera()
    {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try
        {
            cameraId = manager.getCameraIdList()[CAMERAOPENED];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);

        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener()
    {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1)
        {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1)
        {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture)
        {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture)
        {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, R.string.no_permissions, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        startBackgroundThread();
        if (Preview.isAvailable())
            openCamera();
        else
            Preview.setSurfaceTextureListener(textureListener);
        Log.e(TAG, "onResume");
    }

    @Override
    protected void onPause()
    {
        stopBackgroundThread();
        super.onPause();
        Log.e(TAG, "onPause");
    }

    private void stopBackgroundThread()
    {
        mBackgroundThread.quitSafely();
        try
        {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread()
    {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
}
