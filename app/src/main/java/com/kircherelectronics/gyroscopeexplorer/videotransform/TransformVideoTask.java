package com.kircherelectronics.gyroscopeexplorer.videotransform;

import android.app.Activity;
import android.app.ProgressDialog;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.AsyncTask;
import android.util.Log;

import com.kircherelectronics.gyroscopeexplorer.R;
import com.kircherelectronics.gyroscopeexplorer.activity.ParseDataActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TransformVideoTask extends AsyncTask<String , Integer , Void> {
    private static final String TAG = "TransformVideoTask";
    private static final boolean VERBOSE = false;
    private static final boolean WORK_AROUND_BUGS = false;
    private static final String MIME_TYPE = "video/avc";
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;

    private ProgressDialog barProgressDialog;
    private final CharSequence DialogTitle = "Processing";
    private final CharSequence DialogMessage = "blurring video...";
    private Activity parentActivity = null;
    private long inputFileSize = 0;
    private int index = 0;
    private String userName = null;
    private ParseDataActivity.TaskDelegate delegate = null;

    public TransformVideoTask(Activity parent_activity, String userName, int index, ParseDataActivity.TaskDelegate delegate) {
        this.parentActivity = parent_activity;
        this.index = index;
        this.userName = userName;
        this.delegate = delegate;
    }

    @Override
    protected Void doInBackground(String... filename) {
        try {
            transformFile(filename[0]);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPreExecute() {
        barProgressDialog = new ProgressDialog(parentActivity);

        barProgressDialog.setTitle(DialogTitle);
        String msg = null;
        if (index <= 16)
            msg = DialogMessage + " " + userName + ": Area_" + index;
        else
            msg = DialogMessage + " " + userName + ": Area_free";

        barProgressDialog.setMessage(msg);
        barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        barProgressDialog.setProgress(0);
        barProgressDialog.setMax(100);
        barProgressDialog.show();
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(Void v) {
        barProgressDialog.dismiss();
        if (null != delegate) {
            delegate.taskCompletionResult(index);
        }
        super.onPostExecute(v);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        barProgressDialog.setProgress(values[0]);
        super.onProgressUpdate(values);
    }

    private void transformFile(String filename) throws IOException {
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;

        MediaExtractor extractor = null;
        MediaMuxer mediaMuxer = null;

        try {
            //========== Extractor ==========
            File inputFile = new File(filename);   // must be an absolute path
            inputFileSize = inputFile.length();
            Log.d(TAG, "Video size is " + inputFileSize + " bytes");
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

            MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
            Log.d(TAG, "Video resolution is " + inputFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                    inputFormat.getInteger(MediaFormat.KEY_HEIGHT));

            //========== Encoder ==========
//            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_HEIGHT, VIDEO_WIDTH);     // eason: flip width/height for PnG selfie video
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
//                    inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
//            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
//                    inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
//            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
//                    inputFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1*1000*1000);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

//            outputData.setMediaFormat(outputFormat);
            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            //========== Decoder ==========

            // Could use width/height from the MediaFormat to get full-size frames.
            outputSurface = new OutputSurface();
            String frag_shader = TextResourceReader.readTextFileFromResource(parentActivity, R.raw.gaussian_5);
            outputSurface.changeFragmentShader(frag_shader);

            // Create a MediaCodec decoder, and configure it with the MediaFormat from the
            // extractor.  It's very important to use the format from the extractor because
            // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
//            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
            decoder.start();

            //========== Muxer ==========
            String output_filename = filename.replace(".mp4", "_blur.mp4");
            mediaMuxer = new MediaMuxer(output_filename, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);


            doTransform(extractor, trackIndex, decoder, outputSurface, inputSurface, encoder, mediaMuxer);
            Log.i(TAG, "transformation done");
        } finally {
            // release everything we grabbed
            if (outputSurface != null) {
                outputSurface.release();
                outputSurface = null;
            }
            if (inputSurface != null) {
                inputSurface.release();
                inputSurface = null;
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
                encoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            }
        }
    }

    private void doTransform(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
                             OutputSurface outputSurface, InputSurface inputSurface, MediaCodec encoder,
                             MediaMuxer muxer) throws IOException {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;
        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;
        int outputVideoTrackIndex = -1;
        long extract_byte = 0;

        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "edit loop");
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
                        publishProgress((int) ((extract_byte / (float)inputFileSize) * 100));

                        inputChunk++;
                        extractor.advance();
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }
            // Assume output is available.  Loop until both assumptions are false.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                // Start by draining any pending output from the encoder.  It's important to
                // do this before we try to stuff any more data in.
                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from encoder available");
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();

                    Log.d(TAG, "encoder output resolution is " + newFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                            newFormat.getInteger(MediaFormat.KEY_HEIGHT));
                    outputVideoTrackIndex = muxer.addTrack(newFormat);
                    muxer.start();

                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    // Write the data to the output "file".
                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        //TODO: write to output mp4 file
//                        outputData.addChunk(encodedData, info.flags, info.presentationTimeUs);
                        muxer.writeSampleData(outputVideoTrackIndex, encodedData, info);

                        outputCount++;
                        if (VERBOSE)
                            Log.d(TAG, "encoder output " + info.size + " bytes");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }
                // Encoder is drained, check to see if we've got a new frame of output from
                // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                // but we still get information through BufferInfo.)
                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from decoder available");
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        //decoderOutputBuffers = decoder.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "decoder output buffers changed (we don't care)");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // expected before first buffer of data
                        MediaFormat newFormat = decoder.getOutputFormat();
                        Log.d(TAG, "decoder output resolution is " + newFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                                newFormat.getInteger(MediaFormat.KEY_HEIGHT));
                        if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                    } else if (decoderStatus < 0) {
                        Log.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: "+decoderStatus);
                    } else { // decoderStatus >= 0
                        if (VERBOSE) Log.d(TAG, "surface decoder given buffer "
                                + decoderStatus + " (size=" + info.size + ")");
                        // The ByteBuffers are null references, but we still get a nonzero
                        // size for the decoded data.
                        boolean doRender = (info.size != 0);
                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            // This waits for the image and renders it after it arrives.
                            if (VERBOSE) Log.d(TAG, "awaiting frame");
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage();
                            // Send it to the encoder.
                            inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                            if (VERBOSE) Log.d(TAG, "swapBuffers");
                            inputSurface.swapBuffers();
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // forward decoder EOS to encoder
                            if (VERBOSE) Log.d(TAG, "signaling input EOS");
                            if (WORK_AROUND_BUGS) {
                                // Bail early, possibly dropping a frame.
                                return;
                            } else {
                                encoder.signalEndOfInputStream();
                            }
                        }
                    }
                }
            }
        }
        if (inputChunk != outputCount) {
//            throw new RuntimeException("frame lost: " + inputChunk + " in, " +
//                    outputCount + " out");
            Log.e(TAG, "frame lost: " + inputChunk + " in, " +
                    outputCount + " out");
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
}
