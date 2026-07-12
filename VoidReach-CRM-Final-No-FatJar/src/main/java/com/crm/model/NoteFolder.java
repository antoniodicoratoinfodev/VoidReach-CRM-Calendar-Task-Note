package com.crm.model;

import java.util.UUID;

public final class NoteFolder {
    private final String id;
    private String name;

    public NoteFolder(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public NoteFolder(String id, String name) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("A note folder must have an ID.");
        this.id = id;
        setName(name);
    }

    public String getId() { return id; }
    public String getName() { return name; }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Enter a folder name.");
        this.name = name.trim();
    }
}
