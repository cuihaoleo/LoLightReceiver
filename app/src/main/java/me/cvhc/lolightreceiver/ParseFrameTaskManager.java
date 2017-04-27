package me.cvhc.lolightreceiver;


import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParseFrameTaskManager {

    private static class ResultPair {
        public int order;
        public double[] result;

        public ResultPair(int order, double[] result) {
            this.order = order;
            this.result = result;
        }
    }

    private static final opencv_core.Size DEST_SIZE = new opencv_core.Size(960, 540);
    private static final int GAUSSIAN_KERNEL_SIZE = 20;
    private static final int GAUSSIAN_KERNEL_SIGMA = 4;

    private static opencv_core.Mat KERNEL2D;
    private static opencv_core.Mat TRANSFORM_MATRIX;
    private static int SLICE_X, SLICE_Y;
    private static opencv_core.Size SLICE_SIZE;

    static void setGlobalParams(opencv_core.Mat transformMatrix, int sliceX, int sliceY) {
        TRANSFORM_MATRIX = transformMatrix;
        SLICE_X = sliceX;
        SLICE_Y = sliceY;
        SLICE_SIZE = new opencv_core.Size(DEST_SIZE.width() / sliceX, DEST_SIZE.height() / sliceY);

        opencv_core.Mat kernel1D = opencv_imgproc.getGaussianKernel(GAUSSIAN_KERNEL_SIZE, GAUSSIAN_KERNEL_SIGMA);
        KERNEL2D = new opencv_core.Mat();
        opencv_core.mulTransposed(kernel1D, KERNEL2D, false);
        opencv_core.copyMakeBorder(KERNEL2D, KERNEL2D, 2, 2, 2, 2, opencv_core.BORDER_CONSTANT);
        opencv_imgproc.resize(KERNEL2D, KERNEL2D, SLICE_SIZE, 0, 0, opencv_imgproc.INTER_LINEAR);
        kernel1D.release();
    }

    private ExecutorService mExecutor;
    private CompletionService<ResultPair> mCompletionService;
    private List<ArrayList<Double>> mRecords;
    private int mCount = 0;
    private int mCoreSize;
    private int mCurrentSize = 0;

    public ParseFrameTaskManager(int coreSize) {
        mCoreSize = coreSize;

        mExecutor = Executors.newFixedThreadPool(coreSize);
        mCompletionService = new ExecutorCompletionService<>(mExecutor);

        mRecords = new ArrayList<>(SLICE_X * SLICE_Y);
        for (int i=0; i<SLICE_X*SLICE_Y; i++) {
            mRecords.add(new ArrayList<Double>());
        }
    }

    private void takeOne() {
        Future<ResultPair> future;
        ResultPair pair;

        try {
            future = mCompletionService.take();
            mCurrentSize--;
            pair = future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException();
        }

        for (int i=0; i<mRecords.size(); i++) {
            mRecords.get(i).set(pair.order, pair.result[i]);
        }
    }

    public void parseFrame(opencv_core.Mat image) {
        while (mCurrentSize >= mCoreSize) {
            takeOne();
        }

        mCompletionService.submit(new ParseFrameRunnable(image.clone(), mCount++));
        mCurrentSize++;
        for (int i=0; i<mRecords.size(); i++) {
            mRecords.get(i).add(0.0);
        }
    }

    public List<ArrayList<Double>> collectResults() {
        while (mCurrentSize > 0) {
            takeOne();
        }

        mExecutor.shutdown();
        return mRecords;
    }

    private class ParseFrameRunnable implements Callable<ResultPair> {
        private opencv_core.Mat mImage;
        private int mOrder;

        ParseFrameRunnable(opencv_core.Mat image, int order) {
            mImage = image;
            mOrder = order;
        }

        @Override
        public ResultPair call() throws Exception {
            if (TRANSFORM_MATRIX != null) {
                opencv_imgproc.warpPerspective(mImage, mImage, TRANSFORM_MATRIX, DEST_SIZE);
            } else {
                opencv_imgproc.resize(mImage, mImage, DEST_SIZE);
            }

            double[] result = new double[SLICE_X*SLICE_Y];
            int linearIndex = 0;
            for (int yi=0; yi<SLICE_Y; yi++) {
                for (int xi=0; xi<SLICE_X; xi++) {
                    opencv_core.Rect rect = new opencv_core.Rect(
                            xi * SLICE_SIZE.width(), yi * SLICE_SIZE.height(),
                            SLICE_SIZE.width(), SLICE_SIZE.height());

                    opencv_core.Mat part = new opencv_core.Mat(mImage, rect);
                    opencv_imgproc.cvtColor(part, part, opencv_imgproc.COLOR_BGR2GRAY);

                    part.convertTo(part, opencv_core.CV_64F);
                    opencv_core.MatExpr product = part.mul(KERNEL2D);

                    opencv_core.Scalar meanBrightness = opencv_core.mean(product.asMat());
                    result[linearIndex++] = meanBrightness.get(0);
                    //records.get(linearIndex++).add(meanBrightness.get(0));

                    product.close();
                    part.close();
                }
            }

            mImage.close();
            return new ResultPair(mOrder, result);
        }
    }
}