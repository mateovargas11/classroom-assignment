package com.university.decoder;

import com.university.domain.Classroom;
import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import com.university.problem.ClassroomAssignmentProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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

        int[][] classroomAvailability = new int[ProblemInstance.NUM_CLASSROOMS][ProblemInstance.MAX_DAYS];

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

            int bestDay = findBestDayForSubject(classroomList, durationBlocks, classroomAvailability);

            if (bestDay >= 0) {
                for (int classroomIdx : classroomList) {
                    int startBlockInDay = classroomAvailability[classroomIdx][bestDay];
                    int globalStartBlock = bestDay * BLOCKS_PER_DAY + startBlockInDay;

                    for (int b = 0; b < durationBlocks && (globalStartBlock + b) < TOTAL_BLOCKS; b++) {
                        matrix[globalStartBlock + b][classroomIdx] = subject.getId();
                    }

                    classroomAvailability[classroomIdx][bestDay] = startBlockInDay + durationBlocks;
                }
            }
        }

        return matrix;
    }

    private int findBestDayForSubject(List<Integer> classrooms, int durationBlocks, int[][] availability) {
        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            boolean allFit = true;

            for (int classroomIdx : classrooms) {
                int usedBlocks = availability[classroomIdx][day];
                if (usedBlocks + durationBlocks > BLOCKS_PER_DAY) {
                    allFit = false;
                    break;
                }
            }

            if (allFit) {
                return day;
            }
        }

        return -1;
    }

    public void printMatrix(IntegerSolution solution, int maxDays) {
        String[][] matrix = decodeToMatrix(solution);

        int daysToShow = Math.min(maxDays, ProblemInstance.MAX_DAYS);

        System.out.println("\n=== Matriz de Asignacion ===");
        System.out.println("Filas: Bloques de 0.5h | Columnas: Salones");
        System.out.printf("Total: %d filas (bloques) x %d columnas (salones)\n", TOTAL_BLOCKS,
                ProblemInstance.NUM_CLASSROOMS);
        System.out.printf("Cada dia tiene %d bloques (%.1f horas)\n", BLOCKS_PER_DAY, BLOCKS_PER_DAY * 0.5);
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
                double hour = 8.0 + (b * 0.5);
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

    public void printScheduleSummary(IntegerSolution solution) {
        String[][] matrix = decodeToMatrix(solution);

        System.out.println("\n=== Resumen de Horarios por Dia ===");

        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            Set<String> subjectsInDay = new HashSet<>();
            int startBlock = day * BLOCKS_PER_DAY;

            for (int b = 0; b < BLOCKS_PER_DAY; b++) {
                for (int c = 0; c < ProblemInstance.NUM_CLASSROOMS; c++) {
                    String subjectId = matrix[startBlock + b][c];
                    if (!subjectId.equals(ProblemInstance.EMPTY_SUBJECT_ID)) {
                        subjectsInDay.add(subjectId);
                    }
                }
            }

            if (!subjectsInDay.isEmpty()) {
                System.out.printf("Dia %2d: %d examenes\n", day + 1, subjectsInDay.size());
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

    public void exportToCSV(IntegerSolution solution, String basePath) throws IOException {
        new java.io.File("output").mkdirs();

        exportAssignmentsCSV(solution, basePath + "_asignaciones.csv");
        exportMatrixCSV(solution, basePath + "_matriz.csv");
        exportScheduleCSV(solution, basePath + "_horarios.csv");

        System.out.println("\nArchivos exportados:");
        System.out.println("  - " + basePath + "_asignaciones.csv");
        System.out.println("  - " + basePath + "_matriz.csv");
        System.out.println("  - " + basePath + "_horarios.csv");
    }

    private void exportAssignmentsCSV(IntegerSolution solution, String filePath) throws IOException {
        Map<Integer, Set<Integer>> assignments = problem.decodeAssignments(solution);
        String[][] matrix = decodeToMatrix(solution);

        Map<Integer, Integer> subjectDays = new HashMap<>();
        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            Set<String> subjectsInDay = new HashSet<>();
            int startBlock = day * BLOCKS_PER_DAY;
            for (int b = 0; b < BLOCKS_PER_DAY; b++) {
                for (int c = 0; c < ProblemInstance.NUM_CLASSROOMS; c++) {
                    String subjectId = matrix[startBlock + b][c];
                    if (!subjectId.equals(ProblemInstance.EMPTY_SUBJECT_ID)) {
                        subjectsInDay.add(subjectId);
                    }
                }
            }
            for (String subjectId : subjectsInDay) {
                int subjectIdx = instance.getSubjectIndex(subjectId);
                if (subjectIdx < instance.getSubjects().size()) {
                    subjectDays.put(subjectIdx, day + 1);
                }
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("materia_id,materia_nombre,inscriptos,duracion_horas,dia,salones,capacidad_total,estado");

            List<Map.Entry<Integer, Set<Integer>>> sortedEntries = new ArrayList<>(assignments.entrySet());
            sortedEntries.sort((a, b) -> Integer.compare(
                    instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                    instance.getSubjectByIndex(a.getKey()).getEnrolledStudents()));

            for (Map.Entry<Integer, Set<Integer>> entry : sortedEntries) {
                int subjectIdx = entry.getKey();
                Set<Integer> classrooms = entry.getValue();
                Subject s = instance.getSubjectByIndex(subjectIdx);

                int totalCapacity = 0;
                StringBuilder salonesStr = new StringBuilder();
                for (int classroomIdx : classrooms) {
                    Classroom c = instance.getClassroomByIndex(classroomIdx);
                    totalCapacity += c.getCapacity();
                    if (salonesStr.length() > 0)
                        salonesStr.append(";");
                    salonesStr.append(c.getId());
                }

                String status = s.getEnrolledStudents() <= totalCapacity ? "OK" : "INSUFICIENTE";
                int day = subjectDays.getOrDefault(subjectIdx, 0);

                writer.printf("%s,\"%s\",%d,%.1f,%d,\"%s\",%d,%s\n",
                        s.getId(),
                        s.getName().replace("\"", "'"),
                        s.getEnrolledStudents(),
                        s.getDurationHours(),
                        day,
                        salonesStr.toString(),
                        totalCapacity,
                        status);
            }
        }
    }

    private void exportMatrixCSV(IntegerSolution solution, String filePath) throws IOException {
        String[][] matrix = decodeToMatrix(solution);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            StringBuilder header = new StringBuilder("dia,bloque,hora");
            for (int c = 0; c < ProblemInstance.NUM_CLASSROOMS; c++) {
                header.append(",").append(instance.getClassroomByIndex(c).getId());
            }
            writer.println(header);

            for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                int startBlock = day * BLOCKS_PER_DAY;
                for (int b = 0; b < BLOCKS_PER_DAY; b++) {
                    int blockNum = startBlock + b;
                    double hour = 8.0 + (b * 0.5);

                    StringBuilder row = new StringBuilder();
                    row.append(day + 1).append(",").append(b + 1).append(",").append(String.format("%.1f", hour));

                    for (int c = 0; c < ProblemInstance.NUM_CLASSROOMS; c++) {
                        row.append(",").append(matrix[blockNum][c]);
                    }
                    writer.println(row);
                }
            }
        }
    }

    private void exportScheduleCSV(IntegerSolution solution, String filePath) throws IOException {
        String[][] matrix = decodeToMatrix(solution);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("dia,materia_id,materia_nombre,hora_inicio,hora_fin,salon,capacidad");

            for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                Map<String, ScheduleEntry> daySchedule = new LinkedHashMap<>();
                int startBlock = day * BLOCKS_PER_DAY;

                for (int c = 0; c < ProblemInstance.NUM_CLASSROOMS; c++) {
                    String currentSubject = null;
                    int startBlockInDay = -1;

                    for (int b = 0; b <= BLOCKS_PER_DAY; b++) {
                        String subjectId = (b < BLOCKS_PER_DAY) ? matrix[startBlock + b][c]
                                : ProblemInstance.EMPTY_SUBJECT_ID;

                        if (!subjectId.equals(currentSubject)) {
                            if (currentSubject != null && !currentSubject.equals(ProblemInstance.EMPTY_SUBJECT_ID)) {
                                String key = currentSubject + "_" + c;
                                double startHour = 8.0 + (startBlockInDay * 0.5);
                                double endHour = 8.0 + (b * 0.5);
                                Classroom classroom = instance.getClassroomByIndex(c);

                                int subjectIdx = instance.getSubjectIndex(currentSubject);
                                String subjectName = subjectIdx < instance.getSubjects().size()
                                        ? instance.getSubjectByIndex(subjectIdx).getName()
                                        : currentSubject;

                                daySchedule.put(key, new ScheduleEntry(
                                        day + 1, currentSubject, subjectName, startHour, endHour,
                                        classroom.getId(), classroom.getCapacity()));
                            }
                            currentSubject = subjectId;
                            startBlockInDay = b;
                        }
                    }
                }

                for (ScheduleEntry entry : daySchedule.values()) {
                    writer.printf("%d,%s,\"%s\",%.1f,%.1f,%s,%d\n",
                            entry.day, entry.subjectId, entry.subjectName.replace("\"", "'"),
                            entry.startHour, entry.endHour, entry.classroomId, entry.capacity);
                }
            }
        }
    }

    private record ScheduleEntry(int day, String subjectId, String subjectName,
            double startHour, double endHour, String classroomId, int capacity) {
    }
}
