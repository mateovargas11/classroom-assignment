package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Operador de reparación para el modelo de matriz directa.
 * Asegura que cada materia tenga bloques consecutivos asignados y capacidad
 * suficiente.
 */
public class SolutionRepairOperator {

    private final ProblemInstance instance;
    private final int numClassrooms;
    private final int numSubjects;
    private final int emptySubjectIndex;
    private final int vectorSize;

    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final List<Integer> sortedClassroomsByCapacityAsc;
    private final int[] blocksPerSubject;

    private final Random random = new Random();

    public SolutionRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject) {
        this.instance = instance;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();
        this.emptySubjectIndex = instance.getEmptySubjectIndex();
        this.vectorSize = numClassrooms * ClassroomAssignmentProblem.BLOCKS_PER_DAY * ProblemInstance.MAX_DAYS;

        // Ordenar salones por capacidad
        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.sortedClassroomsByCapacityAsc = new ArrayList<>(sortedClassroomsByCapacityDesc);
        Collections.reverse(sortedClassroomsByCapacityAsc);

        // Calcular slots por materia (1 hora = 1 slot)
        this.blocksPerSubject = new int[numSubjects];
        for (int i = 0; i < numSubjects; i++) {
            blocksPerSubject[i] = instance.getSubjects().get(i).getDurationSlots();
        }
    }

    /**
     * Repara una solución asegurando que todas las materias estén correctamente
     * asignadas.
     */
    public void repair(IntegerSolution solution) {
        // Primero, limpiar asignaciones inconsistentes
        cleanInconsistentAssignments(solution);

        // Luego, asegurar que todas las materias estén asignadas
        ensureAllSubjectsAssigned(solution);
    }

    /**
     * Limpia asignaciones donde una materia aparece en bloques no contiguos.
     */
    private void cleanInconsistentAssignments(IntegerSolution solution) {
        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            List<int[]> blocks = findSubjectBlocks(solution, subjectIdx);

            if (blocks.isEmpty())
                continue;

            // Agrupar por día y salón
            Map<String, List<Integer>> groupedBlocks = new HashMap<>();
            for (int[] block : blocks) {
                String key = block[0] + "_" + block[1]; // día_salón
                groupedBlocks.computeIfAbsent(key, k -> new ArrayList<>()).add(block[2]);
            }

            // Para cada grupo, mantener solo bloques contiguos
            int durationBlocks = blocksPerSubject[subjectIdx];

            for (Map.Entry<String, List<Integer>> entry : groupedBlocks.entrySet()) {
                List<Integer> blockList = entry.getValue();
                Collections.sort(blockList);

                // Si hay más bloques de los necesarios o no son contiguos, limpiar
                if (blockList.size() > durationBlocks || !areContiguous(blockList)) {
                    String[] parts = entry.getKey().split("_");
                    int day = Integer.parseInt(parts[0]);
                    int classroom = Integer.parseInt(parts[1]);

                    // Limpiar todos estos bloques
                    for (int block : blockList) {
                        int pos = encodePosition(day, classroom, block);
                        solution.variables().set(pos, emptySubjectIndex);
                    }
                }
            }
        }
    }

    private boolean areContiguous(List<Integer> blocks) {
        if (blocks.size() <= 1)
            return true;
        for (int i = 1; i < blocks.size(); i++) {
            if (blocks.get(i) - blocks.get(i - 1) != 1)
                return false;
        }
        return true;
    }

    /**
     * Asegura que todas las materias estén asignadas con la capacidad correcta.
     */
    private void ensureAllSubjectsAssigned(IntegerSolution solution) {
        // Calcular disponibilidad actual
        int[][] nextFreeBlock = new int[numClassrooms][ProblemInstance.MAX_DAYS];

        // Marcar bloques ocupados
        for (int pos = 0; pos < vectorSize; pos++) {
            int subjectIdx = solution.variables().get(pos);
            if (subjectIdx >= 0 && subjectIdx < numSubjects) {
                int[] decoded = decodePosition(pos);
                int day = decoded[0];
                int classroom = decoded[1];
                int block = decoded[2];
                nextFreeBlock[classroom][day] = Math.max(nextFreeBlock[classroom][day], block + 1);
            }
        }

        // Verificar cada materia
        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            if (!isSubjectProperlyAssigned(solution, subjectIdx)) {
                // Eliminar asignación existente
                removeSubject(solution, subjectIdx);

                // Reasignar
                assignSubject(solution, subjectIdx, nextFreeBlock);
            }
        }
    }

    private boolean isSubjectProperlyAssigned(IntegerSolution solution, int subjectIdx) {
        Subject subject = instance.getSubjectByIndex(subjectIdx);
        int durationBlocks = blocksPerSubject[subjectIdx];
        int enrolled = subject.getEnrolledStudents();

        // Encontrar todos los bloques asignados a esta materia
        Map<String, List<Integer>> assignmentsByDayClassroom = new HashMap<>();

        for (int pos = 0; pos < vectorSize; pos++) {
            if (solution.variables().get(pos) == subjectIdx) {
                int[] decoded = decodePosition(pos);
                String key = decoded[0] + "_" + decoded[1]; // día_salón
                assignmentsByDayClassroom.computeIfAbsent(key, k -> new ArrayList<>()).add(decoded[2]);
            }
        }

        if (assignmentsByDayClassroom.isEmpty())
            return false;

        // Verificar que hay asignaciones válidas
        int totalCapacity = 0;
        Set<Integer> classroomsUsed = new HashSet<>();

        for (Map.Entry<String, List<Integer>> entry : assignmentsByDayClassroom.entrySet()) {
            List<Integer> blocks = entry.getValue();
            Collections.sort(blocks);

            // Verificar bloques contiguos y cantidad correcta
            if (blocks.size() >= durationBlocks && areContiguous(blocks.subList(0, durationBlocks))) {
                String[] parts = entry.getKey().split("_");
                int classroom = Integer.parseInt(parts[1]);
                classroomsUsed.add(classroom);
                totalCapacity += instance.getClassroomByIndex(classroom).getCapacity();
            }
        }

        return totalCapacity >= enrolled;
    }

    private void removeSubject(IntegerSolution solution, int subjectIdx) {
        for (int pos = 0; pos < vectorSize; pos++) {
            if (solution.variables().get(pos) == subjectIdx) {
                solution.variables().set(pos, emptySubjectIndex);
            }
        }
    }

    private void assignSubject(IntegerSolution solution, int subjectIdx, int[][] nextFreeBlock) {
        Subject subject = instance.getSubjectByIndex(subjectIdx);
        int durationSlots = blocksPerSubject[subjectIdx];
        int enrolled = subject.getEnrolledStudents();

        List<Integer> classroomsNeeded = findClassroomsForCapacity(enrolled);

        // Estrategia 1: Buscar un día donde todos los salones tengan espacio
        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            int maxStartBlock = 0;
            boolean allFit = true;

            for (int classroomIdx : classroomsNeeded) {
                int startBlock = findNextFreeBlock(solution, classroomIdx, day);
                if (startBlock + durationSlots > ClassroomAssignmentProblem.BLOCKS_PER_DAY) {
                    allFit = false;
                    break;
                }
                maxStartBlock = Math.max(maxStartBlock, startBlock);
            }

            if (allFit && maxStartBlock + durationSlots <= ClassroomAssignmentProblem.BLOCKS_PER_DAY) {
                // Asignar
                for (int classroomIdx : classroomsNeeded) {
                    for (int b = 0; b < durationSlots; b++) {
                        int pos = encodePosition(day, classroomIdx, maxStartBlock + b);
                        solution.variables().set(pos, subjectIdx);
                    }
                }
                return;
            }
        }

        // Estrategia 2: Asignar salones en días diferentes si es necesario
        int capacityAssigned = 0;

        // Primero intentar con los salones preferidos
        for (int classroomIdx : classroomsNeeded) {
            if (capacityAssigned >= enrolled)
                break;

            for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                int startBlock = findNextFreeBlock(solution, classroomIdx, day);
                if (startBlock + durationSlots <= ClassroomAssignmentProblem.BLOCKS_PER_DAY) {
                    for (int b = 0; b < durationSlots; b++) {
                        int pos = encodePosition(day, classroomIdx, startBlock + b);
                        solution.variables().set(pos, subjectIdx);
                    }
                    capacityAssigned += instance.getClassroomByIndex(classroomIdx).getCapacity();
                    break;
                }
            }
        }

        // Si aún no hay suficiente capacidad, buscar en TODOS los salones
        if (capacityAssigned < enrolled) {
            for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                if (capacityAssigned >= enrolled)
                    break;
                if (classroomsNeeded.contains(classroomIdx))
                    continue;

                for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                    int startBlock = findNextFreeBlock(solution, classroomIdx, day);
                    if (startBlock + durationSlots <= ClassroomAssignmentProblem.BLOCKS_PER_DAY) {
                        for (int b = 0; b < durationSlots; b++) {
                            int pos = encodePosition(day, classroomIdx, startBlock + b);
                            solution.variables().set(pos, subjectIdx);
                        }
                        capacityAssigned += instance.getClassroomByIndex(classroomIdx).getCapacity();
                        break;
                    }
                }
            }
        }
    }

    private int findNextFreeBlock(IntegerSolution solution, int classroom, int day) {
        for (int block = 0; block < ClassroomAssignmentProblem.BLOCKS_PER_DAY; block++) {
            int pos = encodePosition(day, classroom, block);
            int subjectIdx = solution.variables().get(pos);
            if (subjectIdx == emptySubjectIndex || subjectIdx < 0 || subjectIdx >= numSubjects) {
                return block;
            }
        }
        return ClassroomAssignmentProblem.BLOCKS_PER_DAY;
    }

    private List<Integer> findClassroomsForCapacity(int enrolled) {
        List<Integer> result = new ArrayList<>();
        int capacityAccumulated = 0;

        // Primero intentar con un solo salón (best-fit)
        // Mezclar aleatoriamente salones con la misma capacidad para distribuir mejor
        List<Integer> candidates = new ArrayList<>();
        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                candidates.add(classroomIdx);
            }
        }

        // Si hay candidatos, elegir uno aleatoriamente entre los que tienen capacidad
        // suficiente
        if (!candidates.isEmpty()) {
            // Agrupar por capacidad y elegir aleatoriamente dentro del grupo
            Map<Integer, List<Integer>> byCapacity = new HashMap<>();
            for (int idx : candidates) {
                int cap = instance.getClassroomByIndex(idx).getCapacity();
                byCapacity.computeIfAbsent(cap, k -> new ArrayList<>()).add(idx);
            }

            // Elegir el grupo con la menor capacidad suficiente
            int minCapacity = byCapacity.keySet().stream()
                    .filter(cap -> cap >= enrolled)
                    .min(Integer::compare)
                    .orElse(Integer.MAX_VALUE);

            if (minCapacity != Integer.MAX_VALUE) {
                List<Integer> bestFitGroup = byCapacity.get(minCapacity);
                result.add(bestFitGroup.get(random.nextInt(bestFitGroup.size())));
                return result;
            }
        }

        // Si no cabe en uno solo, usar los más grandes
        // Agrupar por capacidad y mezclar aleatoriamente dentro de cada grupo
        Map<Integer, List<Integer>> byCapacity = new HashMap<>();
        for (int classroomIdx : sortedClassroomsByCapacityDesc) {
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            byCapacity.computeIfAbsent(cap, k -> new ArrayList<>()).add(classroomIdx);
        }

        // Ordenar grupos por capacidad (descendente) y mezclar dentro de cada grupo
        List<Integer> sortedCapacities = new ArrayList<>(byCapacity.keySet());
        sortedCapacities.sort(Collections.reverseOrder());

        for (int capacity : sortedCapacities) {
            List<Integer> group = new ArrayList<>(byCapacity.get(capacity));
            Collections.shuffle(group, random); // Mezclar aleatoriamente dentro del grupo

            for (int classroomIdx : group) {
                if (capacityAccumulated >= enrolled)
                    break;
                result.add(classroomIdx);
                capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
            }

            if (capacityAccumulated >= enrolled)
                break;
        }

        return result;
    }

    private List<int[]> findSubjectBlocks(IntegerSolution solution, int subjectIdx) {
        List<int[]> blocks = new ArrayList<>();
        for (int pos = 0; pos < vectorSize; pos++) {
            if (solution.variables().get(pos) == subjectIdx) {
                blocks.add(decodePosition(pos));
            }
        }
        return blocks;
    }

    private int[] decodePosition(int position) {
        int blocksPerDay = numClassrooms * ClassroomAssignmentProblem.BLOCKS_PER_DAY;
        int day = position / blocksPerDay;
        int remainder = position % blocksPerDay;
        int classroom = remainder / ClassroomAssignmentProblem.BLOCKS_PER_DAY;
        int block = remainder % ClassroomAssignmentProblem.BLOCKS_PER_DAY;
        return new int[] { day, classroom, block };
    }

    private int encodePosition(int day, int classroom, int block) {
        return day * numClassrooms * ClassroomAssignmentProblem.BLOCKS_PER_DAY
                + classroom * ClassroomAssignmentProblem.BLOCKS_PER_DAY + block;
    }
}
