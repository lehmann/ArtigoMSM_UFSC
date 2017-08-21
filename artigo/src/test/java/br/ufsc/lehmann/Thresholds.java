package br.ufsc.lehmann;

public interface Thresholds {

	public static final int GEOGRAPHIC_LATLON = 50;
	public static final int GEOGRAPHIC_EUCLIDEAN = 500;
	public static final int TEMPORAL = 100;
	public static final int STOP_CENTROID_LATLON = 300;
	public static final int STOP_CENTROID_EUCLIDEAN = 3000;
	public static final int STOP_STREET_NAME = Integer.MAX_VALUE;
	public static final int MOVE_ANGLE = 10;
	public static final int MOVE_DISTANCE = 150;
	public static final double MOVE_INNER_POINTS_PERC = .75;
}
