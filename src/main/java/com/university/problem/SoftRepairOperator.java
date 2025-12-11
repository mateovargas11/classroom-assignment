package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Operador de reparación que garantiza factibilidad y elimina redundancias:
 * - Cada materia tiene al menos un salón asignado
 * - La capacidad total cubre los inscriptos
 * - Elimina salones redundantes cuando es posible
 */
public class SoftRepairOperator {

    private final ProblemInstance instance;
    private final int maxClassroomsPerSubject;
    private final List<Integer> sortedClassroomsByCapacityDesc;

    public SoftRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject) {
        this.instance = instance;
        this.maxClassroomsPerSubject = maxClassroomsPerSubject;

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
     * Repara la solución asegurando factibilidad y eliminando redundancias.
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

        // 1. Recopilar salones actualmente asignados (válidos y únicos)
        Set<Integer> assignedClassrooms = new LinkedHashSet<>();
        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            int classroomIdx = solution.variables().get(basePos + slot);
            if (classroomIdx >= 0 && classroomIdx < instance.getClassrooms().size()) {
                assignedClassrooms.add(classroomIdx);
            }
        }

        // 2. Calcular capacidad actual
        int totalCapacity = calculateCapacity(assignedClassrooms);

        // 3. Si no hay asignaciones o capacidad insuficiente, reparar agregando salones
        if (assignedClassrooms.isEmpty() || totalCapacity < enrolled) {
            assignedClassrooms = repairCapacity(assignedClassrooms, enrolled, totalCapacity);
        }

        // 4. Eliminar salones redundantes si hay más de uno asignado
        if (assignedClassrooms.size() > 1) {
            assignedClassrooms = removeRedundantClassrooms(assignedClassrooms, enrolled);
        }

        // 5. Escribir los salones reparados de vuelta a la solución
        writeToSolution(solution, basePos, assignedClassrooms);
    }

    /**
     * Elimina salones redundantes.
     * Recorre los salones ordenados por capacidad ascendente (más pequeños primero)
     * y elimina aquellos que son innecesarios para cubrir la capacidad requerida.
     */
    private Set<Integer> removeRedundantClassrooms(Set<Integer> classrooms, int enrolled) {
        if (classrooms.size() <= 1) {
            return classrooms;
        }

        // Ordenar por capacidad ascendente (más pequeños primero para eliminar)
        List<Integer> sortedByCapacityAsc = new ArrayList<>(classrooms);
        sortedByCapacityAsc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(a).getCapacity(),
                instance.getClassroomByIndex(b).getCapacity()));

        Set<Integer> result = new LinkedHashSet<>(classrooms);
        int currentCapacity = calculateCapacity(result);

        // Intentar eliminar salones pequeños mientras la capacidad siga siendo suficiente
        for (int classroomIdx : sortedByCapacityAsc) {
            if (result.size() <= 1) {
                // Siempre mantener al menos un salón
                break;
            }

            int classroomCapacity = instance.getClassroomByIndex(classroomIdx).getCapacity();
            int capacityAfterRemoval = currentCapacity - classroomCapacity;

            // Solo eliminar si la capacidad restante cubre los inscriptos
            if (capacityAfterRemoval >= enrolled) {
                result.remove(classroomIdx);
                currentCapacity = capacityAfterRemoval;
            }
        }

        return result;
    }

    /**
     * Repara la capacidad agregando salones grandes hasta cubrir los inscriptos.
     */
    private Set<Integer> repairCapacity(Set<Integer> currentClassrooms, int enrolled, int currentCapacity) {
        Set<Integer> result = new LinkedHashSet<>(currentClassrooms);
        int capacity = currentCapacity;

        // Agregar salones grandes hasta cubrir la capacidad
        for (int classroomIdx : sortedClassroomsByCapacityDesc) {
            if (capacity >= enrolled) {
                break;
            }
            if (result.size() >= maxClassroomsPerSubject) {
                // No hay más slots, necesitamos reemplazar salones pequeños
                result = replaceSmallestWithLargest(result, enrolled);
                break;
            }
            if (!result.contains(classroomIdx)) {
                result.add(classroomIdx);
                capacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
            }
        }

        return result;
    }

    /**
     * Reemplaza los salones más pequeños con los más grandes hasta cubrir
     * capacidad.
     */
    private Set<Integer> replaceSmallestWithLargest(Set<Integer> currentClassrooms, int enrolled) {
        Set<Integer> result = new LinkedHashSet<>();
        int capacity = 0;

        // Usar los salones más grandes disponibles
        for (int classroomIdx : sortedClassroomsByCapacityDesc) {
            if (capacity >= enrolled || result.size() >= maxClassroomsPerSubject) {
                break;
            }
            result.add(classroomIdx);
            capacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
        }

        return result;
    }

    private int calculateCapacity(Set<Integer> classrooms) {
        int total = 0;
        for (int idx : classrooms) {
            total += instance.getClassroomByIndex(idx).getCapacity();
        }
        return total;
    }

    private void writeToSolution(IntegerSolution solution, int basePos, Set<Integer> classrooms) {
        List<Integer> classroomList = new ArrayList<>(classrooms);
        for (int slot = 0; slot < maxClassroomsPerSubject; slot++) {
            if (slot < classroomList.size()) {
                solution.variables().set(basePos + slot, classroomList.get(slot));
            } else {
                solution.variables().set(basePos + slot, -1);
            }
        }
    }
}
