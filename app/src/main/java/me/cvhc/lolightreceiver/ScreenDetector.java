package me.cvhc.lolightreceiver;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgcodecs;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

public class ScreenDetector {
    private static final String TAG = ScreenDetector.class.getSimpleName();
    Context context;
    String workingDir;
    String execPath;

    public ScreenDetector(Context c) {
        context = c;

        String[] abiList;
        AssetManager assetManager = context.getResources().getAssets();
        InputStream inputStream = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abiList = Build.SUPPORTED_ABIS;
        } else {
            abiList = new String[] { Build.CPU_ABI };
        }

        for (String abi: abiList) {
            try {
                inputStream = assetManager.open(abi + "/screen_detector");
            } catch (IOException e) {
                inputStream = null;
                continue;
            }

            Log.d(TAG, "Use ABI: " + abi);
            break;
        }

        if (inputStream == null) {
            throw new RuntimeException();
        }

        workingDir = context.getCacheDir().getPath();
        execPath = workingDir + "/screen_detector";

        File outFile = new File(execPath);
        OutputStream outputStream;

        try {
            outputStream = new FileOutputStream(outFile);

            int read;
            byte[] buffer = new byte[4096];
            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
            }

            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        outFile.setExecutable(true);
        Log.d(TAG, "Executable: " + execPath);
    }

    public opencv_core.Point2f[] detect(opencv_core.Mat image) {
        File cacheDir = context.getCacheDir();
        File imgFile;

        try {
            imgFile = File.createTempFile("image", ".png", cacheDir);
        } catch (IOException e) {
            throw new RuntimeException();
        }

        Log.d(TAG, "Image: " + imgFile.getPath());

        opencv_imgcodecs.imwrite(imgFile.getPath(), image);

        String[] cmd = {execPath, imgFile.getAbsolutePath()};
        InputStream inputStream;
        Process process;

        try {
            process = Runtime.getRuntime().exec(cmd, null, cacheDir);
            process.waitFor();
            inputStream = process.getInputStream();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException();
        }

        Scanner scanner = new Scanner(inputStream);

        opencv_core.Point2f[] points = new opencv_core.Point2f[4];
        int width = image.cols();
        int height = image.rows();
        points[0] = new opencv_core.Point2f(
                1.0f * scanner.nextInt() / width,
                1.0f * scanner.nextInt() / height);
        points[1] = new opencv_core.Point2f(
                1.0f * scanner.nextInt() / width,
                1.0f * scanner.nextInt() / height);
        points[2] = new opencv_core.Point2f(
                1.0f * scanner.nextInt() / width,
                1.0f * scanner.nextInt() / height);
        points[3] = new opencv_core.Point2f(
                1.0f * scanner.nextInt() / width,
                1.0f * scanner.nextInt() / height);

        for (opencv_core.Point2f p: points) {
            Log.d(TAG, "Point: " + p.x() + ", " + p.y());
        }

        try {
            scanner.close();
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        return points;
    }
}
