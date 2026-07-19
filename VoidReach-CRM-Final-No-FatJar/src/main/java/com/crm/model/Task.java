package com.crm.model;

import java.util.UUID;

public class Task {
    public static final int MINUTES_PER_DAY = 24 * 60;
    public static final int MIN_DURATION_MINUTES = 1;

    private String title;
    private String description;
    private int startMin;
    private int duration;
    private String color;
    private boolean completed;
    private final String id;

    public Task(String title, String description, int startMin, int duration, String color) {
        this(UUID.randomUUID().toString(), title, description, startMin, duration, color);
    }

    public Task(String id, String title, String description, int startMin, int duration, String color) {
        this(id, title, description, startMin, duration, color, false);
    }

    public Task(String id, String title, String description, int startMin, int duration, String color,
                boolean completed) {
        validateSchedule(startMin, duration);
        this.id = id;
        this.title = title;
        this.description = description;
        this.startMin = startMin;
        this.duration = duration;
        this.color = color;
        this.completed = completed;
    }

    public String getTitle() { return title; }

    public String getDescription() { return description; }

    public int getStartMin() { return startMin; }
    public void setStartMin(int startMin) {
        validateSchedule(startMin, duration);
        this.startMin = startMin;
    }

    public int getDuration() { return duration; }
    public void setDuration(int duration) {
        validateSchedule(startMin, duration);
        this.duration = duration;
    }

    public String getColor() { return color; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public String getId() { return id; }

    public static void validateSchedule(int startMin, int duration) {
        if (startMin < 0 || startMin >= MINUTES_PER_DAY) {
            throw new IllegalArgumentException("The start time must be between 00:00 and 23:59.");
        }
        if (duration < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException("A task must have a positive duration.");
        }
        if (duration > MINUTES_PER_DAY - startMin) {
            throw new IllegalArgumentException("A task cannot end after 24:00.");
        }
    }
}
