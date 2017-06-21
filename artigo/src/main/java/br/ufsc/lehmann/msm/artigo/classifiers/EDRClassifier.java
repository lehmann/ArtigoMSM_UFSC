package br.ufsc.lehmann.msm.artigo.classifiers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.lehmann.method.EDR;
import br.ufsc.lehmann.method.EDR.EDRSemanticParameter;
import br.ufsc.lehmann.msm.artigo.IMeasureDistance;
import br.ufsc.lehmann.msm.artigo.classifiers.NearestNeighbour.DataEntry;
import br.ufsc.lehmann.msm.artigo.problems.BikeDataReader;
import br.ufsc.lehmann.msm.artigo.problems.Climate;

public class EDRClassifier implements IMeasureDistance<SemanticTrajectory> {

	private EDR edr;
	
	public EDRClassifier(EDRSemanticParameter... params) {
		edr = new EDR(params);
	}
	
	@Override
	public double distance(SemanticTrajectory t1, SemanticTrajectory t2) {
		return edr.distance(t1, t2);
	}

	@Override
	public String name() {
		return "EDR";
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		List<SemanticTrajectory> trajectories = new BikeDataReader().read();
		ArrayList<DataEntry<SemanticTrajectory, String>> entries = new ArrayList<>();
		Random y = new Random(trajectories.size());
		for (SemanticTrajectory traj : trajectories) {
			entries.add(new DataEntry<>(traj, y.nextBoolean() ? "chuva" : "sol"));
		}
		NearestNeighbour<SemanticTrajectory, String> nn = new NearestNeighbour<SemanticTrajectory, String>(entries, Math.min(trajectories.size(), 3),
				new EDRClassifier(new EDRSemanticParameter(Semantic.GEOGRAPHIC, 100.0), //
						new EDRSemanticParameter(Semantic.TEMPORAL, 30 * 60 * 1000L), //
						new EDRSemanticParameter(BikeDataReader.USER, null), //
						new EDRSemanticParameter(BikeDataReader.GENDER, null), //
						new EDRSemanticParameter(BikeDataReader.BIRTH_YEAR, null)));
		String classified = nn.classify(new DataEntry<>(trajectories.get(0), "descubra"));
		System.out.println(classified);
	}
}
