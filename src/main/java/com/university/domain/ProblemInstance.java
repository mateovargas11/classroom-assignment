package com.university.domain;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ProblemInstance {
    
    public static final int NUM_CLASSROOMS = 39;
    public static final int MAX_DAYS = 25;
    public static final int SLOTS_PER_CLASSROOM_PER_DAY = 13;
    public static final int BLOCKS_PER_SLOT = 2;
    public static final double BLOCK_DURATION_HOURS = 0.5;
    
    public static final String EMPTY_SUBJECT_ID = "V";
    public static final Subject EMPTY_SUBJECT = new Subject(EMPTY_SUBJECT_ID, "Vacio", 0, 0.5);
    
    private List<Subject> subjects;
    private List<Classroom> classrooms;
    
    private Map<String, Integer> subjectIndexMap;
    private Map<String, Integer> classroomIndexMap;

    public ProblemInstance() {}

    public ProblemInstance(List<Subject> subjects, List<Classroom> classrooms) {
        this.subjects = subjects;
        this.classrooms = classrooms;
        buildIndexMaps();
    }

    private void buildIndexMaps() {
        subjectIndexMap = new HashMap<>();
        for (int i = 0; i < subjects.size(); i++) {
            subjectIndexMap.put(subjects.get(i).getId(), i);
        }
        subjectIndexMap.put(EMPTY_SUBJECT_ID, subjects.size());
        
        classroomIndexMap = new HashMap<>();
        for (int i = 0; i < classrooms.size(); i++) {
            classroomIndexMap.put(classrooms.get(i).getId(), i);
        }
    }

    public int getVectorSize() {
        return NUM_CLASSROOMS * SLOTS_PER_CLASSROOM_PER_DAY * MAX_DAYS;
    }

    public int getNumberOfSubjectsIncludingEmpty() {
        return subjects.size() + 1;
    }

    public Subject getSubjectByIndex(int index) {
        if (index == subjects.size()) {
            return EMPTY_SUBJECT;
        }
        return subjects.get(index);
    }

    public int getSubjectIndex(String subjectId) {
        return subjectIndexMap.getOrDefault(subjectId, subjects.size());
    }

    public int getEmptySubjectIndex() {
        return subjects.size();
    }

    public Classroom getClassroomByIndex(int index) {
        return classrooms.get(index);
    }

    public int getClassroomIndex(String classroomId) {
        return classroomIndexMap.get(classroomId);
    }

    public int[] decodeVectorPosition(int position) {
        int slotsPerDay = NUM_CLASSROOMS * SLOTS_PER_CLASSROOM_PER_DAY;
        int day = position / slotsPerDay;
        int remainder = position % slotsPerDay;
        int classroom = remainder / SLOTS_PER_CLASSROOM_PER_DAY;
        int slot = remainder % SLOTS_PER_CLASSROOM_PER_DAY;
        return new int[]{day, classroom, slot};
    }

    public int encodeVectorPosition(int day, int classroom, int slot) {
        return day * NUM_CLASSROOMS * SLOTS_PER_CLASSROOM_PER_DAY + 
               classroom * SLOTS_PER_CLASSROOM_PER_DAY + slot;
    }

    public int getMatrixRows() {
        return SLOTS_PER_CLASSROOM_PER_DAY * BLOCKS_PER_SLOT * MAX_DAYS;
    }

    public int getMatrixCols() {
        return NUM_CLASSROOMS;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
        buildIndexMaps();
    }

    public List<Classroom> getClassrooms() {
        return classrooms;
    }

    public void setClassrooms(List<Classroom> classrooms) {
        this.classrooms = classrooms;
        buildIndexMaps();
    }
}
