package com.university.decoder;

import com.university.domain.Classroom;
import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import com.university.problem.ClassroomAssignmentProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

public class SolutionDecoder {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;
    private final int[] minClassroomsPerSubject;

    private static final int BLOCKS_PER_DAY = ProblemInstance.SLOTS_PER_CLASSROOM_PER_DAY
            * ProblemInstance.BLOCKS_PER_SLOT;
    private static final int TOTAL_BLOCKS = BLOCKS_PER_DAY * ProblemInstance.MAX_DAYS;

    public SolutionDecoder(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this.instance = instance;
        this.problem = problem;
        this.minClassroomsPerSubject = calculateMinClassroomsPerSubject();
    }

    private int[] calculateMinClassroomsPerSubject() {
        List<Integer> sortedClassrooms = new ArrayList<>();
        for (int i = 0; i < instance.getClassrooms().size(); i++) {
            sortedClassrooms.add(i);
        }
        sortedClassrooms.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        int[] minClassrooms = new int[instance.getSubjects().size()];

        for (int i = 0; i < instance.getSubjects().size(); i++) {
            Subject subject = instance.getSubjects().get(i);
            int enrolled = subject.getEnrolledStudents();

            int classroomsNeeded = 0;
            int capacityAccumulated = 0;

            for (int classroomIdx : sortedClassrooms) {
                if (capacityAccumulated >= enrolled)
                    break;
                capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
                classroomsNeeded++;
            }

            minClassrooms[i] = Math.max(1, classroomsNeeded);
        }

        return minClassrooms;
    }

    public String[][] decodeToMatrix(IntegerSolution solution) {
        String[][] matrix = new String[TOTAL_BLOCKS][ProblemInstance.NUM_CLASSROOMS];

        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            for (int j = 0; j < ProblemInstance.NUM_CLASSROOMS; j++) {
                matrix[i][j] = ProblemInstance.EMPTY_SUBJECT_ID;
            }
        }

        Map<Integer, Set<Integer>> assignments = problem.decodeAssignments(solution);

        int[] nextAvailableBlock = new int[ProblemInstance.NUM_CLASSROOMS];

