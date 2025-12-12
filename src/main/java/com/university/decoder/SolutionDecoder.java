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

/**
 * Decodificador de soluciones para el modelo de matriz directa.
 * El vector de solución ya representa directamente la matriz de horarios.
 */
public class SolutionDecoder {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;

    private static final int BLOCKS_PER_DAY = ClassroomAssignmentProblem.BLOCKS_PER_DAY;
    private static final int TOTAL_BLOCKS = BLOCKS_PER_DAY * ProblemInstance.MAX_DAYS;

    public SolutionDecoder(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this.instance = instance;
        this.problem = problem;
    }

    /**
     * Decodifica la solución a una matriz [bloques_totales][salones].
     * Cada celda contiene el ID de la materia que ocupa ese bloque.
     */
    public String[][] decodeToMatrix(IntegerSolution solution) {
        int numClassrooms = instance.getClassrooms().size();
        String[][] matrix = new String[TOTAL_BLOCKS][numClassrooms];

        // Inicializar con vacío
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            for (int j = 0; j < numClassrooms; j++) {
                matrix[i][j] = ProblemInstance.EMPTY_SUBJECT_ID;
            }
        }

        // Llenar desde el vector de solución
        int vectorSize = problem.getVectorSize();
        for (int pos = 0; pos < vectorSize; pos++) {
            int subjectIdx = solution.variables().get(pos);

            int[] decoded = problem.decodePosition(pos);
            int day = decoded[0];
            int classroom = decoded[1];
            int block = decoded[2];

            int globalBlock = day * BLOCKS_PER_DAY + block;

            if (globalBlock < TOTAL_BLOCKS && classroom < numClassrooms) {
                if (subjectIdx >= 0 && subjectIdx < instance.getSubjects().size()) {
                    matrix[globalBlock][classroom] = instance.getSubjectByIndex(subjectIdx).getId();
                } else {
                    matrix[globalBlock][classroom] = ProblemInstance.EMPTY_SUBJECT_ID;
                }
            }
        }

