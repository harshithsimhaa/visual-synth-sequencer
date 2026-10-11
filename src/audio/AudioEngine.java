package audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Real-time synth engine.
 *
 * One mixer thread owns ONE SourceDataLine and renders every active voice
 * block-by-block (~11 ms), so notes mix together instead of each opening
 * its own audio line. Each voice has its own SweepFilter instance.
 */
public class AudioEngine {

    public enum Waveform {
        SINE(1.0), TRIANGLE(1.0), SAWTOOTH(0.7), SQUARE(0.6), NOISE(0.5);

        private final double defaultGain;

        Waveform(double gain) {
            this.defaultGain = gain;
        }

        public double getDefaultGain() {
            return defaultGain;
        }
    }

    // ---- Audio format & tuning ------------------------------------------
    private static final int SAMPLE_RATE = 44100;
    private static final int BYTES_PER_FRAME = 2;      // 16-bit mono
    private static final int MAX_VOICES = 8;            // oldest voice is stolen beyond this
    private static final int BLOCK_FRAMES = 512;        // render block (~11.6 ms)
    private static final int LINE_BUFFER_MS = 60;       // lower = less latency, higher = safer
    private static final int FADE_FRAMES = (int) (SAMPLE_RATE * 0.01); // 10 ms anti-click fade
    private static final double MASTER_GAIN = 0.5;      // headroom when voices stack

    private static final AudioFormat FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        SAMPLE_RATE,
        16,
        1,
        BYTES_PER_FRAME,
        SAMPLE_RATE,
        true // big-endian
    );

    private static final ConcurrentLinkedQueue<Voice> pending = new ConcurrentLinkedQueue<>();
    private static volatile boolean running = true;
    private static final Thread mixerThread = new Thread(AudioEngine::mixLoop, "AudioEngine-Mixer");

    static {
        mixerThread.setDaemon(true);
        mixerThread.start();
    }

    private AudioEngine() {
        // Utility class
    }

    // ---- Public API -----------------------------------------------------

    public static void playTone(double frequency, double durationMs, Waveform waveform, double volume) {
        if (!running || waveform == null) return;
        // Reject NaN, non-positive and above-Nyquist values instead of crashing later
        if (!(frequency > 0) || frequency >= SAMPLE_RATE / 2.0) return;
        if (!(durationMs > 0) || Double.isNaN(volume)) return;

        volume = Math.max(0.0, Math.min(1.0, volume));
        pending.add(new Voice(waveform, frequency, durationMs, volume));
    }

    public static void playTone(double frequency, double durationMs, Waveform waveform) {
        if (waveform == null) return;
        playTone(frequency, durationMs, waveform, waveform.getDefaultGain());
    }

    public static void shutdown() {
        running = false;
        mixerThread.interrupt();
    }

    // ---- Mixer thread ----------------------------------------------------

    private static void mixLoop() {
        List<Voice> active = new ArrayList<>();
        float[] mix = new float[BLOCK_FRAMES];
        byte[] out = new byte[BLOCK_FRAMES * BYTES_PER_FRAME];
        int lineBufferBytes = (SAMPLE_RATE * LINE_BUFFER_MS / 1000) * BYTES_PER_FRAME;
        SourceDataLine line = null;

        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, lineBufferBytes);
            line.start();

            while (running) {
                // Pick up newly triggered notes
                Voice v;
                while ((v = pending.poll()) != null) {
                    if (active.size() >= MAX_VOICES) active.remove(0); // voice stealing
                    active.add(v);
                }

                // Render and mix all voices; finished voices are removed
                Arrays.fill(mix, 0f);
                active.removeIf(voice -> voice.render(mix));

                // Soft-clip (tanh) and convert to 16-bit big-endian PCM
                for (int i = 0; i < BLOCK_FRAMES; i++) {
                    double s = Math.tanh(mix[i] * MASTER_GAIN);
                    short pcm = (short) (s * 32767.0);
                    out[i * 2]     = (byte) ((pcm >> 8) & 0xFF);
                    out[i * 2 + 1] = (byte) (pcm & 0xFF);
                }

                // Blocks when the line buffer is full, which paces the loop
                line.write(out, 0, out.length);
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("AudioEngine: could not open audio line: " + e.getMessage());
        } finally {
            running = false;
            pending.clear();
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    // ---- Voice -----------------------------------------------------------

    private static final class Voice {
        private final Waveform waveform;
        private final double phaseInc;
        private final double volume;
        private final int totalFrames;
        private final int fadeFrames;
        private final SweepFilter filter = new SweepFilter(SAMPLE_RATE);

        private double phase = 0.0;
        private int frame = 0;

        Voice(Waveform waveform, double frequency, double durationMs, double volume) {
            this.waveform = waveform;
            this.phaseInc = frequency / SAMPLE_RATE;
            this.volume = volume;
            this.totalFrames = Math.max(1, (int) (SAMPLE_RATE * (durationMs / 1000.0)));
            // Short notes get a shorter fade so fade-in and fade-out never overlap
            this.fadeFrames = Math.min(FADE_FRAMES, totalFrames / 2);
        }

        /** Adds this voice into the mix buffer. Returns true when the voice has finished. */
        boolean render(float[] mix) {
            for (int i = 0; i < mix.length && frame < totalFrames; i++, frame++) {
                double sample = generateSample();
                sample = (double) filter.process((float) sample);

                double envelope = 1.0;
                if (fadeFrames > 0) {
                    int edgeDistance = Math.min(frame, totalFrames - frame);
                    envelope = Math.min(1.0, (double) edgeDistance / fadeFrames);
                }

                mix[i] += (float) (sample * volume * envelope);

                phase += phaseInc;
                if (phase >= 1.0) phase -= 1.0;
            }
            return frame >= totalFrames;
        }

        private double generateSample() {
            return switch (waveform) {
                case SINE -> Math.sin(2.0 * Math.PI * phase);
                case TRIANGLE -> 2.0 * Math.abs(2.0 * (phase - Math.floor(phase + 0.5))) - 1.0;
                case SAWTOOTH -> 2.0 * (phase - Math.floor(phase + 0.5));
                case SQUARE -> phase < 0.5 ? 1.0 : -1.0;
                case NOISE -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
            };
        }
    }
}