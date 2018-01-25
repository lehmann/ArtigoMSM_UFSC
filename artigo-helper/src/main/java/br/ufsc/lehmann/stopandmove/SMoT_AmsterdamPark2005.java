package br.ufsc.lehmann.stopandmove;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.core.trajectory.semantic.Stop;
import br.ufsc.db.source.DataSource;
import br.ufsc.db.source.DataSourceType;
import br.ufsc.lehmann.msm.artigo.problems.AmsterdamPark2005DatabaseReader;
import br.ufsc.lehmann.msm.artigo.problems.AmsterdamPark2005Problem;

public class SMoT_AmsterdamPark2005 {

	private static DataSource source;

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		AmsterdamPark2005Problem problem = new AmsterdamPark2005Problem();
		List<SemanticTrajectory> trajs = problem.data();
		source = new DataSource("postgres", "postgres", "localhost", 5432, "postgis", DataSourceType.PGSQL, "amsterdan.amsterdan_stop", null, "geom");

		long start = System.currentTimeMillis();
		Connection conn = source.getRetriever().getConnection();

		ResultSet lastStop = conn.createStatement().executeQuery("select max(stop_id) from amsterdan.amsterdan_park_2005_stop");
		lastStop.next();
		AtomicInteger sid = new AtomicInteger(lastStop.getInt(1));
		ResultSet lastMove = conn.createStatement().executeQuery("select max(move_id) from amsterdan.amsterdan_park_2005_move");
		lastMove.next();
		AtomicInteger mid = new AtomicInteger(lastMove.getInt(1));
		PreparedStatement update = conn.prepareStatement("update amsterdan.amsterdan_park_2005 set semantic_stop_id = ?, semantic_move_id = ? where label = ? and gid in (SELECT * FROM unnest(?))");
		PreparedStatement insertStop = conn.prepareStatement("insert into amsterdan.amsterdan_park_2005_stop(stop_id, start_time, start_lat, start_lon, begin, end_time, end_lat, end_lon, length, centroid_lat, centroid_lon, \"POI\") values (?,?,?,?,?,?,?,?,?,?,?,?)");
		PreparedStatement insertMove = conn.prepareStatement("insert into amsterdan.amsterdan_park_2005_move(move_id, start_time, start_stop_id, begin, end_time, end_stop_id, length) values (?,?,?,?,?,?,?)");
		try {
			conn.setAutoCommit(false);
			FastSMoT<String, Number> fastSMoT = new FastSMoT<>(AmsterdamPark2005DatabaseReader.STOP_NAME, 2 * 60  * 1000);
			List<StopAndMove> bestSMoT = new ArrayList<>();
			for (SemanticTrajectory T : trajs) {
				bestSMoT.add(fastSMoT.findStops(T, sid, mid));
			}
			int stopCount = 0;
			for (StopAndMove stopAndMove : bestSMoT) {
				stopCount += stopAndMove.getStops().size();
			}
			System.out.println("Stops found: " + stopCount);
			StopAndMoveExtractor.persistStopAndMove(conn, update, insertStop, new StopAndMoveExtractor.StopPersisterCallback() {

				@Override
				public void parameterize(PreparedStatement statement, Stop stop) throws SQLException {
					statement.setString(12, String.valueOf(stop.getStopName()));
				}
				
			}, insertMove, bestSMoT);

		} finally {
			update.close();
			insertStop.close();
			insertMove.close();
			conn.close();
		}
		long end = System.currentTimeMillis();
		System.out.println("Time: " + (end - start));
	}

}
