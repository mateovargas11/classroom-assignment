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
    private final int[][] optimalClassroomsPerSubject;
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
        
        this.minClassroomsPerSubject = new int[instance.getSubjects().size()];
        this.optimalClassroomsPerSubject = new int[instance.getSubjects().size()][];
        calculateOptimalAssignments();
        
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
    
    private void calculateOptimalAssignments() {
        for (int i = 0; i < instance.getSubjects().size(); i++) {
            Subject subject = instance.getSubjects().get(i);
            int enrolled = subject.getEnrolledStudents();
            
            List<Integer> selectedClassrooms = new ArrayList<>();
            int capacityAccumulated = 0;
            
            for (int classroomIdx : sortedClassroomsByCapacity) {
                if (capacityAccumulated >= enrolled) break;
                int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
                if (cap > 0) {
                    selectedClassrooms.add(classroomIdx);
                    capacityAccumulated += cap;
                }
            }
            
            minClassroomsPerSubject[i] = Math.max(1, selectedClassrooms.size());
            optimalClassroomsPerSubject[i] = selectedClassrooms.stream()
                .mapToInt(Integer::intValue).toArray();
        }
    }

    @Override
    public IntegerSolution createSolution() {
        IntegerSolution solution = super.createSolution();
        
        Random random = new Random();
        
        for (int subjectIdx = 0; subjectIdx < instance.getSubjects().size(); subjectIdx++) {
            int[] optimalClassrooms = optimalClassroomsPerSubject[subjectIdx];
            
            for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
                int pos = subjectIdx * maxClassroomsPerSubject + slot;
                
                if (slot < optimalClassrooms.length) {
                    if (random.nextDouble() < 0.8) {
                        solution.variables().set(pos, optimalClassrooms[slot]);
                    } else {
                        int randomClassroom = sortedClassroomsByCapacity.get(
                            random.nextInt(Math.min(10, sortedClassroomsByCapacity.size())));
                        solution.variables().set(pos, randomClassroom);
                    }
                } else {
                    solution.variables().set(pos, -1);
                }
            }
        }
        
        return solution;
    }

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        int totalClassroomAssignments = 0;
        int capacityDeficit = 0;
        int unassignedSubjects = 0;
        int excessClassrooms = 0;
        
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
                int numAssigned = assignedClassrooms.size();
                int minNeeded = minClassroomsPerSubject[subjectIdx];
                totalClassroomAssignments += numAssigned;
                
                if (numAssigned > minNeeded) {
                    excessClassrooms += (numAssigned - minNeeded);
                }
                
                int totalCapacity = 0;
                for (int classroomIdx : assignedClassrooms) {
                    totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }
                
                if (subject.getEnrolledStudents() > totalCapacity) {
                    capacityDeficit += (subject.getEnrolledStudents() - totalCapacity);
                }
            }
        }
        
        double objective = totalClassroomAssignments + (excessClassrooms * 10000);
        
        solution.objectives()[0] = objective;
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
    
    public int[] getOptimalClassroomsForSubject(int subjectIndex) {
        return optimalClassroomsPerSubject[subjectIndex];
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
