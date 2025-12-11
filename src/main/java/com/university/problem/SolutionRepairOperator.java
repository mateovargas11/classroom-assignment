package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

public class SolutionRepairOperator {

    private final ProblemInstance instance;
    private final int maxClassroomsPerSubject;
    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final List<Integer> sortedClassroomsByCapacityAsc;

    public SolutionRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject) {
        this.instance = instance;
        this.maxClassroomsPerSubject = maxClassroomsPerSubject;

        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < instance.getClassrooms().size(); i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.sortedClassroomsByCapacityAsc = new ArrayList<>(sortedClassroomsByCapacityDesc);
        Collections.reverse(sortedClassroomsByCapacityAsc);
    }

    public void repair(IntegerSolution solution) {
        for (int subjectIdx = 0; subjectIdx < instance.getSubjects().size(); subjectIdx++) {
            repairSubjectAssignment(solution, subjectIdx);
        }
    }

    private void repairSubjectAssignment(IntegerSolution solution, int subjectIdx) {
        Subject subject = instance.getSubjectByIndex(subjectIdx);
        int enrolled = subject.getEnrolledStudents();

        Set<Integer> assignedClassrooms = new TreeSet<>((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            int pos = subjectIdx * maxClassroomsPerSubject + slot;
            int classroomIdx = solution.variables().get(pos);
            if (classroomIdx >= 0) {
                assignedClassrooms.add(classroomIdx);
            }
        }

        if (assignedClassrooms.isEmpty()) {
            int pos = subjectIdx * maxClassroomsPerSubject;
            int bestFit = findBestFitClassroom(enrolled);
            solution.variables().set(pos, bestFit);
            return;
        }

        List<Integer> classroomList = new ArrayList<>(assignedClassrooms);
        int capacityNeeded = enrolled;
        int capacityAccumulated = 0;
        List<Integer> requiredClassrooms = new ArrayList<>();

        for (int classroomIdx : classroomList) {
            if (capacityAccumulated >= capacityNeeded)
                break;
            requiredClassrooms.add(classroomIdx);
            capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
        }

        if (capacityAccumulated < capacityNeeded) {
            for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                if (capacityAccumulated >= capacityNeeded)
                    break;
                if (!requiredClassrooms.contains(classroomIdx)) {
                    requiredClassrooms.add(classroomIdx);
                    capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }
            }
        }

        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            int pos = subjectIdx * maxClassroomsPerSubject + slot;
            if (slot < requiredClassrooms.size()) {
                solution.variables().set(pos, requiredClassrooms.get(slot));
            } else {
                solution.variables().set(pos, -1);
            }
        }
    }

    private int findBestFitClassroom(int enrolled) {
        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                return classroomIdx;
            }
        }
        return sortedClassroomsByCapacityDesc.get(0);
    }
}
