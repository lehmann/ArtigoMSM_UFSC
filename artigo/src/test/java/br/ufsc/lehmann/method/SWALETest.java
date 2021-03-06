package br.ufsc.lehmann.method;

import br.ufsc.core.IMeasureDistance;
import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.ftsm.related.LCSS;
import br.ufsc.lehmann.NElementProblem;
import br.ufsc.lehmann.Thresholds;
import br.ufsc.lehmann.msm.artigo.Problem;
import br.ufsc.lehmann.msm.artigo.classifiers.SWALEClassifier;
import br.ufsc.lehmann.msm.artigo.problems.DublinBusProblem;
import br.ufsc.lehmann.msm.artigo.problems.GeolifeProblem;
import br.ufsc.lehmann.msm.artigo.problems.HermoupolisProblem;
import br.ufsc.lehmann.msm.artigo.problems.NewYorkBusProblem;
import br.ufsc.lehmann.msm.artigo.problems.PatelProblem;
import br.ufsc.lehmann.msm.artigo.problems.PisaProblem;
import br.ufsc.lehmann.msm.artigo.problems.SanFranciscoCabProblem;
import br.ufsc.lehmann.msm.artigo.problems.SergipeTracksProblem;
import br.ufsc.lehmann.msm.artigo.problems.VehicleProblem;
import br.ufsc.lehmann.prototype.PrototypeProblem;

public interface SWALETest {

	default IMeasureDistance<SemanticTrajectory> measurer(Problem problem) {
		if(problem instanceof NElementProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(0.0, -10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, 0.0)));
		} else if(problem instanceof NewYorkBusProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_LATLON.doubleValue())));
		} else if(problem instanceof DublinBusProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_LATLON.doubleValue())));
		} else if(problem instanceof GeolifeProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_EUCLIDEAN.doubleValue())));
		} else if(problem instanceof PatelProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_EUCLIDEAN.doubleValue())));
		} else if(problem instanceof VehicleProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_EUCLIDEAN.doubleValue())));
		} else if(problem instanceof SanFranciscoCabProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_LATLON.doubleValue())));
		} else if(problem instanceof SergipeTracksProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_LATLON.doubleValue())));
		} else if(problem instanceof PrototypeProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_EUCLIDEAN.doubleValue())));
		} else if(problem instanceof PisaProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_LATLON.doubleValue())));
		} else if(problem instanceof HermoupolisProblem) {
			return new SWALEClassifier(new SWALE.SWALEParameters(-10, 10, new LCSS.LCSSSemanticParameter<>(Semantic.SPATIAL, Thresholds.STOP_CENTROID_LATLON.doubleValue())));
		}
		return null;
	}
}
