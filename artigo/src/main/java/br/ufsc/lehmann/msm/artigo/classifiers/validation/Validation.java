/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package br.ufsc.lehmann.msm.artigo.classifiers.validation;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.google.common.collect.ArrayTable;

import br.ufsc.core.IMeasureDistance;
import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.ftsm.base.TrajectorySimilarityCalculator;
import br.ufsc.lehmann.NumberDistance;
import br.ufsc.lehmann.classifier.Binarizer;
import br.ufsc.lehmann.msm.artigo.classifiers.algorithms.IClassifier;
import br.ufsc.lehmann.msm.artigo.classifiers.algorithms.ITrainer;
import br.ufsc.lehmann.msm.artigo.clusterers.ClusteringResult;
import smile.math.Math;
import smile.math.Random;

/**
 * A utility class for validating predictive models on test data.
 * 
 * @author Haifeng
 */
public class Validation {
	
	private Semantic groundTruthSemantic;
	private IMeasureDistance<SemanticTrajectory> measure;
	private Random random;

	public Validation(Semantic groundTruthSemantic, IMeasureDistance<SemanticTrajectory> measure) {
		this(groundTruthSemantic, measure, new Random(System.currentTimeMillis()));
	}

	public Validation(Semantic groundTruthSemantic, IMeasureDistance<SemanticTrajectory> measure, Random random) {
		this.groundTruthSemantic = groundTruthSemantic;
		this.measure = measure;
		this.random = random;
	}

	public ClusteringResult cluster(TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance, SemanticTrajectory[] testData, int numberOfClusters) {
		HierarchicalClusteringDistanceBetweenTrajectoriesExecutor clustering = new HierarchicalClusteringDistanceBetweenTrajectoriesExecutor(numberOfClusters);
		return clustering.cluster(Arrays.asList(testData), measure, groundTruthSemantic);
	}

	public ClusteringResult cluster(SemanticTrajectory[] testData, double[][] distances, int numberOfClusters) {
		HierarchicalClusteringDistanceBetweenTrajectoriesExecutor clustering = new HierarchicalClusteringDistanceBetweenTrajectoriesExecutor(numberOfClusters);
		return clustering.cluster(Arrays.asList(testData), groundTruthSemantic, distances);
	}

	public PrecisionAtRecallResults precisionAtRecallWithResult(TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance, SemanticTrajectory[] testData, int recallLevel) {
		Map<Object, DescriptiveStatistics> stats = new HashMap<>();
		double[] pAtRecall = precisionAtRecall(measureDistance, testData, recallLevel, stats);
		return new PrecisionAtRecallResults(pAtRecall, stats);
	}
	
	public double[] precisionAtRecall(TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance, SemanticTrajectory[] testData, int recallLevel) {
		Map<Object, DescriptiveStatistics> stats = new HashMap<>();
		double[] ret = precisionAtRecall(measureDistance, testData, recallLevel, stats);
		DescriptiveStatistics total = new DescriptiveStatistics();
		for (Map.Entry<Object, DescriptiveStatistics> entry : stats.entrySet()) {
			System.out.printf("%s = %.2f +/- %.2f\n", entry.getKey(), entry.getValue().getMean(), entry.getValue().getStandardDeviation());
			total.addValue(entry.getValue().getMean());
		}
		System.out.printf("Mean intraclass similarity = %.2f\n", total.getMean());
		return ret;
	}

