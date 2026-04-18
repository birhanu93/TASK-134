package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.VehicleType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class TripManagementWindow implements Saveable {
    private final AppContext ctx;
    private final ObservableList<Trip> trips = FXCollections.observableArrayList();
    private final ObservableList<Order> selectedRiders = FXCollections.observableArrayList();
    private Runnable createTripAction;

    ChoiceBox<VehicleType> vehicleBox;
    Spinner<Integer> capacity;
    DatePicker date;
    TextField timeFrom;
    TextField timeTo;
    TextField driver;
    Button createBtn;
    ListView<Trip> tripList;
    ListView<Order> ridersList;
    ChoiceBox<Customer> customerBox;
    TextField pickupLine;
    TextField pickupCity;
    TextField dropLine;
    TextField dropCity;
    Spinner<Integer> riderCount;
    ChoiceBox<ServicePriority> priorityBox;
    TextField miles;
    TextField duration;
    Button addRiderBtn;
    Button dispatchBtn;
    Button closeBtn;
    Button cancelBtn;
    Label feedback;

    public TripManagementWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        vehicleBox = new ChoiceBox<>(FXCollections.observableArrayList(VehicleType.values()));
        vehicleBox.setId("vehicleBox");
        vehicleBox.setValue(VehicleType.STANDARD);
        capacity = new Spinner<>(Trip.MIN_CAPACITY, Trip.MAX_CAPACITY, 4);
        capacity.setId("capacity");
        date = new DatePicker(LocalDate.now());
        date.setId("date");
        timeFrom = new TextField("14:00");
        timeFrom.setId("timeFrom");
        timeTo = new TextField("15:00");
        timeTo.setId("timeTo");
        driver = new TextField();
        driver.setId("driver");
        driver.setPromptText("driver placeholder (optional)");
        createBtn = new Button("Create trip");
        createBtn.setId("createTrip");

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(4);
        int r = 0;
        form.add(new Label("Vehicle"), 0, r); form.add(vehicleBox, 1, r++);
        form.add(new Label("Capacity"), 0, r); form.add(capacity, 1, r++);
        form.add(new Label("Date"), 0, r); form.add(date, 1, r++);
        form.add(new Label("From (HH:mm)"), 0, r); form.add(timeFrom, 1, r++);
        form.add(new Label("To (HH:mm)"), 0, r); form.add(timeTo, 1, r++);
        form.add(new Label("Driver"), 0, r); form.add(driver, 1, r++);
        form.add(createBtn, 1, r);

        tripList = new ListView<>(trips);
        tripList.setId("tripList");
        tripList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Trip t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setText(null); return; }
                setText(t.id() + " | " + t.status() + " | " + t.vehicleType()
                        + " cap=" + t.capacity()
                        + " | " + t.scheduledWindow().start() + " – " + t.scheduledWindow().end());
            }
        });

        ridersList = new ListView<>(selectedRiders);
        ridersList.setId("ridersList");
        ridersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Order o, boolean empty) {
                super.updateItem(o, empty);
                if (empty || o == null) { setText(null); return; }
                setText(o.id() + " [" + o.state() + "] riders=" + o.riderCount()
                        + " cust=" + o.customerId());
            }
        });
        tripList.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> {
            if (now == null) { selectedRiders.clear(); return; }
            try {
                selectedRiders.setAll(ctx.securedTripService.riderOrders(now.id()));
            } catch (RuntimeException ex) {
                selectedRiders.clear();
            }
        });

        customerBox = new ChoiceBox<>();
        customerBox.setId("customerBox");
        try {
            customerBox.setItems(FXCollections.observableArrayList(
                    ctx.securedCustomerService.list()));
        } catch (RuntimeException ignored) {
            // Dispatcher without customer visibility: leave empty, UI feedback on submit.
        }
        pickupLine = new TextField();
        pickupLine.setId("pickupLine");
        pickupLine.setPromptText("pickup line1");
        pickupCity = new TextField();
        pickupCity.setId("pickupCity");
        pickupCity.setPromptText("pickup city");
        dropLine = new TextField();
        dropLine.setId("dropLine");
        dropLine.setPromptText("dropoff line1");
        dropCity = new TextField();
        dropCity.setId("dropCity");
        dropCity.setPromptText("dropoff city");
        riderCount = new Spinner<>(1, 6, 1);
        riderCount.setId("riderCount");
        priorityBox = new ChoiceBox<>(FXCollections.observableArrayList(ServicePriority.values()));
        priorityBox.setId("priorityBox");
        priorityBox.setValue(ServicePriority.NORMAL);
        miles = new TextField("3.0");
        miles.setId("miles");
        duration = new TextField("10");
        duration.setId("duration");
        addRiderBtn = new Button("Add rider to selected trip");
        addRiderBtn.setId("addRider");

        dispatchBtn = new Button("Dispatch");
        dispatchBtn.setId("dispatch");
        closeBtn = new Button("Close");
        closeBtn.setId("close");
        cancelBtn = new Button("Cancel trip");
        cancelBtn.setId("cancel");

        feedback = new Label();
        feedback.setId("feedback");

        createTripAction = () -> {
            try {
                LocalDateTime start = LocalDateTime.of(date.getValue(), LocalTime.parse(timeFrom.getText()));
                LocalDateTime end = LocalDateTime.of(date.getValue(), LocalTime.parse(timeTo.getText()));
                Trip t = ctx.securedTripService.create(vehicleBox.getValue(),
                        capacity.getValue(), new TimeWindow(start, end),
                        driver.getText().isBlank() ? null : driver.getText());
                feedback.setText("Created trip " + t.id());
                refreshTrips();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        };
        createBtn.setOnAction(e -> createTripAction.run());

        addRiderBtn.setOnAction(e -> {
            Trip selected = tripList.getSelectionModel().getSelectedItem();
            if (selected == null) { feedback.setText("Select a trip first."); return; }
            Customer c = customerBox.getValue();
            if (c == null) { feedback.setText("Pick a customer."); return; }
            try {
                Address p = new Address(pickupLine.getText(), pickupCity.getText(), null, null, null);
                Address d = new Address(dropLine.getText(), dropCity.getText(), null, null, null);
                Order o = ctx.securedTripService.addRiderOrder(selected.id(), c.id(), p, d,
                        riderCount.getValue(), priorityBox.getValue(),
                        Double.parseDouble(miles.getText()),
                        Integer.parseInt(duration.getText()),
                        null, null, null);
                feedback.setText("Added rider " + o.id() + " to trip " + selected.id());
                selectedRiders.setAll(ctx.securedTripService.riderOrders(selected.id()));
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });

        dispatchBtn.setOnAction(e -> tripAction(t -> {
            ctx.securedTripService.dispatch(t.id());
            return "Dispatched " + t.id();
        }));
        closeBtn.setOnAction(e -> tripAction(t -> {
            ctx.securedTripService.close(t.id());
            return "Closed " + t.id();
        }));
        cancelBtn.setOnAction(e -> tripAction(t -> {
            ctx.securedTripService.cancel(t.id(), "UI");
            return "Canceled " + t.id();
        }));

        refreshTrips();

        VBox leftCol = new VBox(8, new Label("New trip"), form, new Label("Trips"), tripList);
        leftCol.setPadding(new Insets(8));

        GridPane riderForm = new GridPane();
        riderForm.setHgap(8);
        riderForm.setVgap(4);
        int rr = 0;
        riderForm.add(new Label("Customer"), 0, rr); riderForm.add(customerBox, 1, rr++);
        riderForm.add(new Label("Pickup line1"), 0, rr); riderForm.add(pickupLine, 1, rr++);
        riderForm.add(new Label("Pickup city"), 0, rr); riderForm.add(pickupCity, 1, rr++);
        riderForm.add(new Label("Dropoff line1"), 0, rr); riderForm.add(dropLine, 1, rr++);
        riderForm.add(new Label("Dropoff city"), 0, rr); riderForm.add(dropCity, 1, rr++);
        riderForm.add(new Label("Rider count"), 0, rr); riderForm.add(riderCount, 1, rr++);
        riderForm.add(new Label("Priority"), 0, rr); riderForm.add(priorityBox, 1, rr++);
        riderForm.add(new Label("Miles"), 0, rr); riderForm.add(miles, 1, rr++);
        riderForm.add(new Label("Duration"), 0, rr); riderForm.add(duration, 1, rr++);
        riderForm.add(addRiderBtn, 1, rr);

        HBox tripActions = new HBox(8, dispatchBtn, closeBtn, cancelBtn);
        VBox rightCol = new VBox(8,
                new Label("Riders on selected trip"), ridersList,
                new Label("Add rider"), riderForm,
                tripActions, feedback);
        rightCol.setPadding(new Insets(8));

        HBox root = new HBox(12, leftCol, rightCol);
        root.setPadding(new Insets(12));

        Stage stage = new Stage();
        stage.setTitle("Trips & Carpools");
        stage.setScene(new Scene(root, 1100, 700));
        return stage;
    }

    private void refreshTrips() {
        try {
            trips.setAll(ctx.securedTripService.list());
        } catch (RuntimeException ex) {
            trips.clear();
        }
    }

    private void tripAction(java.util.function.Function<Trip, String> action) {
        Trip selected = tripList.getSelectionModel().getSelectedItem();
        if (selected == null) { feedback.setText("Select a trip first."); return; }
        try {
            String msg = action.apply(selected);
            feedback.setText(msg);
            refreshTrips();
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }

    @Override
    public void save() {
        if (createTripAction != null) createTripAction.run();
    }
}
