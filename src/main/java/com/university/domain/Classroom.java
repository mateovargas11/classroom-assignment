package com.university.domain;

public class Classroom {
    private String id;
    private String name;
    private int capacity;

    public Classroom() {}

    public Classroom(String id, String name, int capacity) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public String toString() {
        return String.format("Classroom[id=%s, name=%s, capacity=%d]", id, name, capacity);
    }
}

