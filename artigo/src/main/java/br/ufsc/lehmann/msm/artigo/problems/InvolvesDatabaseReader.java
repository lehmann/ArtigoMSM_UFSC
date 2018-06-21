package br.ufsc.lehmann.msm.artigo.problems;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import br.ufsc.core.trajectory.EqualsDistanceFunction;
import br.ufsc.core.trajectory.Semantic;
import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.core.trajectory.StopSemantic;
import br.ufsc.core.trajectory.TPoint;
import br.ufsc.core.trajectory.TemporalDuration;
import br.ufsc.core.trajectory.semantic.AttributeDescriptor;
import br.ufsc.core.trajectory.semantic.AttributeType;
import br.ufsc.core.trajectory.semantic.Move;
import br.ufsc.core.trajectory.semantic.Stop;
import br.ufsc.core.trajectory.semantic.StopMove;
import br.ufsc.db.source.DataRetriever;
import br.ufsc.db.source.DataSource;
import br.ufsc.db.source.DataSourceType;
import br.ufsc.lehmann.AngleDistance;
import br.ufsc.lehmann.DTWDistance;
import br.ufsc.lehmann.EllipsesDistance;
import br.ufsc.lehmann.MoveSemantic;
import br.ufsc.lehmann.NumberDistance;
import br.ufsc.lehmann.msm.artigo.StopMoveSemantic;
import br.ufsc.utils.Angle;
import br.ufsc.utils.Distance;
import br.ufsc.utils.LatLongDistanceFunction;

public class InvolvesDatabaseReader {
	
	private static final LatLongDistanceFunction DISTANCE_FUNCTION = new LatLongDistanceFunction();
	public static final BasicSemantic<Integer> DIMENSAO_DATA = new BasicSemantic<>(3);
	public static final BasicSemantic<Integer> USER_ID = new BasicSemantic<>(4);
	public static final StopSemantic STOP_CENTROID_SEMANTIC = new StopSemantic(5, new AttributeDescriptor<Stop, TPoint>(AttributeType.STOP_CENTROID, DISTANCE_FUNCTION));
	public static final StopSemantic STOP_STREET_NAME_SEMANTIC = new StopSemantic(5, new AttributeDescriptor<Stop, String>(AttributeType.STOP_STREET_NAME, new EqualsDistanceFunction<String>()));
	
	public static final MoveSemantic MOVE_ANGLE_SEMANTIC = new MoveSemantic(6, new AttributeDescriptor<Move, Double>(AttributeType.MOVE_ANGLE, new AngleDistance()));
	public static final MoveSemantic MOVE_DISTANCE_SEMANTIC = new MoveSemantic(6, new AttributeDescriptor<Move, Double>(AttributeType.MOVE_TRAVELLED_DISTANCE, new NumberDistance()));
	public static final MoveSemantic MOVE_POINTS_SEMANTIC = new MoveSemantic(6, new AttributeDescriptor<Move, TPoint[]>(AttributeType.MOVE_POINTS, new DTWDistance(DISTANCE_FUNCTION)));
	public static final MoveSemantic MOVE_ELLIPSES_SEMANTIC = new MoveSemantic(6, new AttributeDescriptor<Move, TPoint[]>(AttributeType.MOVE_POINTS, new EllipsesDistance(DISTANCE_FUNCTION)));

	public static final BasicSemantic<String> TRAJECTORY_IDENTIFIER = new BasicSemantic<String>(7) {
		@Override
		public String getData(SemanticTrajectory p, int i) {
			return USER_ID.getData(p, i) + "/" + DIMENSAO_DATA.getData(p, i);
		}
	};
	private boolean onlyStops;

	public InvolvesDatabaseReader(boolean onlyStops) {
		this.onlyStops = onlyStops;
	}

	public List<SemanticTrajectory> read(Integer... users) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		DataSource source = new DataSource("postgres", "postgres", "localhost", 5432, "postgis", DataSourceType.PGSQL, "public.amsterdan_park_cbsmot", null, null);
		DataRetriever retriever = source.getRetriever();
		System.out.println("Executing SQL...");
		Connection conn = retriever.getConnection();
		conn.setAutoCommit(false);
		Statement st = conn.createStatement();
		st.setFetchSize(1000);