        List<Map.Entry<Integer, Set<Integer>>> sortedAssignments = new ArrayList<>(assignments.entrySet());
        sortedAssignments.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                instance.getSubjectByIndex(a.getKey()).getEnrolledStudents()));

        for (Map.Entry<Integer, Set<Integer>> entry : sortedAssignments) {
            int subjectIdx = entry.getKey();
            Set<Integer> classrooms = entry.getValue();
            Subject subject = instance.getSubjectByIndex(subjectIdx);

            int durationBlocks = subject.getDurationBlocks();

            for (int classroomIdx : classrooms) {
                int startBlock = nextAvailableBlock[classroomIdx];

                if (startBlock + durationBlocks > TOTAL_BLOCKS) {
                    continue;
                }

                for (int b = 0; b < durationBlocks; b++) {
                    matrix[startBlock + b][classroomIdx] = subject.getId();
                }

                nextAvailableBlock[classroomIdx] = startBlock + durationBlocks;
            }
        }

        return matrix;
    }

    public void printMatrix(IntegerSolution solution, int maxDays) {
        String[][] matrix = decodeToMatrix(solution);

        int daysToShow = Math.min(maxDays, ProblemInstance.MAX_DAYS);

        System.out.println("\n=== Matriz de Asignacion ===");
        System.out.println("Filas: Bloques de 0.5h | Columnas: Salones");
        System.out.printf("Total: %d filas (bloques) x %d columnas (salones)\n", TOTAL_BLOCKS,
                ProblemInstance.NUM_CLASSROOMS);
        System.out.printf("Mostrando primeros %d dias y primeros 10 salones\n", daysToShow);

        for (int day = 0; day < daysToShow; day++) {
            System.out.println("\n--- Dia " + (day + 1) + " ---");

            System.out.print("Hora\\Salon  ");
            for (int c = 0; c < Math.min(10, ProblemInstance.NUM_CLASSROOMS); c++) {
                String id = instance.getClassroomByIndex(c).getId();
                System.out.printf("%6s ", id.length() > 6 ? id.substring(0, 6) : id);
            }
            if (ProblemInstance.NUM_CLASSROOMS > 10) {
                System.out.print("...");
            }
            System.out.println();

            int startBlock = day * BLOCKS_PER_DAY;
            for (int b = 0; b < BLOCKS_PER_DAY; b++) {
                int blockNum = startBlock + b;
                double hour = b * 0.5;
                System.out.printf("%5.1fh      ", hour);

                for (int c = 0; c < Math.min(10, ProblemInstance.NUM_CLASSROOMS); c++) {
                    String subjectId = matrix[blockNum][c];
                    System.out.printf("%6s ", subjectId);
                }
                if (ProblemInstance.NUM_CLASSROOMS > 10) {
                    System.out.print("...");
                }
                System.out.println();
            }
        }
    }

    public void printSummary(IntegerSolution solution) {
        Map<Integer, Set<Integer>> assignments = problem.decodeAssignments(solution);

        Set<Integer> allUsedClassrooms = new HashSet<>();
        for (Set<Integer> classrooms : assignments.values()) {
            allUsedClassrooms.addAll(classrooms);
        }

        System.out.println("\n=== Resumen de Asignacion ===");
        System.out.println(
                "Salones unicos utilizados: " + allUsedClassrooms.size() + " de " + instance.getClassrooms().size());
        System.out.println("Materias asignadas: " + assignments.size() + " de " + instance.getSubjects().size());

        int totalAssignments = 0;
        int subjectsWithMultipleClassrooms = 0;
        int maxClassroomsPerSubject = 0;
        int totalExcess = 0;

        for (Map.Entry<Integer, Set<Integer>> entry : assignments.entrySet()) {
            int subjectIdx = entry.getKey();
            int numClassrooms = entry.getValue().size();
            int minNeeded = minClassroomsPerSubject[subjectIdx];

            totalAssignments += numClassrooms;
            if (numClassrooms > 1) {
                subjectsWithMultipleClassrooms++;
            }
            if (numClassrooms > maxClassroomsPerSubject) {
                maxClassroomsPerSubject = numClassrooms;
            }
            if (numClassrooms > minNeeded) {
                totalExcess += (numClassrooms - minNeeded);
            }
        }

        System.out.println("Total asignaciones materia-salon: " + totalAssignments);
        System.out.println("Minimo teorico de asignaciones: " + problem.getTotalMinClassrooms());
        System.out.println("Exceso total de salones: " + totalExcess);
        System.out.println("Materias divididas en multiples salones: " + subjectsWithMultipleClassrooms);
        System.out.println("Maximo salones por materia: " + maxClassroomsPerSubject);

        System.out.println("\n--- Detalle por materia (ordenado por inscriptos) ---");

        List<Map.Entry<Integer, Set<Integer>>> sortedEntries = new ArrayList<>(assignments.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                instance.getSubjectByIndex(a.getKey()).getEnrolledStudents()));

        int shown = 0;
        for (Map.Entry<Integer, Set<Integer>> entry : sortedEntries) {
            if (shown >= 25) {
                System.out.println("... y " + (sortedEntries.size() - 25) + " materias mas");
                break;
            }

            int subjectIdx = entry.getKey();
            Set<Integer> classrooms = entry.getValue();
            Subject s = instance.getSubjectByIndex(subjectIdx);
            int minNeeded = minClassroomsPerSubject[subjectIdx];

            int totalCapacity = 0;
            for (int classroomIdx : classrooms) {
                totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
            }

            String capStatus = s.getEnrolledStudents() <= totalCapacity ? "OK" : "INSUF";
            String countStatus = classrooms.size() <= minNeeded ? "" : " EXCESO+" + (classrooms.size() - minNeeded);

            System.out.printf("  %s (%d inscr): %d/%d salon(es), cap=%d [%s%s]\n",
                    s.getName().length() > 35 ? s.getName().substring(0, 35) + "..." : s.getName(),
                    s.getEnrolledStudents(),
                    classrooms.size(),
                    minNeeded,
                    totalCapacity,
                    capStatus,
                    countStatus);

            if (classrooms.size() > 1 || shown < 10) {
                List<Integer> sortedClassrooms = new ArrayList<>(classrooms);
                sortedClassrooms.sort((a, b) -> Integer.compare(
                        instance.getClassroomByIndex(b).getCapacity(),
                        instance.getClassroomByIndex(a).getCapacity()));
                for (int classroomIdx : sortedClassrooms) {
                    Classroom c = instance.getClassroomByIndex(classroomIdx);
                    System.out.printf("      -> %s (cap=%d)\n", c.getId(), c.getCapacity());
                }
            }
            shown++;
        }

        List<Integer> unassigned = new ArrayList<>();
        for (int i = 0; i < instance.getSubjects().size(); i++) {
            if (!assignments.containsKey(i)) {
                unassigned.add(i);
            }
        }

        if (!unassigned.isEmpty()) {
            System.out.println("\n--- Materias NO asignadas (" + unassigned.size() + ") ---");
            int showUnassigned = 0;
            for (int subjectIdx : unassigned) {
                if (showUnassigned >= 10) {
                    System.out.println("... y " + (unassigned.size() - 10) + " mas");
                    break;
                }
                Subject s = instance.getSubjectByIndex(subjectIdx);
                System.out.printf("  %s (%d inscr)\n", s.getName(), s.getEnrolledStudents());
                showUnassigned++;
            }
        }
    }
}
