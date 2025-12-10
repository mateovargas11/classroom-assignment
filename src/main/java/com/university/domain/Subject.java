package com.university.domain;

public class Subject {
    private String id;
    private String name;
    private int enrolledStudents;
    private double durationHours;

    public Subject() {}

    public Subject(String id, String name, int enrolledStudents, double durationHours) {
        this.id = id;
        this.name = name;
        this.enrolledStudents = enrolledStudents;
        this.durationHours = durationHours;
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

    public int getEnrolledStudents() {
        return enrolledStudents;
    }

    public void setEnrolledStudents(int enrolledStudents) {
        this.enrolledStudents = enrolledStudents;
    }

    public double getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(double durationHours) {
        this.durationHours = durationHours;
    }

    public int getDurationBlocks() {
        return (int) Math.ceil(durationHours * 2);
    }

    @Override
    public String toString() {
        return String.format("Subject[id=%s, name=%s, enrolled=%d, duration=%.1fh]", 
            id, name, enrolledStudents, durationHours);
    }
}