		Map<Integer, Stop> stops = new HashMap<>();
		ResultSet stopsData = st.executeQuery(
				"SELECT id, start_timestamp, end_timestamp, longitude, latitude, \"closest_PDV\", \"PDV_distance\", is_home, id_colaborador_unidade, \"closest_colab_PDV\", \"colab_PDV_distance\"\r\n" + 
				"	FROM involves.\"stops_FastCBSMoT\";");
		while (stopsData.next()) {
			int stopId = stopsData.getInt("id");
			Stop stop = stops.get(stopId);
			if (stop == null) {
				stop = new Stop(stopId, null, //
						stopsData.getTimestamp("start_timestamp").getTime(), //
						stopsData.getTimestamp("end_timestamp").getTime(), //
						null, //
						-1, //
						null, //
						-1, //
						new TPoint(stopsData.getDouble("latitude"), stopsData.getDouble("longitude")), //
						null, //
						null//
				);
				stops.put(stopId, stop);
			}
		}
		Map<Integer, Move> moves = new HashMap<>();
		ResultSet movesData = st
				.executeQuery("SELECT id, id_colaborador_unidade, start_timestamp, end_timestamp\r\n" + 
						"	FROM involves.\"moves_FastCBSMoT\"");
		while (movesData.next()) {
			int moveId = movesData.getInt("id");
			Move move = moves.get(moveId);
			if (move == null) {
				move = new Move(moveId, //
						null, //
						null, //
						movesData.getTimestamp("start_timestamp").getTime(), //
						movesData.getTimestamp("end_timestamp").getTime(), //
						-1, //
						-1, //
						null);
				move.setAttribute(AttributeType.MOVE_USER, movesData.getInt("id_colaborador_unidade"));
				moves.put(moveId, move);
				
			}
		}

		String sql = "select (to_number(_id, 'X999')::text || id_dado_gps::text) as id_dado_gps, "//
				+ "id_usuario, id_dimensao_data, dt_coordenada, "//
				+ "st_x(st_transform(st_setsrid(st_makepoint(longitude, latitude), 4326), 900913)) as lat, "//
				+ "st_y(st_transform(st_setsrid(st_makepoint(longitude, latitude), 4326), 900913)) as lon, "
				+ "case when map.is_stop then map.semantic_id else null end as semantic_stop_id, "//
				+ "case when map.is_move then map.semantic_id else null end as semantic_move_id "//
				+ "from involves.dados_colaboradores gps ";//
		sql += "left join involves.\"stops_moves_FastCBSMoT\" map on (to_number(gps._id, 'X999')::text || gps.id_dado_gps::text)::bigint = map.gps_point_id ";
		sql += "where provedor = 'gps' ";//
		sql += "order by id_usuario, id_dimensao_data, dt_coordenada, _id";
		PreparedStatement preparedStatement = conn.prepareStatement(sql);
		ResultSet data = preparedStatement.executeQuery();
		Multimap<String, InvolvesRecord> records = MultimapBuilder.hashKeys().linkedListValues().build();
		System.out.println("Fetching...");
		while(data.next()) {
			InvolvesRecord record = new InvolvesRecord(
					data.getLong("id_dado_gps"),
					data.getInt("id_usuario"),
					data.getInt("id_dimensao_data"),
					data.getTimestamp("dt_coordenada"),
				data.getDouble("lat"),
				data.getDouble("lon"),
				data.getInt("semantic_stop_id"),
				data.getInt("semantic_move_id")
			);
			records.put(record.getId_usuario() + "/" + record.getId_dimensao_data(), record);
		}
		preparedStatement.close();
		
		st.close();
		System.out.printf("Loaded %d GPS points from database\n", records.size());
		System.out.printf("Loaded %d trajectories from database\n", records.keySet().size());

