package sammamish.fixes;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copied from javazoom.jl.player.Player, with bug fixes
 */

// REVIEW: the audio device should not be opened until the
// first MPEG audio frame has been decoded.
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Player implements Closeable {

    /**
     * The MPEG audio bitstream.
     */
    // javac blank final bug.
    /*final*/ private final Bitstream bitstream;

    /**
     * The MPEG audio decoder.
     */
    /*final*/ private final Decoder decoder;

    /**
     * The AudioDevice the audio samples are written to.
     */
    private final AudioDevice audio;

    /**
     * Has the player been closed?
     */
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    /**
     * Creates a new <code>Player</code> instance.
     */
    public Player(InputStream stream) throws JavaLayerException {
        this(stream, null);
    }

    public Player(InputStream stream, AudioDevice device) throws JavaLayerException {
        bitstream = new Bitstream(stream);
        decoder = new Decoder();

        if (device != null) {
            audio = device;
        } else {
            FactoryRegistry r = FactoryRegistry.systemRegistry();
            audio = r.createAudioDevice();
        }
        audio.open(decoder);
    }

    public void play() throws JavaLayerException {
        play(Integer.MAX_VALUE);
    }

    /**
     * Plays a number of MPEG audio frames.
     *
     * @param frames The number of frames to play.
     * @return true if the last frame was played, or false if there are
     * more frames.
     */
    public boolean play(int frames) throws JavaLayerException {
        if (!isPlaying.compareAndSet(false, true)) {
            throw new IllegalStateException("Already playing");
        }

        boolean ret = true;

        while (frames-- > 0 && ret) {
            ret = decodeFrame();
        }

        if (!ret) {
            // last frame, ensure all data flushed to the audio device.
            audio.flush();
            close();
        }
        return ret;
    }

    /**
     * Cloases this player. Any audio currently playing is stopped
     * immediately.
     */
    @Override
    public void close() {
        if (isPlaying.get()
                && isClosing.compareAndSet(false, true)) {
            // this may fail, so ensure object state is set up before
            // calling this method.
            audio.close();
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
            }
        }
    }

    /**
     * Decodes a single frame.
     *
     * @return true if there are no more frames to decode, false otherwise.
     */
    private boolean decodeFrame() throws JavaLayerException {
        try {
            Header h = bitstream.readFrame();

            if (h == null)
                return false;

            // sample buffer set when decoder constructed
            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);

            audio.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        } catch (RuntimeException ex) {
            throw new JavaLayerException("Exception decoding audio frame", ex);
        }
/*
		catch (IOException ex)
		{
			System.out.println("exception decoding audio frame: "+ex);
			return false;
		}
		catch (BitstreamException bitex)
		{
			System.out.println("exception decoding audio frame: "+bitex);
			return false;
		}
		catch (DecoderException decex)
		{
			System.out.println("exception decoding audio frame: "+decex);
			return false;
		}
*/
        return true;
    }


}