	private double[] precisionAtRecall(TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance,
			SemanticTrajectory[] testData, int recallLevel, Map<Object, DescriptiveStatistics> stats) {
		List<SemanticTrajectory> trajs = Arrays.asList(testData);
		double[] ret = new double[recallLevel + 1];
		ArrayTable<SemanticTrajectory, SemanticTrajectory, Double> allDistances = ArrayTable.create(trajs, trajs);
		Map<Object, LongAdder> occurrences = new HashMap<>();
		Semantic semantic = groundTruthSemantic;
		for (int i = 0; i < testData.length; i++) {
			Object classData = semantic.getData(testData[i], 0);
			if(classData != null) {
				occurrences.computeIfAbsent(classData, (t) -> new LongAdder()).increment();
			}
		}

		ExecutorService executorService = new ThreadPoolExecutor((int) (Runtime.getRuntime().availableProcessors() / 3),
				(int) (Runtime.getRuntime().availableProcessors() / 3), 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>((int) (Math.pow(testData.length, 2) / 2)));
//		ExecutorService executorService = Executors.newSingleThreadExecutor();
		DelayQueue<DelayedDistanceMeasure> queueProcess = new DelayQueue<>();
		for (int i = 0; i < testData.length; i++) {
			for (int j = i; j < testData.length; j++) {
				try {
					Future<Double> future = executorService.submit(new MeasureCallable(measureDistance, testData[i], testData[j]));
					queueProcess.add(new DelayedDistanceMeasure(testData[i], i, testData[j], j, future, i * testData.length + j));
				} catch (RejectedExecutionException e) {
					//wait 5 seconds and re-try submit the task
					j--;
					try {
						Thread.sleep(5 * 1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					continue;
				}
			}
		}
		double totalQueued = queueProcess.size();
		boolean[] processed = new boolean[] {true, false, false, false, false, false, false, false, false, false, false};
		while (!queueProcess.isEmpty()) {
			DelayedDistanceMeasure toProcess = queueProcess.poll();
			if (toProcess == null) {
				Thread.yield();
				continue;
			}
			int done = (int) ((1 - ((queueProcess.size() / totalQueued))) * 10.0);
			if(!processed[done]) {
				System.out.printf("%d%% - ", done * 10);
				processed[done] = true;
			}
			Future<Double> fut = toProcess.distance;
			if (!fut.isDone()) {
				queueProcess.add(new DelayedDistanceMeasure(toProcess.a, toProcess.aIndex, toProcess.b, toProcess.bIndex, toProcess.distance, queueProcess.size()/* ms */));
				Thread.yield();
			} else {
				try {
					double distance = fut.get();
					if (distance == -1) {
						//re-execute task
						Future<Double> future = executorService.submit(new MeasureCallable(measureDistance, toProcess.a, toProcess.b));
						queueProcess.add(new DelayedDistanceMeasure(toProcess.a, toProcess.aIndex, toProcess.b, toProcess.bIndex, future, queueProcess.size() * 100));
					} else {
						allDistances.put(toProcess.a, toProcess.b, distance);
						allDistances.put(toProcess.b, toProcess.a, distance);
					}
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println();
		executorService.shutdown();

		double[][] matrix = new double[testData.length][testData.length];
		List<Object> classes = new ArrayList<>();
		for (int i = 0; i < testData.length; i++) {
			Object data = semantic.getData(testData[i], 0);
			classes.add(data);
			for (int j = 0; j < testData.length; j++) {
				Double d = allDistances.get(testData[i], testData[j]);
				matrix[i][j] = d;
			}
		}

		ret = computeFromClassNames(classes, matrix, recallLevel);

		return ret;
	}
	
	public static double[][] trasposeMatrix(double[][] matrix) {
		int m = matrix.length;
		int n = matrix[0].length;

		double[][] trasposedMatrix = new double[n][m];

		for (int x = 0; x < n; x++) {
			for (int y = 0; y < m; y++) {
				trasposedMatrix[x][y] = matrix[y][x];
			}
		}
		return trasposedMatrix;
	}

	private static class MeasureCallable implements Callable<Double> {

		private TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance;
		private SemanticTrajectory t1;
		private SemanticTrajectory t2;
		boolean error = false;

		MeasureCallable(TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance,
				SemanticTrajectory t1, SemanticTrajectory t2) {
			this.measureDistance = measureDistance;
			this.t1 = t1;
			this.t2 = t2;
		}

		@Override
		public Double call() throws Exception {
			try {
				double distance = 1 - this.measureDistance.getSimilarity(t1, t2);
				return distance;
			} catch (Error e) {
				System.err.println(e.getMessage());
				this.error = true;
				return -1.0;
			}
		}
	}
	
	public static void main(String[] args) {
		int count = 0;
		StringObjeto[] trajsArray = new StringObjeto[] {
				new StringObjeto("A", "A"),
				new StringObjeto("B", "B"),
				new StringObjeto("B", "B"),
				new StringObjeto(null, "C"),
				new StringObjeto(null, "C"),
				new StringObjeto("A", "D"),
		};
		Map<Object, DescriptiveStatistics> stats = new HashMap<>();
		ArrayTable<StringObjeto, StringObjeto, Integer> allDistances = ArrayTable.create(Arrays.asList(trajsArray), Arrays.asList(trajsArray));
		Map<Object, LongAdder> occurrences = new HashMap<>();
		for (int i = 0; i < trajsArray.length; i++) {
			Object classData = trajsArray[i].clazz;
			occurrences.computeIfAbsent(classData, (t) -> new LongAdder()).increment();
		}
		NumberDistance numberDistance = new NumberDistance();
		double[][] matrix = new double[trajsArray.length][trajsArray.length];
		List<Object> classes = new ArrayList<>();
		for (int i = 0; i < trajsArray.length; i++) {
			classes.add(trajsArray[i].clazz);
			for (int j = 0; j < trajsArray.length; j++) {
				allDistances.put(trajsArray[i], trajsArray[j], Math.abs(trajsArray[i].count.compareTo(trajsArray[j].count)));
				matrix[i][j] = Math.abs(trajsArray[i].count.compareTo(trajsArray[j].count));
			}
		}
		int recallLevel = 10;
//		double[] ret = new double[recallLevel + 1];
//		ret[0] = 1.0;
//		double[][] precisionRecall = new double[trajsArray.length][];
//		for (int i = 0; i < trajsArray.length; i++) {
//			List<Map.Entry<StringObjeto, Integer>> rows = allDistances.row(trajsArray[i]).entrySet().stream().sorted(Comparator.comparing(Map.Entry::getValue)).collect(Collectors.toList());
//			Object classData = trajsArray[i].clazz;
//			int groundTruthCounter = occurrences.get(classData).intValue();
//			precisionRecall[i] = new double[groundTruthCounter];
//			int correctClass = 0;
//			for (int j = 0; correctClass < groundTruthCounter && j < trajsArray.length; j++) {
//				Entry<StringObjeto, Integer> entry = rows.get(j);
//				Object otherClassData = entry.getKey().clazz;
//				int h = correctClass;
//				if(Objects.equals(classData, otherClassData)) {
//					correctClass++;
//				}
//				precisionRecall[i][h] = correctClass / (j + 1.0);
//			}
//		}
//		for (int i = 1; i <= recallLevel; i++) {
//			final int finalI = i;
//			ret[i] = Arrays.stream(precisionRecall).mapToDouble(a -> a[Math.max(0, (int) ((a.length / (double) recallLevel) * finalI) - 1)]).sum() / trajsArray.length;
//		}
//		System.out.println("P@R: " + Arrays.toString(ret));
//		System.out.println("AUC: " + AUC.precisionAtRecall(ret));
		
		double[] computeFromClassNames = computeFromClassNames(classes, matrix, recallLevel);
		System.out.println("P@R (lucas): " + Arrays.toString(computeFromClassNames));
		System.out.println("AUC (lucas): " + AUC.precisionAtRecall(computeFromClassNames));
	}

	public static double[] computeFromClassNames(List<Object> classes, double[][] matrix, int recallLevel) {
		// Ranking of trajectories
		List<ObjectIntPair> ranking = new ArrayList<>(classes.size());
		for (int i = 0; i < classes.size(); i++) {
			ranking.add(new ObjectIntPair(classes.get(i), i));
		}
		return compute(ranking, classes, matrix, recallLevel);
	}

	private static double[] compute(List<ObjectIntPair> ranking, List<Object> classes, double[][] matrix, double recallLevel) {
		double[][] fullMatrix = matrix;

		// Complete the upper half of the full matrix
		for (int i = 0; i < fullMatrix.length; i++)
			for (int j = i + 1; j < fullMatrix[0].length; j++)
				fullMatrix[i][j] = fullMatrix[j][i];

		double[] precisionAtRecall = new double[(int) (recallLevel + 1)];
		precisionAtRecall[0] = 1.0;

		int idx = 0;
		Map<Object, double[]> precisionRecallPerClass = new HashMap<>();
		for (Object cls : new LinkedHashSet<>(classes)) {
			if(cls != null) {
				double[] precisionArRecallClass = precisionRecallPerClass.computeIfAbsent(cls, (t) -> new double[(int) (recallLevel + 1)]);
				precisionArRecallClass[0] = 1.0;
			}
		}
		for (Object cls : classes) {
			if(cls == null) {
				idx++;
				continue;
			}
			double[] precisionAtRecallClass = precisionRecallPerClass.get(cls);
			final int idxSort = idx;
			Collections.sort(ranking,
					(o1, o2) -> fullMatrix[idxSort][o1.getValue()] == fullMatrix[idxSort][o2.getValue()] ? 0
							: fullMatrix[idxSort][o1.getValue()] > fullMatrix[idxSort][o2.getValue()] ? 1 : -1);

			long classCount = 0;
			
//			if(cls.equals("Microsoft-Dormitory via Market/C")) {
//				System.out.print(cls + " - ");
//				for (ObjectIntPair objectIntPair : ranking) {
//					System.out.print(objectIntPair.getKey() + ", ");
//				}
//				System.out.println();
//			}
			
			for (Object cls2 : classes)
				if (cls.equals(cls2))
					classCount++;

			for (int recall = 1; recall <= recallLevel; recall++) {
				long meTarget = Math.max(Math.round((classCount) * recall / recallLevel), 1);
				long meCount = meTarget;
				long othersCount = 0;

				for (ObjectIntPair t2 : ranking) {
					if (meTarget == 0)
						break;
					if (cls.equals(t2.getKey()))
						meTarget--;
					else
						othersCount++;
				}
				precisionAtRecall[recall] += (double) meCount / (meCount + othersCount);
				precisionAtRecallClass[recall] += (double) meCount / (meCount + othersCount);
			}
			idx++;
		}

		for (int i = 1; i < precisionAtRecall.length; i++)
			precisionAtRecall[i] /= classes.stream().filter(t -> t != null).count();
		for (Map.Entry<Object, double[]> entry : precisionRecallPerClass.entrySet()) {
			for (int i = 1; i < entry.getValue().length; i++) {
				entry.getValue()[i] /= classes.stream().filter((o) -> entry.getKey().equals(o)).count();
			}
			System.out.printf("P@R(%s): %s\n", entry.getKey(), ArrayUtils.toString(entry.getValue(), "0.0"));
			System.out.printf("MAP (%s) = %.4f\n", entry.getKey(), MAP.precisionAtRecall(entry.getValue()));
		}

		return precisionAtRecall;
	}
	
	public static class ObjectIntPair {

		private Object key;
		private int value;
		public ObjectIntPair(Object key, int value) {
			super();
			this.key = key;
			this.value = value;
		}
		public Object getKey() {
			return key;
		}
		public void setKey(Object key) {
			this.key = key;
		}
		public int getValue() {
			return value;
		}
		public void setValue(int value) {
			this.value = value;
		}
		@Override
		public String toString() {
			return "ObjectIntPair [key=" + key + ", value=" + value + "]";
		}
		
	}
	
	private static class Objeto {

		private String clazz;
		private int count;

		public Objeto(String clazz, int count) {
			this.clazz = clazz;
			this.count = count;
		}
		
	}

	private static class StringObjeto {

		private String clazz;
		private String count;

		public StringObjeto(String clazz, String count) {
			this.clazz = clazz;
			this.count = count;
		}
		
	}
	
	static class DelayedDistanceMeasure implements Delayed {

		private SemanticTrajectory a;
		private SemanticTrajectory b;
		private Future<Double> distance;
		private long delay;
		private int aIndex;
		private int bIndex;

		DelayedDistanceMeasure(SemanticTrajectory a, int aIndex, SemanticTrajectory b, int bIndex, Future<Double> distance, int delay) {
			this.a = a;
			this.aIndex = aIndex;
			this.b = b;
			this.bIndex = bIndex;
			this.distance = distance;
			this.delay = TimeUnit.MILLISECONDS.toNanos(delay);
		}

		@Override
		public int compareTo(Delayed other) {
			if (other == this) // compare zero if same object
				return 0;
			if (other instanceof DelayedDistanceMeasure) {
				DelayedDistanceMeasure x = (DelayedDistanceMeasure) other;
				long diff = delay - x.delay;
				if (diff < 0)
					return -1;
				else if (diff > 0)
					return 1;
				else
					return 1;
			}
			long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
			return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(delay - System.nanoTime(), TimeUnit.NANOSECONDS);
		}

	}

	
    /**
     * Tests a classifier on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param classifier a trained classifier to be tested.
     * @param x the test data set.
     * @param y the test data labels.
     * @return the accuracy on the test dataset
     */
    public<Label> double test(IClassifier<Label> classifier, SemanticTrajectory[] x, Object[] y) {
        int n = x.length;
        Object[] predictions = new Object[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = classifier.classify(x[i]);
        }
        
        return new Accuracy().measure(y, predictions);
    }
    
    /**
     * Tests a classifier on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param classifier a trained classifier to be tested.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measure the performance measures of classification.
     * @return the test results with the same size of order of measures
     */
    public<Label> double test(IClassifier<Label> classifier, SemanticTrajectory[] x, Object[] y, ClassificationMeasure measure) {
        int n = x.length;
        Object[] predictions = new Object[n];
        IntStream.iterate(0, i -> i + 1).limit(n).parallel().forEach((i) -> {
        	predictions[i] = classifier.classify(x[i]);
        });
        
        return measure.measure(y, predictions);
    }
    
    /**
     * Tests a classifier on a validation set.
     * 
     * @param <T> the data type of input objects.
     * @param classifier a trained classifier to be tested.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measures the performance measures of classification.
     * @return the test results with the same size of order of measures
     */
    public<Label> double[] test(IClassifier<Label> classifier, SemanticTrajectory[] x, Label[] y, Binarizer binarizer, ClassificationMeasure[] measures) {
        int n = x.length;
        Boolean[] groundTruth = new Boolean[y.length];
        for (int i = 0; i < y.length; i++) {
        	groundTruth[i] = binarizer.isTrue(y[i]);
        }
        Object[] predictions = new Object[n];
        IntStream.iterate(0, i -> i + 1).limit(n).parallel().forEach((i) -> {
        	Label classified = classifier.classify(x[i]);
        	predictions[i] = binarizer.isTrue(classified);
        });
        
        int m = measures.length;
        double[] results = new double[m];
        for (int i = 0; i < m; i++) {
            double r = measures[i].measure(groundTruth, predictions);
			results[i] = r;
        }
        
        return results;
    }
    
    /**
     * Leave-one-out cross validation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @return the accuracy on test dataset
     */
    public<Label> double loocv(ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y) {
        int m = 0;
        int n = x.length;
        
        LOOCV loocv = new LOOCV(n);
        for (int i = 0; i < n; i++) {
            SemanticTrajectory[] trainx = Math.slice(x, loocv.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            if (classifier.classify(x[loocv.test[i]]).equals(y[loocv.test[i]])) {
                m++;
            }
        }
        
        return (double) m / n;
    }
    
    /**
     * Leave-one-out cross validation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measure the performance measure of classification.
     * @return the test results with the same size of order of measures
     */
    public<Label> double loocv(ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y, ClassificationMeasure measure) {
        int n = x.length;
        Object[] predictions = new Object[n];
        
        LOOCV loocv = new LOOCV(n);
        for (int i = 0; i < n; i++) {
            SemanticTrajectory[] trainx = Math.slice(x, loocv.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            predictions[loocv.test[i]] = classifier.classify(x[loocv.test[i]]);
        }
        
        return measure.measure(y, predictions);
    }
    
    /**
     * Leave-one-out cross validation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measures the performance measures of classification.
     * @return the test results with the same size of order of measures
     */
    public<Label> double[] loocv(ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y, ClassificationMeasure[] measures) {
        int n = x.length;
        Object[] predictions = new Object[n];
        
        LOOCV loocv = new LOOCV(n);
        for (int i = 0; i < n; i++) {
            SemanticTrajectory[] trainx = Math.slice(x, loocv.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            predictions[loocv.test[i]] = classifier.classify(x[loocv.test[i]]);
        }
        
        int m = measures.length;
        double[] results = new double[m];
        for (int i = 0; i < m; i++) {
            results[i] = measures[i].measure(y, predictions);
        }
        
        return results;
    }
    
    /**
     * Cross validation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param k k-fold cross validation.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measure the performance measure of classification.
     * @return the test results with the same size of order of measures
     */
    public<Label> double cv(int k, ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y, ClassificationMeasure measure) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k for k-fold cross validation: " + k);
        }
        
        int n = x.length;
		double result = 0.0;
        
        CrossValidation cv = null;
        SemanticTrajectory[][] trains = null;
		for (int t = 0; t < 10; t++) {
			try {
				cv = new CrossValidation(n, k, this.random);
				trains = new SemanticTrajectory[k][];
				for (int i = 0; i < k; i++) {
					SemanticTrajectory[] trainx = Math.slice(x, cv.train[i]);

					List<Label> labels = new ArrayList<>();
					for (SemanticTrajectory traj : trainx) {
						Label data = (Label) groundTruthSemantic.getData(traj, 0);
						labels.add(data);
					}
					List<Label> uniqueLabels = new ArrayList<>(new LinkedHashSet<>(labels));
					if (uniqueLabels.size() == 1) {
						throw new IllegalStateException("Only one class");
					}
					trains[i] = trainx;
				}
			} catch (IllegalStateException e) {
				System.err.println("Error generating CV");
				cv = null;
			}
		}
		if(cv == null) {
			throw new RuntimeException("Impossibru generate CVs");
		}
		CrossValidation finalCV = cv;
        for (int i = 0; i < k; i++) {
        	final int finalI = i;
            SemanticTrajectory[] trainx = trains[i];
        	
        	IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);
        	
        	Object[] predictions = new Object[cv.test[i].length];
        	Object[] real = new Object[cv.test[i].length];
        	IntStream.iterate(0, l -> l + 1).limit(cv.test[i].length).parallel().forEach((l) -> {
        		predictions[l] = classifier.classify(x[finalCV.test[finalI][l]]);
        		real[l] = y[finalCV.test[finalI][l]];
        	});
        	result += measure.measure(real, predictions);
        }
        
        return result / k;
    }
    
    /**
     * Cross validation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param k k-fold cross validation.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @param binarizer 
     * @param measures the performance measures of classification.
     * @return the test results with the same size of order of measures
     */
    public<Label> double[] cv(int k, ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y, Binarizer binarizer, ClassificationMeasure[] measures) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k for k-fold cross validation: " + k);
        }
        
        int n = x.length;
        int m = measures.length;
        double[] results = new double[m];
        Boolean[] groundTruth = new Boolean[y.length];
        for (int i = 0; i < y.length; i++) {
			groundTruth[i] = binarizer.isTrue(y[i]);
		}

        CrossValidation cv = new CrossValidation(n, k, this.random);
        for (int i = 0; i < k; i++) {
        	final int finalI = i;
            SemanticTrajectory[] trainx = Math.slice(x, cv.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            Object[] predictions = new Object[cv.test[i].length];
            Boolean[] real = new Boolean[cv.test[i].length];
            IntStream.iterate(0, j -> j + 1).limit(cv.test[i].length).parallel().forEach((j) -> {
            	Label classifiedAs = classifier.classify(x[cv.test[finalI][j]]);
				predictions[j] = binarizer.isTrue(classifiedAs);
            	real[j] = groundTruth[cv.test[finalI][j]];
            });
            for (int j = 0; j < m; j++) {
            	results[j] += measures[j].measure(real, predictions);
            }
        }
        for (int i = 0; i < results.length; i++) {
        	results[i] /= k;
		}
        
        return results;
    }
    
    /**
     * Bootstrap accuracy estimation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param k k-round bootstrap estimation.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @return the k-round accuracies
     */
    public<Label> double[] bootstrap(int k, ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k for k-fold bootstrap: " + k);
        }
        
        int n = x.length;
        double[] results = new double[k];
        Accuracy measure = new Accuracy();
        
        Bootstrap bootstrap = new Bootstrap(n, k);
        for (int i = 0; i < k; i++) {
            SemanticTrajectory[] trainx = Math.slice(x, bootstrap.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            int nt = bootstrap.test[i].length;
            Object[] truth = new Object[nt];
            Object[] predictions = new Object[nt];
            for (int j = 0; j < nt; j++) {
                int l = bootstrap.test[i][j];
                truth[j] = y[l];
                predictions[j] = classifier.classify(x[l]);
            }

            results[i] = measure.measure(truth, predictions);
        }
        
        return results;
    }
    
    /**
     * Bootstrap performance estimation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param k k-fold bootstrap estimation.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measure the performance measures of classification.
     * @return k-by-m test result matrix, where k is the number of
     * bootstrap samples and m is the number of performance measures.
     */
    public <Label> double[] bootstrap(int k, ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y, ClassificationMeasure measure) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k for k-fold bootstrap: " + k);
        }
        
        int n = x.length;
        double[] results = new double[k];
        
        Bootstrap bootstrap = new Bootstrap(n, k);
        for (int i = 0; i < k; i++) {
            SemanticTrajectory[] trainx = Math.slice(x, bootstrap.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            int nt = bootstrap.test[i].length;
            Object[] truth = new Object[nt];
            Object[] predictions = new Object[nt];
            for (int j = 0; j < nt; j++) {
                int l = bootstrap.test[i][j];
                truth[j] = y[l];
                predictions[j] = classifier.classify(x[l]);
            }

            results[i] = measure.measure(truth, predictions);
        }
        
        return results;
    }
    
    /**
     * Bootstrap performance estimation of a classification model.
     * 
     * @param <T> the data type of input objects.
     * @param k k-fold bootstrap estimation.
     * @param trainer a classifier trainer that is properly parameterized.
     * @param x the test data set.
     * @param y the test data labels.
     * @param measures the performance measures of classification.
     * @return k-by-m test result matrix, where k is the number of
     * bootstrap samples and m is the number of performance measures.
     */
    public <Label> double[][] bootstrap(int k, ITrainer<Label> trainer, SemanticTrajectory[] x, Object[] y, ClassificationMeasure[] measures) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k for k-fold bootstrap: " + k);
        }
        
        int n = x.length;
        int m = measures.length;
        double[][] results = new double[k][m];
        
        Bootstrap bootstrap = new Bootstrap(n, k);
        for (int i = 0; i < k; i++) {
            SemanticTrajectory[] trainx = Math.slice(x, bootstrap.train[i]);
            
            IClassifier<Label> classifier = trainer.train(trainx, groundTruthSemantic, this.measure);

            int nt = bootstrap.test[i].length;
            Object[] truth = new Object[nt];
            Object[] predictions = new Object[nt];
            for (int j = 0; j < nt; j++) {
                int l = bootstrap.test[i][j];
                truth[j] = y[l];
                predictions[j] = classifier.classify(x[l]);
            }

            for (int j = 0; j < m; j++) {
                results[i][j] = measures[j].measure(truth, predictions);
            }
        }
        
        return results;
    }

	public SemanticTrajectory[][] topK(TrajectorySimilarityCalculator<SemanticTrajectory> measureDistance,
			SemanticTrajectory[] trajectories, int k) {
		SemanticTrajectory[] trajsArray = trajectories;
		
		ExecutorService executorService = new ThreadPoolExecutor((int) (Runtime.getRuntime().availableProcessors() / 1.5),
				(int) (Runtime.getRuntime().availableProcessors() / 1.5), 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		DelayQueue<DelayedDistanceMeasure> queueProcess = new DelayQueue<>();
		for (int i = 0; i < trajsArray.length; i++) {
			int finalI = i;
			for (int j = i; j < trajsArray.length; j++) {
				int finalJ = j;
				Future<Double> future = executorService.submit(new Callable<Double>() {
					
					@Override
					public Double call() throws Exception {
						double distance = 1 - measureDistance.getSimilarity(trajsArray[finalI], trajsArray[finalJ]);
						return distance;
					}
				});
				queueProcess.add(new DelayedDistanceMeasure(trajsArray[i], i, trajsArray[j], j, future, 50));
			}
		}
		double[][] allDistances = new double[trajectories.length][trajectories.length];
		while (!queueProcess.isEmpty()) {
			DelayedDistanceMeasure toProcess = queueProcess.poll();
			if (toProcess == null) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			Future<Double> fut = toProcess.distance;
			if (!fut.isDone()) {
				queueProcess.add(toProcess);
			} else {
				try {
					double distance = fut.get();
					allDistances[toProcess.aIndex][toProcess.bIndex] = distance;
					allDistances[toProcess.bIndex][toProcess.aIndex] = distance;
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
		}
		executorService.shutdown();
		SemanticTrajectory[][] ret = new SemanticTrajectory[trajectories.length][k];
		for (int i = 0; i < trajsArray.length; i++) {
			double[] distances = allDistances[i];
			DoubleStream stream = Arrays.stream(distances);
			List<Double> biggerValues = stream.boxed().sorted(Comparator.reverseOrder()).limit(k).collect(Collectors.toList());
			for (int j = 0, l = 0; j < distances.length && l < k; j++) {
				if(biggerValues.contains(distances[j])) {
					ret[i][l++] = trajectories[j];
				}
			}
		}
		return ret;
	}
	
	public static class PrecisionAtRecallResults {

		private double[] pAtRecall;
		private Map<Object, DescriptiveStatistics> stats;

		public PrecisionAtRecallResults(double[] pAtRecall, Map<Object, DescriptiveStatistics> stats) {
			this.pAtRecall = pAtRecall;
			this.stats = stats;
		}

		public double[] getpAtRecall() {
			return pAtRecall;
		}

		public Map<Object, DescriptiveStatistics> getStats() {
			return stats;
		}
	}
}
