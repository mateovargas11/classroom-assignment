package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

public class ClassroomAssignmentProblem extends AbstractIntegerProblem {
    
    private final ProblemInstance instance;
    private final int maxClassroomsPerSubject;
    private final int[] minClassroomsPerSubject;
    private final List<Integer> sortedClassroomsByCapacity;
    
    public ClassroomAssignmentProblem(ProblemInstance instance) {
        this.instance = instance;
        this.maxClassroomsPerSubject = 5;
        
        this.sortedClassroomsByCapacity = new ArrayList<>();
        for (int i = 0; i < instance.getClassrooms().size(); i++) {
            sortedClassroomsByCapacity.add(i);
        }
        sortedClassroomsByCapacity.sort((a, b) -> 
            Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()
            )
        );
        
        this.minClassroomsPerSubject = calculateMinClassroomsPerSubject();
        
        int numSubjects = instance.getSubjects().size();
        int vectorSize = numSubjects * maxClassroomsPerSubject;
        int numClassrooms = instance.getClassrooms().size();
        
        numberOfObjectives(1);
        numberOfConstraints(2);
        name("ClassroomAssignmentProblem");
        
        List<Integer> lowerBounds = new ArrayList<>(vectorSize);
        List<Integer> upperBounds = new ArrayList<>(vectorSize);
        
        for (int i = 0; i < vectorSize; i++) {
            lowerBounds.add(-1);
            upperBounds.add(numClassrooms - 1);
        }
        
        variableBounds(lowerBounds, upperBounds);
    }
    
    private int[] calculateMinClassroomsPerSubject() {
        int[] minClassrooms = new int[instance.getSubjects().size()];
        
        for (int i = 0; i < instance.getSubjects().size(); i++) {
            Subject subject = instance.getSubjects().get(i);
            int enrolled = subject.getEnrolledStudents();
            
            int classroomsNeeded = 0;
            int capacityAccumulated = 0;
            
            for (int classroomIdx : sortedClassroomsByCapacity) {
                if (capacityAccumulated >= enrolled) break;
                capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
                classroomsNeeded++;
            }
            
            minClassrooms[i] = Math.max(1, classroomsNeeded);
        }
        
        return minClassrooms;
    }

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        int totalClassroomAssignments = 0;
        int capacityDeficit = 0;
        int unassignedSubjects = 0;
        
        for (int subjectIdx = 0; subjectIdx < instance.getSubjects().size(); subjectIdx++) {
            Subject subject = instance.getSubjectByIndex(subjectIdx);
            Set<Integer> assignedClassrooms = new HashSet<>();
            
            for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
                int pos = subjectIdx * maxClassroomsPerSubject + slot;
                int classroomIdx = solution.variables().get(pos);
                if (classroomIdx >= 0) {
                    assignedClassrooms.add(classroomIdx);
                }
            }
            
            if (assignedClassrooms.isEmpty()) {
                unassignedSubjects++;
            } else {
                totalClassroomAssignments += assignedClassrooms.size();
                
                int totalCapacity = 0;
                for (int classroomIdx : assignedClassrooms) {
                    totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }
                
                if (subject.getEnrolledStudents() > totalCapacity) {
                    capacityDeficit += (subject.getEnrolledStudents() - totalCapacity);
                }
            }
        }
        
        solution.objectives()[0] = totalClassroomAssignments;
        solution.constraints()[0] = -capacityDeficit;
        solution.constraints()[1] = -unassignedSubjects;
        
        return solution;
    }
    
    public Map<Integer, Set<Integer>> decodeAssignments(IntegerSolution solution) {
        Map<Integer, Set<Integer>> assignments = new HashMap<>();
        
        for (int subjectIdx = 0; subjectIdx < instance.getSubjects().size(); subjectIdx++) {
            Set<Integer> classrooms = new HashSet<>();
            
            for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
                int pos = subjectIdx * maxClassroomsPerSubject + slot;
                int classroomIdx = solution.variables().get(pos);
                if (classroomIdx >= 0) {
                    classrooms.add(classroomIdx);
                }
            }
            
            if (!classrooms.isEmpty()) {
                assignments.put(subjectIdx, classrooms);
            }
        }
        
        return assignments;
    }
    
    public int getMinClassroomsForSubject(int subjectIndex) {
        return minClassroomsPerSubject[subjectIndex];
    }
    
    public int getTotalMinClassrooms() {
        int total = 0;
        for (int min : minClassroomsPerSubject) {
            total += min;
        }
        return total;
    }
    
    public int getMaxClassroomsPerSubject() {
        return maxClassroomsPerSubject;
    }

    public ProblemInstance getInstance() {
        return instance;
    }
}