		List<Move> allMoves = new ArrayList<>(moves.values());
		List<SemanticTrajectory> ret = null;
		if(onlyStops) {
			ret = readStopsTrajectories(stops, moves, records);
		} else {
			ret = readRawPoints(stops, moves, records);
		}
		compute(CollectionUtils.removeAll(allMoves, moves.values()));
		return ret;
	}

	private List<SemanticTrajectory> readStopsTrajectories(Map<Integer, Stop> stops, Map<Integer, Move> moves, Multimap<String, InvolvesRecord> records) {
		List<SemanticTrajectory> ret = new ArrayList<>();
		Set<String> keys = records.keySet();
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for (String trajId : keys) {
			SemanticTrajectory s = new SemanticTrajectory(trajId, 7);
			Collection<InvolvesRecord> collection = records.get(trajId);
			int i = 0;
			for (InvolvesRecord record : collection) {
				TPoint point = new TPoint(record.getLat(), record.getLon());
				if(record.getSemanticStopId() != null) {
					Stop stop = stops.remove(record.getSemanticStopId());
					if(stop == null) {
						continue;
					}
					if(i > 0) {
						Stop previousStop = STOP_CENTROID_SEMANTIC.getData(s, i - 1);
						if(previousStop != null) {
							Move move = new Move(-1, previousStop, stop, previousStop.getEndTime(), stop.getStartTime(), stop.getBegin() - 1, 0, new TPoint[0], 
									-1,
//									Angle.getAngle(previousStop.getEndPoint(), stop.getStartPoint()),
									Distance.getDistance(new TPoint[] {previousStop.getCentroid(), stop.getCentroid()}, DISTANCE_FUNCTION));
							s.addData(i, MOVE_ANGLE_SEMANTIC, move);
							//injecting a move between two consecutives stops
							stops.put(record.getSemanticStopId(), stop);
						} else {
							s.addData(i, STOP_CENTROID_SEMANTIC, stop);
						}
					} else {
						s.addData(i, STOP_CENTROID_SEMANTIC, stop);
					}
				} else if(record.getSemanticMoveId() != null) {
					Move move = moves.remove(record.getSemanticMoveId());
					if(move == null) {
						for (int j = i - 1; j > -1; j--) {
							move = MOVE_ANGLE_SEMANTIC.getData(s, j);
							if(move != null) {
								break;
							}
						}
						if(move != null) {
							TPoint[] points = (TPoint[]) move.getAttribute(AttributeType.MOVE_POINTS);
							List<TPoint> a = new ArrayList<TPoint>(Arrays.asList(points));
							a.add(point);
							move.setAttribute(AttributeType.MOVE_POINTS, a.toArray(new TPoint[a.size()]));
							continue;
						} else {
							throw new RuntimeException("Move does not found");
						}
					}
					TPoint[] points = (TPoint[]) move.getAttribute(AttributeType.MOVE_POINTS);
					List<TPoint> a = new ArrayList<TPoint>(points == null ? Collections.emptyList() : Arrays.asList(points));
					a.add(point);
					move.setAttribute(AttributeType.MOVE_POINTS, a.toArray(new TPoint[a.size()]));
					s.addData(i, MOVE_ANGLE_SEMANTIC, move);
				}
				s.addData(i, Semantic.GID, record.getId());
				s.addData(i, Semantic.SPATIAL, point);
				s.addData(i, Semantic.TEMPORAL, new TemporalDuration(Instant.ofEpochMilli(record.getDt_coordenada().getTime()), Instant.ofEpochMilli(record.getDt_coordenada().getTime())));
				s.addData(i, USER_ID, record.getId_usuario());
				s.addData(i, DIMENSAO_DATA, record.getId_dimensao_data());
				i++;
			}
			stats.addValue(s.length());
			ret.add(s);
		}
		System.out.printf("Semantic Trajectories statistics: mean - %.2f, min - %.2f, max - %.2f, sd - %.2f\n", stats.getMean(), stats.getMin(), stats.getMax(), stats.getStandardDeviation());
		return ret;
	}

	private List<SemanticTrajectory> readRawPoints(Map<Integer, Stop> stops, Map<Integer, Move> moves, Multimap<String, InvolvesRecord> records) {
		List<SemanticTrajectory> ret = new ArrayList<>();
		Set<String> keys = records.keySet();
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for (String trajId : keys) {
			SemanticTrajectory s = new SemanticTrajectory(trajId, 7);
			Collection<InvolvesRecord> collection = records.get(trajId);
			int i = 0;
			for (InvolvesRecord record : collection) {
				s.addData(i, Semantic.GID, record.getId());
				TPoint point = new TPoint(record.getId(), record.getLat(), record.getLon(), record.getDt_coordenada());
				s.addData(i, Semantic.SPATIAL, point);
				s.addData(i, Semantic.TEMPORAL, new TemporalDuration(Instant.ofEpochMilli(record.getDt_coordenada().getTime()), Instant.ofEpochMilli(record.getDt_coordenada().getTime())));
				s.addData(i, USER_ID, record.getId_usuario());
				s.addData(i, DIMENSAO_DATA, record.getId_dimensao_data());
				if(record.getSemanticStopId() != null) {
					Stop stop = stops.get(record.getSemanticStopId());
					s.addData(i, STOP_CENTROID_SEMANTIC, stop);
				}
				if(record.getSemanticMoveId() != null) {
					Move move = moves.get(record.getSemanticMoveId());
					TPoint[] points = (TPoint[]) move.getAttribute(AttributeType.MOVE_POINTS);
					List<TPoint> a = new ArrayList<TPoint>(points == null ? Collections.emptyList() : Arrays.asList(points));
					a.add(point);
					move.setAttribute(AttributeType.MOVE_POINTS, a.toArray(new TPoint[a.size()]));
					s.addData(i, MOVE_ANGLE_SEMANTIC, move);
				}
				i++;
			}
			stats.addValue(s.length());
			ret.add(s);
		}
		System.out.printf("Semantic Trajectories statistics: mean - %.2f, min - %.2f, max - %.2f, sd - %.2f\n", stats.getMean(), stats.getMin(), stats.getMax(), stats.getStandardDeviation());
		return ret;
	}

	private void compute(Collection<Move> moves) {
		for (Move move : moves) {
			List<TPoint> points = new ArrayList<>();
			if(move.getStart() != null) {
				points.add(move.getStart().getEndPoint());
			}
			if(move.getPoints() != null) {
				points.addAll(Arrays.asList(move.getPoints()));
			}
			if(move.getEnd() != null) {
				points.add(move.getEnd().getStartPoint());
			}
			move.setAttribute(AttributeType.MOVE_ANGLE, Angle.getAngle(points.get(0), points.get(points.size() - 1)));
			move.setAttribute(AttributeType.MOVE_TRAVELLED_DISTANCE, Distance.getDistance(points.toArray(new TPoint[points.size()]), DISTANCE_FUNCTION));
		}
	}
}