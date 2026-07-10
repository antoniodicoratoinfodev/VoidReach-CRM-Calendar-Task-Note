package com.crm.model;

public class Task {
    private String title;
    private String description;
    private int startMin;
    private int duration;
    private String color;

    public Task(String title, String description, int startMin, int duration, String color) {
        this.title = title;
        this.description = description;
        this.startMin = startMin;
        this.duration = duration;
        this.color = color;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getStartMin() { return startMin; }
    public void setStartMin(int startMin) { this.startMin = startMin; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
