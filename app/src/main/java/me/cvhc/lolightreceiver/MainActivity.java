package me.cvhc.lolightreceiver;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.MathArrays;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

import static me.cvhc.lolightreceiver.CpuCoreUtils.getNumberOfCores;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String DIRNAME = "CameraTest";
    private static final int ACTION_TAKE_VIDEO = 1;
    private static final int ACTION_OPEN_VIDEO_FILE = 2;

    private static final opencv_core.Size DEST_SIZE = new opencv_core.Size(960, 540);

    private Button mButtonVideo;
    private Button mButtonOpenFile;
    private Button mButtonApply;
    private Button mButtonSave;
    private EditText mEditCols;
    private EditText mEditRows;
    private EditText mEditPacketSize;
    private PhotoView mPhotoView;
    private PhotoViewAttacher mAttacher;
    private Bitmap mDisplayBitmap;

    AndroidFrameConverter mAndroidFrameConverter;
    OpenCVFrameConverter.ToMat mOpenCVFrameConverter;

    private opencv_core.Mat mTransformMatrix;

    private String mVideoPath;
    private opencv_core.Mat mExampleImage;
    private long mStartTime = -1;
    private long mDuration = -1;
    private FFmpegFrameGrabber mGrabber;
    private File mDataDirectory;

    private opencv_core.Point2f[] mPoints;

    private Button.OnClickListener mButtonVideoOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            startActivityForResult(takeVideoIntent, ACTION_TAKE_VIDEO);
        }
    };

    private Button.OnClickListener mButtonOpenFileOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent mediaChooser = new Intent(Intent.ACTION_GET_CONTENT);
            mediaChooser.setType("video/*");
            startActivityForResult(mediaChooser, ACTION_OPEN_VIDEO_FILE);
        }
    };

    final Button.OnClickListener mButtonApplyOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPoints[0] == null || mPoints[1] == null
                    || mPoints[2] == null || mPoints[3] == null) {
                showToast("Specify 4 points before!");
                return;
            }

            int w = mExampleImage.cols();
            int h = mExampleImage.rows();

            opencv_core.Point2f source = new opencv_core.Point2f(4);
            opencv_core.Point2f destination = new opencv_core.Point2f(4);

            source.position(0).x(w*mPoints[0].x()).y(h*mPoints[0].y());
            source.position(1).x(w*mPoints[1].x()).y(h*mPoints[1].y());
            source.position(2).x(w*mPoints[2].x()).y(h*mPoints[2].y());
            source.position(3).x(w*mPoints[3].x()).y(h*mPoints[3].y());

            destination.position(0).x(0.0f).y(0.0f);
            destination.position(1).x(DEST_SIZE.width()-1).y(0.0f);
            destination.position(2).x(DEST_SIZE.width()-1).y(DEST_SIZE.height()-1);
            destination.position(3).x(0).y(DEST_SIZE.height()-1);

            mTransformMatrix = opencv_imgproc.getPerspectiveTransform(source.position(0), destination.position(0));

            opencv_core.Mat dis = mExampleImage.clone();
            opencv_imgproc.warpPerspective(dis, dis, mTransformMatrix, DEST_SIZE);
            mPhotoView.setImageBitmap(mDisplayBitmap = Mat2Bitmap(dis));

            showToast("Transformed!");
            setupButtons();
        }
    };

    private class BackgroundTask extends AsyncTask<Void, Double, String> {
        int mSliceX, mSliceY;
        int mPacketSize;
        private String mVideoFilePath;
        private ProgressDialog progressDialog;
        private ArrayList<String> decodedStrings = new ArrayList<>();

        private class StandardSignalSource {
            LinkedList<Integer> buffer = new LinkedList<>();
            int flagBit;

            public StandardSignalSource(boolean startFromZero) {
                flagBit = startFromZero ? 0 : 1;
            }

            public int next() {
                if (buffer.size() > 0) {
                    return buffer.pop();
                } else {
                    int nZero = Global.ORIGINAL_VIDEO_FPS / Global.BIT_ZERO_FREQ;
                    int nOne = Global.ORIGINAL_VIDEO_FPS / Global.BIT_ONE_FREQ;
                    int nWhat = flagBit == 0 ? nZero : nOne;

                    for (int i = 0; i * Global.DATARATE < Global.ORIGINAL_VIDEO_FPS; i++) {
                        boolean highlight = i % nWhat < nWhat / 2;
                        for (int j = 0; j * Global.ORIGINAL_VIDEO_FPS < Global.INTERMEDIATE_DATA_FPS; j++) {
                            buffer.addLast(highlight ? 1 : 0);
                        }
                    }

                    flagBit = 1 - flagBit;
                    return next();
                }
            }
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("Solving...");
            progressDialog.setCancelable(false);
            progressDialog.setMax(100);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setProgressNumberFormat(null);
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    MainActivity.this.getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            BackgroundTask.this.cancel(true);
                        }
                    });
            progressDialog.show();
        }

        public BackgroundTask(String videoFilePath, int sliceX, int sliceY, int packetSize) {
            mVideoFilePath = videoFilePath;
            mSliceX = sliceX;
            mSliceY = sliceY;
            mPacketSize = packetSize;
        }

        private int findShift(double[] signal) {
            double meanVal = StatUtils.mean(signal);
            double[] signalCopy = signal.clone();
            for (int i=0; i<signalCopy.length; i++) {
                signalCopy[i] -= meanVal;
            }

            StandardSignalSource source = new StandardSignalSource(false);
            double[] signalBuffer = new double[signalCopy.length];

            for (int i=0; i<signalBuffer.length; i++) {
                signalBuffer[i] = source.next();
            }

            double bestCorr = Double.NEGATIVE_INFINITY;
            int bestShift = 0;

            int nFramesPerCycle = Global.INTERMEDIATE_DATA_FPS / Global.DATARATE;
            for (int i = 0; i < 2 * nFramesPerCycle; i++) {
                double result = MathArrays.linearCombination(signalBuffer, signalCopy);
                if (result > bestCorr) {
                    bestCorr = result;
                    bestShift = i;
                }

                // shift by one
                System.arraycopy(signalBuffer, 1, signalBuffer, 0, signalBuffer.length - 1);
                signalBuffer[signalBuffer.length-1] = source.next();
            }

            return nFramesPerCycle - bestShift % nFramesPerCycle;
        }

        private void tryFixError(BitSet bitset) {
            if (mPacketSize == 0) {
                return;
            }

            int[] accumulator = new int[mPacketSize];
            int[] count = new int[mPacketSize];
            for (int i = 0; i < bitset.size(); i++) {
                count[i % mPacketSize] += 1;
                if (bitset.get(i)) {
                    accumulator[i % mPacketSize] += 1;
                }
            }

            for (int i = 0; i < mPacketSize; i++) {
                int diff = 2 * accumulator[i] - count[i];
                if (diff > 0) {
                    accumulator[i] = 1;
                } else if (diff == 0) {
                    accumulator[i] = Math.random() - 0.5 >= 0 ? 1 : 0;
                } else {
                    accumulator[i] = 0;
                }

            }

            for (int i = 0; i < bitset.size(); i++) {
                bitset.set(i, accumulator[i%mPacketSize] == 1);
            }

            Log.d(TAG, "Fixed: " + bitset.toString());
        }

        @Override
        protected String doInBackground(Void... voids) {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(mVideoFilePath);
            int frameCount = 0;
            List<ArrayList<Double>> records;
            ArrayList<Long> timestamps = new ArrayList<>();

            try {
                grabber.start();
                grabber.setTimestamp(0);

                // just drop the first frame
                Frame frame = grabber.grabFrame(false, true, true, false);
                double frameRate = grabber.getFrameRate();
                if (frameRate < 60.0) {
                    return "Video framerate too low!";
                }

                opencv_core.Size size = new opencv_core.Size(
                        DEST_SIZE.width() / mSliceX,
                        DEST_SIZE.height() / mSliceY);

                if (mStartTime != 0) {
                    grabber.setTimestamp(mStartTime);
                }

                ParseFrameTaskManager.setGlobalParams(mTransformMatrix, mSliceX, mSliceY);
                int MAX_THREAD_N = getNumberOfCores();
                ParseFrameTaskManager manager = new ParseFrameTaskManager(MAX_THREAD_N + 1);

                while (true) {
                    frame = grabber.grabFrame(false, true, true, false);
                    long timestamp = grabber.getTimestamp();
                    long delta = timestamp - mStartTime;

                    publishProgress((double)delta / mDuration);

                    if (delta > mDuration || frame == null) {
                        break;
                    }

                    opencv_core.Mat mat = mOpenCVFrameConverter.convert(frame);
                    if (mat == null) {
                        continue;
                    }

                    frameCount++;
                    timestamps.add(timestamp);

                    Log.d(TAG, "Parsing frame @" + timestamp);
                    manager.parseFrame(mat);
                }

                grabber.release();
                records = manager.collectResults();
            } catch (FrameGrabber.Exception e) {
                e.printStackTrace();
                throw new RuntimeException();
            }

            // dump result
            try {
                File fOut = new File(mDataDirectory, "raw_data.txt");
                FileOutputStream fileOutputStream = new FileOutputStream(fOut);
                for (int i=0; i<timestamps.size(); i++) {
                    fileOutputStream.write((timestamps.get(i) + "\t").getBytes());
                    for (int j=0; j<mSliceX*mSliceY; j++) {
                        fileOutputStream.write((records.get(j).get(i) + "\t").getBytes());
                    }
                    fileOutputStream.write("\n".getBytes());
                }

                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // decode
            double[] arrayTimestamp = new double[frameCount];
            for (int i=0; i<frameCount; i++) {
                arrayTimestamp[i] = timestamps.get(i) - timestamps.get(0);
            }

            // interpolate and resample
            int blockN = mSliceX * mSliceY;
            long timescale = timestamps.get(frameCount-1) - timestamps.get(0);
            int lengthInterpolated = (int)(timescale * Global.INTERMEDIATE_DATA_FPS / 1000000);
            double[] newX = new double[lengthInterpolated];
            double[][] newY = new double[blockN][lengthInterpolated];

            for (int i=0; i<lengthInterpolated; i++) {
                newX[i] = i * 1000000.0 / Global.INTERMEDIATE_DATA_FPS;
            }

            for (int i=0; i<blockN; i++) {
                List<Double> list = records.get(i);
                double[] arr = new double[frameCount];

                for (int j=0; j<frameCount; j++) {
                    arr[j] = list.get(j);
                }

                UnivariateInterpolator interpolator = new SplineInterpolator();
                UnivariateFunction func = interpolator.interpolate(arrayTimestamp, arr);
                for (int j=0; j<lengthInterpolated; j++) {
                    newY[i][j] = func.value(newX[j]);
                }
            }

            try {
                File fOut = new File(mDataDirectory, "resampled.txt");
                FileOutputStream fileOutputStream = new FileOutputStream(fOut);
                for (int i=0; i<lengthInterpolated; i++) {
                    fileOutputStream.write((newX[i] + "\t").getBytes());
                    for (int j=0; j<blockN; j++) {
                        fileOutputStream.write((newY[j][i] + "\t").getBytes());
                    }
                    fileOutputStream.write("\n".getBytes());
                }

                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            int shift = findShift(newY[0]);
            Log.d(TAG, "Shift: " + shift);

            int windowSize = Global.INTERMEDIATE_DATA_FPS / Global.DATARATE;
            int freqZero = windowSize / (Global.INTERMEDIATE_DATA_FPS / Global.BIT_ZERO_FREQ);
            int freqOne = windowSize / (Global.INTERMEDIATE_DATA_FPS / Global.BIT_ONE_FREQ);
            ArrayList<Boolean> bitBuffer = new ArrayList<>();

            /*StandardSignalSource source0 = new StandardSignalSource(true);
            StandardSignalSource source1 = new StandardSignalSource(false);
            double[] stdSignal0 = new double[windowSize];
            double[] stdSignal1 = new double[windowSize];

            for (int k=0; k<windowSize; k++) {
                stdSignal0[k] = source0.next();
                stdSignal1[k] = source1.next();
            }*/

            for (int start = shift; start + windowSize < lengthInterpolated; start += windowSize) {
                StringBuilder stringBuilder = new StringBuilder();

                for (int pos = 1; pos < mSliceX*mSliceY; pos++) {
                    double[] window = Arrays.copyOfRange(newY[pos], start, start + windowSize);
                    double mean = StatUtils.mean(window);

                    for (int i = 0; i < windowSize; i++) {
                        window[i] -= mean;
                    }

                    /*double sum0 = MathArrays.linearCombination(window, stdSignal0) / StatUtils.sum(stdSignal0);
                    double sum1 = MathArrays.linearCombination(window, stdSignal1) / StatUtils.sum(stdSignal1);
                    boolean bit = sum0 < sum1;
                    bitBuffer.add(bit);

                    stringBuilder.append(bit ? 1 : 0);*/

                    opencv_core.Mat mat = new opencv_core.Mat(window);
                    opencv_core.Mat result = new opencv_core.Mat();
                    opencv_core.dft(mat, result, opencv_core.DFT_COMPLEX_OUTPUT, 0);
                    DoubleIndexer indexer = result.createIndexer();

                    double zeroCompo = Math.hypot(indexer.get(freqZero, 0), indexer.get(freqZero, 1));
                    double oneCompo = Math.hypot(indexer.get(freqOne, 0), indexer.get(freqOne, 1));
                    boolean bit = zeroCompo < oneCompo;
                    bitBuffer.add(bit);

                    stringBuilder.append(bit ? 1 : 0);
                }

                Log.d(TAG, "Bits: " + stringBuilder);
            }

            // dump result
            try {
                File fOut = new File(mDataDirectory, "bits.txt");
                FileOutputStream fileOutputStream = new FileOutputStream(fOut);
                for (int i=0; i<bitBuffer.size(); i++) {
                    fileOutputStream.write((bitBuffer.get(i) ? "1" : "0").getBytes());
                }
                fileOutputStream.write("\n".getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            BitSet receivedBitSet = new BitSet(bitBuffer.size());
            for (int i=0; i<bitBuffer.size(); i++) {
                receivedBitSet.set(i, bitBuffer.get(i));
            }

            tryFixError(receivedBitSet);
            ArrayList<byte[]> decodedBitSet = decodeBits(receivedBitSet);

            try {
                for (byte[] bytes: decodedBitSet) {
                    String str = new String(bytes, "ASCII");
                    decodedStrings.add(str);
                    Log.d(TAG, "Decoded string: " + str);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Double... progress) {
            progressDialog.setProgress((int)(progress[0] * 100));
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();

            if (result == null) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Result")
                        //.setMessage("Hello, World!")
                        .setMessage(TextUtils.join(" | ", decodedStrings))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Error")
                        .setMessage(result)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            setupButtons();
            mPhotoView.setImageBitmap(mDisplayBitmap = Mat2Bitmap(mExampleImage));
            mButtonApplyOnClickListener.onClick(null);
        }
    }

    final Button.OnClickListener mButtonSaveOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            mButtonVideo.setEnabled(false);
            mButtonOpenFile.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonSave.setEnabled(false);
            int cols = Integer.parseInt(mEditCols.getText().toString());
            int rows = Integer.parseInt(mEditRows.getText().toString());

            String stringPacketSize = mEditPacketSize.getText().toString();
            int packetSize;

            if (stringPacketSize.length() > 0) {
                packetSize = Integer.parseInt(stringPacketSize);
            } else {
                packetSize = 0;
            }

            mDisplayBitmap.recycle();
            new BackgroundTask(mVideoPath, cols, rows, packetSize).execute();
        }
    };

    final PhotoViewAttacher.OnPhotoTapListener mTapListener = new PhotoViewAttacher.OnPhotoTapListener() {
        @Override
        public void onPhotoTap(View arg0, float x, float y) {
            if (y < 0.5) {
                mPoints[x<0.5 ? 0:1] = new opencv_core.Point2f(x, y);
            } else {
                mPoints[x>0.5 ? 2:3] = new opencv_core.Point2f(x, y);
            }

            opencv_core.Mat dis = mExampleImage.clone();
            for (opencv_core.Point2f p: mPoints) {
                if (p != null) {
                    Float xx = p.x() * dis.cols();
                    Float yy = p.y() * dis.rows();
                    opencv_imgproc.circle(
                            dis, new opencv_core.Point(xx.intValue(), yy.intValue()), 6,
                            new opencv_core.Scalar(0, 0, 255, 0), -1, opencv_core.LINE_8, 0);
                }
            }

            mPhotoView.setImageBitmap(mDisplayBitmap = Mat2Bitmap(dis));
            mAttacher.update();
        }

        @Override
        public void onOutsidePhotoTap() {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mButtonVideo = (Button)findViewById(R.id.buttonVideo);
        mButtonSave = (Button)findViewById(R.id.buttonSave);
        mButtonOpenFile = (Button)findViewById(R.id.buttonOpenFile);
        mButtonApply = (Button)findViewById(R.id.buttonApply);
        mPhotoView = (PhotoView)findViewById(R.id.photoView);
        mEditCols = (EditText)findViewById(R.id.editCols);
        mEditRows = (EditText)findViewById(R.id.editRows);
        mEditCols = (EditText)findViewById(R.id.editCols);
        mEditPacketSize = (EditText)findViewById(R.id.editPacketSize);

        mButtonVideo.setOnClickListener(mButtonVideoOnClickListener);
        mButtonOpenFile.setOnClickListener(mButtonOpenFileOnClickListener);
        mButtonSave.setOnClickListener(mButtonSaveOnClickListener);
        mButtonApply.setOnClickListener(mButtonApplyOnClickListener);

        mAttacher = new PhotoViewAttacher(mPhotoView);
        mAttacher.setOnPhotoTapListener(mTapListener);
        mAttacher.setMinimumScale(0.1F);
        mAttacher.setMaximumScale(120.0F);

        mPoints = new opencv_core.Point2f[4];
        mAndroidFrameConverter = new AndroidFrameConverter();
        mOpenCVFrameConverter = new OpenCVFrameConverter.ToMat();

        setupButtons();

        mDataDirectory = new File(Environment.getExternalStorageDirectory(), DIRNAME);
        if (!mDataDirectory.exists()) {
            mDataDirectory.mkdir();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTION_TAKE_VIDEO: {
                if (resultCode == RESULT_OK) {
                    handleCameraVideo(data);
                }
                break;
            }
            case ACTION_OPEN_VIDEO_FILE: {
                if (resultCode == RESULT_OK) {
                    handleCameraVideo(data);
                }
                break;
            }
        }
    }

    private void handleCameraVideo(Intent intent) {
        Uri videoUri = intent.getData();
        mVideoPath = RealPathUtils.getPath(this, videoUri);
        Log.d(TAG, "Video location: " + mVideoPath);
        mGrabber = new FFmpegFrameGrabber(mVideoPath);

        try {
            mGrabber.start();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        }

        long videoLength = mGrabber.getLengthInTime();
        Log.d(TAG, "Video Length: " + videoLength);

        final long defaultStart, defaultDuration;
        if (videoLength > 4000000) {
            defaultStart = videoLength / 2;
            defaultDuration = 2000000;
        } else {
            defaultStart = 500000;
            defaultDuration = 2000000;
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_position, null);

        final EditText mEditStartTime = (EditText) dialogView.findViewById(R.id.editStartTime);
        mEditStartTime.setHint("" + defaultStart / 1000);
        final EditText mEditDuration = (EditText) dialogView.findViewById(R.id.editDuration);
        mEditDuration.setHint("" + defaultDuration / 1000);

        dialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String strStartTime = mEditStartTime.getText().toString();
                String strDuration = mEditDuration.getText().toString();

                if (strStartTime.length() > 0) {
                    mStartTime = Long.parseLong(strStartTime) * 1000;
                } else {
                    mStartTime = defaultStart;
                }

                if (strDuration.length() > 0) {
                    mDuration = Long.parseLong(strDuration) * 1000;
                } else {
                    mDuration = defaultDuration;
                }

                dialog.dismiss();
                displayExampleImage(mStartTime + mDuration / 2);
            }
        });

        dialog.setView(dialogView);
        dialog.show();

        setupButtons();
    }

    private void displayExampleImage(long time) {
        Frame frame;

        try {
            mGrabber.setTimestamp(time);
            frame = mGrabber.grabFrame(false, true, true, false);
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException();
        }

        mExampleImage = mOpenCVFrameConverter.convert(frame);
        Log.d(TAG, "Channels: " + mExampleImage.channels());
        Log.d(TAG, "Resolution: " + mExampleImage.cols() + "x" + mExampleImage.rows());
        mPhotoView.setImageBitmap(mDisplayBitmap = Mat2Bitmap(mExampleImage));
    }

    private void setupButtons() {
        mButtonOpenFile.setEnabled(true);
        mButtonVideo.setEnabled(true);
        mButtonApply.setEnabled(mVideoPath != null);
        mButtonSave.setEnabled(mVideoPath != null);
    }

    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static Bitmap Mat2Bitmap(opencv_core.Mat mat) {
        // slow but reliable
        AndroidFrameConverter converterToBitmap = new AndroidFrameConverter();
        OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();

        return converterToBitmap.convert(converterToMat.convert(mat));
    }

    private static final int ESCAPE_LENGTH = 6;
    private static byte[] SPECIAL_CHARS = {' ',  ',', '.', '?'};

    // start pattern: 0b0111111
    // (de)escape: 0b111110 -> 0b11111
    private ArrayList<byte[]> decodeBits(BitSet input) {
        ArrayList<Boolean> decoded = new ArrayList<>();
        int oneCount = 0;

        for (int i=0; i<input.size(); i++) {
            boolean bit = input.get(i);
            if (bit) {  // bit == 1
                oneCount++;
                decoded.add(true);

                if (oneCount == ESCAPE_LENGTH) {  // start pattern
                    while (oneCount-- > 0) {
                        decoded.remove(decoded.size()-1);
                    }

                    if (decoded.size() > 0) {
                        decoded.remove(decoded.size()-1);
                    }

                    decoded.add(null);
                }
            } else {  // bit == 0
                //if (oneCount != ESCAPE_LENGTH - 1) {  // escape
                    decoded.add(false);
                //}
                oneCount = 0;
            }
        }

        int delimiter;
        delimiter = decoded.indexOf(null);
        if (delimiter >= 0) {
            int toRemove = delimiter % 5;
            while (toRemove-- > 0) {
                decoded.remove(0);
            }
        }

        if (decoded.get(0) == null) {
            decoded.remove(0);
        }

        if (decoded.get(decoded.size()-1) != null) {
            decoded.add(null);
        }

        ArrayList<byte[]> result = new ArrayList<>();
        while ((delimiter = decoded.indexOf(null)) >= 0) {
            //byte[] bytes = new byte[(delimiter + 7) / 8];
            byte[] bytes = new byte[(delimiter + 4) / 5];

            for (int i=0; i<delimiter; i++) {
                if (decoded.get(0)) {
                    //bytes[i / 8] |= 1 << (i%8);
                    bytes[i / 5] |= 1 << (i%5);
                }
                decoded.remove(0);
            }

            decoded.remove(0);
            for (int i=0; i<bytes.length; i++) {
                if (bytes[i] >= 28) {
                    bytes[i] = SPECIAL_CHARS[bytes[i] - 28];
                } else if (bytes[i] != 0) {
                    bytes[i] += 96;
                }
            }
            result.add(bytes);
        }

        return result;
    }
}