        return matrix;
    }

    /**
     * Imprime la matriz de asignación para visualización.
     */
    public void printMatrix(IntegerSolution solution, int maxDays) {
        String[][] matrix = decodeToMatrix(solution);
        int numClassrooms = instance.getClassrooms().size();
        int daysToShow = Math.min(maxDays, ProblemInstance.MAX_DAYS);

        System.out.println("\n=== Matriz de Asignación ===");
        System.out.println("Filas: Bloques de 0.5h | Columnas: Salones");
        System.out.printf("Total: %d filas (bloques) x %d columnas (salones)\n", TOTAL_BLOCKS, numClassrooms);
        System.out.printf("Cada día tiene %d bloques (%.1f horas)\n", BLOCKS_PER_DAY, BLOCKS_PER_DAY * 0.5);
        System.out.printf("Mostrando primeros %d días y primeros 10 salones\n", daysToShow);

        for (int day = 0; day < daysToShow; day++) {
            System.out.println("\n--- Día " + (day + 1) + " ---");

            System.out.print("Hora\\Salón  ");
            for (int c = 0; c < Math.min(10, numClassrooms); c++) {
                String id = instance.getClassroomByIndex(c).getId();
                System.out.printf("%6s ", id.length() > 6 ? id.substring(0, 6) : id);
            }
            if (numClassrooms > 10) {
                System.out.print("...");
            }
            System.out.println();

            int startBlock = day * BLOCKS_PER_DAY;
            for (int b = 0; b < BLOCKS_PER_DAY; b++) {
                int blockNum = startBlock + b;
                double hour = 8.0 + (b * 0.5);
                System.out.printf("%5.1fh      ", hour);

                for (int c = 0; c < Math.min(10, numClassrooms); c++) {
                    String subjectId = matrix[blockNum][c];
                    System.out.printf("%6s ", subjectId);
                }
                if (numClassrooms > 10) {
                    System.out.print("...");
                }
                System.out.println();
            }
        }
    }

    /**
     * Imprime un resumen de horarios por día.
     */
    public void printScheduleSummary(IntegerSolution solution) {
        String[][] matrix = decodeToMatrix(solution);
        int numClassrooms = instance.getClassrooms().size();

        System.out.println("\n=== Resumen de Horarios por Día ===");

        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            Set<String> subjectsInDay = new HashSet<>();
            int startBlock = day * BLOCKS_PER_DAY;

            for (int b = 0; b < BLOCKS_PER_DAY; b++) {
                for (int c = 0; c < numClassrooms; c++) {
                    String subjectId = matrix[startBlock + b][c];
                    if (!subjectId.equals(ProblemInstance.EMPTY_SUBJECT_ID)) {
                        subjectsInDay.add(subjectId);
                    }
                }
            }

            if (!subjectsInDay.isEmpty()) {
                System.out.printf("Día %2d: %d exámenes\n", day + 1, subjectsInDay.size());
            }
        }
    }

    /**
     * Imprime un resumen detallado de las asignaciones.
     */
    public void printSummary(IntegerSolution solution) {
        Map<Integer, ClassroomAssignmentProblem.SubjectAssignment> assignments = problem.decodeAssignments(solution);
        int[] minClassroomsPerSubject = problem.getMinClassroomsPerSubject();

        Set<Integer> allUsedClassrooms = new HashSet<>();
        for (ClassroomAssignmentProblem.SubjectAssignment assignment : assignments.values()) {
            allUsedClassrooms.addAll(assignment.classrooms);
        }

        System.out.println("\n=== Resumen de Asignación ===");
        System.out.println(
                "Salones únicos utilizados: " + allUsedClassrooms.size() + " de " + instance.getClassrooms().size());

        int assignedCount = 0;
        for (ClassroomAssignmentProblem.SubjectAssignment a : assignments.values()) {
            if (!a.classrooms.isEmpty())
                assignedCount++;
        }
        System.out.println("Materias asignadas: " + assignedCount + " de " + instance.getSubjects().size());

        int totalAssignments = 0;
        int subjectsWithMultipleClassrooms = 0;
        int maxClassroomsPerSubject = 0;
        int totalExcess = 0;

        for (Map.Entry<Integer, ClassroomAssignmentProblem.SubjectAssignment> entry : assignments.entrySet()) {
            int subjectIdx = entry.getKey();
            ClassroomAssignmentProblem.SubjectAssignment assignment = entry.getValue();
            int numClassrooms = assignment.classrooms.size();

            if (numClassrooms == 0)
                continue;

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

        System.out.println("Total asignaciones materia-salón: " + totalAssignments);
        System.out.println("Mínimo teórico de asignaciones: " + problem.getTotalMinClassrooms());
        System.out.println("Exceso total de salones: " + totalExcess);
        System.out.println("Materias divididas en múltiples salones: " + subjectsWithMultipleClassrooms);
        System.out.println("Máximo salones por materia: " + maxClassroomsPerSubject);

        System.out.println("\n--- Detalle por materia (ordenado por inscriptos) ---");

        List<Map.Entry<Integer, ClassroomAssignmentProblem.SubjectAssignment>> sortedEntries = new ArrayList<>(
                assignments.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                instance.getSubjectByIndex(a.getKey()).getEnrolledStudents()));

        int shown = 0;
        for (Map.Entry<Integer, ClassroomAssignmentProblem.SubjectAssignment> entry : sortedEntries) {
            if (shown >= 25) {
                System.out.println("... y " + (sortedEntries.size() - 25) + " materias más");
                break;
            }

            int subjectIdx = entry.getKey();
            ClassroomAssignmentProblem.SubjectAssignment assignment = entry.getValue();

            if (assignment.classrooms.isEmpty())
                continue;

            Subject s = instance.getSubjectByIndex(subjectIdx);
            int minNeeded = minClassroomsPerSubject[subjectIdx];

            int totalCapacity = 0;
            for (int classroomIdx : assignment.classrooms) {
                totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
            }

            String capStatus = s.getEnrolledStudents() <= totalCapacity ? "OK" : "INSUF";
            String countStatus = assignment.classrooms.size() <= minNeeded ? ""
                    : " EXCESO+" + (assignment.classrooms.size() - minNeeded);

            String daysStr = assignment.days.stream()
                    .sorted()
                    .map(d -> String.valueOf(d + 1))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("-");

            System.out.printf("  %s (%d inscr): %d/%d salón(es), día(s)=%s, cap=%d [%s%s]\n",
                    s.getName().length() > 30 ? s.getName().substring(0, 30) + "..." : s.getName(),
                    s.getEnrolledStudents(),
                    assignment.classrooms.size(),
                    minNeeded,
                    daysStr,
                    totalCapacity,
                    capStatus,
                    countStatus);

            if (assignment.classrooms.size() > 1 || shown < 10) {
                List<Integer> sortedClassrooms = new ArrayList<>(assignment.classrooms);
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

        // Materias no asignadas
        List<Integer> unassigned = new ArrayList<>();
        for (int i = 0; i < instance.getSubjects().size(); i++) {
            ClassroomAssignmentProblem.SubjectAssignment a = assignments.get(i);
            if (a == null || a.classrooms.isEmpty()) {
                unassigned.add(i);
            }
        }

        if (!unassigned.isEmpty()) {
            System.out.println("\n--- Materias NO asignadas (" + unassigned.size() + ") ---");
            int showUnassigned = 0;
            for (int subjectIdx : unassigned) {
                if (showUnassigned >= 10) {
                    System.out.println("... y " + (unassigned.size() - 10) + " más");
                    break;
                }
                Subject s = instance.getSubjectByIndex(subjectIdx);
                System.out.printf("  %s (%d inscr)\n", s.getName(), s.getEnrolledStudents());
                showUnassigned++;
            }
        }
    }

    /**
     * Exporta la solución a archivos CSV.
     */
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
        Map<Integer, ClassroomAssignmentProblem.SubjectAssignment> assignments = problem.decodeAssignments(solution);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("materia_id,materia_nombre,inscriptos,duracion_horas,dias,salones,capacidad_total,estado");

            List<Map.Entry<Integer, ClassroomAssignmentProblem.SubjectAssignment>> sortedEntries = new ArrayList<>(
                    assignments.entrySet());
            sortedEntries.sort((a, b) -> Integer.compare(
                    instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                    instance.getSubjectByIndex(a.getKey()).getEnrolledStudents()));

            for (Map.Entry<Integer, ClassroomAssignmentProblem.SubjectAssignment> entry : sortedEntries) {
                int subjectIdx = entry.getKey();
                ClassroomAssignmentProblem.SubjectAssignment assignment = entry.getValue();

                if (assignment.classrooms.isEmpty())
                    continue;

                Subject s = instance.getSubjectByIndex(subjectIdx);

                int totalCapacity = 0;
                StringBuilder salonesStr = new StringBuilder();
                for (int classroomIdx : assignment.classrooms) {
                    Classroom c = instance.getClassroomByIndex(classroomIdx);
                    totalCapacity += c.getCapacity();
                    if (salonesStr.length() > 0)
                        salonesStr.append(";");
                    salonesStr.append(c.getId());
                }

                String diasStr = assignment.days.stream()
                        .sorted()
                        .map(d -> String.valueOf(d + 1))
                        .reduce((a, b) -> a + ";" + b)
                        .orElse("");

                String status = s.getEnrolledStudents() <= totalCapacity ? "OK" : "INSUFICIENTE";

                writer.printf("%s,\"%s\",%d,%.1f,\"%s\",\"%s\",%d,%s\n",
                        s.getId(),
                        s.getName().replace("\"", "'"),
                        s.getEnrolledStudents(),
                        s.getDurationHours(),
                        diasStr,
                        salonesStr.toString(),
                        totalCapacity,
                        status);
            }
        }
    }

    private void exportMatrixCSV(IntegerSolution solution, String filePath) throws IOException {
        String[][] matrix = decodeToMatrix(solution);
        int numClassrooms = instance.getClassrooms().size();

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            StringBuilder header = new StringBuilder("dia,bloque,hora");
            for (int c = 0; c < numClassrooms; c++) {
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

                    for (int c = 0; c < numClassrooms; c++) {
                        row.append(",").append(matrix[blockNum][c]);
                    }
                    writer.println(row);
                }
            }
        }
    }

    private void exportScheduleCSV(IntegerSolution solution, String filePath) throws IOException {
        String[][] matrix = decodeToMatrix(solution);
        int numClassrooms = instance.getClassrooms().size();

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("dia,materia_id,materia_nombre,hora_inicio,hora_fin,salon,capacidad");

            for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                Map<String, ScheduleEntry> daySchedule = new LinkedHashMap<>();
                int startBlock = day * BLOCKS_PER_DAY;

                for (int c = 0; c < numClassrooms; c++) {
                    String currentSubject = null;
                    int startBlockInDay = -1;

                    for (int b = 0; b <= BLOCKS_PER_DAY; b++) {
                        String subjectId = (b < BLOCKS_PER_DAY) ? matrix[startBlock + b][c]
                                : ProblemInstance.EMPTY_SUBJECT_ID;

                        if (!subjectId.equals(currentSubject)) {
                            if (currentSubject != null && !currentSubject.equals(ProblemInstance.EMPTY_SUBJECT_ID)) {
                                String key = currentSubject + "_" + c + "_" + startBlockInDay;
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
