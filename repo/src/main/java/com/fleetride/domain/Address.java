package com.fleetride.domain;

import java.util.Objects;

public final class Address {
    private final String line1;
    private final String city;
    private final String state;
    private final String zip;
    private final Integer floor;

    public Address(String line1, String city, String state, String zip, Integer floor) {
        if (line1 == null || line1.isBlank()) {
            throw new IllegalArgumentException("line1 required");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city required");
        }
        this.line1 = line1;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.floor = floor;
    }

    public String line1() { return line1; }
    public String city() { return city; }
    public String state() { return state; }
    public String zip() { return zip; }
    public Integer floor() { return floor; }

    public String format() {
        StringBuilder sb = new StringBuilder(line1).append(", ").append(city);
        if (state != null) sb.append(", ").append(state);
        if (zip != null) sb.append(" ").append(zip);
        if (floor != null) sb.append(" (Fl ").append(floor).append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address a)) return false;
        return Objects.equals(line1, a.line1) && Objects.equals(city, a.city)
                && Objects.equals(state, a.state) && Objects.equals(zip, a.zip)
                && Objects.equals(floor, a.floor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(line1, city, state, zip, floor);
    }
}
