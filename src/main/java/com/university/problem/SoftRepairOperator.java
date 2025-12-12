package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Operador de reparación suave para el modelo de matriz directa.
 * Realiza ajustes menores para mejorar la factibilidad sin cambios drásticos.
 */
public class SoftRepairOperator {

    private final ProblemInstance instance;
    private final int numClassrooms;
    private final int numSubjects;
    private final int emptySubjectIndex;
    private final int vectorSize;

    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final int[] blocksPerSubject;

    private final Random random = new Random();

    public SoftRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject) {
        this.instance = instance;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();
        this.emptySubjectIndex = instance.getEmptySubjectIndex();
        this.vectorSize = numClassrooms * ClassroomAssignmentProblem.BLOCKS_PER_DAY * ProblemInstance.MAX_DAYS;

        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        // Calcular slots por materia (1 hora = 1 slot)
        this.blocksPerSubject = new int[numSubjects];
        for (int i = 0; i < numSubjects; i++) {
            blocksPerSubject[i] = instance.getSubjects().get(i).getDurationSlots();
        }
    }

    /**
     * Aplica una reparación suave: intenta consolidar bloques fragmentados
     * y eliminar redundancias sin hacer cambios drásticos.
     */
    public void repair(IntegerSolution solution) {
        // Consolidar bloques fragmentados de cada materia
        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            consolidateSubjectBlocks(solution, subjectIdx);
        }

        // Eliminar bloques huérfanos (materia asignada en menos bloques de los
        // necesarios)
        removeOrphanBlocks(solution);
    }

    /**
     * Consolida los bloques de una materia para que estén contiguos.
     */
    private void consolidateSubjectBlocks(IntegerSolution solution, int subjectIdx) {
        int durationBlocks = blocksPerSubject[subjectIdx];

        // Encontrar todos los bloques de esta materia
        Map<String, List<Integer>> blocksByDayClassroom = new HashMap<>();

        for (int pos = 0; pos < vectorSize; pos++) {
            if (solution.variables().get(pos) == subjectIdx) {
                int[] decoded = decodePosition(pos);
                String key = decoded[0] + "_" + decoded[1];
                blocksByDayClassroom.computeIfAbsent(key, k -> new ArrayList<>()).add(decoded[2]);
            }
        }

        // Para cada día-salón, consolidar los bloques
        for (Map.Entry<String, List<Integer>> entry : blocksByDayClassroom.entrySet()) {
            List<Integer> blocks = entry.getValue();
            if (blocks.size() < durationBlocks)
                continue;

            Collections.sort(blocks);

            // Verificar si están contiguos
            boolean contiguous = true;
            for (int i = 1; i < blocks.size(); i++) {
                if (blocks.get(i) - blocks.get(i - 1) != 1) {
                    contiguous = false;
                    break;
                }
            }

            if (!contiguous) {
                // Mover bloques para hacerlos contiguos
                String[] parts = entry.getKey().split("_");
                int day = Integer.parseInt(parts[0]);
                int classroom = Integer.parseInt(parts[1]);

                int startBlock = blocks.get(0);

                // Limpiar todos los bloques actuales
                for (int block : blocks) {
                    int pos = encodePosition(day, classroom, block);
                    solution.variables().set(pos, emptySubjectIndex);
                }

                // Reasignar de forma contigua
                for (int i = 0; i < Math.min(durationBlocks, blocks.size()); i++) {
                    if (startBlock + i < ClassroomAssignmentProblem.BLOCKS_PER_DAY) {
                        int pos = encodePosition(day, classroom, startBlock + i);
                        solution.variables().set(pos, subjectIdx);
                    }
                }
            }
        }
    }

    /**
     * Elimina bloques huérfanos donde una materia tiene menos bloques de los
     * necesarios.
     */
    private void removeOrphanBlocks(IntegerSolution solution) {
        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            int durationBlocks = blocksPerSubject[subjectIdx];

            // Contar bloques por día-salón
            Map<String, Integer> countByDayClassroom = new HashMap<>();

            for (int pos = 0; pos < vectorSize; pos++) {
                if (solution.variables().get(pos) == subjectIdx) {
                    int[] decoded = decodePosition(pos);
                    String key = decoded[0] + "_" + decoded[1];
                    countByDayClassroom.merge(key, 1, Integer::sum);
                }
            }

            // Eliminar asignaciones incompletas
            for (Map.Entry<String, Integer> entry : countByDayClassroom.entrySet()) {
                if (entry.getValue() < durationBlocks) {
                    String[] parts = entry.getKey().split("_");
                    int day = Integer.parseInt(parts[0]);
                    int classroom = Integer.parseInt(parts[1]);

                    // Eliminar estos bloques
                    for (int block = 0; block < ClassroomAssignmentProblem.BLOCKS_PER_DAY; block++) {
                        int pos = encodePosition(day, classroom, block);
                        if (solution.variables().get(pos) == subjectIdx) {
                            solution.variables().set(pos, emptySubjectIndex);
                        }
                    }
                }
            }
        }
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
