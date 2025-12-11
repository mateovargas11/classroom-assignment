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
        sortedClassroomsByCapacity.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.minClassroomsPerSubject = new int[instance.getSubjects().size()];
        this.optimalClassroomsPerSubject = new int[instance.getSubjects().size()][];
        calculateOptimalAssignments();

        int numSubjects = instance.getSubjects().size();
        int vectorSize = numSubjects * maxClassroomsPerSubject;
        int numClassrooms = instance.getClassrooms().size();

        numberOfObjectives(2);
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
                if (capacityAccumulated >= enrolled)
                    break;
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

        Map<Integer, Set<Integer>> assignments = decodeAssignments(solution);

        for (int subjectIdx = 0; subjectIdx < instance.getSubjects().size(); subjectIdx++) {
            Subject subject = instance.getSubjectByIndex(subjectIdx);
            Set<Integer> assignedClassrooms = assignments.get(subjectIdx);

            if (assignedClassrooms == null || assignedClassrooms.isEmpty()) {
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

        double objective1 = totalClassroomAssignments + (excessClassrooms * 10000);

        int[] subjectDays = calculateSubjectDays(assignments);
        double separationScore = calculateSeparationScore(subjectDays);
        double objective2 = -separationScore;

        solution.objectives()[0] = objective1;
        solution.objectives()[1] = objective2;
        solution.constraints()[0] = -capacityDeficit;
        solution.constraints()[1] = -unassignedSubjects;

        return solution;
    }

    private int[] calculateSubjectDays(Map<Integer, Set<Integer>> assignments) {
        int[] subjectDays = new int[instance.getSubjects().size()];
        Arrays.fill(subjectDays, -1);

        int[][] classroomAvailability = new int[ProblemInstance.NUM_CLASSROOMS][ProblemInstance.MAX_DAYS];
        int blocksPerDay = ProblemInstance.SLOTS_PER_CLASSROOM_PER_DAY * ProblemInstance.BLOCKS_PER_SLOT;

        List<Map.Entry<Integer, Set<Integer>>> sortedAssignments = new ArrayList<>(assignments.entrySet());
        sortedAssignments.sort((a, b) -> {
            int sizeCompare = Integer.compare(b.getValue().size(), a.getValue().size());
            if (sizeCompare != 0)
                return sizeCompare;
            return Integer.compare(
                    instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                    instance.getSubjectByIndex(a.getKey()).getEnrolledStudents());
        });

        for (Map.Entry<Integer, Set<Integer>> entry : sortedAssignments) {
            int subjectIdx = entry.getKey();
            Set<Integer> classrooms = entry.getValue();
            Subject subject = instance.getSubjectByIndex(subjectIdx);

            int durationBlocks = subject.getDurationBlocks();
            List<Integer> classroomList = new ArrayList<>(classrooms);

            for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                boolean allFit = true;
                for (int classroomIdx : classroomList) {
                    if (classroomAvailability[classroomIdx][day] + durationBlocks > blocksPerDay) {
                        allFit = false;
                        break;
                    }
                }

                if (allFit) {
                    subjectDays[subjectIdx] = day;
                    for (int classroomIdx : classroomList) {
                        classroomAvailability[classroomIdx][day] += durationBlocks;
                    }
                    break;
                }
            }
        }

        return subjectDays;
    }

    private double calculateSeparationScore(int[] subjectDays) {
        List<int[]> conflictPairs = instance.getConflictPairs();

        if (conflictPairs.isEmpty()) {
            return 0;
        }

        double totalSeparation = 0;
        int validPairs = 0;

        for (int[] pair : conflictPairs) {
            int subject1 = pair[0];
            int subject2 = pair[1];

            if (subject1 < subjectDays.length && subject2 < subjectDays.length) {
                int day1 = subjectDays[subject1];
                int day2 = subjectDays[subject2];

                if (day1 >= 0 && day2 >= 0) {
                    int separation = Math.abs(day1 - day2);
                    totalSeparation += separation;
                    validPairs++;
                }
            }
        }

        if (validPairs == 0) {
            return 0;
        }

        return totalSeparation / validPairs;
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
