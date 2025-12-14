package com.university.domain;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class ProblemInstance {

    public static final int MAX_DAYS = 25;
    public static final String EMPTY_SUBJECT_ID = "V";
    public static final Subject EMPTY_SUBJECT = new Subject(EMPTY_SUBJECT_ID, "Vacio", 0, 0.5);

    private final List<Subject> subjects;
    private final List<Classroom> classrooms;
    private final List<int[]> conflictPairs;


    public ProblemInstance(List<Subject> subjects, List<Classroom> classrooms, List<int[]> conflictPairs) {
        this.subjects = subjects;
        this.classrooms = classrooms;
        this.conflictPairs = conflictPairs != null ? conflictPairs : new ArrayList<>();
    }

    public Subject getSubjectByIndex(int index) {
        if (index == subjects.size()) {
            return EMPTY_SUBJECT;
        }
        return subjects.get(index);
    }
    public Classroom getClassroomByIndex(int index) {
        return classrooms.get(index);
    }

    public List<Subject> getSubjects() {
        return subjects;
    }


    public List<Classroom> getClassrooms() {
        return classrooms;
    }

    public List<int[]> getConflictPairs() {
        return conflictPairs;
    }
}
