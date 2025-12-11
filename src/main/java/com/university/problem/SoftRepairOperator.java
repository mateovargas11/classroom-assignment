package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Operador de reparación "suave" que solo corrige restricciones críticas:
 * - Materias sin asignar
 * - Capacidad insuficiente
 * 
 * NO optimiza el número de salones asignados, permitiendo que NSGA-II explore
 * libremente diferentes configuraciones.
 */
public class SoftRepairOperator {

    private final ProblemInstance instance;
    private final int maxClassroomsPerSubject;
    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final Random random;

    public SoftRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject) {
        this.instance = instance;
        this.maxClassroomsPerSubject = maxClassroomsPerSubject;
        this.random = new Random();

        // Ordenar salones por capacidad descendente
        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < instance.getClassrooms().size(); i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));
    }

    /**
     * Repara la solución asegurando que:
     * 1. Cada materia tenga al menos un salón asignado
     * 2. La capacidad total sea suficiente para los inscriptos
     * 
     * NO elimina salones "extras" - permite que NSGA-II explore.
     */
    public void repair(IntegerSolution solution) {
        for (int subjectIdx = 0; subjectIdx < instance.getSubjects().size(); subjectIdx++) {
            repairSubjectAssignment(solution, subjectIdx);
        }
    }

    private void repairSubjectAssignment(IntegerSolution solution, int subjectIdx) {
        Subject subject = instance.getSubjectByIndex(subjectIdx);
        int enrolled = subject.getEnrolledStudents();
        int basePos = subjectIdx * maxClassroomsPerSubject;

        // Obtener salones actualmente asignados y calcular capacidad
        Set<Integer> assignedClassrooms = new HashSet<>();
        int totalCapacity = 0;

        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            int classroomIdx = solution.variables().get(basePos + slot);
            if (classroomIdx >= 0 && classroomIdx < instance.getClassrooms().size()) {
                if (assignedClassrooms.add(classroomIdx)) {
                    totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }
            }
        }

        // Si no hay ningún salón asignado, asignar uno aleatorio
        if (assignedClassrooms.isEmpty()) {
            int randomClassroom = random.nextInt(instance.getClassrooms().size());
            solution.variables().set(basePos, randomClassroom);
            assignedClassrooms.add(randomClassroom);
            totalCapacity = instance.getClassroomByIndex(randomClassroom).getCapacity();
        }

        // Si la capacidad es insuficiente, agregar más salones
        if (totalCapacity < enrolled) {
            int slot = findNextEmptySlot(solution, basePos);

            for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                if (totalCapacity >= enrolled || slot >= maxClassroomsPerSubject) {
                    break;
                }

                if (!assignedClassrooms.contains(classroomIdx)) {
                    solution.variables().set(basePos + slot, classroomIdx);
                    assignedClassrooms.add(classroomIdx);
                    totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                    slot++;
                }
            }
        }

        // Eliminar duplicados (mismo salón en múltiples slots)
        removeDuplicatesFromSlots(solution, basePos, assignedClassrooms);
    }

    private int findNextEmptySlot(IntegerSolution solution, int basePos) {
        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            if (solution.variables().get(basePos + slot) < 0) {
                return slot;
            }
        }
        return maxClassroomsPerSubject; // No hay slots vacíos
    }

    private void removeDuplicatesFromSlots(IntegerSolution solution, int basePos, Set<Integer> uniqueClassrooms) {
        List<Integer> classroomList = new ArrayList<>(uniqueClassrooms);
        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            if (slot < classroomList.size()) {
                solution.variables().set(basePos + slot, classroomList.get(slot));
            } else {
                solution.variables().set(basePos + slot, -1);
            }
        }
    }
}
