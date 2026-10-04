package app;

import javax.swing.*;
import java.awt.*;

import audio.AudioEngine;
import model.PatternStorage;
import model.SequencerModel;
import ui.GridPanel;

public class MainApp extends JFrame {
    private final SequencerModel model;
    private final GridPanel gridPanel;
    private Timer timer;
    private JLabel bpmLabel;
    private JSlider bpmSlider;
    
    private int currentStep = -1;
    private int bpm = 120;
    private boolean isSquareWave = false;

    public MainApp() {
        setTitle("Visual Synth Sequencer — Executive Control Panel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Initialize core model and visual panel
        model = new SequencerModel();
        gridPanel = new GridPanel(model);
        add(gridPanel, BorderLayout.CENTER);

        // Build top control toolbar
        JToolBar toolbar = createToolBar();
        add(toolbar, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(null);
        setResizable(false);

        initTimingEngine();
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // Transport Controls
        JButton playBtn = new JButton("▶ Play");
        JButton stopBtn = new JButton("⏹ Stop");

        playBtn.addActionListener(e -> startSequencer());
        stopBtn.addActionListener(e -> stopSequencer());

        // BPM Controls
        bpmLabel = new JLabel("BPM: " + bpm);
        bpmLabel.setPreferredSize(new Dimension(70, 20));
        bpmSlider = new JSlider(60, 240, bpm);
        bpmSlider.setPreferredSize(new Dimension(150, 25));
        bpmSlider.addChangeListener(e -> {
            bpm = bpmSlider.getValue();
            bpmLabel.setText("BPM: " + bpm);
            try {
                model.setBpm(bpm);
            } catch (IllegalArgumentException ignored) {
            }
            updateTimerSpeed();
        });

        // Waveform Selection Toggle
        JToggleButton waveToggle = new JToggleButton("Waveform: Sine");
        waveToggle.addActionListener(e -> {
            isSquareWave = waveToggle.isSelected();
            waveToggle.setText(isSquareWave ? "Waveform: Square" : "Waveform: Sine");
        });

        // Save & Load File Pattern Controls
        JButton saveBtn = new JButton("Save Pattern");
        JButton loadBtn = new JButton("Load Pattern");

        saveBtn.addActionListener(e -> handleSavePattern());
        loadBtn.addActionListener(e -> handleLoadPattern());

        // Assemble Controls into Toolbar
        toolBar.add(playBtn);
        toolBar.add(stopBtn);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(bpmLabel);
        toolBar.add(bpmSlider);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(waveToggle);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(saveBtn);
        toolBar.add(loadBtn);

        return toolBar;
    }

    private void initTimingEngine() {
        int delay = calculateDelay(bpm);
        timer = new Timer(delay, e -> advanceStep());
    }

    private void updateTimerSpeed() {
        if (timer != null) {
            timer.setDelay(calculateDelay(bpm));
        }
    }

    private int calculateDelay(int bpmValue) {
        // Calculates millisecond interval per 16th note step: (60,000ms / BPM) / 4
        return (60000 / bpmValue) / 4;
    }

    private void startSequencer() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void stopSequencer() {
        if (timer.isRunning()) {
            timer.stop();
        }
        currentStep = -1;
        gridPanel.setPlayheadStep(currentStep);
        gridPanel.repaint();
    }

    private void advanceStep() {
        // Advance playhead step (0 to 15)
        currentStep = (currentStep + 1) % SequencerModel.COLS;
        gridPanel.setPlayheadStep(currentStep);
        gridPanel.repaint();

        // Query active cells in current column and trigger non-blocking audio synthesis
        for (int row = 0; row < SequencerModel.ROWS; row++) {
            if (model.isCellActive(row, currentStep)) {
                double frequency = model.getFrequency(row);
                AudioEngine.playTone(frequency, 120, isSquareWave);
            }
        }
    }

    private void handleSavePattern() {
        PatternStorage.saveWithDialog(this, model);
    }

    private void handleLoadPattern() {
        PatternStorage.loadWithDialog(this, model, () -> {
            gridPanel.repaint();
            bpm = model.getBpm();
            if (bpmSlider != null) {
                bpmSlider.setValue(bpm);
            }
            if (bpmLabel != null) {
                bpmLabel.setText("BPM: " + bpm);
            }
            updateTimerSpeed();
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainApp().setVisible(true));
    }
}