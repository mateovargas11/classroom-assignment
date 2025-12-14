package com.university.decoder;

import com.university.domain.Classroom;
import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import com.university.problem.ClassroomAssignmentProblem;
import com.university.problem.ClassroomAssignmentProblem.DecodedAssignment;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Decodificador de soluciones para la representación basada en slots.
 * La decodificación asigna automáticamente el horario más temprano disponible.
 */
public class SolutionDecoder {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;


    public SolutionDecoder(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this.instance = instance;
        this.problem = problem;
    }

    /**
     * Exporta la solución a archivos CSV.
     */
    public void exportToCSV(IntegerSolution solution, String basePath) throws IOException {
        new java.io.File("output").mkdirs();

        exportAssignmentsCSV(solution, basePath + "_asignaciones.csv");
    }

    private void exportAssignmentsCSV(IntegerSolution solution, String filePath) throws IOException {
        Map<Integer, DecodedAssignment> assignments = problem.decode(solution);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(
                    "materia_id,materia_nombre,inscriptos,duracion_horas,dia,hora_inicio,salones,capacidad_total,estado");

            List<Map.Entry<Integer, DecodedAssignment>> sortedEntries = new ArrayList<>(assignments.entrySet());
            sortedEntries.sort((a, b) -> Integer.compare(
                    instance.getSubjectByIndex(b.getKey()).getEnrolledStudents(),
                    instance.getSubjectByIndex(a.getKey()).getEnrolledStudents()));

            for (var entry : sortedEntries) {
                int subjectIdx = entry.getKey();
                DecodedAssignment assignment = entry.getValue();

                if (!assignment.assigned)
                    continue;

                Subject s = instance.getSubjectByIndex(subjectIdx);

                StringBuilder salonesStr = new StringBuilder();
                for (int classroomIdx : assignment.classrooms) {
                    Classroom c = instance.getClassroomByIndex(classroomIdx);
                    if (!salonesStr.isEmpty())
                        salonesStr.append(";");
                    salonesStr.append(c.getId());
                }

                String status = s.getEnrolledStudents() <= assignment.totalCapacity ? "OK" : "INSUFICIENTE";
                double startHour = 8.0 + (assignment.startBlock * 0.5);

                writer.printf("%s,\"%s\",%d,%.1f,%d,%.1f,\"%s\",%d,%s\n",
                        s.getId(),
                        s.getName().replace("\"", "'"),
                        s.getEnrolledStudents(),
                        s.getDurationHours(),
                        assignment.day + 1,
                        startHour,
                        salonesStr,
                        assignment.totalCapacity,
                        status);
            }
        }
    }
}
