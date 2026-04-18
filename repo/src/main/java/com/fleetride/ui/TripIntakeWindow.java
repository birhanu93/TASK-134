package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class TripIntakeWindow implements Saveable {
    private final AppContext ctx;
    private Runnable submitter;

    ChoiceBox<Customer> customerBox;
    TextField pickupLine;
    TextField pickupCity;
    Spinner<Integer> pickupFloor;
    TextField pickupFloorNotes;
    TextField dropLine;
    TextField dropCity;
    Spinner<Integer> dropFloor;
    TextField dropFloorNotes;
    Spinner<Integer> riders;
    ChoiceBox<VehicleType> vehicle;
    ChoiceBox<ServicePriority> priority;
    DatePicker date;
    TextField timeFrom;
    TextField timeTo;
    TextField miles;
    TextField durationMin;
    TextField coupon;
    Label feedback;

    public TripIntakeWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(12));

        customerBox = new ChoiceBox<>();
        customerBox.setId("customerBox");
        customerBox.setItems(FXCollections.observableArrayList(ctx.securedCustomerService.list()));

        pickupLine = tf("pickupLine");
        pickupCity = tf("pickupCity");
        pickupFloor = new Spinner<>(0, 200, 1);
        pickupFloor.setId("pickupFloor");
        pickupFloorNotes = new TextField();
        pickupFloorNotes.setId("pickupFloorNotes");
        pickupFloorNotes.setPromptText("optional — e.g. loading dock, apt 4B");
        dropLine = tf("dropLine");
        dropCity = tf("dropCity");
        dropFloor = new Spinner<>(0, 200, 1);
        dropFloor.setId("dropFloor");
        dropFloorNotes = new TextField();
        dropFloorNotes.setId("dropFloorNotes");
        dropFloorNotes.setPromptText("optional — e.g. ring bell on arrival");
        riders = new Spinner<>(1, 6, 1);
        riders.setId("riders");
        vehicle = new ChoiceBox<>(FXCollections.observableArrayList(VehicleType.values()));
        vehicle.setId("vehicle");
        priority = new ChoiceBox<>(FXCollections.observableArrayList(ServicePriority.values()));
        priority.setId("priority");
        date = new DatePicker(LocalDate.now());
        date.setId("date");
        timeFrom = new TextField("14:00");
        timeFrom.setId("timeFrom");
        timeTo = new TextField("15:00");
        timeTo.setId("timeTo");
        miles = new TextField("3.0");
        miles.setId("miles");
        durationMin = new TextField("10");
        durationMin.setId("durationMin");
        coupon = new TextField();
        coupon.setId("coupon");

        int r = 0;
        grid.add(new Label("Customer"), 0, r); grid.add(customerBox, 1, r++);
        grid.add(new Label("Pickup line1"), 0, r); grid.add(pickupLine, 1, r++);
        grid.add(new Label("Pickup city"), 0, r); grid.add(pickupCity, 1, r++);
        grid.add(new Label("Pickup floor"), 0, r); grid.add(pickupFloor, 1, r++);
        grid.add(new Label("Pickup floor notes"), 0, r); grid.add(pickupFloorNotes, 1, r++);
        grid.add(new Label("Dropoff line1"), 0, r); grid.add(dropLine, 1, r++);
        grid.add(new Label("Dropoff city"), 0, r); grid.add(dropCity, 1, r++);
        grid.add(new Label("Dropoff floor"), 0, r); grid.add(dropFloor, 1, r++);
        grid.add(new Label("Dropoff floor notes"), 0, r); grid.add(dropFloorNotes, 1, r++);
        grid.add(new Label("Riders"), 0, r); grid.add(riders, 1, r++);
        grid.add(new Label("Vehicle"), 0, r); grid.add(vehicle, 1, r++);
        grid.add(new Label("Priority"), 0, r); grid.add(priority, 1, r++);
        grid.add(new Label("Date"), 0, r); grid.add(date, 1, r++);
        grid.add(new Label("From (HH:mm)"), 0, r); grid.add(timeFrom, 1, r++);
        grid.add(new Label("To (HH:mm)"), 0, r); grid.add(timeTo, 1, r++);
        grid.add(new Label("Miles"), 0, r); grid.add(miles, 1, r++);
        grid.add(new Label("Duration (min)"), 0, r); grid.add(durationMin, 1, r++);
        grid.add(new Label("Coupon code"), 0, r); grid.add(coupon, 1, r++);

        Button submit = new Button("Create order (Ctrl+S)");
        submit.setId("submit");
        submit.setDefaultButton(true);
        feedback = new Label();
        feedback.setId("feedback");

        this.submitter = () -> {
            try {
                Customer c = customerBox.getValue();
                if (c == null) throw new IllegalArgumentException("pick a customer");
                Address p = new Address(pickupLine.getText(), pickupCity.getText(), null, null, pickupFloor.getValue());
                Address d = new Address(dropLine.getText(), dropCity.getText(), null, null, dropFloor.getValue());
                LocalDateTime start = LocalDateTime.of(date.getValue(), LocalTime.parse(timeFrom.getText()));
                LocalDateTime end = LocalDateTime.of(date.getValue(), LocalTime.parse(timeTo.getText()));
                TimeWindow w = new TimeWindow(start, end);
                VehicleType vt = vehicle.getValue() == null ? VehicleType.STANDARD : vehicle.getValue();
                ServicePriority pr = priority.getValue() == null ? ServicePriority.NORMAL : priority.getValue();
                Order order = ctx.securedOrderService.create(c.id(), p, d, riders.getValue(), w, vt, pr,
                        Double.parseDouble(miles.getText()), Integer.parseInt(durationMin.getText()),
                        coupon.getText().isBlank() ? null : coupon.getText(),
                        pickupFloorNotes.getText(), dropFloorNotes.getText());
                ctx.securedOrderService.quoteResolving(order);
                feedback.setText("Created " + order.id());
                ctx.log.info("ui_trip_intake_create",
                        com.fleetride.log.StructuredLogger.fields("order", order.id()));
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        };
        submit.setOnAction(e -> submitter.run());

        VBox root = new VBox(8, grid, submit, feedback);
        root.setPadding(new Insets(12));

        Stage stage = new Stage();
        stage.setTitle("Trip Intake");
        stage.setScene(new Scene(root, 560, 760));
        return stage;
    }

    private static TextField tf(String id) {
        TextField t = new TextField();
        t.setId(id);
        return t;
    }

    @Override
    public void save() {
        if (submitter != null) submitter.run();
    }
}
