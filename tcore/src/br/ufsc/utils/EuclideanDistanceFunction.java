package br.ufsc.utils;

import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.core.trajectory.SpatialDistanceFunction;
import br.ufsc.core.trajectory.TPoint;
import br.ufsc.core.trajectory.ThreeDimensionalPoint;

public class EuclideanDistanceFunction implements SpatialDistanceFunction {

	@Override
	public double distance(TPoint p, TPoint d) {
		if(p instanceof ThreeDimensionalPoint && d instanceof ThreeDimensionalPoint) {
			return Distance.euclidean3D((ThreeDimensionalPoint) p, (ThreeDimensionalPoint) d);
		}
		return Distance.euclidean(p, d);
	}
	
	@Override
	public double length(SemanticTrajectory trajectory) {
		double ret = 0;
		for (int i = 0; i < trajectory.length() - 2; i++) {
			ret += Semantic.SPATIAL.distance(trajectory, i, trajectory, i + 1).doubleValue();
		}
		return ret;
	}
	
	@Override
	public double convert(double units) {
		return units;
	}
	
	@Override
	public double maxDistance() {
		return Double.MAX_VALUE;
	}

	@Override
	public TPoint[] convertToMercator(TPoint[] p) {
		return p;
	}
}
