package br.ufsc.lehmann.classifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Test;

import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.lehmann.NElementProblem;
import br.ufsc.lehmann.msm.artigo.IMeasureDistance;
import br.ufsc.lehmann.msm.artigo.Problem;
import br.ufsc.lehmann.msm.artigo.classifiers.KNNTrainer;
import br.ufsc.lehmann.msm.artigo.validation.Accuracy;
import br.ufsc.lehmann.msm.artigo.validation.ClassificationMeasure;
import br.ufsc.lehmann.msm.artigo.validation.FDR;
import br.ufsc.lehmann.msm.artigo.validation.FMeasure;
import br.ufsc.lehmann.msm.artigo.validation.Fallout;
import br.ufsc.lehmann.msm.artigo.validation.Precision;
import br.ufsc.lehmann.msm.artigo.validation.Recall;
import br.ufsc.lehmann.msm.artigo.validation.Specificity;
import br.ufsc.lehmann.msm.artigo.validation.Validation;

public abstract class AbstractClassifierTest {
	
	abstract IMeasureDistance<SemanticTrajectory> measurer(Problem problem);
	
	@Test
	public void selfClassification() throws Exception {
		NElementProblem problem = new NElementProblem(1, 1);
		HashMap<Object, DescriptiveStatistics> stats = new HashMap<>();
		stats.put("0", new DescriptiveStatistics());
		TestClassificationExecutor executor = new TestClassificationExecutor(stats);

		IMeasureDistance<SemanticTrajectory> classifier = measurer(problem);
		try {
			executor.classifyProblem(problem, classifier);
		} catch (Exception e) {
			e.printStackTrace();
		}
		assertEquals(1.0, stats.get("0").getMean(), 0.000001);
		assertEquals(1, stats.get("0").getValues().length);
	}

	@Test
	public void simpleClassification() throws Exception {
		HashMap<Object, DescriptiveStatistics> stats = new HashMap<>();
		for (int i = 0; i < 5; i++) {
			stats.put(String.valueOf(i), new DescriptiveStatistics());
		}
		TestClassificationExecutor executor = new TestClassificationExecutor(stats);
		NElementProblem problem = new NElementProblem(15, 5);
		IMeasureDistance<SemanticTrajectory> classifier = measurer(problem);
		try {
			executor.classifyProblem(problem, classifier);
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (int i = 0; i < 5; i++) {
			assertEquals(1.0, stats.get(String.valueOf(i)).getMean(), 0.000001);
			assertEquals(2, stats.get(String.valueOf(i)).getValues().length);
		}
	}
	
	@Test
	public void crossValidation_10_accuracy() throws Exception {
		HashMap<Object, DescriptiveStatistics> stats = new HashMap<>();
		for (int i = 0; i < 15; i++) {
			stats.put(String.valueOf(i), new DescriptiveStatistics());
		}
//		NewYorkBusProblem problem = new NewYorkBusProblem();
		NElementProblem problem = new NElementProblem(150, 4);
		SemanticTrajectory[] data = problem.data().toArray(new SemanticTrajectory[problem.data().size()]);
		Object[] dataLabel = new Object[data.length];
		for (int i = 0; i < data.length; i++) {
			dataLabel[i] = problem.discriminator().getData(data[i], 0);
		}
		IMeasureDistance<SemanticTrajectory> classifier = measurer(problem);
		Validation validation = new Validation(problem, classifier);
		double accuracy = validation.<SemanticTrajectory> cv(10, new KNNTrainer<>(), data, dataLabel, new Accuracy());
		System.out.println(accuracy);
		assertTrue("Accuracy = " + accuracy, accuracy > .8);
	}
	
	@Test
	public void crossValidation_10_precision_recall() throws Exception {
		HashMap<Object, DescriptiveStatistics> stats = new HashMap<>();
		for (int i = 0; i < 15; i++) {
			stats.put(String.valueOf(i), new DescriptiveStatistics());
		}
//		NewYorkBusProblem problem = new NewYorkBusProblem();
		NElementProblem problem = new NElementProblem(150, 4);
		SemanticTrajectory[] data = problem.data().toArray(new SemanticTrajectory[problem.data().size()]);
		Object[] dataLabel = new Object[data.length];
		for (int i = 0; i < data.length; i++) {
			dataLabel[i] = problem.discriminator().getData(data[i], 0);
		}
		IMeasureDistance<SemanticTrajectory> classifier = measurer(problem);
		Validation validation = new Validation(problem, classifier);
		ClassificationMeasure[] measures = new ClassificationMeasure[] {//
				new Precision(),//
				new Recall(),//
				new FMeasure(),//
				new Specificity(),//
				new Fallout(),//
				new FDR()
				};
		double[] precision = validation.<SemanticTrajectory> cv(10, new KNNTrainer<>(), data, dataLabel, new Binarizer(dataLabel[0]), measures);
		System.out.println(Arrays.toString(precision));
		assertTrue("Precision = " + precision[0], precision[0] > .8);
	}
}