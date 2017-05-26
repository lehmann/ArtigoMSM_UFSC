package br.ufsc.lehmann.msm.artigo.problems;

import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;

public class BirthYearSemantic extends Semantic<String, Number>{

	public BirthYearSemantic(int index) {
		super(index);
	}

	@Override
	public boolean match(SemanticTrajectory a, int i, SemanticTrajectory b, int j, Number threshlod) {
		return a.getDimensionData(index, i).equals(b.getDimensionData(index, j));
	}

	@Override
	public Number distance(SemanticTrajectory a, int i, SemanticTrajectory b, int j) {
		return match(a, i, b, j, null) ? 0.0 : 1.0;
	}

}