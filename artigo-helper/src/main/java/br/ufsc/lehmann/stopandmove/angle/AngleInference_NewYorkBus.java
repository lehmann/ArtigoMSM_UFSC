package br.ufsc.lehmann.stopandmove.angle;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.ufsc.core.trajectory.SemanticTrajectory;
import br.ufsc.core.trajectory.semantic.Move;
import br.ufsc.lehmann.msm.artigo.problems.NewYorkBusDataReader;
import br.ufsc.lehmann.msm.artigo.problems.NewYorkBusProblem;

public class AngleInference_NewYorkBus {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		NewYorkBusProblem problem = new NewYorkBusProblem("MTA NYCT_Q20A", "MTA NYCT_M102");
		String moveTable = "stops_moves.bus_nyc_20140927_move";
		List<SemanticTrajectory> trajs = problem.data();
		Map<Move, SemanticTrajectory> moves = new HashMap<>();
		for (SemanticTrajectory semanticTrajectory : trajs) {
			for (int j = 0; j < semanticTrajectory.length(); j++) {
				Move data = NewYorkBusDataReader.MOVE_ANGLE_SEMANTIC.getData(semanticTrajectory, j);
				if(data != null && !moves.containsKey(data)) {
					moves.put(data, semanticTrajectory);
				}
			}
		}
		AngleInference.extractMovementAngle(moveTable, moves);
	}
}
