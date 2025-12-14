package com.university.algorithm;

import com.university.problem.SolutionRepairOperator;
import com.university.telemetry.EvolutionTracker;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;

import java.util.List;

/**
 * NSGA-II con telemetría integrada para rastrear la evolución del algoritmo.
 * Usa SolutionRepairOperator para reparar soluciones después de operadores
 * genéticos.
 */
public class NSGAII_WithTelemetry extends NSGAII<IntegerSolution> {

    private final EvolutionTracker tracker;
    private final SolutionRepairOperator repairOperator;
    private final int recordEveryNGenerations;
    private int generationCount;

    public NSGAII_WithTelemetry(
            Problem<IntegerSolution> problem,
            int maxEvaluations,
            int populationSize,
            int matingPoolSize,
            int offspringPopulationSize,
            CrossoverOperator<IntegerSolution> crossoverOperator,
            MutationOperator<IntegerSolution> mutationOperator,
            SelectionOperator<List<IntegerSolution>, IntegerSolution> selectionOperator,
            SolutionListEvaluator<IntegerSolution> evaluator,
            EvolutionTracker tracker,
            SolutionRepairOperator repairOperator,
            int recordEveryNGenerations) {

        super(problem, maxEvaluations, populationSize, matingPoolSize, offspringPopulationSize,
                crossoverOperator, mutationOperator, selectionOperator, evaluator);

        this.tracker = tracker;
        this.repairOperator = repairOperator;
        this.recordEveryNGenerations = recordEveryNGenerations;
        this.generationCount = 0;
    }

    @Override
    protected void updateProgress() {
        super.updateProgress();
        generationCount++;

        // Registrar cada N generaciones para no sobrecargar
        if (generationCount % recordEveryNGenerations == 0) {
            tracker.update(population);
        }
    }

    @Override
    protected List<IntegerSolution> reproduction(List<IntegerSolution> matingPool) {
        // Crear descendencia
        List<IntegerSolution> offspring = super.reproduction(matingPool);

        // Repararcion
        for (IntegerSolution sol : offspring) {
            repairOperator.repair(sol);
        }

        return offspring;
    }

    @Override
    protected List<IntegerSolution> evaluatePopulation(List<IntegerSolution> population) {
        // Repararcion pre evaluación
        for (IntegerSolution sol : population) {
            repairOperator.repair(sol);
        }
        List<IntegerSolution> result = super.evaluatePopulation(population);

        // Registrar la generación inicial (0) después de la primera evaluación
        if (generationCount == 0) {
            tracker.update(population);
        }

        return result;
    }

    public EvolutionTracker getTracker() {
        return tracker;
    }

    public int getGenerationCount() {
        return generationCount;
    }
}
