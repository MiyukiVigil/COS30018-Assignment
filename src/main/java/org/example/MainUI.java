package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.agents.NegotiationConfig;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class MainUI extends Application {
    private TextArea logArea = new TextArea();
    private Label buyerCountLabel = new Label("0");
    private Label dealerCountLabel = new Label("0");
    private Label transactionCountLabel = new Label("0");
    private Label revenueLabel = new Label("RM 0.00");
    private ContainerController cc;
    private UILogger appLogger;
    private int buyerCount = 0;
    private int dealerCount = 0;
    private int dealsClosed = 0;
    private double totalRevenue = 0;
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Label dealerStatusLabel = new Label();
    private Label updateBuyerStatus = new Label();
    private int currentCycle = 0;
    private Map<String, XYChart.Series<Number, Number>> seriesMap = new HashMap<>();
    private LineChart<Number, Number> priceChart;
    private Button playPauseBtn;
    private boolean isAutoPlay = true;
    private ComboBox<String> strategyChoice;
    private ComboBox<String> switchStrategyChoice;
    private TextField deadlineCyclesField;
    private TextField buyerStartPercentField;
    private TextField reservePercentField;
    private TextField maxRoundsField;
    private TextField retryLimitField;
    private TextField stuckRoundsField;
    private TextField strategySwitchCycleField;
    private TextField manualDealerNameField;
    private TextField manualDealerPriceField;
    private Label negotiationControlStatusLabel = new Label("No waiting buyers");
    private List<String> waitingBuyerAgents = new ArrayList<>();
    private List<String> buyerAgents = new ArrayList<>();
    private List<String> dealerAgents = new ArrayList<>();
    private javafx.collections.ObservableList<String> manualBuyerAgents = javafx.collections.FXCollections
            .observableArrayList();
    private ComboBox<String> manualBuyerSelect;
    private TextArea manualLogArea;
    private ComboBox<String> manualDealerSelect;
    private TextField manualFirstOfferField;
    private Button manualSendFirstOfferBtn;
    private TextField manualCounterPriceField;
    private Button manualSendCounterBtn;
    private Button manualAcceptDealBtn;
    private Button manualWalkAwayBtn;
    private List<String> failedDeals = new ArrayList<>();
    private TextArea failuresArea = new TextArea();
    private TextArea sessionsArea = new TextArea();
    private Label activeSessionsLabel = new Label("0");
    private Label activeSessionsLabelMini = new Label("0");
    private Label fixedFeesLabel = new Label("RM 0");
    private Label fixedFeesLabelMini = new Label("RM 0");
    private Label commissionLabel = new Label("RM 0");
    private double totalFixedFees = 0;
    private double totalCommission = 0;
    private int activeSessions = 0;
    private final AtomicLong commandAgentCounter = new AtomicLong();
    private final AtomicLong demoScenarioCounter = new AtomicLong();
    private static final Pattern RM_AMOUNT_PATTERN = Pattern.compile("RM\\s*(\\d+)");

    // Modern Color Palette
    private static final String PRIMARY_BLUE = "#1e40af";
    private static final String ACCENT_BLUE = "#3b82f6";
    private static final String SUCCESS_GREEN = "#10b981";
    private static final String WARNING_ORANGE = "#f59e0b";
    private static final String ERROR_RED = "#ef4444";
    private static final String LIGHT_GRAY = "#f5f7fb";
    private static final String DARK_TEXT = "#1f2937";
    private static final String TEXT_MUTED = "#64748b";
    private static final String SURFACE = "#ffffff";
    private static final String SURFACE_ALT = "#f8fafc";
    private static final String BORDER_SUBTLE = "#e2e8f0";
    private static final String FONT_FAMILY = "'Poppins', 'Segoe UI', Arial";
    private static final String FONT_WEIGHT_MEDIUM = "500";
    private static final String SOFT_SHADOW = "dropshadow(gaussian, rgba(15,23,42,0.08), 12, 0, 0, 3)";

    // Popular Car Models Database
    private static final String[] CAR_MODELS = {
            "Toyota Camry", "Toyota Corolla", "Toyota Fortuner", "Toyota Vios", "Toyota Innova",
            "Honda Civic", "Honda Accord", "Honda CR-V", "Honda City", "Honda Jazz",
            "Nissan Almera", "Nissan X-Trail", "Nissan Navara", "Nissan Qashqai",
            "Mazda 3", "Mazda CX-5", "Mazda CX-9", "Mazda 6",
            "Hyundai Elantra", "Hyundai Santa Fe", "Hyundai Tucson", "Hyundai i10",
            "Kia Cerato", "Kia Sportage", "Kia Niro", "Kia Seltos",
            "BMW X5", "BMW 3 Series", "BMW 5 Series", "BMW X3",
            "Mercedes C-Class", "Mercedes GLC", "Mercedes E-Class", "Mercedes GLE",
            "Proton X70", "Proton X90", "Proton Saga", "Proton Persona",
            "Perodua Myvi", "Perodua Alza", "Perodua Ativa", "Perodua Aruz",
            "Ford EcoSport", "Ford Ranger", "Ford Everest",
            "Suzuki Swift", "Suzuki Ertiga", "Suzuki Vitara"
    };

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        cc = rt.createMainContainer(p);
        loadFonts();

        UILogger logger = msg -> {
            String timestamp = "[" + LocalTime.now().format(timeFormatter) + "] ";
            final String formattedMsg = timestamp + msg + "\n";

            // ── Classification ────────────────────────────────────────────────
            boolean isBuyerReg = msg.contains("Buyer") && (msg.contains("registered") || msg.contains("added"));
            boolean isDealerReg = msg.contains("Dealer") && msg.contains("listed");
            boolean isSetupMsg = msg.contains("BROKER ONLINE") || msg.contains("Fixed Negotiation Fee")
                    || msg.contains("Initializing Space Control");
            boolean isCycleShift = msg.contains("Cycle Shift:");
            boolean isSessionStart = msg.contains("[BROKER] SESSION START:");
            boolean isFeeCharged = msg.contains("[BROKER] FEE CHARGED:");
            boolean isDealSettled = msg.contains("[BROKER] DEAL SETTLED:");
            boolean isRevenue = msg.contains("[BROKER] REVENUE:");
            boolean isNoDeal = msg.contains("[BROKER] NO DEAL:");
            boolean isPerformance = msg.contains("[BROKER] PERFORMANCE:");
            boolean isRelay = msg.contains("[BROKER] RELAY") || msg.contains("[BROKER] INVITE:");
            boolean isPriceUpdate = msg.contains("RM") && (msg.contains("Price updated") ||
                    msg.contains("Willing to pay") ||
                    msg.contains("DEAL CLOSED") ||
                    msg.contains("SUCCESS!"));
            boolean isNegotiationAction = isDealSettled || isNoDeal || isRelay || isPriceUpdate
                    || msg.contains("STATUS:") || msg.contains("AGREED") || msg.contains("NEGOTIATION:");

            Platform.runLater(() -> {
                if (msg.contains("[MANUAL_PROMPT]")) {
                    handleManualPromptLog(msg);
                    return;
                }

                // ── Activity log (filter to meaningful events) ────────────────
                if (isSetupMsg || isBuyerReg || isDealerReg || isCycleShift
                        || isSessionStart || isFeeCharged || isDealSettled
                        || isRevenue || isNoDeal || isPerformance || isNegotiationAction) {
                    logArea.appendText(formattedMsg);
                }

                // ── Stat counters ─────────────────────────────────────────────
                if (isBuyerReg) {
                    buyerCount++;
                    buyerCountLabel.setText(String.valueOf(buyerCount));
                    updateBuyerStatus();
                }
                if (isDealerReg) {
                    dealerCount++;
                    dealerCountLabel.setText(String.valueOf(dealerCount));
                    updateDealerStatus();
                }

                if (isSessionStart) {
                    activeSessions++;
                    activeSessionsLabel.setText(String.valueOf(activeSessions));
                    activeSessionsLabelMini.setText(String.valueOf(activeSessions));
                    sessionsArea.appendText(formattedMsg);
                }

                if (isFeeCharged) {
                    try {
                        Matcher matcher = RM_AMOUNT_PATTERN.matcher(msg);
                        if (matcher.find()) {
                            double amt = Double.parseDouble(matcher.group(1));
                            totalFixedFees += amt;
                            totalRevenue += amt;
                            fixedFeesLabel.setText("RM " + (int) totalFixedFees);
                            fixedFeesLabelMini.setText("RM " + (int) totalFixedFees);
                            revenueLabel.setText(String.format("RM %.2f", totalRevenue));
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (isDealSettled) {
                    dealsClosed++;
                    transactionCountLabel.setText(String.valueOf(dealsClosed));
                    if (activeSessions > 0)
                        activeSessions--;
                    activeSessionsLabel.setText(String.valueOf(activeSessions));
                    activeSessionsLabelMini.setText(String.valueOf(activeSessions));
                    sessionsArea.appendText(formattedMsg);
                }

                if (isRevenue) {
                    try {
                        int plusRm = msg.indexOf("+RM");
                        if (plusRm >= 0) {
                            Matcher matcher = RM_AMOUNT_PATTERN.matcher(msg.substring(plusRm));
                            if (matcher.find()) {
                                double commission = Double.parseDouble(matcher.group(1));
                                totalCommission += commission;
                                totalRevenue += commission;
                                commissionLabel.setText("RM " + (int) totalCommission);
                                revenueLabel.setText(String.format("RM %.2f", totalRevenue));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (isNoDeal) {
                    if (activeSessions > 0)
                        activeSessions--;
                    activeSessionsLabel.setText(String.valueOf(activeSessions));
                    activeSessionsLabelMini.setText(String.valueOf(activeSessions));
                    failedDeals.add(formattedMsg);
                    failuresArea.appendText(formattedMsg);
                    sessionsArea.appendText(formattedMsg);
                }

                if (isCycleShift) {
                    try {
                        currentCycle = Integer.parseInt(msg.substring(msg.lastIndexOf(" ") + 1).trim());
                    } catch (Exception e) {
                        currentCycle++;
                    }
                }

                if (isPriceUpdate) {
                    updatePriceChart(msg);
                }
            });
        };
        appLogger = logger;

        cc.createNewAgent("broker", "org.example.agents.BrokerAgent", new Object[] { logger }).start();
        cc.createNewAgent("space", "org.example.agents.SpaceControl", new Object[] { logger }).start();
        launchSniffer(logger);

        VBox mainContent = createMainContent(logger);

        Scene scene = new Scene(mainContent, 1500, 900);
        scene.setFill(Color.web(LIGHT_GRAY));

        stage.setScene(scene);
        stage.setTitle("Automated Car Negotiation System - Multi-Agent Platform");
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();
    }

    private void updatePriceChart(String msg) {
        try {
            // Extract agent name robustly: look for leading token before ':'
            int colon = msg.indexOf(":");
            if (colon <= 0)
                return;
            String agentName = msg.substring(0, colon).trim();

            // Only plot known buyer/dealer agents to avoid noise from broker/UI messages
            boolean knownAgent = false;
            synchronized (dealerAgents) {
                if (dealerAgents.contains(agentName))
                    knownAgent = true;
            }
            synchronized (buyerAgents) {
                if (buyerAgents.contains(agentName))
                    knownAgent = true;
            }
            if (!knownAgent)
                return;

            Matcher matcher = RM_AMOUNT_PATTERN.matcher(msg);
            Double amount = null;
            while (matcher.find()) {
                amount = Double.parseDouble(matcher.group(1));
            }
            if (amount == null)
                return;

            XYChart.Series<Number, Number> series = seriesMap.computeIfAbsent(agentName, k -> {
                XYChart.Series<Number, Number> s = new XYChart.Series<>();
                s.setName(k);
                if (priceChart != null)
                    priceChart.getData().add(s);
                return s;
            });

            series.getData().add(new XYChart.Data<>(currentCycle, amount));
            if (series.getData().size() > 50) {
                series.getData().remove(0);
            }
        } catch (Exception e) {
            System.err.println("Chart update error: " + e.getMessage());
        }
    }

    private void loadFonts() {
        loadFontResource("/fonts/Poppins-Regular.ttf");
        loadFontResource("/fonts/Poppins-Medium.ttf");
        loadFontResource("/fonts/Poppins-SemiBold.ttf");
        loadFontResource("/fonts/Poppins-Bold.ttf");
    }

    private void loadFontResource(String path) {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                System.err.println("Font not found: " + path);
                return;
            }
            Font.loadFont(stream, 12);
        } catch (IOException e) {
            System.err.println("Failed to load font " + path + ": " + e.getMessage());
        }
    }

    private VBox createMainContent(UILogger logger) {
        TabPane tp = new TabPane();
        tp.setStyle("-fx-font-size: 13; -fx-font-family: " + FONT_FAMILY + "; "
                + "-fx-background-color: transparent; -fx-tab-min-height: 40; -fx-tab-max-height: 40;");

        Tab dashboardTab = new Tab("📊 Dashboard", createBrokerView());
        dashboardTab.setClosable(false);
        Tab buyerTab = new Tab("🧑 Buyer Portal", createBuyerView(logger));
        buyerTab.setClosable(false);
        Tab dealerTab = new Tab("🚗 Dealer Portal", createDealerView(logger));
        dealerTab.setClosable(false);
        Tab manualTab = new Tab("🎮 Manual Mode", createManualPlayView());
        manualTab.setClosable(false);
        Tab sessionsTab = new Tab("🔁 Sessions", createSessionsView());
        sessionsTab.setClosable(false);
        Tab analysisTab = new Tab("📈 Analytics", createMarketAnalysisView());
        analysisTab.setClosable(false);
        Tab failuresTab = new Tab("⚠️ Failures", createFailuresView());
        failuresTab.setClosable(false);
        Tab logTab = new Tab("📋 Activity Log", createActivityLogView());
        logTab.setClosable(false);
        tp.getTabs().addAll(dashboardTab, buyerTab, dealerTab, manualTab, sessionsTab, analysisTab, failuresTab,
                logTab);
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + LIGHT_GRAY + "; -fx-font-family: " + FONT_FAMILY
                + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + ";");
        VBox tabArea = new VBox(tp);
        tabArea.setPadding(new Insets(0, 20, 20, 20));
        VBox.setVgrow(tp, Priority.ALWAYS);
        VBox.setVgrow(tabArea, Priority.ALWAYS);
        root.getChildren().addAll(createAppHeader(), createActionBar(), tabArea);
        return root;
    }

    private VBox createAppHeader() {
        Region stripe = new Region();
        stripe.setPrefWidth(5);
        stripe.setMinWidth(5);
        stripe.setStyle("-fx-background-color: linear-gradient(to bottom, " + ACCENT_BLUE + ", #8b5cf6);");

        Label title = new Label("Automated Car Negotiation System");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: 700; -fx-text-fill: " + DARK_TEXT + ";");
        Label subtitle = new Label(
                "JADE broker-routed marketplace  ·  session-based negotiation  ·  real-time metrics");
        subtitle.setStyle("-fx-font-size: 12; -fx-text-fill: " + TEXT_MUTED + ";");
        VBox titleBox = new VBox(3, title, subtitle);
        titleBox.setPadding(new Insets(0, 0, 0, 14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label cycleLabel = new Label("Cycle: 0");
        cycleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: 700; -fx-text-fill: white; "
                + "-fx-background-color: " + PRIMARY_BLUE + "; -fx-background-radius: 20; -fx-padding: 4 14;");
        logArea.textProperty().addListener((o, ov, nv) -> cycleLabel.setText("Cycle: " + currentCycle));

        HBox row = new HBox(0, stripe, titleBox, spacer, cycleLabel);
        row.setPadding(new Insets(14, 20, 14, 0));
        row.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE
                + "; -fx-border-width: 0 0 1 0;");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return new VBox(row);
    }

    private void toggleAutoplay() {
        isAutoPlay = !isAutoPlay;
        if (playPauseBtn != null) {
            playPauseBtn.setText(isAutoPlay ? "Pause" : "Resume");
        }
        sendSpaceCommand(isAutoPlay ? "RESUME" : "PAUSE");
    }

    private void updateNegotiationControlStatus() {
        if (negotiationControlStatusLabel == null) {
            return;
        }

        int waitingCount = waitingBuyerAgents.size();
        int totalCount = buyerAgents.size();
        String state = isAutoPlay ? "running" : "paused";
        negotiationControlStatusLabel.setText("Buyers: " + totalCount + " total | "
                + waitingCount + " waiting | Simulation " + state);
    }

    private void loggerLog(String message) {
        String timestamp = "[" + LocalTime.now().format(timeFormatter) + "] ";
        logArea.appendText(timestamp + "[UI] " + message + "\n");
    }

    private void sendSpaceCommand(String command) {
        try {
            cc.createNewAgent(nextAgentName("space-command"), "org.example.agents.SpaceCommandAgent",
                    new Object[] { command, "space", "" }).start();
        } catch (Exception e) {
            System.err.println("Error sending command to SpaceControl: " + e.getMessage());
        }
    }

    private void sendDealerPriceAdjustment(String dealerName, String price) {
        try {
            cc.createNewAgent(nextAgentName("dealer-command"), "org.example.agents.SpaceCommandAgent",
                    new Object[] { "PRICE_ADJUSTMENT", dealerName, price }).start();
        } catch (Exception e) {
            showAlert("❌ Error sending price adjustment: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void sendAgentCommand(String agentName, String command) {
        sendAgentCommand(agentName, command, "");
    }

    private void sendAgentCommand(String agentName, String command, String content) {
        try {
            cc.createNewAgent(nextAgentName("agent-command"), "org.example.agents.SpaceCommandAgent",
                    new Object[] { command, agentName, content }).start();
        } catch (Exception e) {
            showAlert("❌ Error sending command to " + agentName + ": " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void launchSniffer(UILogger logger) {
        try {
            // First try a wildcard pattern (captures demo agents like DemoBuyer*,
            // DemoAuto*)
            String wildcardTargets = "broker;space;DemoBuyer*;DemoAuto*;BudgetCars*;FamilyDrive*;TruckHub*";
            try {
                cc.createNewAgent(nextAgentName("sniffer"), "jade.tools.sniffer.Sniffer",
                        new Object[] { wildcardTargets }).start();
                logger.log("STATUS: JADE Sniffer launched with wildcards: " + wildcardTargets);
                return;
            } catch (Exception ex) {
                // Wildcard launch sometimes fails with AMS errors when agents terminate
                // quickly.
                logger.log("STATUS: Wildcard sniffer failed, falling back to explicit agent list: " + ex.getMessage());
            }

            // Fallback: Build a focused sniffer list from currently-known agents to avoid
            // AMS errors
            StringBuilder target = new StringBuilder();
            target.append("broker;space");
            synchronized (dealerAgents) {
                for (String d : dealerAgents) {
                    target.append(";").append(d);
                }
            }
            synchronized (buyerAgents) {
                for (String b : buyerAgents) {
                    target.append(";").append(b);
                }
            }
            cc.createNewAgent(nextAgentName("sniffer"), "jade.tools.sniffer.Sniffer",
                    new Object[] { target.toString() }).start();
            logger.log("STATUS: JADE Sniffer Agent launched for broker, space, and specified agents.");
        } catch (Exception e) {
            logger.log("STATUS: Sniffer not launched: " + e.getMessage());
        }
    }

    private String nextAgentName(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + commandAgentCounter.incrementAndGet();
    }

    private VBox createBrokerView() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        // ── Header ────────────────────────────────────────────────────────────
        Label headerLabel = new Label("Marketplace Dashboard");
        headerLabel.setStyle("-fx-font-size: 22; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");
        Label subLabel = new Label("Live broker metrics · negotiation trajectory · quick-start guide");
        subLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + TEXT_MUTED + ";");
        VBox hdr = new VBox(2, headerLabel, subLabel);

        // ── 6 stat cards (3 per row) ─────────────────────────────────────────
        HBox statsRow1 = new HBox(12,
                createStatCard("🧑 Buyers", buyerCountLabel, ACCENT_BLUE),
                createStatCard("🚗 Dealers", dealerCountLabel, WARNING_ORANGE),
                createStatCard("🔁 Active Sessions", activeSessionsLabel, "#8b5cf6"));
        HBox statsRow2 = new HBox(12,
                createStatCard("✅ Deals Closed", transactionCountLabel, SUCCESS_GREEN),
                createStatCard("💰 Total Revenue", revenueLabel, "#ec4899"),
                createStatCard("💵 Fixed Fees", fixedFeesLabel, "#06b6d4"));
        for (HBox row : new HBox[] { statsRow1, statsRow2 }) {
            for (javafx.scene.Node n : row.getChildren())
                HBox.setHgrow(n, Priority.ALWAYS);
        }
        VBox statsSection = new VBox(10, statsRow1, statsRow2);

        // ── Chart ─────────────────────────────────────────────────────────────
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Cycle");
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (RM)");
        yAxis.setForceZeroInRange(false);

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setAnimated(false);
        priceChart.setTitle(null);
        priceChart.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE
                + "; -fx-border-width: 1; -fx-background-radius: 12; -fx-border-radius: 12;");
        priceChart.setMinHeight(280);
        priceChart.getData().addAll(seriesMap.values());
        VBox.setVgrow(priceChart, Priority.ALWAYS);

        // Chart header row: title + expand button
        Label chartTitle = new Label("Negotiation Price Trajectory");
        chartTitle.setStyle("-fx-font-size: 14; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");
        Label chartHint = new Label("Each line tracks one agent's offer price per cycle.");
        chartHint.setStyle("-fx-font-size: 11; -fx-text-fill: " + TEXT_MUTED + ";");
        VBox chartTitleBox = new VBox(2, chartTitle, chartHint);

        Button expandChartBtn = new Button("⤢ Expand");
        expandChartBtn.setStyle(
                "-fx-font-size: 11; -fx-font-weight: 600; -fx-padding: 5 12; "
                        + "-fx-background-color: " + ACCENT_BLUE + "; -fx-text-fill: white; "
                        + "-fx-background-radius: 7; -fx-cursor: hand;");
        expandChartBtn.setOnMouseEntered(e -> expandChartBtn.setOpacity(0.82));
        expandChartBtn.setOnMouseExited(e -> expandChartBtn.setOpacity(1.0));
        expandChartBtn.setOnAction(e -> openChartInNewWindow());

        Region chartHeaderSpacer = new Region();
        HBox.setHgrow(chartHeaderSpacer, Priority.ALWAYS);
        HBox chartHeaderRow = new HBox(8, chartTitleBox, chartHeaderSpacer, expandChartBtn);
        chartHeaderRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox chartSection = new VBox(6, chartHeaderRow, priceChart);
        chartSection.setPadding(new Insets(14));
        chartSection.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12;"
                + "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");
        VBox.setVgrow(chartSection, Priority.ALWAYS);

        // ── Left column: stats + workflow guide ───────────────────────────────
        VBox guide = buildWorkflowGuide();
        VBox leftCol = new VBox(14, statsSection, guide);
        leftCol.setMinWidth(420);
        leftCol.setMaxWidth(520);

        // ── Two-column body row ───────────────────────────────────────────────
        HBox bodyRow = new HBox(16, leftCol, chartSection);
        HBox.setHgrow(chartSection, Priority.ALWAYS);
        VBox.setVgrow(bodyRow, Priority.ALWAYS);

        box.getChildren().addAll(hdr, bodyRow);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Opens the negotiation price chart in a standalone resizable window. */
    private void openChartInNewWindow() {
        Stage chartStage = new Stage();
        chartStage.setTitle("Negotiation Price Trajectory — Expanded View");

        // Mirror axes so the detached chart can render independently
        NumberAxis x2 = new NumberAxis();
        x2.setLabel("Cycle");
        x2.setForceZeroInRange(false);
        NumberAxis y2 = new NumberAxis();
        y2.setLabel("Price (RM)");
        y2.setForceZeroInRange(false);

        LineChart<Number, Number> detachedChart = new LineChart<>(x2, y2);
        detachedChart.setAnimated(false);
        detachedChart.setTitle("Negotiation Price Trajectory");
        detachedChart.setStyle("-fx-background-color: white;");

        // Duplicate series data into the detached chart (snapshot of current data)
        for (XYChart.Series<Number, Number> src : seriesMap.values()) {
            XYChart.Series<Number, Number> copy = new XYChart.Series<>();
            copy.setName(src.getName());
            for (XYChart.Data<Number, Number> d : src.getData()) {
                copy.getData().add(new XYChart.Data<>(d.getXValue(), d.getYValue()));
            }
            detachedChart.getData().add(copy);
        }

        // Keep detached chart in sync: whenever seriesMap gets new data points,
        // mirror them into the matching series in the detached chart.
        priceChart.getData().addListener(
                (javafx.collections.ListChangeListener<XYChart.Series<Number, Number>>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (XYChart.Series<Number, Number> added : change.getAddedSubList()) {
                                // Add a mirror series if not already present
                                boolean found = detachedChart.getData().stream()
                                        .anyMatch(s -> s.getName().equals(added.getName()));
                                if (!found) {
                                    XYChart.Series<Number, Number> mirror = new XYChart.Series<>();
                                    mirror.setName(added.getName());
                                    Platform.runLater(() -> detachedChart.getData().add(mirror));
                                }
                            }
                        }
                    }
                });

        // Periodically sync data from live seriesMap into detached chart series
        javafx.animation.Timeline syncTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ae -> {
                    for (XYChart.Series<Number, Number> src : seriesMap.values()) {
                        detachedChart.getData().stream()
                                .filter(s -> s.getName().equals(src.getName()))
                                .findFirst()
                                .ifPresent(mirror -> {
                                    int srcSize = src.getData().size();
                                    int mirrorSize = mirror.getData().size();
                                    if (srcSize > mirrorSize) {
                                        for (int i = mirrorSize; i < srcSize; i++) {
                                            XYChart.Data<Number, Number> d = src.getData().get(i);
                                            mirror.getData().add(
                                                    new XYChart.Data<>(d.getXValue(), d.getYValue()));
                                        }
                                    }
                                });
                    }
                }));
        syncTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        syncTimeline.play();
        chartStage.setOnCloseRequest(e -> syncTimeline.stop());

        Label hint = new Label(
                "This window shows a live-synced copy of the price trajectory. "
                        + "New data points are added every ~1 second.");
        hint.setStyle("-fx-font-size: 11; -fx-text-fill: #64748b; -fx-padding: 0 0 6 0;");

        VBox root = new VBox(6, hint, detachedChart);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #f5f7fb;");
        VBox.setVgrow(detachedChart, Priority.ALWAYS);

        Scene scene = new Scene(root, 900, 600);
        chartStage.setScene(scene);
        chartStage.show();
    }

    private VBox buildWorkflowGuide() {
        VBox box = new VBox(0);
        box.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; "
                + "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");
        box.setPadding(new Insets(14, 18, 14, 18));

        Label title = new Label("Getting Started");
        title.setStyle("-fx-font-size: 13; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");

        String[][] steps = {
                { "🚗", "Register Dealers", "Dealer Portal → enter name, car model, retail price & stock" },
                { "🧑", "Register Buyers", "Buyer Portal → enter name, desired car & max budget" },
                { "▶️", "Start Negotiation", "Click \u25b6 Start in the toolbar above" },
                { "🔁", "Monitor Sessions", "Sessions tab shows live session events from the broker" },
                { "📋", "Review Outcomes", "Activity Log & Failures tabs show full negotiation history" }
        };

        HBox stepsRow = new HBox(0);
        stepsRow.setPadding(new Insets(8, 0, 0, 0));
        for (int i = 0; i < steps.length; i++) {
            String[] s = steps[i];
            VBox step = new VBox(3);
            step.setPadding(new Insets(10, 14, 10, 14));
            step.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            HBox.setHgrow(step, Priority.ALWAYS);
            // Alternate background for zebra feel
            String bg = (i % 2 == 0) ? SURFACE : SURFACE_ALT;
            step.setStyle("-fx-background-color: " + bg + ";");

            Label icon = new Label(s[0] + " " + (i + 1));
            icon.setStyle("-fx-font-size: 18; -fx-font-weight: 700; -fx-text-fill: " + TEXT_MUTED + ";");
            Label name = new Label(s[1]);
            name.setStyle("-fx-font-size: 12; -fx-font-weight: 700; -fx-text-fill: " + DARK_TEXT + ";");
            Label desc = new Label(s[2]);
            desc.setStyle("-fx-font-size: 11; -fx-text-fill: " + TEXT_MUTED + "; -fx-wrap-text: true;");
            desc.setMaxWidth(180);
            step.getChildren().addAll(icon, name, desc);
            stepsRow.getChildren().add(step);

            if (i < steps.length - 1) {
                Label arrow = new Label("›");
                arrow.setStyle("-fx-font-size: 22; -fx-text-fill: " + BORDER_SUBTLE + ";");
                arrow.setAlignment(javafx.geometry.Pos.CENTER);
                arrow.setPadding(new Insets(8, 2, 0, 2));
                stepsRow.getChildren().add(arrow);
            }
        }
        box.getChildren().addAll(title, stepsRow);
        return box;
    }

    private void updateDealerStatus() {
        if (dealerCount == 0) {
            dealerStatusLabel
                    .setText("Go to Dealer Portal → Register at least ONE dealer with car inventory (Required first!)");
            dealerStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + ERROR_RED + "; -fx-font-weight: bold;");
        } else {
            dealerStatusLabel.setText("/ " + dealerCount + " dealer agent(s) registered - Ready to accept buyers!");
            dealerStatusLabel
                    .setStyle("-fx-font-size: 13; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");
        }
    }

    private void updateBuyerStatus() {
        if (buyerCount == 0) {
            updateBuyerStatus.setText("Go to Buyer Portal → Register buyer(s) with desired car & budget");
            updateBuyerStatus.setStyle("-fx-font-size: 13; -fx-text-fill: " + ERROR_RED + "; -fx-font-weight: bold;");
        } else {
            updateBuyerStatus.setText("/ " + buyerCount + " buyer agent(s) registered - Ready to accept dealers!");
            updateBuyerStatus
                    .setStyle("-fx-font-size: 13; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");
        }
    }

    private VBox createStatCard(String title, Label valueLabel, String color) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12;"
                + "-fx-border-color: " + color + "; -fx-border-width: 0 0 3 0;"
                + "-fx-border-radius: 12; -fx-effect: " + SOFT_SHADOW + ";");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-weight: 600;");
        valueLabel.setStyle("-fx-font-size: 28; -fx-font-weight: 700; -fx-text-fill: " + color + ";");
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private VBox createBuyerView(UILogger logger) {
        VBox box = new VBox(18);
        box.setPadding(new Insets(22));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("🧑 Buyer Portal");
        headerLabel.setStyle("-fx-font-size: 22; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");
        Label subLabel = new Label(
                "Register a buyer agent with a desired car and maximum budget. The broker will find matching dealers.");
        subLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + TEXT_MUTED + "; -fx-wrap-text: true;");

        // Prerequisite banner
        HBox prereqBanner = new HBox(8);
        prereqBanner.setPadding(new Insets(12, 16, 12, 16));
        prereqBanner.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        prereqBanner.setStyle("-fx-background-color: #fff7ed; -fx-background-radius: 10;"
                + "-fx-border-color: #fdba74; -fx-border-radius: 10; -fx-border-width: 1;");
        Label prereqIcon = new Label("⚠️");
        Label prereqText = new Label("Prerequisite: Register at least one Dealer first (Dealer Portal → List Car).");
        prereqText.setStyle("-fx-font-size: 12; -fx-text-fill: #92400e; -fx-font-weight: 600;");
        prereqBanner.getChildren().addAll(prereqIcon, prereqText);

        // Form card
        VBox card = new VBox(16);
        card.setPadding(new Insets(22));
        card.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12;"
                + "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

        Label formTitle = new Label("Add Buyer Agent");
        formTitle.setStyle("-fx-font-size: 16; -fx-font-weight: 700; -fx-text-fill: " + DARK_TEXT + ";");

        GridPane form = new GridPane();
        form.setHgap(18);
        form.setVgap(14);

        TextField buyerName = createStyledTextField("e.g., Ali Hassan");
        ComboBox<String> carModel = createStyledCarComboBox();
        TextField budget = createStyledTextField("e.g., 120000");

        form.add(makeFieldLabel("Buyer Name", "Unique agent identifier"), 0, 0);
        form.add(buyerName, 1, 0);
        form.add(makeFieldLabel("Desired Car", "Car model to search for"), 0, 1);
        form.add(carModel, 1, 1);
        form.add(makeFieldLabel("Max Budget (RM)", "Upper limit — buyer opens at ~70%"), 0, 2);
        form.add(budget, 1, 2);
        GridPane.setHgrow(buyerName, Priority.ALWAYS);
        GridPane.setHgrow(carModel, Priority.ALWAYS);
        GridPane.setHgrow(budget, Priority.ALWAYS);

        CheckBox manualControlCheck = new CheckBox("Manual Negotiation Mode (Wait for my input)");
        manualControlCheck.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");

        Button addBuyerBtn = createStyledButton("🧑 Add Buyer Agent", ACCENT_BLUE);
        addBuyerBtn.setPrefWidth(240);
        addBuyerBtn.setOnAction(e -> {
            String name = buyerName.getText().trim();
            String car = carModel.getValue() != null ? carModel.getValue() : "";
            String budgetStr = budget.getText().trim();
            if (name.isEmpty() || car.isEmpty() || budgetStr.isEmpty()) {
                showAlert("⚠️ All fields are required!", Alert.AlertType.WARNING);
                return;
            }
            if (dealerCount == 0) {
                showAlert("❌ No dealers registered!\nPlease register a dealer first.", Alert.AlertType.ERROR);
                return;
            }
            try {
                double b = Double.parseDouble(budgetStr);
                if (b <= 0) {
                    showAlert("❌ Budget must be > 0", Alert.AlertType.WARNING);
                    return;
                }
                boolean isManual = manualControlCheck.isSelected();
                cc.createNewAgent(name, "org.example.agents.BuyerAgent",
                        new Object[] { car, budgetStr, logger, buildNegotiationConfig(), true, isManual }).start();
                buyerAgents.add(name);
                if (isManual) {
                    manualBuyerAgents.add(name);
                    if (manualBuyerSelect != null && manualBuyerSelect.getValue() == null) {
                        manualBuyerSelect.setValue(name);
                    }
                }
                waitingBuyerAgents.add(name);
                updateNegotiationControlStatus();
                logger.log("Buyer '" + name + "' added — " + car + " @ RM" + budgetStr);
                launchSniffer(logger);
                buyerName.clear();
                carModel.setValue(null);
                budget.clear();
                showAlert("✅ Buyer " + name + " added. Press ▶ Start.", Alert.AlertType.INFORMATION);
            } catch (NumberFormatException ex) {
                showAlert("❌ Budget must be a valid number.", Alert.AlertType.ERROR);
            } catch (Exception ex) {
                logger.log("❌ Error: " + ex.getMessage());
                showAlert("❌ " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        card.getChildren().addAll(formTitle, manualControlCheck, form, addBuyerBtn);
        box.getChildren().addAll(headerLabel, subLabel, prereqBanner, card);
        VBox.setVgrow(card, Priority.SOMETIMES);
        return box;
    }

    private VBox makeFieldLabel(String title, String subtitle) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + "; -fx-font-weight: 600;");
        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-font-size: 11; -fx-text-fill: " + TEXT_MUTED + ";");
        return new VBox(2, titleLbl, subLbl);
    }

    /** Horizontal toolbar replacing the old collapsible sidebar. */
    private HBox createActionBar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + SURFACE + "; "
                + "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 0 0 1 0;");

        Button demoBtn = createBarButton("\u26a1 Demo", PRIMARY_BLUE);
        Button startBtn = createBarButton("\u25b6 Start", SUCCESS_GREEN);
        playPauseBtn = createBarButton("\u23f8 Pause", WARNING_ORANGE);
        Button stepBtn = createBarButton("\u23ed Step", ACCENT_BLUE);
        Button sniffBtn = createBarButton("\ud83d\udd0d Sniffer", "#6366f1");

        demoBtn.setOnAction(e -> createDemoScenario());
        startBtn.setOnAction(e -> {
            if (waitingBuyerAgents.isEmpty()) {
                showAlert("No waiting buyers.", Alert.AlertType.INFORMATION);
                return;
            }
            for (String b : new ArrayList<>(waitingBuyerAgents))
                sendAgentCommand(b, "START_NEGOTIATION");
            loggerLog("Started " + waitingBuyerAgents.size() + " buyer(s).");
            waitingBuyerAgents.clear();
            isAutoPlay = true;
            playPauseBtn.setText("\u23f8 Pause");
            sendSpaceCommand("RESUME");
            updateNegotiationControlStatus();
        });
        playPauseBtn.setOnAction(e -> {
            toggleAutoplay();
            updateNegotiationControlStatus();
        });
        stepBtn.setOnAction(e -> sendSpaceCommand("STEP"));
        sniffBtn.setOnAction(e -> launchSniffer(msg -> logArea.appendText(msg + "\n")));

        // ── Speed slider ──────────────────────────────────────────────────────
        // Tick positions 0-6 map to: 0.25×, 0.5×, 1× (default), 2×, 5×
        // Delay in ms: 4000, 2000, 1000, 500, 200, 100, 50
        long[] speedDelays = { 4000, 2000, 1000, 500, 200, 100, 50 };
        String[] speedLabels = { "0.25×", "0.5×", "1×", "2×", "5×", "10×", "20×" };

        Slider speedSlider = new Slider(0, speedDelays.length - 1, 2); // default = index 2 → 1000 ms
        speedSlider.setMajorTickUnit(1);
        speedSlider.setMinorTickCount(0);
        speedSlider.setSnapToTicks(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setShowTickLabels(false);
        speedSlider.setPrefWidth(130);
        speedSlider.setStyle("-fx-padding: 0 4;");

        Label speedLabel = new Label("1×");
        speedLabel.setStyle("-fx-font-size: 11; -fx-font-weight: 700; -fx-text-fill: " + TEXT_MUTED
                + "; -fx-min-width: 34; -fx-alignment: center;");
        Tooltip speedTip = new Tooltip("Cycle delay: 1000 ms");
        Tooltip.install(speedSlider, speedTip);

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int idx = (int) Math.round(newVal.doubleValue());
            idx = Math.max(0, Math.min(idx, speedDelays.length - 1));
            long delayMs = speedDelays[idx];
            speedLabel.setText(speedLabels[idx]);
            speedTip.setText("Cycle delay: " + delayMs + " ms");
            sendSpeedCommand(delayMs);
        });

        Label speedIconLabel = new Label("\u23f1");
        speedIconLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + TEXT_MUTED + ";");
        HBox speedBox = new HBox(4, speedIconLabel, speedSlider, speedLabel);
        speedBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        speedBox.setPadding(new Insets(0, 6, 0, 6));
        // ─────────────────────────────────────────────────────────────────────

        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep.setPadding(new Insets(0, 4, 0, 4));
        Separator sep2 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep2.setPadding(new Insets(0, 4, 0, 4));

        negotiationControlStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + TEXT_MUTED + ";");
        updateNegotiationControlStatus();

        updateDealerStatus();
        updateBuyerStatus();

        bar.getChildren().addAll(demoBtn, startBtn, playPauseBtn, stepBtn, sniffBtn,
                sep2, speedBox,
                sep, negotiationControlStatusLabel);
        return bar;
    }

    /**
     * Sends a SET_SPEED command to SpaceControl with the desired cycle delay in
     * milliseconds.
     */
    private void sendSpeedCommand(long delayMs) {
        try {
            cc.createNewAgent(nextAgentName("speed-command"), "org.example.agents.SpaceCommandAgent",
                    new Object[] { "SET_SPEED", "space", String.valueOf(delayMs) }).start();
        } catch (Exception e) {
            System.err.println("Error sending SET_SPEED: " + e.getMessage());
        }
    }

    private Button createBarButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 12; -fx-font-weight: 600; -fx-padding: 7 14; "
                + "-fx-background-color: " + color + "; -fx-text-fill: white; "
                + "-fx-background-radius: 8; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }

    private void createDemoScenario() {
        long demoId = demoScenarioCounter.incrementAndGet();
        NegotiationConfig config = buildDemoNegotiationConfig();

        try {
            String[][] dealers = new String[][] {
                    { "DemoAutoA-" + demoId, "Toyota Camry", "100000", "2" },
                    { "DemoAutoB-" + demoId, "Toyota Camry", "96000", "2" },
                    { "DemoAutoC-" + demoId, "Toyota Camry", "92000", "1" },
                    { "BudgetCars-" + demoId, "Honda Civic", "87000", "2" },
                    { "FamilyDrive-" + demoId, "Honda CR-V", "145000", "1" },
                    { "TruckHub-" + demoId, "Toyota Fortuner", "180000", "1" }
            };
            for (String[] dealer : dealers) {
                createDemoDealer(dealer[0], dealer[1], dealer[2], dealer[3], config);
            }

            String[][] buyers = new String[][] {
                    { "DemoBuyerPremium-" + demoId, "Toyota Camry", "116000" },
                    { "DemoBuyerStubborn-" + demoId, "Toyota Camry", "108000" },
                    { "DemoBuyerTight-" + demoId, "Toyota Camry", "98000" },
                    { "DemoBuyerCivic-" + demoId, "Honda Civic", "102000" },
                    { "DemoBuyerSUV-" + demoId, "Honda CR-V", "150000" },
                    { "DemoBuyerStretch-" + demoId, "Toyota Fortuner", "168000" },
                    { "DemoBuyerBudget-" + demoId, "Toyota Camry", "65000" },
                    { "DemoBuyerOverdrive-" + demoId, "Toyota Camry", "112000" }
            };
            for (String[] buyer : buyers) {
                createDemoBuyer(buyer[0], buyer[1], buyer[2], config);
            }

            dealerCount += dealers.length;
            dealerCountLabel.setText(String.valueOf(dealerCount));
            updateDealerStatus();
            buyerCount += buyers.length;
            buyerCountLabel.setText(String.valueOf(buyerCount));
            updateBuyerStatus();
            updateNegotiationControlStatus();

            launchSniffer(appLogger);

            loggerLog("Demo scenario " + demoId + " added: " + dealers.length + " dealers and " + buyers.length
                    + " waiting buyers. Press Start to stress test negotiation and strategy switching.");
            showAlert("Demo scenario added. Press Start to begin negotiation.", Alert.AlertType.INFORMATION);
        } catch (Exception ex) {
            showAlert("❌ Error creating demo scenario: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private NegotiationConfig buildDemoNegotiationConfig() {
        NegotiationConfig base = buildNegotiationConfig();
        return new NegotiationConfig(
                base.getStrategy(),
                Math.max(base.getDeadlineCycles(), 30),
                Math.min(base.getBuyerStartPercent(), 0.62),
                Math.max(base.getDealerReservePercent(), 0.78),
                Math.max(base.getMaxRoundsPerDealer(), 6),
                Math.max(base.getMaxSearchRetries(), 1),
                Math.max(base.getStuckRoundsBeforeAcceleration(), 1),
                base.getManualDealerTargetPercent(),
                base.getStrategySwitchCycle() > 0 ? Math.min(base.getStrategySwitchCycle(), 2) : 2,
                base.getSwitchStrategy() == base.getStrategy() ? NegotiationConfig.Strategy.CONCEDER
                        : base.getSwitchStrategy());
    }

    private void createDemoDealer(String name, String car, String price, String stock, NegotiationConfig config)
            throws Exception {
        cc.createNewAgent(name, "org.example.agents.DealerAgent",
                new Object[] { car, price, stock, appLogger, config }).start();
        loggerLog("Dealer '" + name + "' listed " + car + " @ RM" + price + " | Stock: " + stock);
        dealerAgents.add(name);
    }

    private void createDemoBuyer(String name, String car, String budget, NegotiationConfig config) throws Exception {
        cc.createNewAgent(name, "org.example.agents.BuyerAgent",
                new Object[] { car, budget, appLogger, config, true }).start();
        buyerAgents.add(name);
        waitingBuyerAgents.add(name);
        loggerLog("Buyer '" + name + "' added and waiting - " + car + " budget RM" + budget);
    }

    private VBox createDealerView(UILogger logger) {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("Dealer Portal");
        headerLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");
        // Info banner
        VBox infoBanner = new VBox();
        infoBanner.setPadding(new Insets(15));
        infoBanner.setStyle(
                "-fx-background-color: #eff6ff; -fx-background-radius: 12; -fx-border-color: #93c5fd; -fx-border-radius: 12; -fx-border-width: 1;");
        Label infoText = new Label("Register car inventory here first. Buyers will negotiate with available dealers.");
        infoText.setStyle("-fx-font-size: 13; -fx-text-fill: #0c4a6e; -fx-font-weight: bold; -fx-wrap-text: true;");
        infoBanner.getChildren().add(infoText);
        VBox formSection = new VBox(18);
        formSection.setPadding(new Insets(25));
        formSection.setStyle("-fx-background-color: " + SURFACE
                + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE
                + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

        Label formTitle = new Label("Register and List New Car");
        formTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setPadding(new Insets(15, 0, 15, 0));

        TextField dealerName = createStyledTextField("e.g., GreenCars Sdn Bhd");
        ComboBox<String> carModel = createStyledCarComboBox();
        TextField retailPrice = createStyledTextField("e.g., 150000");
        TextField stockField = createStyledTextField("e.g., 3");

        Label dealerLabel = new Label("Dealer Name:");
        dealerLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");
        Label carLbl = new Label("Car Model:");
        carLbl.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");
        Label priceLabel = new Label("Retail Price (RM):");
        priceLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");
        Label stockLabel = new Label("Stock Quantity:");
        stockLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");

        form.add(dealerLabel, 0, 0);
        form.add(dealerName, 1, 0);
        form.add(carLbl, 0, 1);
        form.add(carModel, 1, 1);
        form.add(priceLabel, 0, 2);
        form.add(retailPrice, 1, 2);
        form.add(stockLabel, 0, 3);
        form.add(stockField, 1, 3);

        Button addDealerBtn = createStyledButton("List Car", WARNING_ORANGE);
        addDealerBtn.setPrefWidth(280);

        addDealerBtn.setOnAction(e -> {
            String name = dealerName.getText().trim();
            String car = carModel.getValue() != null ? carModel.getValue() : "";
            String price = retailPrice.getText().trim();
            String stock = stockField.getText().trim();

            if (name.isEmpty() || car.isEmpty() || price.isEmpty() || stock.isEmpty()) {
                showAlert("⚠️ All fields are required!", Alert.AlertType.WARNING);
                return;
            }

            try {
                // Validate price is numeric
                double priceAmount = Double.parseDouble(price);
                if (priceAmount <= 0) {
                    showAlert("❌ Price must be greater than 0", Alert.AlertType.WARNING);
                    return;
                }
                int stockAmount = Integer.parseInt(stock);
                if (stockAmount <= 0) {
                    showAlert("❌ Stock must be at least 1", Alert.AlertType.WARNING);
                    return;
                }

                cc.createNewAgent(name, "org.example.agents.DealerAgent",
                        new Object[] { car, price, stock, logger, buildNegotiationConfig() }).start();
                logger.log("Dealer '" + name + "' listed " + car + " @ RM" + price + " | Stock: " + stock);
                dealerAgents.add(name);
                // Try to refresh sniffer to include the newly added dealer
                launchSniffer(logger);
                dealerCount++;
                dealerCountLabel.setText(String.valueOf(dealerCount));
                updateDealerStatus();
                dealerName.clear();
                carModel.setValue(null);
                retailPrice.clear();
                stockField.clear();
                showAlert("✅ Dealer " + name + " registered with " + stock + " unit(s)!", Alert.AlertType.INFORMATION);
                ;
            } catch (NumberFormatException ex) {
                showAlert("❌ Price and Stock must be valid numbers", Alert.AlertType.ERROR);
            } catch (Exception ex) {
                logger.log("❌ Error creating dealer: " + ex.getMessage());
                showAlert("❌ Error: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        HBox btnBox = new HBox(addDealerBtn);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        formSection.getChildren().addAll(formTitle, form, btnBox);

        box.getChildren().addAll(headerLabel, infoBanner, formSection);
        VBox.setVgrow(formSection, Priority.SOMETIMES);

        return box;
    }

    private VBox createMarketAnalysisView() {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("Market Analytics");
        headerLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        TextArea analysisArea = new TextArea();
        analysisArea.setEditable(false);
        analysisArea.setWrapText(true);
        analysisArea.setStyle("-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: "
                + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + ";");
        analysisArea.setText(
                "MARKET ANALYTICS DASHBOARD\n"
                        + "═══════════════════════════════════════════════\n\n"
                        + "SYSTEM OVERVIEW:\n"
                        + "  ✓ Broker-Routed Multi-Agent Negotiation (JADE)\n"
                        + "  ✓ All messages relay through BrokerAgent\n"
                        + "  ✓ Session-based negotiation with unique IDs\n"
                        + "  ✓ Cycle-based concession using SpaceControl\n\n"
                        + "BROKER FEE POLICY:\n"
                        + "  • Fixed Session Fee:  RM 50 (charged at session start)\n"
                        + "  • Commission:         5% of final sale price (on deal only)\n"
                        + "  • No-deal sessions:   Fixed fee still collected\n"
                        + "  • Example: RM 100k sale = RM 5,000 commission + RM 50 fee\n\n"
                        + "NEGOTIATION PROTOCOL (Broker-Routed):\n"
                        + "  1. Dealer registers car listing with broker\n"
                        + "  2. Buyer sends BUYER_SEARCH to broker\n"
                        + "  3. Broker returns BROKER_SHORTLIST to buyer\n"
                        + "  4. Buyer sends BUYER_SHORTLIST (selects dealer + first offer)\n"
                        + "  5. Broker creates session, charges RM 50 fee, invites dealer\n"
                        + "  6. Dealer replies DEALER_COUNTER or DEALER_ACCEPT to broker\n"
                        + "  7. Broker relays BROKER_RELAY_COUNTER / BROKER_RELAY_ACCEPT\n"
                        + "  8. Buyer replies BUYER_COUNTER or BUYER_WALKAWAY to broker\n"
                        + "  9. On DEALER_ACCEPT: broker charges commission, notifies buyer\n\n"
                        + "CYCLE-BASED CONCESSION (SPACE CONTROL):\n"
                        + "  • Deadline:   50 market cycles (configurable)\n"
                        + "  • Dealer:     Lowers ask price as cycles increase\n"
                        + "  • Buyer:      Raises willing offer as cycles increase\n"
                        + "  • Formula:    Price(t) = P0 - (P0 - Pres) * (t/T)^\u03b2\n"
                        + "  • Strategies: BOULWARE (β=2), LINEAR (β=1), CONCEDER (β=0.45)\n\n"
                        + "CURRENT METRICS: See Dashboard and Sessions tabs.\n"
                        + "SETTINGS:        Adjust parameters in the panel above.\n"
                        + "═══════════════════════════════════════════════");

        /*
         * ScrollPane scrollPane = new ScrollPane(analysisArea);
         * scrollPane.setFitToWidth(true);
         * scrollPane.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1;");
         * 
         * box.getChildren().addAll(headerLabel, scrollPane);
         * VBox.setVgrow(scrollPane, Priority.ALWAYS);
         */

        analysisArea.setStyle("-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: "
                + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + "; -fx-border-color: "
                + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        box.getChildren().addAll(headerLabel, createSimulationControlPanel(), analysisArea);
        VBox.setVgrow(analysisArea, Priority.ALWAYS);
        // --------------------------------------------------

        return box;
    }

    private VBox createSimulationControlPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: " + SURFACE
                + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE
                + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

        Label title = new Label("Negotiation Settings");
        title.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        strategyChoice = new ComboBox<>();
        strategyChoice.getItems().addAll("BOULWARE", "CONCEDER", "LINEAR");
        strategyChoice.setValue("BOULWARE");
        strategyChoice.setPrefWidth(180);

        switchStrategyChoice = new ComboBox<>();
        switchStrategyChoice.getItems().addAll("BOULWARE", "CONCEDER", "LINEAR");
        switchStrategyChoice.setValue("CONCEDER");
        switchStrategyChoice.setPrefWidth(180);

        deadlineCyclesField = createStyledTextField("50");
        deadlineCyclesField.setText("50");
        strategySwitchCycleField = createStyledTextField("8");
        strategySwitchCycleField.setText("8");
        buyerStartPercentField = createStyledTextField("70");
        buyerStartPercentField.setText("70");
        reservePercentField = createStyledTextField("70");
        reservePercentField.setText("70");
        maxRoundsField = createStyledTextField("3");
        maxRoundsField.setText("3");
        retryLimitField = createStyledTextField("2");
        retryLimitField.setText("2");
        stuckRoundsField = createStyledTextField("2");
        stuckRoundsField.setText("2");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label("Strategy:"), 0, 0);
        grid.add(strategyChoice, 1, 0);
        grid.add(new Label("Deadline cycles:"), 2, 0);
        grid.add(deadlineCyclesField, 3, 0);
        grid.add(new Label("Switch at cycle:"), 0, 1);
        grid.add(strategySwitchCycleField, 1, 1);
        grid.add(new Label("Then use strategy:"), 2, 1);
        grid.add(switchStrategyChoice, 3, 1);
        grid.add(new Label("Buyer start % of budget:"), 0, 2);
        grid.add(buyerStartPercentField, 1, 2);
        grid.add(new Label("Dealer reserve % of price:"), 2, 2);
        grid.add(reservePercentField, 3, 2);
        grid.add(new Label("Max rounds / dealer:"), 0, 3);
        grid.add(maxRoundsField, 1, 3);
        grid.add(new Label("Search retries:"), 2, 3);
        grid.add(retryLimitField, 3, 3);
        grid.add(new Label("Stuck rounds (accelerate):"), 0, 4);
        grid.add(stuckRoundsField, 1, 4);

        Label manualTitle = new Label("Manual Dealer Price Override");
        manualTitle.setStyle("-fx-font-size: 13; -fx-font-weight: 700; -fx-text-fill: " + TEXT_MUTED + ";");
        manualDealerNameField = createStyledTextField("Dealer agent name (e.g. GreenCars)");
        manualDealerPriceField = createStyledTextField("New asking price (e.g. 95000)");
        Button adjustPriceBtn = createStyledButton("Send Override", SUCCESS_GREEN);
        adjustPriceBtn.setOnAction(e -> {
            String dealer = manualDealerNameField.getText().trim();
            String price = manualDealerPriceField.getText().trim();
            if (dealer.isEmpty() || price.isEmpty()) {
                showAlert("Dealer name and price are required.", Alert.AlertType.WARNING);
                return;
            }
            try {
                Integer.parseInt(price);
                sendDealerPriceAdjustment(dealer, price);
                showAlert("Price override sent to " + dealer, Alert.AlertType.INFORMATION);
            } catch (NumberFormatException ex) {
                showAlert("Price must be a valid integer.", Alert.AlertType.ERROR);
            }
        });
        HBox manualControls = new HBox(12, manualDealerNameField, manualDealerPriceField, adjustPriceBtn);
        panel.getChildren().addAll(title, grid, new Separator(), manualTitle, manualControls);
        return panel;
    }

    private NegotiationConfig buildNegotiationConfig() {
        if (strategyChoice == null) {
            return NegotiationConfig.defaults();
        }

        try {
            NegotiationConfig.Strategy strategy = NegotiationConfig.Strategy.valueOf(strategyChoice.getValue());
            int deadline = Math.max(1, Integer.parseInt(deadlineCyclesField.getText().trim()));
            double buyerStart = percentFieldToRatio(buyerStartPercentField, 70);
            double reserve = percentFieldToRatio(reservePercentField, 70);
            int maxRounds = Math.max(1, Integer.parseInt(maxRoundsField.getText().trim()));
            int retryLimit = Math.max(0, Integer.parseInt(retryLimitField.getText().trim()));
            int stuckRounds = Math.max(1, Integer.parseInt(stuckRoundsField.getText().trim()));
            int switchCycle = Math.max(0, Integer.parseInt(strategySwitchCycleField.getText().trim()));
            NegotiationConfig.Strategy switchStrategy = NegotiationConfig.Strategy
                    .valueOf(switchStrategyChoice.getValue());
            return new NegotiationConfig(strategy, deadline, buyerStart, reserve, maxRounds, retryLimit,
                    stuckRounds, 1.0, switchCycle, switchStrategy);
        } catch (Exception e) {
            showAlert("Invalid negotiation settings. Defaults will be used.", Alert.AlertType.WARNING);
            return NegotiationConfig.defaults();
        }
    }

    private double percentFieldToRatio(TextField field, int fallbackPercent) {
        String raw = field.getText().trim();
        double value = raw.isEmpty() ? fallbackPercent : Double.parseDouble(raw);
        value = Math.max(1, Math.min(100, value));
        return value / 100.0;
    }

    private VBox createActivityLogView() {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("System Activity Log");
        headerLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        TextArea fullLogArea = new TextArea();
        fullLogArea.setEditable(false);
        fullLogArea.setWrapText(true);
        fullLogArea.setStyle("-fx-font-size: 13; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: "
                + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + ";");

        logArea.textProperty().addListener((obs, oldVal, newVal) -> {
            fullLogArea.setText(newVal);
            fullLogArea.setScrollTop(Double.MAX_VALUE);
        });

        // Remove the redundant ScrollPane (TextArea is already scrollable)
        // and allow the TextArea to expand to fill available height.
        fullLogArea.setStyle("-fx-font-size: 13; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: "
                + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + "; -fx-border-color: "
                + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        // ----------------------------------------------------

        HBox controlBox = new HBox(12);
        controlBox.setPadding(new Insets(15));
        controlBox.setStyle("-fx-background-color: " + SURFACE
                + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE
                + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

        Button copyBtn = createStyledButton("Copy Log", ACCENT_BLUE);
        copyBtn.setPrefWidth(120);
        copyBtn.setOnAction(e -> {
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(logArea.getText());
            clipboard.setContent(content);
            showAlert("Log copied to clipboard!", Alert.AlertType.INFORMATION);
        });

        Button clearBtn = createStyledButton("Clear Log", ERROR_RED);
        clearBtn.setPrefWidth(120);
        clearBtn.setOnAction(e -> {
            logArea.clear();
            fullLogArea.clear();
        });

        controlBox.getChildren().addAll(copyBtn, clearBtn);

        box.getChildren().addAll(headerLabel, fullLogArea, controlBox);
        VBox.setVgrow(fullLogArea, Priority.ALWAYS);
        // -------------------------

        return box;
    }

    private VBox createFailuresView() {
        VBox box = new VBox(18);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("Failed Negotiations");
        headerLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        failuresArea.setEditable(false);
        failuresArea.setWrapText(true);
        failuresArea.setStyle(
                "-fx-font-size: 12; -fx-font-family: 'Courier New'; -fx-control-inner-background: white; -fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-border-radius: 4;");
        failuresArea.setPrefRowCount(15);

        HBox controlBox = new HBox(12);
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dbe4ef; -fx-border-width: 1;");

        Button copyBtn = createStyledButton("Copy Failures", ACCENT_BLUE);
        copyBtn.setOnAction(e -> {
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(failuresArea.getText());
            clipboard.setContent(content);
            showAlert("Failures copied to clipboard!", Alert.AlertType.INFORMATION);
        });

        Button clearBtn = createStyledButton("Clear Failures", ERROR_RED);
        clearBtn.setOnAction(e -> {
            failuresArea.clear();
            failedDeals.clear();
        });

        controlBox.getChildren().addAll(copyBtn, clearBtn);

        box.getChildren().addAll(headerLabel, failuresArea, controlBox);
        VBox.setVgrow(failuresArea, Priority.ALWAYS);
        return box;
    }

    /** Sessions tab — live log of session start, settle, and fail events */
    private VBox createSessionsView() {
        VBox box = new VBox(18);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label header = new Label("Negotiation Sessions");
        header.setStyle("-fx-font-size: 26; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");

        // Mini stat row
        HBox miniStats = new HBox(16);
        VBox activeCard = createStatCard("Active", activeSessionsLabelMini, "#8b5cf6");
        VBox feesCard = createStatCard("💵 Fixed Fees", fixedFeesLabelMini, "#06b6d4");
        VBox commCard = createStatCard("Commission", commissionLabel, SUCCESS_GREEN);
        for (VBox c : new VBox[] { activeCard, feesCard, commCard }) {
            c.setPrefWidth(180);
            miniStats.getChildren().add(c);
        }

        // Hint label
        Label hint = new Label(
                "Each row below is a SESSION START, DEAL SETTLED, or NO DEAL event logged by the broker.");
        hint.setStyle("-fx-font-size: 12; -fx-text-fill: " + TEXT_MUTED + "; -fx-wrap-text: true;");
        hint.setMaxWidth(Double.MAX_VALUE);

        // Session event log
        sessionsArea.setEditable(false);
        sessionsArea.setWrapText(true);
        sessionsArea.setStyle("-fx-font-size: 12; -fx-font-family: 'Courier New'; "
                + "-fx-control-inner-background: " + SURFACE + "; "
                + "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; "
                + "-fx-border-radius: 8; -fx-background-radius: 8;");

        HBox ctrlBox = new HBox(12);
        ctrlBox.setPadding(new Insets(12));
        ctrlBox.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 10; "
                + "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1;");
        Button clearBtn = createStyledButton("Clear", ERROR_RED);
        clearBtn.setOnAction(e -> sessionsArea.clear());
        Button copyBtn = createStyledButton("Copy", ACCENT_BLUE);
        copyBtn.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(sessionsArea.getText());
            cb.setContent(content);
            showAlert("Session log copied!", Alert.AlertType.INFORMATION);
        });
        ctrlBox.getChildren().addAll(copyBtn, clearBtn);

        box.getChildren().addAll(header, miniStats, hint, sessionsArea, ctrlBox);
        VBox.setVgrow(sessionsArea, Priority.ALWAYS);
        return box;
    }

    private VBox createManualPlayView() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label title = new Label("🎮 Manual Mode");
        title.setStyle("-fx-font-size: 26; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");

        HBox topBox = new HBox(15);
        topBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        manualBuyerSelect = new ComboBox<>(manualBuyerAgents);
        manualBuyerSelect.setPromptText("Select Manual Buyer");
        manualBuyerSelect.setPrefWidth(200);
        topBox.getChildren().addAll(new Label("Controlling:"), manualBuyerSelect);

        manualLogArea = new TextArea();
        manualLogArea.setEditable(false);
        manualLogArea.setWrapText(true);
        manualLogArea.setStyle("-fx-font-size: 13; -fx-font-family: 'Courier New';");

        VBox actionPanel = new VBox(10);
        actionPanel.setPadding(new Insets(15));
        actionPanel.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE
                + "; -fx-border-width: 1; -fx-background-radius: 8;");

        Label actionTitle = new Label("Action Panel");
        actionTitle.setStyle("-fx-font-weight: bold;");

        // Shortlist controls
        HBox shortlistBox = new HBox(10);
        shortlistBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        manualDealerSelect = new ComboBox<>();
        manualDealerSelect.setPromptText("Select Dealer");
        manualFirstOfferField = new TextField();
        manualFirstOfferField.setPromptText("First Offer RM");
        manualSendFirstOfferBtn = new Button("Send First Offer");
        manualSendFirstOfferBtn.setDisable(true);
        shortlistBox.getChildren().addAll(manualDealerSelect, manualFirstOfferField, manualSendFirstOfferBtn);

        // Negotiation controls
        HBox counterBox = new HBox(10);
        counterBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        manualCounterPriceField = new TextField();
        manualCounterPriceField.setPromptText("Counter RM");
        manualSendCounterBtn = new Button("Send Counter");
        manualAcceptDealBtn = new Button("Accept Offer");
        manualWalkAwayBtn = new Button("Walk Away");

        manualSendCounterBtn.setDisable(true);
        manualAcceptDealBtn.setDisable(true);
        manualWalkAwayBtn.setDisable(true);

        counterBox.getChildren().addAll(manualCounterPriceField, manualSendCounterBtn, manualAcceptDealBtn,
                manualWalkAwayBtn);

        actionPanel.getChildren().addAll(actionTitle, shortlistBox, counterBox);

        // Actions
        manualSendFirstOfferBtn.setOnAction(e -> {
            String dealer = manualDealerSelect.getValue();
            String offer = manualFirstOfferField.getText().trim();
            if (dealer != null && !offer.isEmpty()) {
                sendAgentCommand(manualBuyerSelect.getValue(), "MANUAL_ACTION", "SHORTLIST;" + dealer + ";" + offer);
                manualLogArea.appendText("\n[YOU] Picked " + dealer + " with RM " + offer);
                manualSendFirstOfferBtn.setDisable(true);
            }
        });

        manualSendCounterBtn.setOnAction(e -> {
            String offer = manualCounterPriceField.getText().trim();
            if (!offer.isEmpty()) {
                sendAgentCommand(manualBuyerSelect.getValue(), "MANUAL_ACTION", "COUNTER;" + offer);
                manualLogArea.appendText("\n[YOU] Countered RM " + offer);
                disableCounterControls();
            }
        });

        manualAcceptDealBtn.setOnAction(e -> {
            String offer = manualCounterPriceField.getText().trim();
            sendAgentCommand(manualBuyerSelect.getValue(), "MANUAL_ACTION", "ACCEPT;" + offer);
            manualLogArea.appendText("\n[YOU] Accepted RM " + offer);
            disableCounterControls();
        });

        manualWalkAwayBtn.setOnAction(e -> {
            sendAgentCommand(manualBuyerSelect.getValue(), "MANUAL_ACTION", "WALKAWAY;");
            manualLogArea.appendText("\n[YOU] Walked away.");
            disableCounterControls();
        });

        box.getChildren().addAll(title, topBox, manualLogArea, actionPanel);
        VBox.setVgrow(manualLogArea, Priority.ALWAYS);
        return box;
    }

    private void disableCounterControls() {
        manualSendCounterBtn.setDisable(true);
        manualAcceptDealBtn.setDisable(true);
        manualWalkAwayBtn.setDisable(true);
    }

    private void handleManualPromptLog(String msg) {
        try {
            int promptIdx = msg.indexOf("[MANUAL_PROMPT]");
            if (promptIdx == -1)
                return;

            String beforePrompt = msg.substring(0, promptIdx);
            int colonIdx = beforePrompt.lastIndexOf(":");
            if (colonIdx == -1)
                return;
            String agentName = beforePrompt.substring(0, colonIdx).trim();

            String payload = msg.substring(promptIdx + 16).trim();

            if (payload.startsWith("SHORTLIST:")) {
                String csv = payload.substring(10);
                manualLogArea.appendText("\n\n[" + agentName + "] Received Shortlist Options:\n");
                for (String option : csv.split(",")) {
                    if (option.isEmpty())
                        continue;
                    String[] parts = option.split(":");
                    manualLogArea.appendText("  - " + parts[0] + " (Listed: RM" + parts[1] + ")\n");
                }
                if (agentName.equals(manualBuyerSelect.getValue())) {
                    manualDealerSelect.getItems().clear();
                    for (String d : csv.split(",")) {
                        if (!d.isEmpty())
                            manualDealerSelect.getItems().add(d.split(":")[0]);
                    }
                    manualSendFirstOfferBtn.setDisable(false);
                }
            } else if (payload.startsWith("COUNTER:")) {
                String[] p = payload.substring(8).split(":");
                String dealer = p[0];
                String price = p[1];
                manualLogArea.appendText("\n[" + agentName + "] " + dealer + " counters RM " + price);

                if (agentName.equals(manualBuyerSelect.getValue())) {
                    manualCounterPriceField.setText(price);
                    manualSendCounterBtn.setDisable(false);
                    manualAcceptDealBtn.setDisable(false);
                    manualWalkAwayBtn.setDisable(false);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing manual prompt: " + e.getMessage());
        }
    }

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(
                "-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM
                        + "; -fx-padding: 10 12; " +
                        "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-radius: 8; -fx-border-width: 1; " +
                        "-fx-control-inner-background: " + SURFACE + "; -fx-background-radius: 8; " +
                        "-fx-prompt-text-fill: #94a3b8;");
        return tf;
    }

    private ComboBox<String> createStyledCarComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(CAR_MODELS);
        comboBox.setEditable(true);
        comboBox.setPrefWidth(300);
        comboBox.setStyle(
                "-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM
                        + "; -fx-padding: 6 10; " +
                        "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-radius: 8; -fx-border-width: 1; " +
                        "-fx-control-inner-background: " + SURFACE + "; -fx-background-radius: 8;");
        comboBox.setPromptText("Select or type car model...");

        return comboBox;
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: 600; -fx-padding: 10 22; " +
                        "-fx-background-color: " + color + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-border-radius: 10; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.14), 10, 0, 0, 2);");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.88));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "❌ Error" : "ℹ️ Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public interface UILogger {
        void log(String message);
    }
}
