package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.ContainerController;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import java.util.HashMap;
import java.util.Map;

public class MainUI extends Application {
    private TextArea logArea = new TextArea();
    private Label buyerCountLabel = new Label("0");
    private Label dealerCountLabel = new Label("0");
    private Label transactionCountLabel = new Label("0");
    private Label revenueLabel = new Label("RM 0.00");
    private ContainerController cc;
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

    // Modern Color Palette
    private static final String PRIMARY_BLUE = "#1e40af";
    private static final String ACCENT_BLUE = "#3b82f6";
    private static final String SUCCESS_GREEN = "#10b981";
    private static final String WARNING_ORANGE = "#f59e0b";
    private static final String ERROR_RED = "#ef4444";
    private static final String LIGHT_GRAY = "#f9fafb";
    private static final String DARK_TEXT = "#1f2937";
    
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

        UILogger logger = msg -> {
            String timestamp = "[" + LocalTime.now().format(timeFormatter) + "] ";
            final String formattedMsg = timestamp + msg + "\n";

            boolean isBuyerReg = msg.contains("Buyer") && msg.contains("registered");
            boolean isDealerReg = msg.contains("Dealer") && msg.contains("listed");
            boolean isRevenue = msg.contains("[BROKER] REVENUE:");
            boolean isCycleShift = msg.contains("Cycle Shift:");
            boolean isSetupMsg = msg.contains("BROKER ONLINE") ||
                    msg.contains("Transaction Fee") ||
                    msg.contains("Initializing Space Control");
            boolean isPriceUpdate = msg.contains("has set buying price to RM") ||
                    (msg.contains("has set vehicle") && msg.contains("to RM"));
            boolean isNegotiationAction = msg.contains("DEAL") || msg.contains("SUCCESS") ||
                    msg.contains("COUNTER") || msg.contains("OFFER") ||
                    msg.contains("AGREED") || msg.contains("STATUS:");

            Platform.runLater(() -> {
                if (isSetupMsg || isBuyerReg || isDealerReg || isCycleShift || isPriceUpdate || isNegotiationAction || isRevenue) {
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
                    dealsClosed++;
                    transactionCountLabel.setText(String.valueOf(dealsClosed));
                    try {
                        int rmIndex = msg.indexOf("RM");
                        if (rmIndex != -1) {
                            String amountStr = msg.substring(rmIndex + 2).trim().split(" ")[0];
                            totalRevenue += Double.parseDouble(amountStr);
                            revenueLabel.setText(String.format("RM %.2f", totalRevenue));
                        }
                    } catch (Exception e) {  }
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

        cc.createNewAgent("broker", "org.example.agents.BrokerAgent", new Object[]{logger}).start();
        cc.createNewAgent("space", "org.example.agents.SpaceControl", new Object[]{logger}).start();

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
            int rmIndex = msg.indexOf("to RM");
            double amount = Double.parseDouble(msg.substring(rmIndex + 5).trim().split(" ")[0]);

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

    private VBox createMainContent(UILogger logger) {
        TabPane tp = new TabPane();
        tp.setStyle("-fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial;");
        
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
        return new VBox(tp);
    }

    private void toggleAutoplay() {
        isAutoPlay = !isAutoPlay;
        playPauseBtn.setText(isAutoPlay ? "⏸ Pause" : "▶ Autoplay");
        sendSpaceCommand(isAutoPlay ? "RESUME" : "PAUSE");
    }

    private void sendSpaceCommand(String command) {
        try {
            jade.lang.acl.ACLMessage msg = new jade.lang.acl.ACLMessage(jade.lang.acl.ACLMessage.INFORM);
            msg.addReceiver(new jade.core.AID("space", jade.core.AID.ISLOCALNAME));
            msg.setOntology(command);

            cc.createNewAgent("bridge-" + System.currentTimeMillis(), "jade.core.Agent", null).start();
        } catch (Exception e) {
            System.err.println("Error sending command to SpaceControl: " + e.getMessage());
        }
    }

    private VBox createBrokerView() {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("📊 Marketplace Dashboard");
        headerLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        HBox statsBox = createStatsCard();

        VBox quickGuideBox = createQuickGuideBox();
        Label logLabel = new Label("Activity Log");
        logLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");
        
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
        graphLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Negotiation Cycle");
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (RM)");
        yAxis.setForceZeroInRange(false);

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setAnimated(false);
        priceChart.setStyle("-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-width: 1;");

        priceChart.getData().addAll(seriesMap.values());

        box.getChildren().addAll(headerLabel, statsBox, quickGuideBox, new Separator(), graphLabel, priceChart);
//        VBox.setVgrow(logScroll, Priority.ALWAYS);
        VBox.setVgrow(priceChart, Priority.ALWAYS);
        
        return box;
    }

    private HBox createStatsCard() {
        HBox statsBox = new HBox(20);
        statsBox.setPadding(new Insets(25));
        statsBox.setStyle("-fx-background-color: white; -fx-border-radius: 12; -fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        VBox buyerCard = createStatCard("Active Buyers", buyerCountLabel, ACCENT_BLUE);
        VBox dealerCard = createStatCard("Active Dealers", dealerCountLabel, WARNING_ORANGE);
        VBox transactionCard = createStatCard("Deals Closed", transactionCountLabel, SUCCESS_GREEN);
        VBox revenueCard = createStatCard("Broker's Total Revenue", revenueLabel, "#ec4899");

        statsBox.getChildren().addAll(buyerCard, dealerCard, transactionCard, revenueCard);
        HBox.setHgrow(buyerCard, Priority.ALWAYS);
        HBox.setHgrow(dealerCard, Priority.ALWAYS);
        HBox.setHgrow(transactionCard, Priority.ALWAYS);
        HBox.setHgrow(revenueCard, Priority.ALWAYS);

        return statsBox;
    }

    private VBox createQuickGuideBox() {
        VBox guideBox = new VBox(12);
        guideBox.setPadding(new Insets(20));
        guideBox.setStyle("-fx-background-color: white; -fx-border-radius: 12; -fx-border-color: #fbbf24; -fx-border-width: 2; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 1);");

        Label titleLabel = new Label("Quick Setup Guide");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");
        
        dealerStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666; -fx-wrap-text: true;");
        updateDealerStatus();

//        Label guideLine2 = new Label("Go to Buyer Portal → Register buyer(s) with desired car & budget");
//        guideLine2.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        updateBuyerStatus.setStyle("-fx-font-size: 12; -fx-text-fill: #666; -fx-wrap-text: true;");
        updateBuyerStatus();

        Label guideLine3 = new Label("Watch Activity Log below for real-time negotiation updates ✓");
        guideLine3.setStyle("-fx-font-size: 12; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");

        guideBox.getChildren().addAll(titleLabel, dealerStatusLabel, updateBuyerStatus, guideLine3);
        return guideBox;
    }

    private void updateDealerStatus() {
        if (dealerCount == 0) {
            dealerStatusLabel.setText("Go to Dealer Portal → Register at least ONE dealer with car inventory (Required first!)");
            dealerStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + ERROR_RED + "; -fx-font-weight: bold;");
        } else {
            dealerStatusLabel.setText("/ " + dealerCount + " dealer agent(s) registered - Ready to accept buyers!");
            dealerStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");
        }
    }

    private void updateBuyerStatus() {
        if (buyerCount == 0) {
            updateBuyerStatus.setText("Go to Buyer Portal → Register buyer(s) with desired car & budget");
            updateBuyerStatus.setStyle("-fx-font-size: 12; -fx-text-fill: " + ERROR_RED + "; -fx-font-weight: bold;");
        } else {
            updateBuyerStatus.setText("/ " + buyerCount + " buyer agent(s) registered - Ready to accept dealers!");
            updateBuyerStatus.setStyle("-fx-font-size: 12; -fx-text-fill: " + SUCCESS_GREEN + "; -fx-font-weight: bold;");
        }
    }

    private VBox createStatCard(String title, Label valueLabel, String color) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-border-color: " + color + "; -fx-border-width: 0 0 4 0; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 1);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13; -fx-text-fill: " + DARK_TEXT + "; -fx-font-weight: 500;");

        valueLabel.setStyle("-fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private VBox createBuyerView(UILogger logger) {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("👥 Buyer Portal");
        headerLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        // Warning banner if no dealers
        VBox warningBanner = new VBox();
        warningBanner.setPadding(new Insets(15));
        warningBanner.setStyle("-fx-background-color: #fef3c7; -fx-border-color: #fbbf24; -fx-border-radius: 8; -fx-border-width: 2;");
        Label warningText = new Label("⚠️ IMPORTANT: Register dealers first in the Dealer Portal before adding buyers");
        warningText.setStyle("-fx-font-size: 12; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-wrap-text: true;");
        warningBanner.getChildren().add(warningText);

        VBox formSection = new VBox(18);
        formSection.setPadding(new Insets(25));
        formSection.setStyle("-fx-background-color: white; -fx-border-radius: 12; -fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        Label formTitle = new Label("🔍 Register New Buyer");
        formTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setPadding(new Insets(15, 0, 15, 0));

        TextField buyerName = createStyledTextField("e.g., Ali, Siti");
        ComboBox<String> carModel = createStyledCarComboBox();
        TextField budget = createStyledTextField("e.g., 100000");

        Label nameLabel = new Label("Buyer Name:");
        nameLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + DARK_TEXT + ";");
        Label carLabel = new Label("Desired Car:");
        carLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + DARK_TEXT + ";");
        Label budgetLabel = new Label("Max Budget (RM):");
        budgetLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + DARK_TEXT + ";");

        form.add(nameLabel, 0, 0);
        form.add(buyerName, 1, 0);
        form.add(carLabel, 0, 1);
        form.add(carModel, 1, 1);
        form.add(budgetLabel, 0, 2);
        form.add(budget, 1, 2);

        Button addBuyerBtn = createStyledButton("▶ Start Negotiation", ACCENT_BLUE);
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
                        new Object[]{car, budgetStr, logger}).start();
                logger.log("Buyer '" + name + "' registered - searching for " + car);
                buyerName.clear();
                carModel.setValue(null);
                budget.clear();
                showAlert("✅ Buyer " + name + " started negotiation!", Alert.AlertType.INFORMATION);
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

    private VBox createDealerView(UILogger logger) {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("🚙 Dealer Portal");
        headerLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");
        // Info banner
        VBox infoBanner = new VBox();
        infoBanner.setPadding(new Insets(15));
        infoBanner.setStyle("-fx-background-color: #dbeafe; -fx-border-color: #3b82f6; -fx-border-radius: 8; -fx-border-width: 2;");
        Label infoText = new Label("ℹ️ Register your car inventory here first. Buyers will negotiate with available dealers.");
        infoText.setStyle("-fx-font-size: 12; -fx-text-fill: #0c4a6e; -fx-font-weight: bold; -fx-wrap-text: true;");
        infoBanner.getChildren().add(infoText);
        VBox formSection = new VBox(18);
        formSection.setPadding(new Insets(25));
        formSection.setStyle("-fx-background-color: white; -fx-border-radius: 12; -fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        Label formTitle = new Label("📋 Register & List New Car");
        formTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setPadding(new Insets(15, 0, 15, 0));

        TextField dealerName = createStyledTextField("e.g., GreenCars Sdn Bhd");
        ComboBox<String> carModel = createStyledCarComboBox();
        TextField retailPrice = createStyledTextField("e.g., 150000");

        Label dealerLabel = new Label("Dealer Name:");
        dealerLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + DARK_TEXT + ";");
        Label carLbl = new Label("Car Model:");
        carLbl.setStyle("-fx-font-size: 12; -fx-text-fill: " + DARK_TEXT + ";");
        Label priceLabel = new Label("Retail Price (RM):");
        priceLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + DARK_TEXT + ";");

        form.add(dealerLabel, 0, 0);
        form.add(dealerName, 1, 0);
        form.add(carLbl, 0, 1);
        form.add(carModel, 1, 1);
        form.add(priceLabel, 0, 2);
        form.add(retailPrice, 1, 2);

        Button addDealerBtn = createStyledButton("📋 List Car", WARNING_ORANGE);
        addDealerBtn.setPrefWidth(280);

        addDealerBtn.setOnAction(e -> {
            String name = dealerName.getText().trim();
            String car = carModel.getValue() != null ? carModel.getValue() : "";
            String price = retailPrice.getText().trim();
            
            if (name.isEmpty() || car.isEmpty() || price.isEmpty()) {
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
                
                cc.createNewAgent(name, "org.example.agents.DealerAgent",
                        new Object[]{car, price, logger}).start();
                logger.log("Dealer '" + name + "' listed " + car + " @ RM" + price);
                dealerName.clear();
                carModel.setValue(null);
                retailPrice.clear();
                showAlert("✅ Dealer " + name + " registered!", Alert.AlertType.INFORMATION);
            } catch (NumberFormatException ex) {
                showAlert("❌ Price must be a valid number", Alert.AlertType.ERROR);
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

        Label headerLabel = new Label("📈 Market Analytics");
        headerLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        TextArea analysisArea = new TextArea();
        analysisArea.setEditable(false);
        analysisArea.setWrapText(true);
        analysisArea.setStyle("-fx-font-size: 12; -fx-font-family: 'Courier New'; -fx-control-inner-background: white;");
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

        ScrollPane scrollPane = new ScrollPane(analysisArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1;");

        box.getChildren().addAll(headerLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        return box;
    }

    private VBox createActivityLogView() {
        VBox box = new VBox(25);
        box.setPadding(new Insets(25));
        box.setStyle("-fx-background-color: " + LIGHT_GRAY + ";");

        Label headerLabel = new Label("📋 System Activity Log");
        headerLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_BLUE + ";");

        TextArea fullLogArea = new TextArea();
        fullLogArea.setEditable(false);
        fullLogArea.setWrapText(true);
        fullLogArea.setStyle("-fx-font-size: 11; -fx-font-family: 'Courier New'; -fx-control-inner-background: white;");

        logArea.textProperty().addListener((obs, oldVal, newVal) -> {
            fullLogArea.setText(newVal);
            fullLogArea.setScrollTop(Double.MAX_VALUE);
        });

        ScrollPane logScroll = new ScrollPane(fullLogArea);
        logScroll.setFitToWidth(true);
        logScroll.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1;");

        HBox controlBox = new HBox(12);
        controlBox.setPadding(new Insets(15));
        controlBox.setStyle("-fx-background-color: white; -fx-border-radius: 8; -fx-border-color: #e5e7eb; -fx-border-width: 1;");
        
        Button copyBtn = createStyledButton("📋 Copy Log", ACCENT_BLUE);
        copyBtn.setPrefWidth(120);
        copyBtn.setOnAction(e -> {
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(logArea.getText());
            clipboard.setContent(content);
            showAlert("Log copied to clipboard!", Alert.AlertType.INFORMATION);
        });
        
        Button clearBtn = createStyledButton("🗑️ Clear Log", ERROR_RED);
        clearBtn.setPrefWidth(120);
        clearBtn.setOnAction(e -> {
            logArea.clear();
            fullLogArea.clear();
        });
        
        controlBox.getChildren().addAll(copyBtn, clearBtn);

        box.getChildren().addAll(headerLabel, logScroll, controlBox);
        VBox.setVgrow(logScroll, Priority.ALWAYS);
        
        return box;
    }

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(
            "-fx-font-size: 12; -fx-padding: 12; " +
            "-fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-border-width: 1; " +
            "-fx-control-inner-background: white;"
        );
        return tf;
    }

    private ComboBox<String> createStyledCarComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(CAR_MODELS);
        comboBox.setEditable(true);
        comboBox.setPrefWidth(300);
        comboBox.setStyle(
            "-fx-font-size: 12; -fx-padding: 12; " +
            "-fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-border-width: 1; " +
            "-fx-control-inner-background: white;"
        );
        comboBox.setPromptText("Select or type car model...");
        
        return comboBox;
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-font-size: 13; -fx-font-weight: 500; -fx-padding: 13 28 13 28; " +
            "-fx-background-color: " + color + "; " +
            "-fx-text-fill: white; " +
            "-fx-border-radius: 6; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 4, 0, 0, 1);"
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
