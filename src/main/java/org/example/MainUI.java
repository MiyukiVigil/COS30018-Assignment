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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

            boolean isBuyerReg = msg.contains("Buyer") && (msg.contains("registered") || msg.contains("added"));
            boolean isDealerReg = msg.contains("Dealer") && msg.contains("listed");
            boolean isRevenue = msg.contains("[BROKER] REVENUE:");
            boolean isDealConfirmed = msg.contains("[BROKER] DEAL CONFIRMED:"); // ★ CHANGED: detect deal confirmed
            boolean isPerformance = msg.contains("[BROKER] PERFORMANCE:");
            boolean isCycleShift = msg.contains("Cycle Shift:");
            boolean isSetupMsg = msg.contains("BROKER ONLINE") ||
                    msg.contains("Transaction Fee") ||
                    msg.contains("Initializing Space Control");
            boolean isPriceUpdate = msg.contains("RM") && (
                    msg.contains("has set buying price") ||
                    msg.contains("has set vehicle") ||
                    msg.contains("Buyer offered") ||
                    msg.contains("counter-offered") ||
                    msg.contains("COUNTER: Offered") ||
                    msg.contains("DEAL CLOSED") ||
                    msg.contains("SUCCESS! Purchased")
            );
            boolean isNegotiationAction = msg.contains("DEAL") || msg.contains("SUCCESS") ||
                    msg.contains("COUNTER") || msg.contains("OFFER") ||
                    msg.contains("AGREED") || msg.contains("STATUS:");

            Platform.runLater(() -> {
                if (isSetupMsg || isBuyerReg || isDealerReg || isCycleShift || isPriceUpdate || isNegotiationAction || isRevenue || isDealConfirmed || isPerformance) { // ★ CHANGED: added isDealConfirmed
                    logArea.appendText(formattedMsg);
                }

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

                if (isRevenue) {
                    try {
                        int rmIndex = msg.indexOf("RM");
                        if (rmIndex != -1) {
                            String amountStr = msg.substring(rmIndex + 2).trim().split(" ")[0];
                            totalRevenue += Double.parseDouble(amountStr);
                            revenueLabel.setText(String.format("RM %.2f", totalRevenue));
                        }
                    } catch (Exception e) {  }
                }

                // ★ CHANGED: update deals closed on DEAL CONFIRMED not REVENUE
                if (isDealConfirmed) {
                    dealsClosed++;
                    transactionCountLabel.setText(String.valueOf(dealsClosed));
                }

                if (isCycleShift) {
                    try {
                        currentCycle = Integer.parseInt(msg.substring(msg.indexOf(":") + 1).trim());
                    } catch (Exception e) { currentCycle++; }
                }

                if (isPriceUpdate) {
                    updatePriceChart(msg);
                }
            });
        };
        appLogger = logger;

        cc.createNewAgent("broker", "org.example.agents.BrokerAgent", new Object[]{logger}).start();
        cc.createNewAgent("space", "org.example.agents.SpaceControl", new Object[]{logger}).start();
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
            String agentName = msg.substring(0, msg.indexOf(":")).trim();
            Matcher matcher = RM_AMOUNT_PATTERN.matcher(msg);
            double amount = -1;
            while (matcher.find()) {
                amount = Double.parseDouble(matcher.group(1));
            }
            if (amount < 0) {
                return;
            }

            XYChart.Series<Number, Number> series = seriesMap.computeIfAbsent(agentName, k -> {
                XYChart.Series<Number, Number> s = new XYChart.Series<>();
                s.setName(k);
                if (priceChart != null) priceChart.getData().add(s);
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
        tp.setStyle(
            "-fx-font-size: 13; -fx-font-family: " + FONT_FAMILY + "; " +
                "-fx-background-color: transparent; -fx-tab-min-height: 40; -fx-tab-max-height: 40;"
        );

        Tab dashboardTab = new Tab("Dashboard", createBrokerView());
        dashboardTab.setClosable(false);
        Tab buyerTab = new Tab("Buyer Portal", createBuyerView(logger));
        buyerTab.setClosable(false);
        Tab dealerTab = new Tab("Dealer Portal", createDealerView(logger));
        dealerTab.setClosable(false);
        Tab analysisTab = new Tab("Market Analysis", createMarketAnalysisView());
        analysisTab.setClosable(false);
        Tab logTab = new Tab("Activity Log", createActivityLogView());
        logTab.setClosable(false);

        tp.getTabs().addAll(dashboardTab, buyerTab, dealerTab, analysisTab, logTab);
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + LIGHT_GRAY + "; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + ";");
        HBox mainRow = new HBox(18, tp, createGlobalControlBar());
        mainRow.setPadding(new Insets(16, 24, 24, 24));
        mainRow.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");
        HBox.setHgrow(tp, Priority.ALWAYS);
        VBox.setVgrow(mainRow, Priority.ALWAYS);
        root.getChildren().addAll(createAppHeader(), mainRow);
        return root;
    }

    private VBox createAppHeader() {
        VBox header = new VBox(6);
        header.setPadding(new Insets(18, 28, 16, 28));
        header.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Automated Car Negotiation System");
        title.setStyle("-fx-font-size: 26; -fx-font-weight: 700; -fx-text-fill: " + DARK_TEXT + ";");

        Label subtitle = new Label("JADE multi-agent marketplace with configurable negotiation, live ACL inspection, and performance tracking");
        subtitle.setStyle("-fx-font-size: 13; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + ";");

        header.getChildren().addAll(title, subtitle);
        return header;
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
                    new Object[]{command, "space", ""}).start();
        } catch (Exception e) {
            System.err.println("Error sending command to SpaceControl: " + e.getMessage());
        }
    }

    private void sendDealerPriceAdjustment(String dealerName, String price) {
        try {
            cc.createNewAgent(nextAgentName("dealer-command"), "org.example.agents.SpaceCommandAgent",
                    new Object[]{"PRICE_ADJUSTMENT", dealerName, price}).start();
        } catch (Exception e) {
            showAlert("❌ Error sending price adjustment: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void sendAgentCommand(String agentName, String command) {
        try {
            cc.createNewAgent(nextAgentName("agent-command"), "org.example.agents.SpaceCommandAgent",
                    new Object[]{command, agentName, ""}).start();
        } catch (Exception e) {
            showAlert("❌ Error sending command to " + agentName + ": " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void launchSniffer(UILogger logger) {
        try {
            cc.createNewAgent(nextAgentName("sniffer"), "jade.tools.sniffer.Sniffer",
                    new Object[]{"broker;space;DemoBuyer*;DemoAuto*;BudgetCars*"}).start();
            logger.log("STATUS: JADE Sniffer Agent launched for broker, space, and demo agents.");
        } catch (Exception e) {
            logger.log("STATUS: Sniffer not launched: " + e.getMessage());
        }
    }

    private String nextAgentName(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + commandAgentCounter.incrementAndGet();
    }

    private VBox createBrokerView() {
        VBox box = new VBox(18);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("Marketplace Dashboard");
        headerLabel.setStyle("-fx-font-size: 26; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");

        GridPane statsBox = createStatsCard();

//        logArea.setEditable(false);
//        logArea.setWrapText(true);
//        logArea.setPrefRowCount(22);
//        logArea.setStyle("-fx-font-size: 11; -fx-font-family: 'Courier New'; -fx-control-inner-background: white;");

//        ScrollPane logScroll = new ScrollPane(logArea);
//        logScroll.setFitToWidth(true);
//        logScroll.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1;");

        //Graph to display the relationship between proposed offers between buyer and dealer agents
        //against each cycles of market negotiation activity
        Label graphLabel = new Label("Negotiation Trajectory");
        graphLabel.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Negotiation Cycle");
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (RM)");
        yAxis.setForceZeroInRange(false);

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setAnimated(false);
        priceChart.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");
        priceChart.setMinHeight(260);
        priceChart.setPrefHeight(320);

        priceChart.getData().addAll(seriesMap.values());

        box.getChildren().addAll(headerLabel, statsBox, new Separator(), graphLabel, priceChart);
//        VBox.setVgrow(logScroll, Priority.ALWAYS);
        VBox.setVgrow(priceChart, Priority.ALWAYS);

        return box;
    }

    private GridPane createStatsCard() {
        GridPane statsBox = new GridPane();
        statsBox.setHgap(12);
        statsBox.setVgap(12);
        statsBox.setPadding(new Insets(16));
        statsBox.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

        VBox buyerCard = createStatCard("Active Buyers", buyerCountLabel, ACCENT_BLUE);
        VBox dealerCard = createStatCard("Active Dealers", dealerCountLabel, WARNING_ORANGE);
        VBox transactionCard = createStatCard("Deals Closed", transactionCountLabel, SUCCESS_GREEN);
        VBox revenueCard = createStatCard("Broker's Total Revenue", revenueLabel, "#ec4899");

        statsBox.add(buyerCard, 0, 0);
        statsBox.add(dealerCard, 1, 0);
        statsBox.add(transactionCard, 0, 1);
        statsBox.add(revenueCard, 1, 1);
        GridPane.setHgrow(buyerCard, Priority.ALWAYS);
        GridPane.setHgrow(dealerCard, Priority.ALWAYS);
        GridPane.setHgrow(transactionCard, Priority.ALWAYS);
        GridPane.setHgrow(revenueCard, Priority.ALWAYS);
        GridPane.setVgrow(buyerCard, Priority.ALWAYS);
        GridPane.setVgrow(dealerCard, Priority.ALWAYS);
        GridPane.setVgrow(transactionCard, Priority.ALWAYS);
        GridPane.setVgrow(revenueCard, Priority.ALWAYS);

        return statsBox;
    }

    private VBox createQuickGuideBox() {
        VBox guideBox = new VBox(10);
        guideBox.setPadding(new Insets(16));
        guideBox.setStyle("-fx-background-color: " + SURFACE_ALT + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");
        guideBox.setFillWidth(true);

        Label titleLabel = new Label("Quick Setup Guide");
        titleLabel.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        dealerStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        dealerStatusLabel.setWrapText(true);
        dealerStatusLabel.setMaxWidth(Double.MAX_VALUE);
        updateDealerStatus();

//        Label guideLine2 = new Label("Go to Buyer Portal → Register buyer(s) with desired car & budget");
//        guideLine2.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        updateBuyerStatus.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        updateBuyerStatus.setWrapText(true);
        updateBuyerStatus.setMaxWidth(Double.MAX_VALUE);
        updateBuyerStatus();

        Label guideLine3 = new Label("Watch Activity Log below for real-time negotiation updates ✓");
        guideLine3.setStyle("-fx-font-size: 13; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: 600;");
        guideLine3.setWrapText(true);
        guideLine3.setMaxWidth(Double.MAX_VALUE);

        guideBox.getChildren().addAll(titleLabel, dealerStatusLabel, updateBuyerStatus, guideLine3);
        return guideBox;
    }

    private void updateDealerStatus() {
        if (dealerCount == 0) {
            dealerStatusLabel.setText("Go to Dealer Portal → Register at least ONE dealer with car inventory (Required first!)");
            dealerStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + ERROR_RED + "; -fx-font-weight: bold;");
        } else {
            dealerStatusLabel.setText("/ " + dealerCount + " dealer agent(s) registered - Ready to accept buyers!");
            dealerStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");
        }
    }

    private void updateBuyerStatus() {
        if (buyerCount == 0) {
            updateBuyerStatus.setText("Go to Buyer Portal → Register buyer(s) with desired car & budget");
            updateBuyerStatus.setStyle("-fx-font-size: 13; -fx-text-fill: " + ERROR_RED + "; -fx-font-weight: bold;");
        } else {
            updateBuyerStatus.setText("/ " + buyerCount + " buyer agent(s) registered - Ready to accept dealers!");
            updateBuyerStatus.setStyle("-fx-font-size: 13; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");
        }
    }

    private VBox createStatCard(String title, Label valueLabel, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; -fx-border-color: " + color + "; -fx-border-width: 0 0 3 0; -fx-border-radius: 12; -fx-effect: " + SOFT_SHADOW + ";");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-weight: 600;");

        valueLabel.setStyle("-fx-font-size: 30; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private VBox createBuyerView(UILogger logger) {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("Buyer Portal");
        headerLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        // Warning banner if no dealers
        VBox warningBanner = new VBox();
        warningBanner.setPadding(new Insets(15));
        warningBanner.setStyle("-fx-background-color: #fff7ed; -fx-background-radius: 12; -fx-border-color: #fdba74; -fx-border-radius: 12; -fx-border-width: 1;");
        Label warningText = new Label("Register dealers first in the Dealer Portal before adding buyers.");
        warningText.setStyle("-fx-font-size: 13; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-wrap-text: true;");
        warningBanner.getChildren().add(warningText);

        VBox formSection = new VBox(18);
        formSection.setPadding(new Insets(25));
        formSection.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

        Label formTitle = new Label("Add Buyer Agent");
        formTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setPadding(new Insets(15, 0, 15, 0));

        TextField buyerName = createStyledTextField("e.g., Ali, Siti");
        ComboBox<String> carModel = createStyledCarComboBox();
        TextField budget = createStyledTextField("e.g., 100000");

        Label nameLabel = new Label("Buyer Name:");
        nameLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");
        Label carLabel = new Label("Desired Car:");
        carLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");
        Label budgetLabel = new Label("Max Budget (RM):");
        budgetLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + ";");

        form.add(nameLabel, 0, 0);
        form.add(buyerName, 1, 0);
        form.add(carLabel, 0, 1);
        form.add(carModel, 1, 1);
        form.add(budgetLabel, 0, 2);
        form.add(budget, 1, 2);

        Button addBuyerBtn = createStyledButton("Add Buyer Agent", ACCENT_BLUE);
        addBuyerBtn.setPrefWidth(280);

        addBuyerBtn.setOnAction(e -> {
            String name = buyerName.getText().trim();
            String car = carModel.getValue() != null ? carModel.getValue() : "";
            String budgetStr = budget.getText().trim();

            if (name.isEmpty() || car.isEmpty() || budgetStr.isEmpty()) {
                showAlert("⚠️ All fields are required!", Alert.AlertType.WARNING);
                return;
            }

            if (dealerCount == 0) {
                showAlert("❌ No dealers registered!\n\nPlease register at least one dealer in the Dealer Portal first.", Alert.AlertType.ERROR);
                return;
            }

            try {
                // Validate budget is numeric
                double budgetAmount = Double.parseDouble(budgetStr);
                if (budgetAmount <= 0) {
                    showAlert("❌ Budget must be greater than 0", Alert.AlertType.WARNING);
                    return;
                }

                cc.createNewAgent(name, "org.example.agents.BuyerAgent",
                        new Object[]{car, budgetStr, logger, buildNegotiationConfig(), true}).start();
                buyerAgents.add(name);
                waitingBuyerAgents.add(name);
                updateNegotiationControlStatus();
                logger.log("Buyer '" + name + "' added and waiting - " + car + " budget RM" + budgetStr);
                buyerName.clear();
                carModel.setValue(null);
                budget.clear();
                showAlert("✅ Buyer " + name + " added. Press Start to begin negotiation.", Alert.AlertType.INFORMATION);
            } catch (NumberFormatException ex) {
                showAlert("❌ Budget must be a valid number", Alert.AlertType.ERROR);
            } catch (Exception ex) {
                logger.log("❌ Error creating buyer: " + ex.getMessage());
                showAlert("❌ Error: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        HBox btnBox = new HBox(addBuyerBtn);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        formSection.getChildren().addAll(formTitle, form, btnBox);

        box.getChildren().addAll(headerLabel, warningBanner, formSection);
        VBox.setVgrow(formSection, Priority.SOMETIMES);

        return box;
    }

    private VBox createGlobalControlBar() {
        final double expandedWidth = 320;
        final double collapsedWidth = 72;
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 14; -fx-border-radius: 14; " +
                "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");
        panel.setPrefWidth(expandedWidth);
        panel.setMinWidth(expandedWidth);
        panel.setMaxWidth(expandedWidth);

        Label title = new Label("Simulation Controls");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: 700; -fx-text-fill: " + PRIMARY_BLUE + ";");

        negotiationControlStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + "; -fx-font-weight: bold;");
        negotiationControlStatusLabel.setWrapText(true);
        negotiationControlStatusLabel.setMaxWidth(Double.MAX_VALUE);
        updateNegotiationControlStatus();

        Label statusTitle = new Label("Status:");
        statusTitle.setStyle("-fx-font-size: 13; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-weight: 600;");

        Button toggleControlsBtn = new Button("<");
        toggleControlsBtn.setStyle(
            "-fx-font-size: 13; -fx-font-weight: 600; -fx-padding: 6 12; " +
                "-fx-background-color: #e2e8f0; -fx-text-fill: " + DARK_TEXT + "; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
        );
        toggleControlsBtn.setMaxWidth(Double.MAX_VALUE);

        Button startBtn = createStyledButton("Start", SUCCESS_GREEN);
        startBtn.setMaxWidth(Double.MAX_VALUE);
        startBtn.setOnAction(e -> {
            if (waitingBuyerAgents.isEmpty()) {
                showAlert("No waiting buyers to start.", Alert.AlertType.INFORMATION);
                return;
            }
            for (String buyer : new ArrayList<>(waitingBuyerAgents)) {
                sendAgentCommand(buyer, "START_NEGOTIATION");
            }
            loggerLog("Started " + waitingBuyerAgents.size() + " waiting buyer negotiation(s).");
            waitingBuyerAgents.clear();
            isAutoPlay = true;
            playPauseBtn.setText("Pause");
            sendSpaceCommand("RESUME");
            updateNegotiationControlStatus();
        });

        playPauseBtn = createStyledButton("Pause", WARNING_ORANGE);
        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        playPauseBtn.setOnAction(e -> {
            toggleAutoplay();
            updateNegotiationControlStatus();
        });

        Button stepBtn = createStyledButton("Step Cycle", ACCENT_BLUE);
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setOnAction(e -> sendSpaceCommand("STEP"));

        Button snifferBtn = createStyledButton("Sniffer", WARNING_ORANGE);
        snifferBtn.setMaxWidth(Double.MAX_VALUE);
        snifferBtn.setOnAction(e -> launchSniffer(msg -> logArea.appendText(msg + "\n")));

        Button demoBtn = createStyledButton("Demo Setup", PRIMARY_BLUE);
        demoBtn.setMaxWidth(Double.MAX_VALUE);
        demoBtn.setOnAction(e -> createDemoScenario());

        Button stopBtn = createStyledButton("Stop", ERROR_RED);
        stopBtn.setMaxWidth(Double.MAX_VALUE);
        stopBtn.setOnAction(e -> {
            if (buyerAgents.isEmpty()) {
                showAlert("No buyer negotiations to stop.", Alert.AlertType.INFORMATION);
                return;
            }
            for (String buyer : new ArrayList<>(buyerAgents)) {
                sendAgentCommand(buyer, "STOP_NEGOTIATION");
            }
            waitingBuyerAgents.clear();
            buyerAgents.clear();
            sendSpaceCommand("PAUSE");
            isAutoPlay = false;
            playPauseBtn.setText("Resume");
            updateNegotiationControlStatus();
            loggerLog("Stopped all buyer negotiations.");
        });

        VBox statusBox = new VBox(4, statusTitle, negotiationControlStatusLabel);
        statusBox.setStyle("-fx-alignment: top-left;");

        VBox buttonStack = new VBox(10, demoBtn, startBtn, playPauseBtn, stopBtn, stepBtn, snifferBtn);
        buttonStack.setStyle("-fx-alignment: top-left;");
        VBox controlsContent = new VBox(12, statusBox, buttonStack);

        VBox quickGuideBox = createQuickGuideBox();
        quickGuideBox.setMaxWidth(Double.MAX_VALUE);
        quickGuideBox.setVisible(true);
        quickGuideBox.setManaged(true);
        VBox.setVgrow(quickGuideBox, Priority.NEVER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerRow = new HBox(8, title, spacer, toggleControlsBtn);
        headerRow.setStyle("-fx-alignment: center-left;");

        toggleControlsBtn.setOnAction(e -> {
            boolean isVisible = controlsContent.isVisible();
            controlsContent.setVisible(!isVisible);
            controlsContent.setManaged(!isVisible);
            quickGuideBox.setVisible(!isVisible);
            quickGuideBox.setManaged(!isVisible);
            title.setVisible(!isVisible);
            title.setManaged(!isVisible);
            spacer.setVisible(!isVisible);
            spacer.setManaged(!isVisible);
            panel.setPrefWidth(isVisible ? collapsedWidth : expandedWidth);
            panel.setMinWidth(isVisible ? collapsedWidth : expandedWidth);
            panel.setMaxWidth(isVisible ? collapsedWidth : expandedWidth);
            headerRow.setStyle(isVisible ? "-fx-alignment: center;" : "-fx-alignment: center-left;");
            toggleControlsBtn.setText(isVisible ? ">" : "<");
        });

        panel.getChildren().addAll(headerRow, controlsContent, quickGuideBox);
        return panel;
    }

    private void createDemoScenario() {
        long demoId = demoScenarioCounter.incrementAndGet();
        NegotiationConfig config = buildDemoNegotiationConfig();

        try {
            String[][] dealers = new String[][]{
                    {"DemoAutoA-" + demoId, "Toyota Camry", "100000", "2"},
                    {"DemoAutoB-" + demoId, "Toyota Camry", "96000", "2"},
                    {"DemoAutoC-" + demoId, "Toyota Camry", "92000", "1"},
                    {"BudgetCars-" + demoId, "Honda Civic", "87000", "2"},
                    {"FamilyDrive-" + demoId, "Honda CR-V", "145000", "1"},
                    {"TruckHub-" + demoId, "Toyota Fortuner", "180000", "1"}
            };
            for (String[] dealer : dealers) {
                createDemoDealer(dealer[0], dealer[1], dealer[2], dealer[3], config);
            }

            String[][] buyers = new String[][]{
                    {"DemoBuyerPremium-" + demoId, "Toyota Camry", "116000"},
                    {"DemoBuyerStubborn-" + demoId, "Toyota Camry", "108000"},
                    {"DemoBuyerTight-" + demoId, "Toyota Camry", "98000"},
                    {"DemoBuyerCivic-" + demoId, "Honda Civic", "102000"},
                    {"DemoBuyerSUV-" + demoId, "Honda CR-V", "150000"},
                    {"DemoBuyerStretch-" + demoId, "Toyota Fortuner", "168000"},
                    {"DemoBuyerBudget-" + demoId, "Toyota Camry", "65000"},
                    {"DemoBuyerOverdrive-" + demoId, "Toyota Camry", "112000"}
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
                base.getSwitchStrategy() == base.getStrategy() ? NegotiationConfig.Strategy.CONCEDER : base.getSwitchStrategy()
        );
    }

    private void createDemoDealer(String name, String car, String price, String stock, NegotiationConfig config) throws Exception {
        cc.createNewAgent(name, "org.example.agents.DealerAgent",
                new Object[]{car, price, stock, appLogger, config}).start();
        loggerLog("Dealer '" + name + "' listed " + car + " @ RM" + price + " | Stock: " + stock);
    }

    private void createDemoBuyer(String name, String car, String budget, NegotiationConfig config) throws Exception {
        cc.createNewAgent(name, "org.example.agents.BuyerAgent",
                new Object[]{car, budget, appLogger, config, true}).start();
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
        infoBanner.setStyle("-fx-background-color: #eff6ff; -fx-background-radius: 12; -fx-border-color: #93c5fd; -fx-border-radius: 12; -fx-border-width: 1;");
        Label infoText = new Label("Register car inventory here first. Buyers will negotiate with available dealers.");
        infoText.setStyle("-fx-font-size: 13; -fx-text-fill: #0c4a6e; -fx-font-weight: bold; -fx-wrap-text: true;");
        infoBanner.getChildren().add(infoText);
        VBox formSection = new VBox(18);
        formSection.setPadding(new Insets(25));
        formSection.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

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

            if (name.isEmpty() || car.isEmpty() || price.isEmpty() || stock.isEmpty()){
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
                        new Object[]{car, price, stock, logger, buildNegotiationConfig()}).start();
                logger.log("Dealer '" + name + "' listed " + car + " @ RM" + price + " | Stock: " + stock);
                dealerName.clear();
                carModel.setValue(null);
                retailPrice.clear();
                stockField.clear();
                showAlert("✅ Dealer " + name + " registered with " + stock + " unit(s)!", Alert.AlertType.INFORMATION);;
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
        analysisArea.setStyle("-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + ";");
        analysisArea.setText(
                "╔════════════════════════════════════════════════════════════╗\n" +
                        "║                                      MARKET ANALYTICS DASHBOARD                                        ║\n" + //Modify the space to organise the text
                        "╚════════════════════════════════════════════════════════════╝\n\n" +
                        "SYSTEM OVERVIEW:\n" +
                        "  ✓ Multi-Agent Negotiation Platform\n" +
                        "  ✓ Real-time Buyer-Dealer Matching\n" +
                        "  ✓ Dynamic Cycle-Based Negotiation Engine\n\n" + //Change this to Cycle-Based System in case of adding the pause and resume function
                        "PRICING STRUCTURE:\n" +
                        "  • Transaction Fee:      RM50 per negotiation\n" +
                        "  • Commission:           5% of final sale price\n" +
                        "  • Example: RM100k sale = RM5k commission + RM50 fee = RM5,050\n\n" +
                        "BASE NEGOTIATION RULES:\n" +
                        "  • Buyer Opening:     70% of maximum budget\n" +
                        "  • Dealer Reserve:    70% of retail price (floor)\n" +
                        "  • Max Rounds:        3 rounds per dealer\n" +
                        "  • Multi-Dealer:      Buyers try all available dealers\n\n" +
                        "CYCLE-BASED MARKET SYSTEM (SPACE CONTROL):\n" +
                        "  • Maximum Cycles:    50 Market Cycles (Deadline)\n" +
                        "  • Buyer Behavior:    Increases willing offer as time runs out\n" +
                        "  • Dealer Behavior:   Lowers asking price as time runs out\n" +
                        "  • Concession Rate:   Quadratic (Beta = 2.0)\n\n" +
                        "CURRENT METRICS:\n" +
                        "  • Total Transactions: See Dashboard tab\n" +
                        "  • Platform Revenue:   See Dashboard tab\n" +
                        "  • Active Participants: See Dashboard tab\n\n" +
                        "KEY FEATURES:\n" +
                        "  ✓ Concurrent multi-buyer support\n" +
//            "  ✓ Intelligent dealer fallback strategy\n" +
                        "  ✓ Real-time price tracking & negotiation\n" +
                        "  ✓ Automatic deal closure on agreement\n" +
                        "  ✓ No-deal detection (budget exceeded)\n" +
                        "╚════════════════════════════════════════════════════════════╝"
        );

        /*
        ScrollPane scrollPane = new ScrollPane(analysisArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1;");

        box.getChildren().addAll(headerLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        */
        
        analysisArea.setStyle("-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        box.getChildren().addAll(headerLabel, createSimulationControlPanel(), analysisArea);
        VBox.setVgrow(analysisArea, Priority.ALWAYS);
        // --------------------------------------------------

        return box;
    }

    private VBox createSimulationControlPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

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
        grid.add(new Label("Then strategy:"), 2, 1);
        grid.add(switchStrategyChoice, 3, 1);
        grid.add(new Label("Buyer start %:"), 0, 2);
        grid.add(buyerStartPercentField, 1, 2);
        grid.add(new Label("Dealer reserve %:"), 2, 2);
        grid.add(reservePercentField, 3, 2);
        grid.add(new Label("Max rounds/dealer:"), 0, 3);
        grid.add(maxRoundsField, 1, 3);
        grid.add(new Label("Search retries:"), 2, 3);
        grid.add(retryLimitField, 3, 3);
        grid.add(new Label("Stuck rounds:"), 0, 4);
        grid.add(stuckRoundsField, 1, 4);

        manualDealerNameField = createStyledTextField("Dealer agent name");
        manualDealerPriceField = createStyledTextField("New target price");
        Button adjustPriceBtn = createStyledButton("Adjust Dealer Price", SUCCESS_GREEN);
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
                showAlert("Price adjustment sent to " + dealer, Alert.AlertType.INFORMATION);
            } catch (NumberFormatException ex) {
                showAlert("Price must be a valid integer.", Alert.AlertType.ERROR);
            }
        });

        HBox manualControls = new HBox(12, manualDealerNameField, manualDealerPriceField, adjustPriceBtn);
        panel.getChildren().addAll(title, grid, manualControls);
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
            NegotiationConfig.Strategy switchStrategy = NegotiationConfig.Strategy.valueOf(switchStrategyChoice.getValue());
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
        fullLogArea.setStyle("-fx-font-size: 13; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + ";");

        logArea.textProperty().addListener((obs, oldVal, newVal) -> {
            fullLogArea.setText(newVal);
            fullLogArea.setScrollTop(Double.MAX_VALUE);
        });

        /*
        ScrollPane logScroll = new ScrollPane(fullLogArea);
        logScroll.setFitToWidth(true);
        logScroll.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1;");
        */
        
        // Remove the redundant ScrollPane (TextArea is already scrollable)
        // and allow the TextArea to expand to fill available height.
        fullLogArea.setStyle("-fx-font-size: 13; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + "; -fx-control-inner-background: " + SURFACE + "; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        // ----------------------------------------------------

        HBox controlBox = new HBox(12);
        controlBox.setPadding(new Insets(15));
        controlBox.setStyle("-fx-background-color: " + SURFACE + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: " + BORDER_SUBTLE + "; -fx-border-width: 1; -fx-effect: " + SOFT_SHADOW + ";");

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
        
        /*
        box.getChildren().addAll(headerLabel, logScroll, controlBox);
        VBox.setVgrow(logScroll, Priority.ALWAYS);
        */
        box.getChildren().addAll(headerLabel, fullLogArea, controlBox);
        VBox.setVgrow(fullLogArea, Priority.ALWAYS);
        // -------------------------

        return box;
    }

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(
            "-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + "; -fx-padding: 10 12; " +
                "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-radius: 8; -fx-border-width: 1; " +
                "-fx-control-inner-background: " + SURFACE + "; -fx-background-radius: 8; " +
                "-fx-prompt-text-fill: #94a3b8;"
        );
        return tf;
    }

    private ComboBox<String> createStyledCarComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(CAR_MODELS);
        comboBox.setEditable(true);
        comboBox.setPrefWidth(300);
        comboBox.setStyle(
            "-fx-font-size: 14; -fx-font-family: " + FONT_FAMILY + "; -fx-font-weight: " + FONT_WEIGHT_MEDIUM + "; -fx-padding: 6 10; " +
                "-fx-border-color: " + BORDER_SUBTLE + "; -fx-border-radius: 8; -fx-border-width: 1; " +
                "-fx-control-inner-background: " + SURFACE + "; -fx-background-radius: 8;"
        );
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
                "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.14), 10, 0, 0, 2);"
        );
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
