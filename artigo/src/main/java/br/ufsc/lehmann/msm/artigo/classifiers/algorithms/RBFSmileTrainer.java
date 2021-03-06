package br.ufsc.lehmann.msm.artigo.classifiers.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import br.ufsc.core.IMeasureDistance;
import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;
import smile.classification.RBFNetwork;
import smile.math.distance.Metric;

public class RBFSmileTrainer<Label> implements ITrainer<Label> {

	@Override
	public IClassifier<Label> train(SemanticTrajectory[] trainx, Semantic<Label, ?> discriminator, IMeasureDistance<SemanticTrajectory> measure) {
		ArrayList<SemanticTrajectory> train = new ArrayList<>(Arrays.asList(trainx));
		List<Label> labels = new ArrayList<>();
		for (SemanticTrajectory traj : train) {
			Label data = discriminator.getData(traj, 0);
			labels.add(data);
		}
		int[] y = new int[labels.size()];
		List<Label> uniqueLabels = new ArrayList<>(new LinkedHashSet<>(labels));
		for (int i = 0; i < y.length; i++) {
			y[i] = uniqueLabels.indexOf(labels.get(i));
		}
		
		RBFNetwork.Trainer<SemanticTrajectory> knn = new RBFNetwork.Trainer<>(new SmileDistanceWrapper(measure));
		return new RBFSmileClassifier<Label>(knn.train(trainx, y), uniqueLabels);
	}
	
	class SmileDistanceWrapper implements Metric<SemanticTrajectory> {

		private IMeasureDistance<SemanticTrajectory> measure;

		public SmileDistanceWrapper(IMeasureDistance<SemanticTrajectory> measure) {
			this.measure = measure;
		}

		@Override
		public double d(SemanticTrajectory x, SemanticTrajectory y) {
			return measure.distance(x, y);
		}
		
	}
}
