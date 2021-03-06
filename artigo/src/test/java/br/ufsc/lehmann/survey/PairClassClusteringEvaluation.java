package br.ufsc.lehmann.survey;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.stream.Stream;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class PairClassClusteringEvaluation  extends AbstractPairClassClusteringEvaluation {

	public static void main(String[] args) throws JsonSyntaxException, JsonIOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException, IOException {
		Stream<java.nio.file.Path> files = java.nio.file.Files.walk(Paths.get("./src/test/resources/similarity-measures/hasl"));
		files.filter(path -> path.toFile().isFile() && path.toString().endsWith(".test")).forEach(path -> {
			String fileName = path.toString();
			System.out.printf("Executing file %s\n", fileName);
			PrintStream bkp = System.out;
			try {
				int i = 1;
				File out = new File(path.toFile().getParentFile(), path.getFileName().toString() + ".pair-clustering.out");
				while(out.exists()) {
					out = new File(path.toFile().getParentFile(), path.getFileName().toString() + i++ + ".pair-clustering.out");
				}
				System.setOut(new PrintStream(new FileOutputStream(out)));
				new PairClassClusteringEvaluation().executeDescriptor(fileName);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				System.setOut(bkp);
			}
		});
		files.close();
	}
}
