import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class EsportEarningsAnalyzer extends JFrame {

    // ---------------- Data Fields ----------------
    private File selectedFile;
    // Aggregated data using GameName
    private Map<String, Double> earningsMap;
    private BufferedImage chartImage;

    // ---------------- Panels for Chart and Summary/Legend ----------------
    private JPanel chartPanel;
    private JTextArea summaryTextArea;
    // New legend panel to display colored swatches with titles
    private JPanel legendPanel;
    // Using CardLayout to swap between summary and legend
    private JPanel eastPanel;
    // Stores references to individual legend items for later hover updates
    private List<JPanel> legendItemPanels = new ArrayList<>();

    // Donut chart panel
    private DonutChartPanel pieChartPanel;

    // Chart colors
    private Color[] CHART_COLORS = {
        new Color(70, 130, 180),
        new Color(255, 99, 71),
        new Color(50, 205, 50),
        new Color(255, 215, 0),
        new Color(186, 85, 211),
        new Color(30, 144, 255),
        new Color(255, 127, 80),
        new Color(154, 205, 50),
        new Color(255, 105, 180),
        new Color(100, 149, 237),
        new Color(255, 165, 0),
        new Color(34, 139, 34),
        new Color(220, 20, 60),
        new Color(0, 191, 255),
        new Color(139, 69, 19),
        new Color(75, 0, 130),
        new Color(128, 128, 0),
        new Color(25, 25, 112),
        new Color(210, 105, 30),
        new Color(0, 128, 128)
    };

    // ---------------- Theme and Control UI Settings ----------------
    private final Font PIXEL_FONT = new Font("Press Start 2P", Font.PLAIN, 12);
    private final Color BODY_TEXT_COLOR = new Color(0xE0E0E0);
    private final Color HEADER_TEXT_COLOR = new Color(0xB19CD9);
    private final Color HEADER_SHADOW = new Color(0xB19CD9);
    private final Color PANEL_BG = new Color(20, 20, 20, 220);
    private final Color BUTTON_BG = new Color(0x1F1F3A);
    private final Color BUTTON_FG = new Color(0xB19CD9);
    private final int BORDER_THICKNESS = 3;
    private final int BORDER_RADIUS = 8;

    // ------------- Control Components -------------
    private JButton importBtn;
    private JButton exportBtn;
    private JButton toggleSummaryBtn;
    private JButton toggleOthersBtn;
    private JSlider topNSlider;
    private JLabel topNValueLabel;
    private JPanel manualThresholdPanel;
    private JTextField thresholdInput;
    private JPanel exportOptionsPanel;
    private JButton exportCSVBtn;
    private JButton exportJPEGBtn;
    private JButton exportPNGBtn;
    private JLabel errorMessageLabel;

    // ------------- Filtering Variables -------------
    private boolean showOthers = false;
    private int maxDisplay = 10;
    private double threshold = 0.0;

    // Background image loaded from the classpath.
    private BufferedImage backgroundImage;

    public EsportEarningsAnalyzer() {
        setTitle("Esports Earnings Distribution");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Load the background.gif from the classpath.
        try {
            backgroundImage = ImageIO.read(getClass().getResource("/background.jpg"));
        } catch (Exception e) {
            System.err.println("background.jpg not found. Using default background.");
            backgroundImage = null;
        }

        // Create a custom content pane that paints the background image.
        JPanel contentPane = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                super.paintComponent(g);
            }
        };
        contentPane.setOpaque(false);
        setContentPane(contentPane);

        // ---------------- Header Panel ----------------
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel titleLabel = new JLabel("Esports Earnings Distribution", JLabel.CENTER);
        titleLabel.setFont(PIXEL_FONT.deriveFont(Font.BOLD, 28f));
        titleLabel.setForeground(HEADER_TEXT_COLOR);
        titleLabel.setText("<html><span style='text-shadow: 0px 0px 8px #" +
                Integer.toHexString(HEADER_SHADOW.getRGB() & 0xffffff) +
                ";'>" + titleLabel.getText() + "</span></html>");
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        JLabel subtitleLabel = new JLabel("Comparing Total Money Distribution per Esports Title", JLabel.CENTER);
        subtitleLabel.setFont(PIXEL_FONT.deriveFont(Font.PLAIN, 14f));
        subtitleLabel.setForeground(BODY_TEXT_COLOR);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        contentPane.add(headerPanel, BorderLayout.NORTH);

        // ---------------- Main Container ----------------
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        // =================== Controls Panel ===================
        JPanel controlsPanel = new JPanel();
        controlsPanel.setOpaque(false);
        controlsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));

        importBtn = createButton("Upload File");
        importBtn.addActionListener(e -> openFile());
        controlsPanel.add(importBtn);

        exportBtn = createButton("Export Data");
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> exportOptionsPanel.setVisible(!exportOptionsPanel.isVisible()));
        controlsPanel.add(exportBtn);

        // Toggle button to swap between summary and legend
        toggleSummaryBtn = createButton("Toggle Summary");
        toggleSummaryBtn.addActionListener(e -> {
            CardLayout cl = (CardLayout)(eastPanel.getLayout());
            if (summaryTextArea.isShowing()) {
                cl.show(eastPanel, "LEGEND");
            } else {
                cl.show(eastPanel, "SUMMARY");
            }
        });
        controlsPanel.add(toggleSummaryBtn);

        toggleOthersBtn = createButton("Show Others");
        toggleOthersBtn.addActionListener(e -> {
            showOthers = !showOthers;
            if (showOthers) {
                toggleOthersBtn.setText("Hide Others");
                manualThresholdPanel.setVisible(true);
                double newMax = computeThresholdSliderMax(earningsMap);
                topNSlider.setMaximum((int)newMax);
                threshold = newMax / 2.0;
                topNSlider.setValue((int)threshold);
                topNValueLabel.setText(formatCurrency(threshold));
                thresholdInput.setText(String.valueOf(threshold));
            } else {
                toggleOthersBtn.setText("Show Others");
                manualThresholdPanel.setVisible(false);
                topNSlider.setMaximum(20);
                topNSlider.setValue(maxDisplay);
                topNValueLabel.setText(String.valueOf(maxDisplay));
            }
            updateSummaryAndChart();
        });
        controlsPanel.add(toggleOthersBtn);

        JPanel sliderPanel = new JPanel();
        sliderPanel.setOpaque(false);
        JLabel sliderLabel = new JLabel("Filter:");
        sliderLabel.setFont(PIXEL_FONT.deriveFont(10f));
        sliderLabel.setForeground(HEADER_TEXT_COLOR);
        sliderPanel.add(sliderLabel);
        topNSlider = new JSlider(0, 20, 10);
        topNSlider.setOpaque(false);
        topNSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (showOthers) {
                    threshold = topNSlider.getValue();
                    topNValueLabel.setText(formatCurrency(threshold));
                    thresholdInput.setText(String.valueOf(threshold));
                } else {
                    maxDisplay = topNSlider.getValue();
                    topNValueLabel.setText(String.valueOf(maxDisplay));
                }
                updateSummaryAndChart();
            }
        });
        sliderPanel.add(topNSlider);
        topNValueLabel = new JLabel(String.valueOf(topNSlider.getValue()));
        topNValueLabel.setFont(PIXEL_FONT.deriveFont(10f));
        topNValueLabel.setForeground(HEADER_TEXT_COLOR);
        sliderPanel.add(topNValueLabel);
        controlsPanel.add(sliderPanel);

        manualThresholdPanel = new JPanel();
        manualThresholdPanel.setOpaque(false);
        JLabel thresholdLabel = new JLabel("Threshold:");
        thresholdLabel.setFont(PIXEL_FONT.deriveFont(10f));
        thresholdLabel.setForeground(HEADER_TEXT_COLOR);
        manualThresholdPanel.add(thresholdLabel);
        thresholdInput = new JTextField("0", 5);
        thresholdInput.setFont(PIXEL_FONT.deriveFont(10f));
        thresholdInput.setBackground(BUTTON_BG);
        thresholdInput.setForeground(BUTTON_FG);
        thresholdInput.setBorder(new RoundedBorder(BUTTON_FG, 2, 4));
        thresholdInput.addActionListener(e -> {
            try {
                double val = Double.parseDouble(thresholdInput.getText());
                if(val < 0) val = 0;
                threshold = val;
                topNSlider.setValue((int)threshold);
                topNValueLabel.setText(formatCurrency(threshold));
                updateSummaryAndChart();
            } catch (NumberFormatException ex) {
                // ignore bad input
            }
        });
        manualThresholdPanel.add(thresholdInput);
        manualThresholdPanel.setVisible(false);
        controlsPanel.add(manualThresholdPanel);

        errorMessageLabel = new JLabel("");
        errorMessageLabel.setFont(PIXEL_FONT.deriveFont(10f));
        errorMessageLabel.setForeground(new Color(0xFF5555));
        controlsPanel.add(errorMessageLabel);

        exportOptionsPanel = new JPanel();
        exportOptionsPanel.setOpaque(false);
        exportOptionsPanel.setBorder(BorderFactory.createTitledBorder(new RoundedBorder(HEADER_TEXT_COLOR, 2, 8), "Export Options", 0, 0, PIXEL_FONT.deriveFont(10f), HEADER_TEXT_COLOR));
        exportCSVBtn = createSmallButton("CSV");
        exportCSVBtn.addActionListener(e -> exportCSV());
        exportOptionsPanel.add(exportCSVBtn);
        exportJPEGBtn = createSmallButton("JPEG");
        exportJPEGBtn.addActionListener(e -> exportImage("jpg"));
        exportOptionsPanel.add(exportJPEGBtn);
        exportPNGBtn = createSmallButton("PNG");
        exportPNGBtn.addActionListener(e -> exportImage("png"));
        exportOptionsPanel.add(exportPNGBtn);
        exportOptionsPanel.setVisible(false);
        controlsPanel.add(exportOptionsPanel);

        mainPanel.add(controlsPanel, BorderLayout.NORTH);

        // =================== Chart Panel (Center) ===================
        chartPanel = new JPanel(new BorderLayout());
        chartPanel.setOpaque(false);
        chartPanel.setBorder(BorderFactory.createTitledBorder(new RoundedBorder(HEADER_TEXT_COLOR, 2, 8),
                "Total Money Distribution per Esports Title", 0, 0, PIXEL_FONT, HEADER_TEXT_COLOR));
        chartPanel.setPreferredSize(new Dimension(600, 500));
        mainPanel.add(chartPanel, BorderLayout.CENTER);

        // =================== East Panel for Summary/Legend ===================
        // Using CardLayout to easily swap between the summaryTextArea and legendPanel.
        eastPanel = new JPanel(new CardLayout());
        // Aggregated Earnings Summary Panel (inside a scroll pane)
        summaryTextArea = new JTextArea("Please upload an Esports Analytics file to see the data.");
        summaryTextArea.setEditable(false);
        summaryTextArea.setFont(PIXEL_FONT.deriveFont(10f));
        summaryTextArea.setBackground(PANEL_BG);
        summaryTextArea.setForeground(new Color(0xFAEBD7)); // antiquewhite
        summaryTextArea.setLineWrap(true);
        summaryTextArea.setWrapStyleWord(true);
        summaryTextArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane summaryScrollPane = new JScrollPane(summaryTextArea);
        summaryScrollPane.setPreferredSize(new Dimension(300, 400));
        eastPanel.add(summaryScrollPane, "SUMMARY");

        // Legend Panel: Increase width and make scrollable
        legendPanel = new JPanel();
        legendPanel.setBackground(PANEL_BG);
        legendPanel.setForeground(new Color(0xFAEBD7));
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.add(new JLabel("Legend will appear here after uploading data."));
        JScrollPane legendScrollPane = new JScrollPane(legendPanel);
        legendScrollPane.setPreferredSize(new Dimension(400, 600));
        legendScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        eastPanel.add(legendScrollPane, "LEGEND");

        // Start by showing the summary view.
        CardLayout cl = (CardLayout)(eastPanel.getLayout());
        cl.show(eastPanel, "SUMMARY");

        mainPanel.add(eastPanel, BorderLayout.EAST);

        contentPane.add(mainPanel, BorderLayout.CENTER);

        // ---------------- Footer Panel ----------------
        JPanel footerPanel = new JPanel();
        footerPanel.setOpaque(false);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel footerLabel = new JLabel("\u00A9 Jhane Rose Sadicon | Esports Analytics", JLabel.CENTER);
        footerLabel.setFont(PIXEL_FONT.deriveFont(10f));
        footerLabel.setForeground(Color.WHITE);
        footerPanel.add(footerLabel);
        contentPane.add(footerPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
    }

    // ------------------ Helper Methods for Button Creation ------------------
    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(PIXEL_FONT);
        btn.setBackground(BUTTON_BG);
        btn.setForeground(BUTTON_FG);
        btn.setFocusPainted(false);
        btn.setBorder(new RoundedBorder(BUTTON_FG, BORDER_THICKNESS, BORDER_RADIUS));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(BUTTON_FG);
                btn.setForeground(Color.WHITE);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BUTTON_BG);
                btn.setForeground(BUTTON_FG);
            }
        });
        return btn;
    }

    private JButton createSmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(PIXEL_FONT.deriveFont(10f));
        btn.setBackground(BUTTON_BG);
        btn.setForeground(BUTTON_FG);
        btn.setFocusPainted(false);
        btn.setBorder(new RoundedBorder(BUTTON_FG, 2, 4));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(BUTTON_FG);
                btn.setForeground(Color.WHITE);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BUTTON_BG);
                btn.setForeground(BUTTON_FG);
            }
        });
        return btn;
    }

    // ------------------ File Import and CSV Parsing ------------------
    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select CSV File");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            processFile(selectedFile);
        }
    }

    private void processFile(File file) {
        try {
            earningsMap = new HashMap<>();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (tokens.length < 3) continue;
                double totalMoney = 0;
                try {
                    totalMoney = Double.parseDouble(tokens[1].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String gameName = tokens[2].trim().replaceAll("^\"|\"$", "");
                if (!gameName.isEmpty()) {
                    earningsMap.put(gameName, earningsMap.getOrDefault(gameName, 0.0) + totalMoney);
                }
            }
            reader.close();
            if (showOthers) {
                double newMax = computeThresholdSliderMax(earningsMap);
                topNSlider.setMaximum((int)newMax);
                threshold = newMax / 2.0;
                topNSlider.setValue((int)threshold);
                topNValueLabel.setText(formatCurrency(threshold));
                thresholdInput.setText(String.valueOf(threshold));
            }
            updateSummaryAndChart();
            exportBtn.setEnabled(true);
            errorMessageLabel.setText("");
        } catch (IOException e) {
            errorMessageLabel.setText("Error reading file: " + e.getMessage());
        }
    }

    // ------------------ Filtering and Aggregation ------------------
    private Map<String, Double> getFilteredData(Map<String, Double> dataMap) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(dataMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<String, Double> filtered = new LinkedHashMap<>();
        if (showOthers) {
            for (Map.Entry<String, Double> entry : entries) {
                if (entry.getValue() >= threshold) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            if (entries.size() <= maxDisplay) {
                for (Map.Entry<String, Double> entry : entries) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            } else {
                double otherSum = 0.0;
                for (int i = 0; i < entries.size(); i++) {
                    if (i < maxDisplay) {
                        filtered.put(entries.get(i).getKey(), entries.get(i).getValue());
                    } else {
                        otherSum += entries.get(i).getValue();
                    }
                }
                filtered.put("Other", otherSum);
            }
        }
        return filtered;
    }

    private String formatCurrency(double num) {
        return java.text.NumberFormat.getCurrencyInstance(Locale.US).format(num);
    }

    private double computeThresholdSliderMax(Map<String, Double> dataMap) {
        return dataMap.values().stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(1000);
    }

    // ------------------ Update Summary/Legend and Chart ------------------
    private void updateSummaryAndChart() {
        Map<String, Double> filteredData = getFilteredData(earningsMap);
        updateSummary(filteredData);
        updateLegend(filteredData); // update legend with color swatches, titles, and store legend items
        createPieChart(filteredData);
    }

    private void updateSummary(Map<String, Double> filteredData) {
        StringBuilder summary = new StringBuilder();
        DecimalFormat formatter = new DecimalFormat("#,###.00");
        summary.append("Aggregated Earnings:\n\n");
        double total = filteredData.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Map.Entry<String, Double> entry : filteredData.entrySet()) {
            double percent = total > 0 ? (entry.getValue() / total * 100) : 0;
            summary.append(String.format("%-35s : %s (%.1f%%)\n",
                    entry.getKey(), formatCurrency(entry.getValue()), percent));
        }
        summary.append("\nTotal Earnings: ").append(formatCurrency(total));
        summaryTextArea.setText(summary.toString());
    }

    // Update Legend Panel with legend items
    private void updateLegend(Map<String, Double> filteredData) {
        legendPanel.removeAll();
        legendItemPanels.clear();
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        DecimalFormat formatter = new DecimalFormat("#,###.00");
        double total = filteredData.values().stream().mapToDouble(Double::doubleValue).sum();
        // Increase header font size for better visibility
        JLabel header = new JLabel("Esport Title Legend");
        header.setFont(PIXEL_FONT.deriveFont(Font.BOLD, 10f));
        header.setForeground(HEADER_TEXT_COLOR);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        legendPanel.add(header);
        legendPanel.add(Box.createVerticalStrut(10));
        int index = 0;
        for (Map.Entry<String, Double> entry : filteredData.entrySet()) {
            Color swatchColor = CHART_COLORS[index % CHART_COLORS.length];
            JPanel itemPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            itemPanel.setOpaque(true);
            itemPanel.setBackground(PANEL_BG);
            // Create a small colored swatch label.
            JLabel swatch = new JLabel();
            swatch.setPreferredSize(new Dimension(15, 15));
            swatch.setOpaque(true);
            swatch.setBackground(swatchColor);
            swatch.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            itemPanel.add(swatch);
            // Create the title label.
            JLabel titleLabel = new JLabel(entry.getKey() + " - " + formatCurrency(entry.getValue())
                    + " (" + String.format("%.1f", entry.getValue()/total*100) + "%)");
            titleLabel.setFont(PIXEL_FONT.deriveFont(10f));
            titleLabel.setForeground(BODY_TEXT_COLOR);
            itemPanel.add(titleLabel);
            legendPanel.add(itemPanel);
            legendItemPanels.add(itemPanel);
            index++;
        }
        legendPanel.revalidate();
        legendPanel.repaint();
    }

    // Update legend highlighting based on hovered slice index.
    private void updateLegendHover(int hoveredIndex) {
        for (int i = 0; i < legendItemPanels.size(); i++) {
            JPanel item = legendItemPanels.get(i);
            if (i == hoveredIndex) {
                item.setBorder(BorderFactory.createLineBorder(CHART_COLORS[i % CHART_COLORS.length], 2));
            } else {
                item.setBorder(null);
            }
        }
        legendPanel.revalidate();
        legendPanel.repaint();
    }

    private void createPieChart(Map<String, Double> filteredData) {
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(filteredData.entrySet());
        double total = sortedEntries.stream().mapToDouble(Map.Entry::getValue).sum();
        pieChartPanel = new DonutChartPanel(sortedEntries, total, CHART_COLORS);
        chartPanel.removeAll();
        chartPanel.add(pieChartPanel, BorderLayout.CENTER);
        chartPanel.revalidate();
        chartPanel.repaint();
        updateChartImage();
    }

    private void updateChartImage() {
        if (pieChartPanel == null) return;
        chartImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = chartImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 800, 600);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g2d.getFontMetrics();
        String title = "Total Money Distribution per Esports Title";
        int titleWidth = fm.stringWidth(title);
        g2d.drawString(title, (800 - titleWidth) / 2, 30);
        pieChartPanel.paintToImage(g2d, 800, 600);
        g2d.dispose();
    }

    // ------------------ Export Methods ------------------
    private void exportCSV() {
        if (earningsMap == null || earningsMap.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data to export", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save CSV File");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv"))
                file = new File(file.getAbsolutePath() + ".csv");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("GameName,TotalEarnings\n");
                for (Map.Entry<String, Double> entry : earningsMap.entrySet()) {
                    writer.write(entry.getKey() + "," + entry.getValue() + "\n");
                }
                JOptionPane.showMessageDialog(this, "Data exported successfully to " + file.getName(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error exporting data: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportImage(String format) {
        if (chartImage == null) {
            JOptionPane.showMessageDialog(this, "No chart to save", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Chart as " + format.toUpperCase());
        fileChooser.setFileFilter(new FileNameExtensionFilter(format.toUpperCase() + " Files", format));
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith("." + format))
                file = new File(file.getAbsolutePath() + "." + format);
            try {
                ImageIO.write(chartImage, format, file);
                JOptionPane.showMessageDialog(this, "Chart saved successfully to " + file.getName(),
                        "Save Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving chart: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ------------------ DonutChartPanel ------------------
    private class DonutChartPanel extends JPanel {
        private final List<Map.Entry<String, Double>> entries;
        private final double total;
        private final Color[] colors;
        private int hoverIndex = -1;
        private final DecimalFormat df = new DecimalFormat("#,###.00");
        private double animationProgress = 0.0;
        private javax.swing.Timer animationTimer;
        private double[] startAngles;
        private double[] sweepAngles;
        private Map<String, String> additionalInfo;

        public DonutChartPanel(List<Map.Entry<String, Double>> entries, double total, Color[] colors) {
            this.entries = entries;
            this.total = total;
            this.colors = colors;
            startAngles = new double[entries.size()];
            sweepAngles = new double[entries.size()];
            calculateAngles();

            additionalInfo = new HashMap<>();
            additionalInfo.put("Strategy", "Popular: StarCraft, Age of Empires");
            additionalInfo.put("Collectible Card Game", "Popular: Magic, Hearthstone");
            additionalInfo.put("Sports", "Popular: FIFA, Madden NFL");
            additionalInfo.put("Fighting Game", "Popular: Street Fighter, Tekken");
            additionalInfo.put("Multiplayer Battle Arena", "Popular: Dota 2, LoL");
            additionalInfo.put("First Person Shooter", "Popular: CS, COD");
            additionalInfo.put("Racing", "Popular: Forza, Gran Turismo");

            animationTimer = new javax.swing.Timer(20, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    animationProgress += 0.02;
                    if (animationProgress >= 1.0) {
                        animationProgress = 1.0;
                        animationTimer.stop();
                    }
                    repaint();
                }
            });
            animationTimer.start();

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    updateHoverIndex(e.getX(), e.getY());
                }
            });
            addMouseListener(new MouseAdapter() {
                public void mouseExited(MouseEvent e) {
                    setHoverIndex(-1);
                }
            });
        }

        private void calculateAngles() {
            double currentAngle = 90;
            for (int i = 0; i < entries.size(); i++) {
                startAngles[i] = currentAngle;
                sweepAngles[i] = 360 * (entries.get(i).getValue() / total);
                currentAngle += sweepAngles[i];
            }
        }

        private void updateHoverIndex(int mouseX, int mouseY) {
            int width = getWidth();
            int height = getHeight();
            int size = Math.min(width, height) - 50;
            int x = (width - size) / 2;
            int y = (height - size) / 2;
            int centerX = x + size / 2;
            int centerY = y + size / 2;
            int radius = size / 2;
            int dx = mouseX - centerX, dy = mouseY - centerY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance > radius || distance < radius * 0.4) {
                setHoverIndex(-1);
                return;
            }
            double theta = Math.toDegrees(Math.atan2(centerY - mouseY, mouseX - centerX));
            if (theta < 0) theta += 360;
            for (int i = 0; i < entries.size(); i++) {
                double startAngle = startAngles[i];
                double endAngle = (startAngle + sweepAngles[i]) % 360;
                if (startAngle <= endAngle) {
                    if (theta >= startAngle && theta < endAngle) {
                        setHoverIndex(i);
                        return;
                    }
                } else {
                    if (theta >= startAngle || theta < endAngle) {
                        setHoverIndex(i);
                        return;
                    }
                }
            }
            setHoverIndex(-1);
        }

        public void setHoverIndex(int index) {
            if (hoverIndex != index) {
                hoverIndex = index;
                updateLegendHover(hoverIndex);
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();

            // Glass Morphism Background: semi-transparent, rounded gray rectangle
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g2d.setColor(new Color(200, 200, 200)); // gray tone
            g2d.fillRoundRect(0, 0, width, height, 20, 20);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

            int size = Math.min(width, height) - 50;
            int x = (width - size) / 2, y = (height - size) / 2;
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            FontMetrics fm = g2d.getFontMetrics();
            String title = "Total Money Distribution per Esports Title";
            int titleWidth = fm.stringWidth(title);
            g2d.drawString(title, (width - titleWidth) / 2, 20);

            for (int i = 0; i < entries.size(); i++) {
                double previousSum = 0;
                for (int j = 0; j < i; j++) previousSum += sweepAngles[j];
                if (previousSum / 360 > animationProgress) continue;
                double angleToDraw = sweepAngles[i];
                if ((startAngles[i] - 90 + sweepAngles[i]) / 360 > animationProgress) {
                    angleToDraw = (animationProgress * 360) - (startAngles[i] - 90);
                    if (angleToDraw <= 0) continue;
                }
                Color sliceColor = colors[i % colors.length];
                if (i == hoverIndex) {
                    sliceColor = new Color(
                            Math.min(255, sliceColor.getRed() + 30),
                            Math.min(255, sliceColor.getGreen() + 30),
                            Math.min(255, sliceColor.getBlue() + 30));
                    int expandedSize = size + 10;
                    int expandedX = (width - expandedSize) / 2;
                    int expandedY = (height - expandedSize) / 2;
                    g2d.setColor(sliceColor);
                    g2d.fill(new Arc2D.Double(expandedX, expandedY, expandedSize, expandedSize,
                            startAngles[i], angleToDraw, Arc2D.PIE));
                } else {
                    g2d.setColor(sliceColor);
                    g2d.fill(new Arc2D.Double(x, y, size, size, startAngles[i], angleToDraw, Arc2D.PIE));
                }
            }

            int holeSize = (int)(size * 0.4);
            int holeX = x + (size - holeSize) / 2, holeY = y + (size - holeSize) / 2;
            g2d.setColor(Color.WHITE);
            g2d.fill(new Ellipse2D.Double(holeX, holeY, holeSize, holeSize));

            if (hoverIndex >= 0 && hoverIndex < entries.size()) {
                Map.Entry<String, Double> entry = entries.get(hoverIndex);
                double percentage = entry.getValue() / total * 100;
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                String genreText = entry.getKey();
                String percentText = String.format("%.2f%%", percentage);
                String valueText = "$" + df.format(entry.getValue());
                fm = g2d.getFontMetrics();
                int genreWidth = fm.stringWidth(genreText);
                int percentWidth = fm.stringWidth(percentText);
                int valueWidth = fm.stringWidth(valueText);
                int centerX = x + size / 2, centerY = y + size / 2;
                int textBoxWidth = Math.max(Math.max(genreWidth, percentWidth), valueWidth) + 20;
                int textBoxHeight = 60;
                g2d.setColor(new Color(255, 255, 255, 200));
                g2d.fillRoundRect(centerX - textBoxWidth / 2, centerY - 30, textBoxWidth, textBoxHeight, 10, 10);
                g2d.setColor(Color.BLACK);
                g2d.drawString(genreText, centerX - genreWidth / 2, centerY - 10);
                g2d.drawString(percentText, centerX - percentWidth / 2, centerY + 10);
                g2d.drawString(valueText, centerX - valueWidth / 2, centerY + 30);
                String extraText = additionalInfo.get(genreText);
                if (extraText != null) {
                    Font extraFont = new Font("SansSerif", Font.ITALIC, 10);
                    g2d.setFont(extraFont);
                    FontMetrics extraFm = g2d.getFontMetrics();
                    int extraWidth = extraFm.stringWidth(extraText);
                    g2d.drawString(extraText, centerX - extraWidth / 2, centerY + 45);
                }
                g2d.setColor(colors[hoverIndex % colors.length]);
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRoundRect(centerX - textBoxWidth / 2, centerY - 30, textBoxWidth, textBoxHeight, 10, 10);
            }
            g2d.dispose();
        }

        public void paintToImage(Graphics2D g2d, int width, int height) {
            int size = 400;
            int x = (width - size) / 2, y = 50;
            for (int i = 0; i < entries.size(); i++) {
                g2d.setColor(colors[i % colors.length]);
                g2d.fill(new Arc2D.Double(x, y, size, size, startAngles[i], sweepAngles[i], Arc2D.PIE));
            }
            int holeSize = (int)(size * 0.4);
            int holeX = x + (size - holeSize) / 2, holeY = y + (size - holeSize) / 2;
            g2d.setColor(Color.WHITE);
            g2d.fill(new Ellipse2D.Double(holeX, holeY, holeSize, holeSize));
        }
    }

    // ------------------ Main Method ------------------
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { e.printStackTrace(); }
        SwingUtilities.invokeLater(() -> new EsportEarningsAnalyzer().setVisible(true));
    }
}

/**
 * RoundedBorder is a helper class that draws rounded borders.
 */
class RoundedBorder extends AbstractBorder {
    private Color color;
    private int thickness;
    private int radius;

    public RoundedBorder(Color color, int thickness, int radius) {
        this.color = color;
        this.thickness = thickness;
        this.radius = radius;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        g2.drawRoundRect(x, y, width - thickness, height - thickness, radius, radius);
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(thickness + radius / 2, thickness + radius / 2, thickness + radius / 2, thickness + radius / 2);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.top = insets.right = insets.bottom = thickness + radius / 2;
        return insets;
    }
}
