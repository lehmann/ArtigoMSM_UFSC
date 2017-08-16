package br.ufsc.lehmann.method;

import br.ufsc.core.IMeasureDistance;
import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.lehmann.NElementProblem;
import br.ufsc.lehmann.Thresholds;
import br.ufsc.lehmann.method.CVTI.CVTISemanticParameter;
import br.ufsc.lehmann.msm.artigo.Problem;
import br.ufsc.lehmann.msm.artigo.problems.DublinBusDataReader;
import br.ufsc.lehmann.msm.artigo.problems.DublinBusProblem;
import br.ufsc.lehmann.msm.artigo.problems.NewYorkBusDataReader;
import br.ufsc.lehmann.msm.artigo.problems.NewYorkBusProblem;
import br.ufsc.lehmann.msm.artigo.problems.PatelDataReader;
import br.ufsc.lehmann.msm.artigo.problems.PatelProblem;
import br.ufsc.lehmann.msm.artigo.problems.SanFranciscoCabDataReader;
import br.ufsc.lehmann.msm.artigo.problems.SanFranciscoCabProblem;
import br.ufsc.lehmann.msm.artigo.problems.SergipeTracksDataReader;
import br.ufsc.lehmann.msm.artigo.problems.SergipeTracksProblem;

public interface CVTITest {

	default IMeasureDistance<SemanticTrajectory> measurer(Problem problem) {
		if(problem instanceof NElementProblem) {
			return new CVTI(new CVTISemanticParameter(NElementProblem.dataSemantic, null));
		} else if(problem instanceof NewYorkBusProblem) {
			return new CVTI(new CVTISemanticParameter(NewYorkBusDataReader.STOP_SEMANTIC, Thresholds.STOP_CENTROID_LATLON));
		} else if(problem instanceof DublinBusProblem) {
			return new CVTI(new CVTISemanticParameter(DublinBusDataReader.STOP_SEMANTIC, Thresholds.STOP_CENTROID_LATLON));
		} else if(problem instanceof PatelProblem) {
			return new CVTI(new CVTISemanticParameter(PatelDataReader.STOP_SEMANTIC, Thresholds.GEOGRAPHIC_LATLON));
		} else if(problem instanceof SanFranciscoCabProblem) {
			return new CVTI(new CVTISemanticParameter(SanFranciscoCabDataReader.STOP_SEMANTIC, Thresholds.STOP_CENTROID_LATLON));
		} else if(problem instanceof SergipeTracksProblem) {
			return new CVTI(new CVTISemanticParameter(SergipeTracksDataReader.STOP_SEMANTIC, Thresholds.STOP_CENTROID_LATLON));
		}
		return null;
	}
}
