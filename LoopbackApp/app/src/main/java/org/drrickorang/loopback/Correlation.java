package org.drrickorang.loopback;

import android.os.Trace;
import android.util.Log;

/**
 * Created by rago on 5/8/15.
 */
public class Correlation {

    private int mBlockSize = 4096;
    private int mSamplingRate = 44100;
    private double [] mDataDownsampled = new double [mBlockSize];
    private double [] mDataAutocorrelated = new double[mBlockSize];

    public double mEstimatedLatencySamples = 0;
    public double mEstimatedLatencyMs = 0;



    public void init(int blockSize, int samplingRate) {
        mBlockSize = blockSize;
        mSamplingRate = samplingRate;
    }

    public boolean computeCorrelation(double [] data, int samplingRate) {
        boolean status = false;
        log("Started Auto Correlation for data with " + data.length + " points");
        mSamplingRate = samplingRate;

        downsampleData(data, mDataDownsampled);

        //correlation vector
        autocorrelation(mDataDownsampled, mDataAutocorrelated);


        int N = data.length; //all samples available
        double groupSize =  (double) N / mBlockSize;  //samples per downsample point.

        double maxValue = 0;
        int maxIndex = -1;

        double minLatencyMs = 8; //min latency expected. This algorithm should be improved.
        int minIndex = (int)(0.5 + minLatencyMs * mSamplingRate / (groupSize*1000));

        //find max
        for(int i=minIndex; i<mDataAutocorrelated.length; i++) {
           if(mDataAutocorrelated[i] > maxValue) {
               maxValue = mDataAutocorrelated[i];
               maxIndex = i;
           }
        }

        log(String.format(" Maxvalue %f, max Index : %d/%d (%d)  minIndex=%d",maxValue, maxIndex, mDataAutocorrelated.length, data.length, minIndex));



        mEstimatedLatencySamples = maxIndex*groupSize;

        mEstimatedLatencyMs = mEstimatedLatencySamples *1000/mSamplingRate;

        log(String.format(" latencySamples: %.2f  %.2f ms", mEstimatedLatencySamples, mEstimatedLatencyMs));

        status = true;

        return status;
    }

    private boolean downsampleData(double [] data, double [] dataDownsampled) {

        boolean status = false;
       // mDataDownsampled = new double[mBlockSize];
        for (int i=0; i<mBlockSize; i++) {
            dataDownsampled[i] = 0;
        }

        int N = data.length; //all samples available
        double groupSize =  (double) N / mBlockSize;

        int currentIndex = 0;
        double nextGroup = groupSize;
        Trace.beginSection("Processing Correlation");
        for (int i = 0; i<N && currentIndex<mBlockSize; i++) {

            if(i> nextGroup) { //advanced to next group.
                currentIndex++;
                nextGroup += groupSize;
            }

            if (currentIndex>=mBlockSize) {
                break;
            }
            dataDownsampled[currentIndex] += Math.abs(data[i]);
        }
        Trace.endSection();


        status = true;

        return status;
    }

    private boolean autocorrelation(double [] data, double [] dataOut) {
        boolean status = false;

        double sumsquared = 0;
        int N = data.length;
        for(int i=0; i<N; i++) {
            double value = data[i];
            sumsquared += value*value;
        }

        //dataOut = new double[N];

        if(sumsquared>0) {
            //correlate (not circular correlation)
            for (int i = 0; i < N; i++) {
                dataOut[i] = 0;
                for (int j = 0; j < N - i; j++) {

                    dataOut[i] += data[j] * data[i + j];
                }
                dataOut[i] = dataOut[i] / sumsquared;
            }
            status = true;
        }

        return status;
    }

    private static void log(String msg) {
        Log.v("Recorder", msg);
    }
}
