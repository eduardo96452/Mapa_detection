package com.example.RealtimeDetection_GoogleMLKit;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.facedetection2023_it.R;
import com.example.facedetection2023_it.ml.Banderas;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.maps.android.SphericalUtil;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class MainActivity
        extends AppCompatActivity
        implements OnSuccessListener<Text>, OnFailureListener,
        ImageReader.OnImageAvailableListener, OnMapReadyCallback, GoogleMap.OnMapClickListener{
    public static int REQUEST_CAMERA = 111;
    public static int REQUEST_GALLERY = 222;
    public Bitmap mSelectedImage;
    public ImageView mImageView;
    public TextView txtResults;
    public Button btCamera, btGaleria;
    public RequestQueue requestQueue;
    GoogleMap Mapa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = findViewById(R.id.image_view);
        txtResults = findViewById(R.id.txtresults);
        btGaleria = findViewById(R.id.btGallery);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);
        requestQueue = Volley.newRequestQueue(this);
    }

    //TODO fragment which show llive footage from camera
    int previewHeight = 0,previewWidth = 0;
    int sensorOrientation;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void setFragment() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        CameraConnectionFragment fragment;
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                sensorOrientation = rotation - getScreenOrientation();
                            }
                        },
                        this,
                        R.layout.camera_fragment,
                        new Size(640, 480));

        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;
        getFragmentManager().beginTransaction().replace(R.id.image_view, fragment).commit();
    }
        protected int getScreenOrientation() {
            switch (getWindowManager().getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_270:
                    return 270;
                case Surface.ROTATION_180:
                    return 180;
                case Surface.ROTATION_90:
                    return 90;
                default:
                    return 0;
            }
        }
    public void abrirGaleria (View view){

        Intent i = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, 1);
}

    @Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && null != data) {
        try {
            if (requestCode == REQUEST_CAMERA)
                mSelectedImage = (Bitmap) data.getExtras().get("data");
            else
                 mSelectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());

            mImageView.setImageBitmap(mSelectedImage);
            PersonalizedModel(null);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

    public void PersonalizedModel(View v) {
        try {
            Banderas model = Banderas.newInstance(getApplicationContext());
            TensorImage image = TensorImage.fromBitmap(mSelectedImage);

            Banderas.Outputs outputs = model.process(image);
            List<Category> probability = outputs.getProbabilityAsCategoryList();

            Collections.sort(probability, new CategoryComparator());

            String res="";
            for (int i = 0; i < probability.size(); i++) {
                res = res + probability.get(i).getLabel() +  " " +  probability.get(i).getScore()*100 + " % \n";
            }
            txtResults.setText(res);
            model.close();
        } catch (IOException e) {
            txtResults.setText("Error al procesar Modelo");
        }
    }

    public void OCRfx(View v) {
    InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
    TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    recognizer.process(image)
            .addOnSuccessListener(this)
            .addOnFailureListener(this);
}

    @Override
    public void onFailure(@NonNull Exception e) {
        
    }

    @Override
    public void onSuccess(Text text) {
        List<Text.TextBlock> blocks = text.getTextBlocks();
        String resultados="";
        if (blocks.size() == 0) {
            resultados = "No hay Texto";
        }else{
            for (int i = 0; i < blocks.size(); i++) {
               List<Text.Line> lines = blocks.get(i).getLines();
                for (int j = 0; j < lines.size(); j++) {
                     List<Text.Element> elements = lines.get(j).getElements();
                     for (int k = 0; k < elements.size(); k++) {
                         resultados = resultados + elements.get(k).getText() + " ";
                     }
                }
                resultados=resultados + "\n";
            }
        }
        txtResults.setText(resultados);
    }

    //TODO getting frames of live camera footage and passing them to model
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;
    @Override
    public void onImageAvailable(ImageReader reader) {
        if (previewWidth == 0 || previewHeight == 0)           return;
        if (rgbBytes == null)    rgbBytes = new int[previewWidth * previewHeight];
        try {
            final Image image = reader.acquireLatestImage();
            if (image == null)    return;
            if (isProcessingFrame) {           image.close();            return;         }

            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =  new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0], yuvBytes[1], yuvBytes[2], previewWidth,  previewHeight,
                                    yRowStride,uvRowStride, uvPixelStride,rgbBytes);
                        }
                    };
            postInferenceCallback =      new Runnable() {
                        @Override
                        public void run() {  image.close(); isProcessingFrame = false;  }
                    };

            processImage();

        } catch (final Exception e) {
        }

    }

    private void processImage() {
        imageConverter.run();
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        try {
            Banderas model = Banderas.newInstance(getApplicationContext());
            TensorImage image = TensorImage.fromBitmap(rgbFrameBitmap);

            Banderas.Outputs outputs = model.process(image);
            List<Category> probability = outputs.getProbabilityAsCategoryList();

            Collections.sort(probability, new CategoryComparator());

            String res="";
            for (int i = 0; i < probability.size(); i++) {
                res = res + probability.get(i).getLabel() +  " " +  probability.get(i).getScore()*100 + " % \n";
            }
            txtResults.setText(res);
            model.close();
        } catch (IOException e) {
            txtResults.setText("Error al procesar Modelo");
        }

        postInferenceCallback.run();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }




    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //mapaa

    @Override
    public void onMapClick(@NonNull LatLng latLng) {

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Mapa = googleMap;

        Mapa.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        Mapa.getUiSettings().setZoomControlsEnabled(true);

        //Mover el Mapa a Quevedo
        CameraUpdate camUpd1 = CameraUpdateFactory
                .newLatLngZoom(new
                                LatLng(-1.0227893720763475, -79.4628673782913),
                        15);

    }

    private void drawRectangle(LatLng center, double radius) {
        // Crear un objeto LatLngBounds.Builder
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        // Calcular las ubicaciones de los cuatro vértices
        LatLng northeast = SphericalUtil.computeOffset(center, radius * Math.sqrt(2.0), 45);
        LatLng northwest = SphericalUtil.computeOffset(center, radius * Math.sqrt(2.0), 315);
        LatLng southeast = SphericalUtil.computeOffset(center, radius * Math.sqrt(2.0), 135);
        LatLng southwest = SphericalUtil.computeOffset(center, radius * Math.sqrt(2.0), 225);

        // Crear un objeto PolygonOptions para el rectángulo
        PolygonOptions rectOptions = new PolygonOptions()
                .add(southeast) // Añadir el vértice inferior-izquierdo
                .add(northeast) // Añadir el vértice superior-izquierdo
                .add(northwest) // Añadir el vértice superior-derecho
                .add(southwest) // Añadir el vértice inferior-derecho
                .strokeColor(Color.GREEN) // Establecer el color del borde del rectángulo
                .fillColor(Color.TRANSPARENT); // Establecer el color de relleno del rectángulo (transparente)

// Añadir el polígono al mapa
        Mapa.addPolygon(rectOptions);

    }



}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
class CategoryComparator implements java.util.Comparator<Category> {
    @Override
    public int compare(Category a, Category b) {
        return (int)(b.getScore()*100) - (int)(a.getScore()*100);
    }
}