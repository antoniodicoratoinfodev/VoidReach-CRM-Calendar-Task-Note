package com.crm.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Contact {
    private final StringProperty name;
    private final StringProperty company;
    private final StringProperty title;
    private final StringProperty email;
    private final StringProperty phone;
    private final StringProperty lastInteraction;
    private final StringProperty tags;

    public Contact(String name, String company, String title, String email, String phone, String lastInteraction, String tags) {
        this.name = new SimpleStringProperty(name);
        this.company = new SimpleStringProperty(company);
        this.title = new SimpleStringProperty(title);
        this.email = new SimpleStringProperty(email);
        this.phone = new SimpleStringProperty(phone);
        this.lastInteraction = new SimpleStringProperty(lastInteraction);
        this.tags = new SimpleStringProperty(tags);
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty companyProperty() { return company; }
    public StringProperty titleProperty() { return title; }
    public StringProperty emailProperty() { return email; }
    public StringProperty phoneProperty() { return phone; }
    public StringProperty lastInteractionProperty() { return lastInteraction; }
    public StringProperty tagsProperty() { return tags; }

    public void setName(String value) { this.name.set(value); }
    public void setCompany(String value) { this.company.set(value); }
    public void setTitle(String value) { this.title.set(value); }
    public void setEmail(String value) { this.email.set(value); }
    public void setPhone(String value) { this.phone.set(value); }
    public void setLastInteraction(String value) { this.lastInteraction.set(value); }
    public void setTags(String value) { this.tags.set(value); }
}
