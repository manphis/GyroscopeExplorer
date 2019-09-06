package com.kircherelectronics.gyroscopeexplorer.task;

import android.app.Activity;
import android.app.ProgressDialog;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class ExtractVideoTask extends AsyncTask<String , Integer , Void> {
    private static final String TAG = "parsedata-ExtractTask";
    private Activity parentActivity;
    private ProgressDialog barProgressDialog;
    private final CharSequence DialogTitle = "Processing";
    private final CharSequence DialogMessage = "Wait to extract data...";
    private static final boolean VERBOSE = true;
    private File outputFile = null;
    private List nameList;
    private long fileSize = 0;

    public ExtractVideoTask(Activity parentActivity, File output_file, List name_list) {
        super();
        this.parentActivity = parentActivity;
        this.outputFile = output_file;
        this.nameList = name_list;
    }

    @Override
    protected Void doInBackground(String... filename) {
        Log.i(TAG, "doInBackground");
        try {
            extractMpegFrames(filename[0]);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPreExecute() {
        barProgressDialog = new ProgressDialog(parentActivity);

        barProgressDialog.setTitle(DialogTitle);
        barProgressDialog.setMessage(DialogMessage);
        barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        barProgressDialog.setProgress(0);
        barProgressDialog.setMax(100);
        barProgressDialog.show();
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(Void v) {
        barProgressDialog.dismiss();
        super.onPostExecute(v);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        barProgressDialog.setProgress(values[0]);
        super.onProgressUpdate(values);
    }

    private void extractMpegFrames(String filename) throws IOException {
        MediaCodec decoder = null;
        CodecOutputSurface outputSurface = null;
        MediaExtractor extractor = null;
        int saveWidth = 720;
        int saveHeight = 720;

        try {
            File inputFile = new File(filename);   // must be an absolute path
            fileSize = inputFile.length();
            Log.d(TAG, "Video size is " + fileSize + " bytes");
            // The MediaExtractor error messages aren't very useful.  Check to see if the input
            // file exists so we can throw a better one if it's not there.
            if (!inputFile.canRead()) {
                throw new FileNotFoundException("Unable to read " + inputFile);
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(inputFile.toString());
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + inputFile);
            }
            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            Log.d(TAG, "Video resolution is " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                    format.getInteger(MediaFormat.KEY_HEIGHT));

            // Could use width/height from the MediaFormat to get full-size frames.
            outputSurface = new CodecOutputSurface(saveWidth, saveHeight);

            // Create a MediaCodec decoder, and configure it with the MediaFormat from the
            // extractor.  It's very important to use the format from the extractor because
            // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, outputSurface.getSurface(), null, 0);
            decoder.start();

            doExtract(extractor, trackIndex, decoder, outputSurface);
        } finally {
            // release everything we grabbed
            if (outputSurface != null) {
                outputSurface.release();
                outputSurface = null;
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    private int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                return i;
            }
        }

        return -1;
    }

    private void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
                          CodecOutputSurface outputSurface) throws IOException {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int decodeCount = 0;
        long frameSaveTime = 0;
        long extract_byte = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
//            if (VERBOSE) Log.d(TAG, "loop");

            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        if (extractor.getSampleTrackIndex() != trackIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " +
                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);
//                        if (VERBOSE) Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" + chunkSize);
//                        Log.i(TAG, "total extract = " + extract_byte);

                        extract_byte += chunkSize;
                        publishProgress((int) ((extract_byte / (float)fileSize) * 100));

                        inputChunk++;
                        extractor.advance();
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    Log.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else { // decoderStatus >= 0
//                    if (VERBOSE) Log.d(TAG, "surface decoder given buffer " + decoderStatus +
//                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        outputDone = true;
                    }

                    boolean doRender = (info.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
//                        if (VERBOSE) Log.d(TAG, "awaiting decode of frame " + decodeCount);
                        outputSurface.awaitNewImage();
                        outputSurface.drawImage(true);

//                        if (decodeCount < 10) {
                            String name = "frame_" + nameList.get(decodeCount) + ".png";
                            File imageFile = new File(outputFile, name);
                            long startWhen = System.nanoTime();
                            outputSurface.saveFrame(imageFile.toString());
                            frameSaveTime += System.nanoTime() - startWhen;
//                        }
                        decodeCount++;
                    }
                }
            }
        }

        int numSaved = decodeCount;
        Log.d(TAG, "Saving " + numSaved + " frames took " +
                (frameSaveTime / numSaved / 1000) + " us per frame");
    }
}
