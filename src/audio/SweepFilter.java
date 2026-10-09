package audio;
public class SweepFilter {

    private final float sampleRate;
    private float low, band, high;
    private double lfoPhase = 0.0;

    private volatile float baseCutoff = 1000f;  
    private volatile float resonance  = 0.6f;   
    private volatile float lfoRate    = 0.5f;   
    private volatile float lfoDepth   = 2.0f;   
    private volatile boolean enabled  = true;

    private float cutoffSmooth;
    private float resonanceSmooth;
    private static final float SMOOTHING = 0.002f;

    public SweepFilter(float sampleRate) {
        this.sampleRate = sampleRate;
        this.cutoffSmooth = baseCutoff;
        this.resonanceSmooth = resonance;
    }

    public void setCutoff(float hz) { baseCutoff = clamp(hz, 30f, 8000f); }
    public void setResonance(float r) { resonance = clamp(r, 0f, 0.95f); }
    public void setLfoRate(float hz) { lfoRate = clamp(hz, 0f, 20f); }
    public void setLfoDepth(float octaves) { lfoDepth = clamp(octaves, 0f, 6f); }
    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    public void reset() {
        low = band = high = 0f;
        lfoPhase = 0.0;
    }

    public float process(float in) {
        if (!enabled) return in;

        cutoffSmooth    += (baseCutoff - cutoffSmooth)    * SMOOTHING;
        resonanceSmooth += (resonance  - resonanceSmooth) * SMOOTHING;

        float lfo = (float) Math.sin(2.0 * Math.PI * lfoPhase);
        lfoPhase += lfoRate / sampleRate;
        if (lfoPhase >= 1.0) lfoPhase -= 1.0;

        float cutoff = cutoffSmooth * (float) Math.pow(2.0, lfo * lfoDepth * 0.5f);
        cutoff = clamp(cutoff, 30f, sampleRate / 6f);   

        float f = 2f * (float) Math.sin(Math.PI * cutoff / sampleRate);
        float q = 2f * (1f - resonanceSmooth);

        low  += f * band;
        high  = in - low - q * band;
        band += f * high;

        if (Float.isNaN(low) || Float.isInfinite(low)) {
            reset();
            return 0f;
        }
        return clamp(low, -1f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}